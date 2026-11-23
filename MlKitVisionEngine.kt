package com.tamer.studentphotomatcher.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tamer.studentphotomatcher.core.model.PhotoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * نتيجة فحص الذكاء الاصطناعي للصورة
 */
data class AiPhotoAnalysis(
    val detectedBarcode: String? = null,
    val detectedOcrText: String? = null,
    val faceCount: Int = 0,
    val faceNotes: String = ""
)

/**
 * محرك الذكاء الاصطناعي المدمج على الجهاز (On-Device ML Kit)
 * لفحص الباركود، والتعرف الضوئي على بطاقات وأرقام الطلاب OCR، وكشف الوجوه
 */
class MlKitVisionEngine(private val context: Context) {

    private val barcodeScanner by lazy {
        BarcodeScanning.getClient()
    }

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
        FaceDetection.getClient(options)
    }

    /**
     * فحص شامل للصورة: باركود + OCR + كشف الوجه
     */
    suspend fun analyzePhoto(photoItem: PhotoItem): AiPhotoAnalysis = withContext(Dispatchers.Default) {
        val bitmap = loadScaledBitmap(photoItem.uri, maxDimension = 1200)
            ?: return@withContext AiPhotoAnalysis(faceNotes = "تعذر تحميل ملف الصورة")

        val image = InputImage.fromBitmap(bitmap, 0)

        // 1. فحص الباركود / QR Code
        var detectedBarcode: String? = null
        try {
            val barcodes = barcodeScanner.process(image).await()
            if (barcodes.isNotEmpty()) {
                val first = barcodes.first()
                detectedBarcode = first.rawValue ?: first.displayValue
            }
        } catch (_: Exception) {}

        // 2. فحص كشف الوجه والتحقق من جودة صورة الطالب
        var faceCount = 0
        var faceNotes = ""
        try {
            val faces = faceDetector.process(image).await()
            faceCount = faces.size
            faceNotes = when (faceCount) {
                0 -> "تحذير: لم يتم العثور على أي وجه في الصورة"
                1 -> "وجه سليم وواضح"
                else -> "تحذير: تم اكتشاف $faceCount وجوه (قد تكون صورة جماعية)"
            }
        } catch (_: Exception) {
            faceNotes = "تعذر فحص الوجه"
        }

        // 3. فحص OCR في حال لم نجد باركود أو لأخذ نصوص الكارت
        var detectedOcrText: String? = null
        try {
            val textResult = textRecognizer.process(image).await()
            val text = textResult.text.trim()
            if (text.isNotBlank()) {
                detectedOcrText = text
            }
        } catch (_: Exception) {}

        bitmap.recycle()

        AiPhotoAnalysis(
            detectedBarcode = detectedBarcode,
            detectedOcrText = detectedOcrText,
            faceCount = faceCount,
            faceNotes = faceNotes
        )
    }

    /**
     * فحص سريع للباركود فقط
     */
    suspend fun scanBarcodeOnly(uri: Uri): String? = withContext(Dispatchers.Default) {
        val bitmap = loadScaledBitmap(uri, maxDimension = 1000) ?: return@withContext null
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val barcodes = barcodeScanner.process(image).await()
            bitmap.recycle()
            barcodes.firstOrNull()?.let { it.rawValue ?: it.displayValue }
        } catch (_: Exception) {
            bitmap.recycle()
            null
        }
    }

    /**
     * تحميل صورة مصغرة ومحسوبة الأبعاد لتفادي نفاد ذاكرة الرام OOM
     */
    private fun loadScaledBitmap(uri: Uri, maxDimension: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            var inSampleSize = 1
            val origMax = maxOf(options.outWidth, options.outHeight)
            if (origMax > maxDimension) {
                inSampleSize = Math.round(origMax.toFloat() / maxDimension.toFloat()).toInt()
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (_: Exception) {
            null
        }
    }
}
