package com.tamer.studentphotomatcher.excel

import com.tamer.studentphotomatcher.core.model.ColumnMapping
import com.tamer.studentphotomatcher.core.model.Student
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * قارئ فائق السرعة والدقة لملفات Microsoft Excel (.xlsx)
 * متوافق بشكل صارم مع مواصفات شيتات وزارة التربية والتعليم والمدارس:
 * - ورقة العمل: "بيانات الطلاب" (مع دعم أي أوراق عمل أخرى)
 * - صف العناوين: الصف الرابع (Index 3)
 * - بداية بيانات الطلاب: الصف الخامس (Index 4)
 * - الأعمدة الأساسية:
 *   A: م / المسلسل
 *   B: كود التلميذ
 *   C: الرقم القومي (يتم حفظه كـ String للحفاظ على الأصفار في البداية)
 *   D: اسم التلميذ
 *   K: اسم ملف الصورة الناتج
 *   L: ملاحظات
 */
object FastXlsxReader {

    data class SheetInfo(val id: String, val name: String, val sheetPath: String)

    data class ParsedWorkbookResult(
        val sheets: List<SheetInfo>,
        val selectedSheetName: String,
        val headers: List<String>,
        val rawRows: List<List<String>>,
        val columnMapping: ColumnMapping,
        val students: List<Student>
    )

    /**
     * قراءة وفحص شيت الإكسيل واستخراج بيانات الطلاب بدقة
     */
    fun readWorkbook(inputStream: InputStream, requestedSheetName: String? = null): ParsedWorkbookResult {
        val zipBytes = inputStream.readBytes()

        // 1. استخراج جدول النصوص المشتركة
        val sharedStrings = extractSharedStrings(zipBytes)

        // 2. استخراج قائمة أسماء أوراق العمل
        val sheets = extractSheetList(zipBytes)
        if (sheets.isEmpty()) {
            throw IllegalArgumentException("ملف الإكسيل لا يحتوي على أي ورقة عمل صالحة")
        }

        // اختيار ورقة "بيانات الطلاب" تلقائياً إن وجدت، أو المطلوبة، أو الأولى
        val selectedSheet = when {
            requestedSheetName != null -> sheets.find { it.name.equals(requestedSheetName, ignoreCase = true) } ?: sheets.first()
            sheets.any { it.name.trim().contains("بيانات الطلاب") } -> sheets.first { it.name.trim().contains("بيانات الطلاب") }
            sheets.any { it.name.trim().contains("الطلاب") || it.name.trim().contains("students", ignoreCase = true) } ->
                sheets.first { it.name.trim().contains("الطلاب") || it.name.trim().contains("students", ignoreCase = true) }
            else -> sheets.first()
        }

        // 3. قراءة جميع صفوف الورقة
        val allRows = parseSheetXmlRows(zipBytes, selectedSheet.sheetPath, sharedStrings)

        // 4. تحديد صف العناوين (الصف الرابع افتراضياً Index 3 إن وُجد، أو كشف ذكي)
        val headerRowIndex = determineHeaderRowIndex(allRows)
        val headers = if (headerRowIndex in allRows.indices) allRows[headerRowIndex] else emptyList()

        // 5. اكتشاف الأعمدة (كود التلميذ، الرقم القومي، اسم التلميذ، المسلسل...)
        val columnMapping = ExcelColumnDetector.autoDetectColumns(headers).copy(
            headerRowIndex = headerRowIndex,
            dataStartRowIndex = headerRowIndex + 1
        )

        // 6. استخراج الطلاب بدءاً من صف البيانات (الصف الخامس Index 4)
        val students = ArrayList<Student>()
        val startRow = columnMapping.dataStartRowIndex

        for (r in startRow until allRows.size) {
            val row = allRows[r]
            if (row.all { it.isBlank() }) continue

            val serial = getCellSafe(row, columnMapping.serialColumnIndex)
            val code = cleanDigitsString(getCellSafe(row, columnMapping.codeColumnIndex))
            // الرقم القومي: الحفاظ عليه كسلسلة نصية كاملة لعدم فقدان الأصفار البادئة
            val nationalId = cleanDigitsString(getCellSafe(row, columnMapping.nationalIdColumnIndex))
            val name = getCellSafe(row, columnMapping.nameColumnIndex).trim()

            // نتأكد أن السطر يحتوي على اسم أو كود أو رقم قومي
            if (name.isNotBlank() || code.isNotBlank() || nationalId.isNotBlank()) {
                val existingPhotoName = getCellSafe(row, columnMapping.outputPhotoColumnIndex)
                val existingNotes = getCellSafe(row, columnMapping.notesColumnIndex)

                students.add(
                    Student(
                        serialNo = serial,
                        code = code,
                        nationalId = nationalId,
                        name = name,
                        rowIndex = r,
                        exportedFileName = existingPhotoName.ifBlank { null },
                        notes = existingNotes.ifBlank { null }
                    )
                )
            }
        }

        return ParsedWorkbookResult(
            sheets = sheets,
            selectedSheetName = selectedSheet.name,
            headers = headers,
            rawRows = allRows,
            columnMapping = columnMapping,
            students = students
        )
    }

    private fun getCellSafe(row: List<String>, index: Int): String {
        return if (index in row.indices) row[index].trim() else ""
    }

    private fun cleanDigitsString(value: String): String {
        var str = value.trim()
        // في حال كان الرقم بصيغة علمية مثل 2.9901E+13 أو به فاصلة عشرية .0
        if (str.endsWith(".0")) {
            str = str.substringBeforeLast(".0")
        }
        return str
    }

    /**
     * تحديد صف العناوين: الصف الرابع (Index 3) كافتراضي أساسي، مع التحقق الذكي
     */
    private fun determineHeaderRowIndex(allRows: List<List<String>>): Int {
        if (allRows.size > 3) {
            val row4 = allRows[3]
            // فحص هل الصف الرابع يحتوي على كلمات عناوين نموذجية
            if (row4.any { text ->
                    text.contains("اسم") || text.contains("كود") || text.contains("قومي") || text.contains("مسلسل") || text == "م"
                }) {
                return 3
            }
        }

        // فحص أي من الصفوف الأربعة الأولى يحتوي على العناوين
        for (i in 0 until minOf(6, allRows.size)) {
            val r = allRows[i]
            if (r.any { it.contains("اسم") || it.contains("كود") || it.contains("قومي") }) {
                return i
            }
        }

        return if (allRows.size > 3) 3 else 0
    }

    private fun extractSharedStrings(zipBytes: ByteArray): List<String> {
        val strings = ArrayList<String>()
        val zip = ZipInputStream(zipBytes.inputStream())
        var entry = zip.nextEntry
        var found = false

        while (entry != null) {
            if (entry.name.equals("xl/sharedStrings.xml", ignoreCase = true)) {
                found = true
                break
            }
            entry = zip.nextEntry
        }

        if (!found) return emptyList()

        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(zip, "UTF-8")

        var eventType = parser.eventType
        var currentText = StringBuilder()
        var insideSi = false
        var insideT = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name.equals("si", ignoreCase = true)) {
                        insideSi = true
                        currentText = StringBuilder()
                    } else if (insideSi && name.equals("t", ignoreCase = true)) {
                        insideT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideSi && insideT) {
                        currentText.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name.equals("t", ignoreCase = true)) {
                        insideT = false
                    } else if (name.equals("si", ignoreCase = true)) {
                        insideSi = false
                        strings.add(currentText.toString())
                    }
                }
            }
            eventType = parser.next()
        }

        return strings
    }

    private fun extractSheetList(zipBytes: ByteArray): List<SheetInfo> {
        val sheets = ArrayList<SheetInfo>()
        val zip = ZipInputStream(zipBytes.inputStream())
        var entry = zip.nextEntry
        var found = false

        while (entry != null) {
            if (entry.name.equals("xl/workbook.xml", ignoreCase = true)) {
                found = true
                break
            }
            entry = zip.nextEntry
        }

        if (!found) {
            return listOf(SheetInfo("1", "بيانات الطلاب", "xl/worksheets/sheet1.xml"))
        }

        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(zip, "UTF-8")

        var eventType = parser.eventType
        var index = 1

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name.equals("sheet", ignoreCase = true)) {
                val name = parser.getAttributeValue(null, "name") ?: "Sheet$index"
                val sheetId = parser.getAttributeValue(null, "sheetId") ?: index.toString()
                val path = "xl/worksheets/sheet$index.xml"
                sheets.add(SheetInfo(sheetId, name, path))
                index++
            }
            eventType = parser.next()
        }

        return if (sheets.isEmpty()) {
            listOf(SheetInfo("1", "بيانات الطلاب", "xl/worksheets/sheet1.xml"))
        } else {
            sheets
        }
    }

    private fun parseSheetXmlRows(
        zipBytes: ByteArray,
        sheetPath: String,
        sharedStrings: List<String>
    ): List<List<String>> {
        val zip = ZipInputStream(zipBytes.inputStream())
        var entry = zip.nextEntry
        var targetEntryFound = false

        while (entry != null) {
            if (entry.name.equals(sheetPath, ignoreCase = true) ||
                entry.name.endsWith(sheetPath.substringAfterLast('/'), ignoreCase = true)
            ) {
                targetEntryFound = true
                break
            }
            entry = zip.nextEntry
        }

        if (!targetEntryFound) {
            // الرجوع لأول ورقة عمل
            val fallbackZip = ZipInputStream(zipBytes.inputStream())
            var fb = fallbackZip.nextEntry
            while (fb != null) {
                if (fb.name.contains("xl/worksheets/sheet", ignoreCase = true)) {
                    return parseSheetRowsStream(fallbackZip, sharedStrings)
                }
                fb = fallbackZip.nextEntry
            }
            return emptyList()
        }

        return parseSheetRowsStream(zip, sharedStrings)
    }

    private fun parseSheetRowsStream(stream: InputStream, sharedStrings: List<String>): List<List<String>> {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(stream, "UTF-8")

        val allRows = ArrayList<List<String>>()
        var currentRowMap = HashMap<Int, String>()
        var currentCellRef = ""
        var currentCellType = ""
        var cellText = StringBuilder()
        var insideV = false
        var insideT = false
        var maxCol = 0

        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when {
                        tag.equals("row", ignoreCase = true) -> {
                            currentRowMap = HashMap()
                        }
                        tag.equals("c", ignoreCase = true) -> {
                            currentCellRef = parser.getAttributeValue(null, "r") ?: ""
                            currentCellType = parser.getAttributeValue(null, "t") ?: ""
                            cellText = StringBuilder()
                        }
                        tag.equals("v", ignoreCase = true) -> insideV = true
                        tag.equals("t", ignoreCase = true) -> insideT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideV || insideT) {
                        cellText.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when {
                        tag.equals("v", ignoreCase = true) -> insideV = false
                        tag.equals("t", ignoreCase = true) -> insideT = false
                        tag.equals("c", ignoreCase = true) -> {
                            val colIdx = columnLetterToIndex(currentCellRef)
                            val raw = cellText.toString().trim()
                            val value = if (currentCellType.equals("s", ignoreCase = true)) {
                                val sIdx = raw.toIntOrNull()
                                if (sIdx != null && sIdx in sharedStrings.indices) {
                                    sharedStrings[sIdx]
                                } else raw
                            } else raw

                            if (colIdx >= 0) {
                                currentRowMap[colIdx] = value
                                if (colIdx > maxCol) maxCol = colIdx
                            }
                        }
                        tag.equals("row", ignoreCase = true) -> {
                            val rowList = ArrayList<String>()
                            for (c in 0..maxCol) {
                                rowList.add(currentRowMap[c] ?: "")
                            }
                            allRows.add(rowList)
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return allRows
    }

    fun columnLetterToIndex(cellRef: String): Int {
        if (cellRef.isBlank()) return -1
        var letters = ""
        for (ch in cellRef) {
            if (ch.isLetter()) letters += ch.uppercaseChar() else break
        }
        if (letters.isEmpty()) return -1

        var index = 0
        for (ch in letters) {
            index = index * 26 + (ch - 'A' + 1)
        }
        return index - 1
    }

    fun indexToColumnLetter(index: Int): String {
        var num = index + 1
        var result = ""
        while (num > 0) {
            val rem = (num - 1) % 26
            result = (('A'.code + rem).toChar()) + result
            num = (num - 1) / 26
        }
        return result
    }
}
