package com.tamer.studentphotomatcher.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * مدير محركات OCR ومنسق عمليات المعالجة
 * يوفر تبديل المحركات، والتحكم في إعدادات الخصوصية، وتحميل الصور بدقة مثالية دون استهلاك الذاكرة
 */
class OcrEngineManager(private val context: Context) {

    private val localEngine = MlKitOcrEngine()
    private val cloudEngine = CloudOcrEngine()

    private var activeEngine: IOcrEngine = localEngine

    val availableEngines: List<IOcrEngine>
        get() = listOf(localEngine, cloudEngine)

    fun setActiveEngine(engineId: String) {
        activeEngine = when (engineId) {
            cloudEngine.engineId -> cloudEngine
            else -> localEngine
        }
    }

    fun getActiveEngine(): IOcrEngine = activeEngine

    /**
     * تشغيل OCR على صورة محددة بواسطة Uri
     */
    suspend fun analyzeImage(uri: Uri): OcrAnalysisResult = withContext(Dispatchers.IO) {
        val bitmap = loadOptimizedBitmap(uri, maxDimension = 1600)
            ?: return@withContext OcrAnalysisResult(notes = "تعذر تحميل ملف الصورة")

        val result = activeEngine.recognizeText(bitmap)
        bitmap.recycle()
        result
    }

    /**
     * تحميل صورة مضبوطة الأبعاد لتجنب نفاد الذاكرة في الهواتف
     */
    private fun loadOptimizedBitmap(uri: Uri, maxDimension: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            var inSampleSize = 1
            val maxSide = maxOf(options.outWidth, options.outHeight)
            if (maxSide > maxDimension) {
                inSampleSize = Math.round(maxSide.toFloat() / maxDimension.toFloat()).toInt()
            }

            val decodeOpts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }
        } catch (_: Exception) {
            null
        }
    }
}
