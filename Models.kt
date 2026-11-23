package com.tamer.studentphotomatcher.core.model

import android.net.Uri

/**
 * تصنيفات نتائج المطابقة الرسمية
 */
enum class MatchClassification(val labelAr: String, val badgeColorHex: Long) {
    CONFIRMED_MATCH("مطابقة مؤكدة", 0xFF10B981),                      // أخضر
    POTENTIAL_MATCH_REVIEW_NEEDED("مطابقة محتملة تحتاج مراجعة", 0xFFF59E0B), // برتقالي
    DATA_CONFLICT("تعارض في البيانات", 0xFFEF4444),                  // أحمر
    NO_MATCHING_RECORD("صورة بلا سجل مطابق", 0xFF64748B),            // رمادي
    RECORD_WITHOUT_PHOTO("سجل بلا صورة", 0xFF8B5CF6),                 // بنفسجي
    APPROVED_MANUAL("معتمد يدويًا", 0xFF059669),                      // أخضر غامق
    REJECTED("مرفوض", 0xFFDC2626)                                    // أحمر داكن
}

/**
 * نموذج بيانات الطالب المستخرج من ملف Excel
 */
data class Student(
    val serialNo: String = "",           // العمود A: م / المسلسل
    val code: String = "",               // العمود B: كود التلميذ
    val nationalId: String = "",         // العمود C: الرقم القومي (سلسلة نصية بدقة للحفاظ على الأصفار)
    val name: String = "",               // العمود D: اسم التلميذ
    val rowIndex: Int = 0,               // رقم الصف في ورقة العمل (بدءاً من 0)
    val matchedPhoto: PhotoItem? = null, // الصورة المربوطة بالطالب
    val classification: MatchClassification = MatchClassification.RECORD_WITHOUT_PHOTO,
    val confidenceScore: Int = 0,        // درجة الثقة من 0 إلى 100
    val matchReason: String = "",        // سبب وتفاصيل المطابقة أو التعارض
    val isApproved: Boolean = false,     // هل تم اعتماد المطابقة من المستخدم
    val isRejected: Boolean = false,     // هل تم رفض المطابقة
    val exportedFileName: String? = null, // العمود K: اسم ملف الصورة الناتج
    val notes: String? = null            // العمود L: ملاحظات
) {
    val id: String get() = if (code.isNotBlank()) code else if (nationalId.isNotBlank()) nationalId else name
}

/**
 * بيانات الصورة ومخرجات التعرف الضوئي OCR
 */
data class PhotoItem(
    val id: String,                      // معرف فريد (مسار أو Uri)
    val uriString: String,
    val originalName: String,
    val extension: String,
    val sizeBytes: Long = 0L,
    val captureDateMillis: Long? = null,
    val detectedRawText: String = "",    // النص الخام المكتشف بالكامل بواسطة OCR
    val detectedName: String? = null,    // اسم الطالب المستخرج من الصورة
    val detectedNationalId: String? = null, // الرقم القومي المستخرج من الصورة
    val detectedCode: String? = null,    // كود الطالب المستخرج من الصورة
    val ocrConfidence: Int = 0,          // درجة ثقة OCR
    val assignedStudentCode: String? = null,
    val isApproved: Boolean = false,
    val isSkipped: Boolean = false
) {
    val uri: Uri get() = Uri.parse(uriString)
}

/**
 * خيارات تسمية الصور الـ 6 المحددة بالمتطلبات
 */
enum class NamingTemplateOption(val labelAr: String, val template: String) {
    STUDENT_NAME_ONLY("اسم الطالب فقط", "{name}"),
    NATIONAL_ID_ONLY("الرقم القومي فقط", "{national_id}"),
    STUDENT_CODE_ONLY("كود الطالب فقط", "{code}"),
    NAME_AND_CODE("اسم الطالب + كود الطالب", "{name}_{code}"),
    NAME_AND_NATIONAL_ID("اسم الطالب + الرقم القومي", "{name}_{national_id}"),
    NATIONAL_ID_AND_CODE("الرقم القومي + كود الطالب", "{national_id}_{code}")
}

/**
 * إعدادات تطهير وتوليد الأسماء
 */
data class RenamingConfig(
    val selectedOption: NamingTemplateOption = NamingTemplateOption.NAME_AND_CODE,
    val customTemplate: String = "{name}_{code}",
    val replaceSpacesWith: String = "_",
    val cleanArabicDiacritics: Boolean = true,
    val lowercaseExtension: Boolean = true,
    val sanitizeCharacters: Boolean = true
)

/**
 * خريطة أعمدة ملف Excel المتوافقة مع المتطلبات الرسمية
 */
data class ColumnMapping(
    val serialColumnIndex: Int = 0,         // العمود A: م / المسلسل
    val codeColumnIndex: Int = 1,           // العمود B: كود التلميذ
    val nationalIdColumnIndex: Int = 2,     // العمود C: الرقم القومي
    val nameColumnIndex: Int = 3,           // العمود D: اسم التلميذ
    val outputPhotoColumnIndex: Int = 10,   // العمود K: اسم ملف الصورة الناتج
    val notesColumnIndex: Int = 11,         // العمود L: ملاحظات
    val headerRowIndex: Int = 3,            // الصف الرابع (index 3)
    val dataStartRowIndex: Int = 4          // الصف الخامس (index 4)
)

/**
 * بيانات ورقة العمل من Excel
 */
data class ExcelSheetData(
    val sheetName: String,
    val headers: List<String>,
    val rows: List<List<String>>,
    val columnMapping: ColumnMapping
)

/**
 * عنصر مراجعة المطابقة
 */
data class ReviewItem(
    val student: Student,
    val photo: PhotoItem?,
    val previewNewName: String,
    val classification: MatchClassification,
    val isApproved: Boolean,
    val isRejected: Boolean,
    val matchExplanation: String
)

/**
 * إحصائيات لوحة التحكم Dashboard
 */
data class DashboardStats(
    val totalStudents: Int = 0,
    val totalPhotos: Int = 0,
    val matchedPhotos: Int = 0,
    val pendingReviewCount: Int = 0,
    val unmatchedPhotosCount: Int = 0,
    val studentsWithoutPhotosCount: Int = 0,
    val exportedPhotosCount: Int = 0,
    val progressPercentage: Float = 0f
)

/**
 * سطر تقرير التصدير والمطابقة
 */
data class ExportReportRow(
    val serialNo: String,
    val studentCode: String,
    val nationalId: String,
    val studentName: String,
    val originalPhotoName: String,
    val exportedPhotoName: String,
    val classification: String,
    val notes: String
)

/**
 * ملخص التقرير النهائي للتصدير
 */
data class ExportSummaryReport(
    val totalStudents: Int,
    val totalPhotos: Int,
    val approvedAndExportedCount: Int,
    val skippedCount: Int,
    val conflictedCount: Int,
    val studentsWithoutPhotosCount: Int,
    val newExcelFileName: String,
    val executionDurationMillis: Long,
    val rows: List<ExportReportRow>
)
