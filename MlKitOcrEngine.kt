package com.tamer.studentphotomatcher.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * محرك OCR المدمج على الجهاز القائم على Google ML Kit
 * يعمل محلياً 100% بدون أي اتصال بالإنترنت، ويحافظ على سرية بيانات وصور الطلاب بالكامل.
 */
class MlKitOcrEngine : IOcrEngine {

    override val engineId: String = "ML_KIT_LOCAL"
    override val displayNameAr: String = "محرك ML Kit المحلي على الجهاز (آمن وسريع)"
    override val isCloudBased: Boolean = false

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognizeText(bitmap: Bitmap): OcrAnalysisResult = withContext(Dispatchers.Default) {
        try {
            // 1. تحسين جودة الصورة والتدرج الرمادي
            val preprocessed = ImagePreprocessor.preprocessForOcr(bitmap)
            val inputImage = InputImage.fromBitmap(preprocessed, 0)

            val visionText = recognizer.process(inputImage).await()
            val raw = visionText.text

            val lines = ArrayList<String>()
            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    lines.add(line.text)
                }
            }

            if (preprocessed != bitmap) {
                preprocessed.recycle()
            }

            // 2. استخراج الرقم القومي بدقة
            val nationalId = ArabicTextExtractor.extractNationalId(raw)

            // 3. استخراج كود الطالب
            val studentCode = ArabicTextExtractor.extractStudentCode(raw, nationalId)

            // 4. استخراج اسم الطالب وتجاهل الكلمات الدخيلة
            val detectedName = ArabicTextExtractor.extractStudentName(lines)

            // 5. حساب درجة الثقة
            var confidence = 0
            if (nationalId != null) confidence += 50
            if (studentCode != null) confidence += 30
            if (detectedName != null) confidence += 20
            confidence = confidence.coerceIn(0, 100)

            OcrAnalysisResult(
                rawText = raw,
                detectedName = detectedName,
                detectedNationalId = nationalId,
                detectedCode = studentCode,
                confidenceScore = confidence,
                textBlocks = lines,
                notes = if (raw.isBlank()) "لم يتم العثور على نصوص مقروءة" else "تم الفحص المحلي بنجاح"
            )
        } catch (e: Exception) {
            OcrAnalysisResult(
                notes = "خطأ في المعالجة المحلية: ${e.message ?: "غير معروف"}"
            )
        }
    }
}
