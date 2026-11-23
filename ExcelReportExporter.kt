package com.tamer.studentphotomatcher.excel

import com.tamer.studentphotomatcher.core.model.MatchReportRow
import com.tamer.studentphotomatcher.core.model.MatchSummaryReport
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * مُصدّر تقارير التدقيق والمطابقة بصيغتي CSV و Excel المتوافقة
 * يدعم إضافة UTF-8 BOM لفتح الملفات بالعربية مباشرة في Excel دون أي مشاكل رموز.
 */
object ExcelReportExporter {

    /**
     * تصدير تقرير تفصيلي شامل بصيغة CSV مدعوم بالكامل في برنامج Excel
     */
    fun exportReportToCsv(report: MatchSummaryReport, outputStream: OutputStream) {
        // كتابة UTF-8 BOM أولاً (0xEF, 0xBB, 0xBF)
        outputStream.write(0xEF)
        outputStream.write(0xBB)
        outputStream.write(0xBF)

        val writer = outputStream.bufferedWriter(StandardCharsets.UTF_8)

        // 1. عنوان التقرير وملخص الإحصائيات
        writer.appendLine("تقرير مطابقة وإعادة تسمية صور الطلاب - راصد صور الطلاب")
        writer.appendLine("إجمالي الطلاب المسجلين,${report.totalStudents}")
        writer.appendLine("إجمالي الصور المحملة,${report.totalPhotos}")
        writer.appendLine("الصور المطابقة بنجاح,${report.matchedCount}")
        writer.appendLine("الطلاب الغائبون / بدون صور,${report.missingStudentsCount}")
        writer.appendLine("الصور الفائضة / غير المطابقة,${report.unassignedPhotosCount}")
        writer.appendLine("مطابقات الباركود والـ QR,${report.barcodeMatches}")
        writer.appendLine("مطابقات التعرف البصري OCR,${report.ocrMatches}")
        writer.appendLine("مطابقات الترتيب التسلسلي,${report.sequentialMatches}")
        writer.appendLine("مطابقات اسم الملف,${report.filenameMatches}")
        writer.appendLine("مطابقات يدوية,${report.manualMatches}")
        writer.appendLine("")

        // 2. رؤوس الأعمدة
        writer.appendLine("كود الطالب / رقم الجلوس,اسم الطالب,الصف / الفصل,اسم الصورة الأصلي,اسم الصورة الجديد,حالة المطابقة,ملاحظات")

        // 3. صفوف البيانات
        for (row in report.rows) {
            val line = listOf(
                escapeCsv(row.studentId),
                escapeCsv(row.studentName),
                escapeCsv(row.studentClass),
                escapeCsv(row.originalPhotoName),
                escapeCsv(row.newPhotoName),
                escapeCsv(row.matchStatus),
                escapeCsv(row.notes)
            ).joinToString(",")
            writer.appendLine(line)
        }

        writer.flush()
    }

    private fun escapeCsv(value: String): String {
        var str = value.trim()
        if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
            str = str.replace("\"", "\"\"")
            return "\"$str\""
        }
        return str
    }
}
