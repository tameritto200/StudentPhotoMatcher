package com.tamer.studentphotomatcher.ocr

import android.graphics.Bitmap

/**
 * مخرجات فحص وتعرف النص بواسطة محرك OCR
 */
data class OcrAnalysisResult(
    val rawText: String = "",
    val detectedName: String? = null,
    val detectedNationalId: String? = null,
    val detectedCode: String? = null,
    val confidenceScore: Int = 0,
    val textBlocks: List<String> = emptyList(),
    val notes: String = ""
)

/**
 * واجهة محرك التعرف الضوئي على النصوص (Pluggable OCR Engine Interface)
 * تتيح تبديل وتطوير محركات OCR (ML Kit محلي، Tesseract محلي، أو خدمة سحابية اختيارية)
 */
interface IOcrEngine {
    val engineId: String
    val displayNameAr: String
    val isCloudBased: Boolean

    suspend fun recognizeText(bitmap: Bitmap): OcrAnalysisResult
}
