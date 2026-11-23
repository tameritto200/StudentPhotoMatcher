package com.tamer.studentphotomatcher.ocr

import android.graphics.Bitmap

/**
 * واجهة المحرك السحابي الاختياري للتعرف على النصوص (Cloud OCR)
 * سياسة الخصوصية والأمان الصارمة:
 * - مُعطّل افتراضياً بشكل كامل (Disabled by default)
 * - لا يحتوي على أي مفاتيح API ثابتة في الكود
 * - لا يرسل أي صورة أو معلومة إلا بعد موافقة صريحة يدوية من المستخدم وتفعيله للمفتاح الخاص به
 */
class CloudOcrEngine(
    var isUserConsentGranted: Boolean = false,
    var userApiKey: String = "",
    var cloudEndpointUrl: String = ""
) : IOcrEngine {

    override val engineId: String = "OPTIONAL_CLOUD_OCR"
    override val displayNameAr: String = "محرك سحابي اختياري (يتطلب موافقة صريحة ومفتاح خاص)"
    override val isCloudBased: Boolean = true

    override suspend fun recognizeText(bitmap: Bitmap): OcrAnalysisResult {
        // التحقق من شرط الأمان والخصوصية: موافقة المستخدم + توفير المفتاح
        if (!isUserConsentGranted || userApiKey.isBlank()) {
            return OcrAnalysisResult(
                notes = "المحرك السحابي معطل حرصاً على الخصوصية. المعالجة تتم محلياً بالكامل."
            )
        }

        // في حال توفر المفتاح والموافقة يمكن إرسال الطلب الآمن عبر HTTPS
        // الافتراضي هو حماية خصوصية الطلاب
        return OcrAnalysisResult(
            notes = "تم حظر إرسال بيانات الطلاب السحابية لعدم تفعيل الإعداد المتقدم."
        )
    }
}
