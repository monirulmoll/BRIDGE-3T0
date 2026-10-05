package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.Drawable
import kotlin.math.sqrt

/**
 * Image Template Matching Engine
 * Implements Normalized Cross-Correlation (NCC) equivalent to OpenCV Imgproc.TM_CCOEFF_NORMED
 */
object TemplateMatcher {

    /**
     * Finds target icon inside screen bitmap using normalized cross-correlation.
     * Corresponds to OpenCV Imgproc.matchTemplate(..., Imgproc.TM_CCOEFF_NORMED)
     * and Core.minMaxLoc(result).
     *
     * @param screenBitmap The captured screen bitmap
     * @param targetIconBitmap The template icon bitmap to find
     * @param minConfidence Match confidence threshold (default 0.85 = 85%)
     * @return Point with center coordinates if match >= minConfidence, null otherwise
     */
    fun findAndClickImage(
        screenBitmap: Bitmap,
        targetIconBitmap: Bitmap,
        minConfidence: Float = 0.85f
    ): Point? {
        val sWidth = screenBitmap.width
        val sHeight = screenBitmap.height
        val tWidth = targetIconBitmap.width
        val tHeight = targetIconBitmap.height

        if (sWidth < tWidth || sHeight < tHeight) return null

        // Convert template to grayscale luminance
        val tPixels = IntArray(tWidth * tHeight)
        targetIconBitmap.getPixels(tPixels, 0, tWidth, 0, 0, tWidth, tHeight)
        val tGray = FloatArray(tWidth * tHeight)
        var tMean = 0f
        for (i in tPixels.indices) {
            val c = tPixels[i]
            val gray = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c))
            tGray[i] = gray
            tMean += gray
        }
        tMean /= tGray.size

        var tVariance = 0f
        for (i in tGray.indices) {
            tGray[i] -= tMean
            tVariance += tGray[i] * tGray[i]
        }
        val tNorm = sqrt(tVariance)
        if (tNorm == 0f) return null

        // Focus search where ChatGPT code blocks and message action buttons are located
        // (X: 55% to 98% of width, Y: 10% to 92% of height)
        val startX = (sWidth * 0.55f).toInt().coerceAtLeast(0)
        val endX = (sWidth * 0.98f).toInt().coerceAtMost(sWidth - tWidth)
        val startY = (sHeight * 0.10f).toInt().coerceAtLeast(0)
        val endY = (sHeight * 0.92f).toInt().coerceAtMost(sHeight - tHeight)

        var bestScore = -1f
        var bestX = -1
        var bestY = -1

        val step = 3 // 3px step for rapid real-time matching
        val patchPixels = IntArray(tWidth * tHeight)
        val patchGray = FloatArray(tWidth * tHeight)

        for (y in startY until endY step step) {
            for (x in startX until endX step step) {
                screenBitmap.getPixels(patchPixels, 0, tWidth, x, y, tWidth, tHeight)

                var pMean = 0f
                for (i in patchPixels.indices) {
                    val c = patchPixels[i]
                    val gray = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c))
                    patchGray[i] = gray
                    pMean += gray
                }
                pMean /= patchGray.size

                var pVariance = 0f
                var crossCorr = 0f
                for (i in patchGray.indices) {
                    val pDev = patchGray[i] - pMean
                    pVariance += pDev * pDev
                    crossCorr += pDev * tGray[i]
                }

                val pNorm = sqrt(pVariance)
                if (pNorm > 0f) {
                    val score = crossCorr / (pNorm * tNorm)
                    if (score > bestScore) {
                        bestScore = score
                        bestX = x
                        bestY = y
                    }
                }
            }
        }

        if (bestScore >= minConfidence && bestX >= 0 && bestY >= 0) {
            val centerX = bestX + tWidth / 2
            val centerY = bestY + tHeight / 2
            return Point(centerX, centerY)
        }

        return null
    }

    fun drawableToBitmap(drawable: Drawable, width: Int = 48, height: Int = 48): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
