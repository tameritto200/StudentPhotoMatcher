package com.tamer.studentphotomatcher.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * معالج تحسين جودة الصور قبل إرسالها للتعرف الضوئي OCR
 * يرفع التباين، يحول للأبيض والأسود بتدرج رمادي، ويعالج الإضاءة لزيادة دقة قراءة النصوص المطبوعة
 */
object ImagePreprocessor {

    /**
     * تحسين صورة الطالب لاستخراج النصوص بدقة عالية
     */
    fun preprocessForOcr(source: Bitmap, maxDimension: Int = 1600): Bitmap {
        // 1. تغيير الحجم للأبعاد المثالية
        val scaled = scaleToFit(source, maxDimension)

        // 2. تطبيق فلتر التدرج الرمادي ورفع التباين (High Contrast Grayscale)
        val contrastBmp = applyHighContrastGrayscale(scaled)
        if (scaled != source && scaled != contrastBmp) {
            scaled.recycle()
        }

        return contrastBmp
    }

    /**
     * استقطاع الجزء السفلي من الصورة (حيث يطبع اسم الطالب والرقم القومي عادة)
     */
    fun cropBottomSection(source: Bitmap, heightRatio: Float = 0.35f): Bitmap {
        val cropHeight = (source.height * heightRatio).toInt().coerceIn(50, source.height)
        val startY = source.height - cropHeight
        return Bitmap.createBitmap(source, 0, startY, source.width, cropHeight)
    }

    private fun scaleToFit(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxSide = maxOf(width, height)
        if (maxSide <= maxDimension) return bitmap

        val ratio = maxDimension.toFloat() / maxSide.toFloat()
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun applyHighContrastGrayscale(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()

        // مصفوفة تدرج رمادي مع رفع التباين بنسبة 35%
        val contrast = 1.35f
        val translate = (-0.5f * contrast + 0.5f) * 255f

        val cm = ColorMatrix(
            floatArrayOf(
                0.33f * contrast, 0.59f * contrast, 0.11f * contrast, 0f, translate,
                0.33f * contrast, 0.59f * contrast, 0.11f * contrast, 0f, translate,
                0.33f * contrast, 0.59f * contrast, 0.11f * contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)

        return output
    }
}
