package com.tamer.studentphotomatcher.organizer

import com.tamer.studentphotomatcher.core.model.NamingTemplateOption
import com.tamer.studentphotomatcher.core.model.PhotoItem
import com.tamer.studentphotomatcher.core.model.RenamingConfig
import com.tamer.studentphotomatcher.core.model.ReviewItem
import com.tamer.studentphotomatcher.core.model.Student

/**
 * محرك تسمية الصور الاحترافي المتوافق مع الخيارات الـ 6 المحددة بالمتطلبات
 * 1. اسم الطالب فقط
 * 2. الرقم القومي فقط
 * 3. كود الطالب فقط
 * 4. اسم الطالب + كود الطالب
 * 5. اسم الطالب + الرقم القومي
 * 6. الرقم القومي + كود الطالب
 *
 * مع تنظيف الأسماء من الرموز الممنوعة وحل مشكلة تكرار الأسماء لمنع الكتابة فوق الملفات.
 */
object PhotoRenamingEngine {

    private val ILLEGAL_CHARS_REGEX = Regex("[\\\\/:*?\"<>|\\r\\n\\t]")

    /**
     * توليد اسم الملف الجديد لطالب وصورة محددة وفق الخيار المحدد
     */
    fun generateFileName(
        student: Student,
        photo: PhotoItem,
        config: RenamingConfig,
        existingNamesCount: MutableMap<String, Int> = HashMap()
    ): String {
        val cleanName = cleanStudentName(student.name, config.cleanArabicDiacritics)
        val cleanCode = student.code.trim()
        val cleanNid = student.nationalId.trim()

        var baseName = when (config.selectedOption) {
            NamingTemplateOption.STUDENT_NAME_ONLY -> cleanName.ifBlank { "student_$cleanCode" }
            NamingTemplateOption.NATIONAL_ID_ONLY -> cleanNid.ifBlank { "nid_$cleanCode" }
            NamingTemplateOption.STUDENT_CODE_ONLY -> cleanCode.ifBlank { "code_$cleanNid" }
            NamingTemplateOption.NAME_AND_CODE -> "${cleanName}_$cleanCode"
            NamingTemplateOption.NAME_AND_NATIONAL_ID -> "${cleanName}_$cleanNid"
            NamingTemplateOption.NATIONAL_ID_AND_CODE -> "${cleanNid}_$cleanCode"
        }

        // تطهير الحروف الممنوعة في نظام ملفات Android
        if (config.sanitizeCharacters) {
            baseName = baseName.replace(ILLEGAL_CHARS_REGEX, "")
        }

        // استبدال المسافات
        if (config.replaceSpacesWith.isNotEmpty()) {
            baseName = baseName.replace(Regex("\\s+"), config.replaceSpacesWith)
        }

        baseName = baseName.trim('_', '-', ' ')
        if (baseName.isBlank()) {
            baseName = "student_${student.id}"
        }

        val extension = if (config.lowercaseExtension) {
            photo.extension.lowercase()
        } else {
            photo.extension
        }

        // التعامل مع تكرار الأسماء لمنع الكتابة فوق أي ملف
        val lowerKey = "$baseName.$extension".lowercase()
        val currentCount = existingNamesCount.getOrDefault(lowerKey, 0)
        existingNamesCount[lowerKey] = currentCount + 1

        val finalBaseName = if (currentCount > 0) {
            "${baseName}_${currentCount + 1}"
        } else {
            baseName
        }

        return "$finalBaseName.$extension"
    }

    /**
     * بناء عناصر المراجعة مع المعاينة الفورية للاسم النهائي
     */
    fun buildReviewItems(
        students: List<Student>,
        config: RenamingConfig
    ): List<ReviewItem> {
        val namesCount = HashMap<String, Int>()
        val reviewItems = ArrayList<ReviewItem>(students.size)

        for (student in students) {
            val photo = student.matchedPhoto
            val previewName = if (photo != null) {
                generateFileName(student, photo, config, namesCount)
            } else {
                "— (طالب بلا صورة)"
            }

            reviewItems.add(
                ReviewItem(
                    student = student,
                    photo = photo,
                    previewNewName = previewName,
                    classification = student.classification,
                    isApproved = student.isApproved,
                    isRejected = student.isRejected,
                    matchExplanation = student.matchReason
                )
            )
        }

        return reviewItems
    }

    private fun cleanStudentName(rawName: String, removeDiacritics: Boolean): String {
        var str = rawName.trim()
        if (removeDiacritics) {
            str = str.replace(Regex("[\u064B-\u065F\u0670]"), "") // إزالة التشكيل
            str = str.replace("ـ", "") // إزالة التطويل
            str = str.replace(Regex("[أإآ]"), "ا") // توحيد الألف
            str = str.replace("ة", "ه") // توحيد التاء المربوطة
            str = str.replace("ى", "ي")
        }
        return str
    }
}
