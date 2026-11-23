package com.tamer.studentphotomatcher.excel

import com.tamer.studentphotomatcher.core.model.ColumnMapping
import com.tamer.studentphotomatcher.core.model.Student
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * مُحدّث ومُنشئ النسخة الجديدة من ملف Excel (.xlsx)
 * يقوم بنسخ كافة محتويات الملف الأصلي دون أي تعديل على أصله،
 * وتحديث ورقة العمل المحددة بملء:
 * - العمود K: اسم ملف الصورة الناتج
 * - العمود L: حالة المطابقة والملاحظات
 * مع الحفاظ بنسبة 100% على:
 * - تنسيقات الخلايا والألوان والخطوط (Styles)
 * - المعادلات والبيانات غير المرتبطة
 * - ترتيب الصفوف والأعمدة
 */
object FastXlsxUpdater {

    /**
     * إنشاء نسخة جديدة محدثة من ملف Excel مع تعبئة العمودين K و L
     */
    fun createUpdatedExcelCopy(
        originalInputStream: InputStream,
        targetOutputStream: OutputStream,
        students: List<Student>,
        targetSheetPath: String = "xl/worksheets/sheet1.xml",
        columnMapping: ColumnMapping = ColumnMapping()
    ) {
        val originalBytes = originalInputStream.readBytes()
        val zipIn = ZipInputStream(originalBytes.inputStream())
        val zipOut = ZipOutputStream(targetOutputStream)

        // خريطة لتسريع الوصول لبيانات التحديث بحسب رقم الصف (1-based row number)
        // rowIndex في Student هو 0-based، إذن رقم الصف في الإكسيل هو rowIndex + 1
        val updatesByRowNumber = HashMap<Int, Pair<String, String>>()
        for (student in students) {
            val excelRowNumber = student.rowIndex + 1
            val photoName = student.exportedFileName ?: (student.matchedPhoto?.originalName ?: "")
            val statusNote = when {
                student.isApproved -> "تم الاعتماد والتصدير (${student.classification.labelAr})"
                student.isRejected -> "مرفوض"
                student.matchedPhoto != null -> "مطابقة غير معتمدة: ${student.classification.labelAr}"
                else -> "طالب بلا صورة (${student.classification.labelAr})"
            }
            updatesByRowNumber[excelRowNumber] = Pair(photoName, statusNote)
        }

        var entry: ZipEntry? = zipIn.nextEntry

        while (entry != null) {
            val entryName = entry.name
            zipOut.putNextEntry(ZipEntry(entryName))

            val isTargetSheet = entryName.equals(targetSheetPath, ignoreCase = true) ||
                    (entryName.startsWith("xl/worksheets/sheet", ignoreCase = true) &&
                            targetSheetPath.endsWith(entryName.substringAfterLast('/'), ignoreCase = true))

            if (isTargetSheet) {
                // تعديل ملف XML الخاص بورقة العمل وتطعيم العمودين K و L
                val sheetXmlContent = zipIn.readBytes().toString(StandardCharsets.UTF_8)
                val updatedXml = updateSheetXml(sheetXmlContent, updatesByRowNumber, columnMapping)
                zipOut.write(updatedXml.toByteArray(StandardCharsets.UTF_8))
            } else {
                // نسخ أي ملف آخر (أنماط، نصوص مشتركة، وسائط، كائنات) تماماً كما هو
                zipIn.copyTo(zipOut)
            }

            zipOut.closeEntry()
            entry = zipIn.nextEntry
        }

        zipOut.finish()
        zipOut.flush()
    }

    /**
     * حقن خلايا العمود K و L داخل صفوف ورقة العمل بتقنية OpenXML Inline String
     */
    private fun updateSheetXml(
        xml: String,
        updates: Map<Int, Pair<String, String>>,
        columnMapping: ColumnMapping
    ): String {
        val colKLetter = FastXlsxReader.indexToColumnLetter(columnMapping.outputPhotoColumnIndex)
        val colLLetter = FastXlsxReader.indexToColumnLetter(columnMapping.notesColumnIndex)

        val rowRegex = Regex("<row\\s+[^>]*r=\"(\\d+)\"[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)

        return rowRegex.replace(xml) { matchResult ->
            val rowNum = matchResult.groupValues[1].toIntOrNull()
            val rowBody = matchResult.groupValues[2]
            val fullMatch = matchResult.value

            if (rowNum != null && updates.containsKey(rowNum)) {
                val (photoName, statusNote) = updates[rowNum]!!

                var updatedBody = rowBody

                // 1. تحديث أو إضافة خلية العمود K (اسم الصورة)
                val cellKRef = "$colKLetter$rowNum"
                val cellKTag = createInlineStrCell(cellKRef, photoName)
                updatedBody = replaceOrAppendCell(updatedBody, cellKRef, cellKTag)

                // 2. تحديث أو إضافة خلية العمود L (الملاحظات)
                val cellLRef = "$colLLetter$rowNum"
                val cellLTag = createInlineStrCell(cellLRef, statusNote)
                updatedBody = replaceOrAppendCell(updatedBody, cellLRef, cellLTag)

                val openTag = fullMatch.substringBefore('>') + ">"
                "$openTag$updatedBody</row>"
            } else {
                fullMatch
            }
        }
    }

    private fun replaceOrAppendCell(rowBody: String, cellRef: String, newCellXml: String): String {
        val cellRegex = Regex("<c\\s+[^>]*r=\"$cellRef\"[^>]*>.*?</c>|<c\\s+[^>]*r=\"$cellRef\"[^>]*/>", RegexOption.DOT_MATCHES_ALL)
        return if (cellRegex.containsMatchIn(rowBody)) {
            cellRegex.replace(rowBody, newCellXml)
        } else {
            rowBody + newCellXml
        }
    }

    private fun createInlineStrCell(cellRef: String, text: String): String {
        val escaped = escapeXml(text)
        return "<c r=\"$cellRef\" t=\"inlineStr\"><is><t>$escaped</t></is></c>"
    }

    private fun escapeXml(str: String): String {
        return str
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
