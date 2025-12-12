// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.posetracker

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import java.util.concurrent.Executors
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class ColorDetectorHelper(
    private var config: PoseTrackerConfig,
    private val listener: DetectorListener
) : BaseDetector {

    private val executor = Executors.newSingleThreadExecutor()
    private var isInitialized = false
    private var isProcessing = false

    override fun initialize() {
        isInitialized = true
        listener.onDebugInfo("Color Detector: Initialized")
    }

    override fun detect(bitmap: Bitmap, videoRect: RectF) {
        if (!isInitialized || isProcessing) {
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }
        if (bitmap.isRecycled) return
        isProcessing = true

        val bitmapCopy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        bitmap.recycle()

        if (bitmapCopy == null) {
            isProcessing = false
            return
        }

        executor.execute {
            try {
                val detection = detectByColor(bitmapCopy, videoRect)
                listener.onTargetDetected(detection)
                bitmapCopy.recycle()
                isProcessing = false
            } catch (e: Exception) {
                listener.onError("Color detection error: ${e.message}")
                bitmapCopy.recycle()
                isProcessing = false
            }
        }
    }

    private fun detectByColor(bitmap: Bitmap, videoRect: RectF): DetectedPose? {
        val width = bitmap.width
        val height = bitmap.height
        val centerX = width / 2
        val centerY = height / 2
        
        val scaleX = videoRect.width() / width
        val scaleY = videoRect.height() / height
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        var bestX = -1
        var bestY = -1
        var bestScore = 0
        
        val gridSize = 32
        val fovRadiusPixels = (config.fovRadius / scaleX).toInt()
        
        for (y in 0 until height step gridSize) {
            for (x in 0 until width step gridSize) {
                val distFromCenter = hypot((x - centerX).toFloat(), (y - centerY).toFloat())
                if (distFromCenter > fovRadiusPixels) continue
                
                var score = 0
                val endY = min(y + gridSize, height)
                val endX = min(x + gridSize, width)
                
                for (py in y until endY step 4) {
                    for (px in x until endX step 4) {
                        val pixel = pixels[py * width + px]
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        
                        val brightness = (r + g + b) / 3
                        val saturation = max(max(r, g), b) - min(min(r, g), b)
                        
                        if (brightness in 60..240 && saturation > 15) {
                            val skinScore = if (r > 95 && g > 40 && b > 20 && 
                                               r > g && r > b && 
                                               max(r, max(g, b)) - min(r, min(g, b)) > 15) 2 else 0
                            
                            val contrastScore = if (brightness in 80..200) 1 else 0
                            
                            score += 1 + skinScore + contrastScore
                        }
                    }
                }
                
                if (score > bestScore) {
                    bestScore = score
                    bestX = x + gridSize / 2
                    bestY = y + gridSize / 2
                }
            }
        }
        
        if (bestScore < 20) return null
        
        val boxWidth = gridSize * 3f
        val boxHeight = gridSize * 5f
        
        val screenLeft = videoRect.left + (bestX - boxWidth / 2) * scaleX
        val screenTop = videoRect.top + (bestY - boxHeight / 2) * scaleY
        val screenRight = videoRect.left + (bestX + boxWidth / 2) * scaleX
        val screenBottom = videoRect.top + (bestY + boxHeight / 2) * scaleY
        
        val boundingBox = RectF(screenLeft, screenTop, screenRight, screenBottom)
        
        val focusPoint = if (config.headShotMode) {
            PointF(
                boundingBox.centerX(),
                boundingBox.top + boundingBox.height() * config.headOffsetY
            )
        } else {
            PointF(boundingBox.centerX(), boundingBox.centerY())
        }
        
        val screenCenterX = videoRect.centerX()
        val screenCenterY = videoRect.centerY()
        val distanceFromCenter = hypot(focusPoint.x - screenCenterX, focusPoint.y - screenCenterY)
        
        if (distanceFromCenter > config.fovRadius) {
            return null
        }
        
        listener.onDebugInfo("Color: Target at (${focusPoint.x.toInt()}, ${focusPoint.y.toInt()}), score: $bestScore")
        
        return DetectedPose(
            boundingBox = boundingBox,
            focusPoint = focusPoint,
            confidence = (bestScore.toFloat() / 100f).coerceIn(0.3f, 0.95f)
        )
    }

    override fun updateConfig(newConfig: PoseTrackerConfig) {
        config = newConfig
    }

    override fun close() {
        executor.shutdown()
        isInitialized = false
    }
}
