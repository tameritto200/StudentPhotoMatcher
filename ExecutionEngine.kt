package com.tamer.studentphotomatcher.executor

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.tamer.studentphotomatcher.core.model.ColumnMapping
import com.tamer.studentphotomatcher.core.model.ExportReportRow
import com.tamer.studentphotomatcher.core.model.ExportSummaryReport
import com.tamer.studentphotomatcher.core.model.RenamingConfig
import com.tamer.studentphotomatcher.core.model.Student
import com.tamer.studentphotomatcher.excel.FastXlsxUpdater
import com.tamer.studentphotomatcher.organizer.PhotoRenamingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * تقدم عملية التصدير
 */
data class ExportProgress(
    val current: Int,
    val total: Int,
    val currentItemName: String,
    val isComplete: Boolean = false,
    val errorMessage: String? = null
)

/**
 * محرك التصدير الآمن (Non-Destructive Safe Export Engine)
 * القواعد الصارمة:
 * 1. نسخ الصور المعتمدة فقط دون تعديل أو نقل أو حذف أي ملف أصلي.
 * 2. منع الكتابة فوق الملفات وتجنب تكرار الأسماء.
 * 3. إنشاء نسخة جديدة من ملف Excel مع تعبئة العمود K و L دون تعديل الملف الأصلي إطلاقاً.
 * 4. إنشاء تقرير تدقيق شامل بصيغة CSV.
 */
class ExecutionEngine(private val context: Context) {

    /**
     * تنفيذ التصدير الكامل
     */
    suspend fun executeExport(
        students: List<Student>,
        renamingConfig: RenamingConfig,
        outputFolderUri: Uri,
        originalExcelUri: Uri,
        columnMapping: ColumnMapping = ColumnMapping(),
        onProgress: (ExportProgress) -> Unit
    ): ExportSummaryReport = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        val outputFolderDoc = DocumentFile.fromTreeUri(context, outputFolderUri)
            ?: throw IllegalArgumentException("تعذر الوصول إلى مجلد الإخراج المحدد")

        // فلترة الطلاب أصحاب الصور المعتمدة فقط
        val approvedStudents = students.filter { it.isApproved && it.matchedPhoto != null && !it.isRejected }
        val totalToExport = approvedStudents.size

        val existingNamesCount = HashMap<String, Int>()
        val reportRows = ArrayList<ExportReportRow>(students.size)
        val updatedStudentsWithExportedNames = ArrayList<Student>(students.size)

        var exportedCount = 0

        // 1. نسخ الصور المعتمدة فقط بالأسماء المنظمة
        for (student in students) {
            val photo = student.matchedPhoto

            if (student.isApproved && photo != null && !student.isRejected) {
                val newFileName = PhotoRenamingEngine.generateFileName(student, photo, renamingConfig, existingNamesCount)

                onProgress(ExportProgress(exportedCount + 1, totalToExport, newFileName))

                var copyNote = "تم التصدير بنجاح"
                try {
                    val mimeType = getMimeType(newFileName)
                    val targetFileDoc = outputFolderDoc.createFile(mimeType, newFileName)
                        ?: throw IllegalStateException("تعذر إنشاء الملف $newFileName")

                    context.contentResolver.openInputStream(photo.uri)?.use { inStream ->
                        context.contentResolver.openOutputStream(targetFileDoc.uri)?.use { outStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    exportedCount++
                } catch (e: Exception) {
                    copyNote = "فشل النسخ: ${e.message}"
                }

                val updatedStudent = student.copy(
                    exportedFileName = newFileName,
                    notes = copyNote
                )
                updatedStudentsWithExportedNames.add(updatedStudent)

                reportRows.add(
                    ExportReportRow(
                        serialNo = student.serialNo,
                        studentCode = student.code,
                        nationalId = student.nationalId,
                        studentName = student.name,
                        originalPhotoName = photo.originalName,
                        exportedPhotoName = newFileName,
                        classification = student.classification.labelAr,
                        notes = copyNote
                    )
                )
            } else {
                // طالب لم يتم اعتماد صورته أو بدون صورة
                val reason = when {
                    student.isRejected -> "تم رفض المطابقة يدويًا"
                    student.matchedPhoto == null -> "طالب بلا صورة في المجلد"
                    else -> "مطابقة غير معتمدة (${student.classification.labelAr})"
                }

                updatedStudentsWithExportedNames.add(student.copy(exportedFileName = null, notes = reason))

                reportRows.add(
                    ExportReportRow(
                        serialNo = student.serialNo,
                        studentCode = student.code,
                        nationalId = student.nationalId,
                        studentName = student.name,
                        originalPhotoName = student.matchedPhoto?.originalName ?: "—",
                        exportedPhotoName = "— (لم تُصدّر)",
                        classification = student.classification.labelAr,
                        notes = reason
                    )
                )
            }
        }

        // 2. إنشاء نسخة جديدة من ملف Excel (محدثة بالعمودين K و L) دون المساس بالأصل
        var newExcelFileName = "بيانات_الطلاب_المحدثة_بالصور.xlsx"
        try {
            val excelDoc = outputFolderDoc.createFile(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                newExcelFileName
            )
            if (excelDoc != null) {
                context.contentResolver.openInputStream(originalExcelUri)?.use { origIn ->
                    context.contentResolver.openOutputStream(excelDoc.uri)?.use { excelOut ->
                        FastXlsxUpdater.createUpdatedExcelCopy(
                            originalInputStream = origIn,
                            targetOutputStream = excelOut,
                            students = updatedStudentsWithExportedNames,
                            columnMapping = columnMapping
                        )
                    }
                }
            }
        } catch (e: Exception) {
            newExcelFileName = "تعذر إنشاء نسخة الإكسيل: ${e.message}"
        }

        // 3. إنشاء تقرير التدقيق CSV داخل مجلد الإخراج
        try {
            val reportDoc = outputFolderDoc.createFile("text/csv", "تقرير_مطابقة_وتصدير_صور_الطلاب.csv")
            if (reportDoc != null) {
                context.contentResolver.openOutputStream(reportDoc.uri)?.use { out ->
                    writeCsvReport(reportRows, out)
                }
            }
        } catch (_: Exception) {}

        val duration = System.currentTimeMillis() - startTime
        onProgress(ExportProgress(totalToExport, totalToExport, "اكتمل التصدير بنجاح", isComplete = true))

        ExportSummaryReport(
            totalStudents = students.size,
            totalPhotos = students.mapNotNull { it.matchedPhoto }.distinctBy { it.id }.size,
            approvedAndExportedCount = exportedCount,
            skippedCount = students.count { it.matchedPhoto != null && !it.isApproved && !it.isRejected },
            conflictedCount = students.count { it.classification == com.tamer.studentphotomatcher.core.model.MatchClassification.DATA_CONFLICT },
            studentsWithoutPhotosCount = students.count { it.matchedPhoto == null },
            newExcelFileName = newExcelFileName,
            executionDurationMillis = duration,
            rows = reportRows
        )
    }

    private fun writeCsvReport(rows: List<ExportReportRow>, outputStream: java.io.OutputStream) {
        // كتابة UTF-8 BOM لفتح الملف مباشرة باللغة العربية في Excel
        outputStream.write(0xEF)
        outputStream.write(0xBB)
        outputStream.write(0xBF)

        val writer = outputStream.bufferedWriter(StandardCharsets.UTF_8)
        writer.appendLine("تقرير مطابقة وتصدير صور الطلاب - Student Photo Matcher & Organizer")
        writer.appendLine("م,كود التلميذ,الرقم القومي,اسم التلميذ,اسم الصورة الأصلي,اسم الصورة المُصدّر,حالة المطابقة,ملاحظات")

        for (r in rows) {
            val line = listOf(
                escapeCsv(r.serialNo),
                escapeCsv(r.studentCode),
                escapeCsv(r.nationalId),
                escapeCsv(r.studentName),
                escapeCsv(r.originalPhotoName),
                escapeCsv(r.exportedPhotoName),
                escapeCsv(r.classification),
                escapeCsv(r.notes)
            ).joinToString(",")
            writer.appendLine(line)
        }

        writer.flush()
    }

    private fun escapeCsv(str: String): String {
        var clean = str.trim()
        if (clean.contains(",") || clean.contains("\"") || clean.contains("\n") || clean.contains("\r")) {
            clean = clean.replace("\"", "\"\"")
            return "\"$clean\""
        }
        return clean
    }

    private fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
    }
}
