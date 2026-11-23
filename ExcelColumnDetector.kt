package com.tamer.studentphotomatcher.excel

import com.tamer.studentphotomatcher.core.model.ColumnMapping
import java.util.regex.Pattern

/**
 * المحلل الذكي للأعمدة للكشف التلقائي عن الأعمدة الرسمية:
 * A: م / المسلسل
 * B: كود التلميذ
 * C: الرقم القومي
 * D: اسم التلميذ
 * K: اسم ملف الصورة الناتج
 * L: ملاحظات
 * مع استخدام الترتيب الافتراضي أعلاه كإعداد افتراضي فوري عند عدم العثور على العناوين.
 */
object ExcelColumnDetector {

    private val SERIAL_PATTERNS = listOf(
        "^م$", "مسلسل", "م\\.", "رقم.*مسلسل", "ت", "no", "sn", "serial", "seq"
    ).map { Pattern.compile(it, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE) }

    private val CODE_PATTERNS = listOf(
        "كود.*تلميذ", "كود.*طالب", "رقم.*تلميذ", "رقم.*طالب", "كود", "code", "student.*code", "std.*code", "id"
    ).map { Pattern.compile(it, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE) }

    private val NATIONAL_ID_PATTERNS = listOf(
        "رقم.*قومي", "الرقم.*القومي", "بطاقة.*قومية", "national.*id", "nid", "ssn"
    ).map { Pattern.compile(it, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE) }

    private val NAME_PATTERNS = listOf(
        "اسم.*تلميذ", "اسم.*طالب", "الاسم.*رباعي", "الاسم.*ثلاثي", "اسم.*الدارس", "الاسم", "name", "student.*name", "full.*name"
    ).map { Pattern.compile(it, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE) }

    private val OUTPUT_PHOTO_PATTERNS = listOf(
        "اسم.*ملف.*صورة", "صورة.*ناتجة", "ملف.*صورة", "الصورة", "photo.*name", "output.*photo", "image.*file"
    ).map { Pattern.compile(it, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE) }

    private val NOTES_PATTERNS = listOf(
        "ملاحظات", "حالة.*مطابقة", "ملاحظة", "notes", "remarks", "status"
    ).map { Pattern.compile(it, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE) }

    /**
     * كشف الأعمدة تلقائياً من قائمة رؤوس الأعمدة مع استخدام الإعدادات الافتراضية
     */
    fun autoDetectColumns(headers: List<String>): ColumnMapping {
        var serialIdx = -1
        var codeIdx = -1
        var nationalIdIdx = -1
        var nameIdx = -1
        var outputPhotoIdx = -1
        var notesIdx = -1

        for ((index, header) in headers.withIndex()) {
            val cleanHeader = header.trim().lowercase()
            if (cleanHeader.isBlank()) continue

            if (serialIdx == -1 && matchesAny(cleanHeader, SERIAL_PATTERNS)) {
                serialIdx = index
                continue
            }
            if (codeIdx == -1 && matchesAny(cleanHeader, CODE_PATTERNS)) {
                codeIdx = index
                continue
            }
            if (nationalIdIdx == -1 && matchesAny(cleanHeader, NATIONAL_ID_PATTERNS)) {
                nationalIdIdx = index
                continue
            }
            if (nameIdx == -1 && matchesAny(cleanHeader, NAME_PATTERNS)) {
                nameIdx = index
                continue
            }
            if (outputPhotoIdx == -1 && matchesAny(cleanHeader, OUTPUT_PHOTO_PATTERNS)) {
                outputPhotoIdx = index
                continue
            }
            if (notesIdx == -1 && matchesAny(cleanHeader, NOTES_PATTERNS)) {
                notesIdx = index
                continue
            }
        }

        // إذا لم يتم العثور على عمود معين، نستخدم الترتيب الافتراضي (A=0, B=1, C=2, D=3, K=10, L=11)
        val finalSerialIdx = if (serialIdx != -1) serialIdx else 0
        val finalCodeIdx = if (codeIdx != -1) codeIdx else 1
        val finalNationalIdIdx = if (nationalIdIdx != -1) nationalIdIdx else 2
        val finalNameIdx = if (nameIdx != -1) nameIdx else 3
        val finalOutputPhotoIdx = if (outputPhotoIdx != -1) outputPhotoIdx else 10
        val finalNotesIdx = if (notesIdx != -1) notesIdx else 11

        return ColumnMapping(
            serialColumnIndex = finalSerialIdx,
            codeColumnIndex = finalCodeIdx,
            nationalIdColumnIndex = finalNationalIdIdx,
            nameColumnIndex = finalNameIdx,
            outputPhotoColumnIndex = finalOutputPhotoIdx,
            notesColumnIndex = finalNotesIdx,
            headerRowIndex = 3,
            dataStartRowIndex = 4
        )
    }

    private fun matchesAny(text: String, patterns: List<Pattern>): Boolean {
        for (pattern in patterns) {
            if (pattern.matcher(text).find()) {
                return true
            }
        }
        return false
    }
}
