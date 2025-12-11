// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.posetracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.util.concurrent.Executors
import kotlin.math.hypot
import kotlin.math.min

interface PoseDetectorListener {
    fun onPoseDetected(pose: DetectedPose?)
    fun onError(error: String)
}

class PoseDetectorHelper(
    private val context: Context,
    private val config: PoseTrackerConfig,
    private val listener: PoseDetectorListener
) {
    private var objectDetector: ObjectDetector? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var isInitialized = false

    fun initialize() {
        executor.execute {
            try {
                val options = ObjectDetector.ObjectDetectorOptions.builder()
                    .setScoreThreshold(config.confidence)
                    .setMaxResults(5)
                    .build()

                objectDetector = ObjectDetector.createFromFileAndOptions(
                    context,
                    MODEL_FILE,
                    options
                )
                isInitialized = true
            } catch (e: Exception) {
                listener.onError("Failed to initialize pose detector: ${e.message}")
            }
        }
    }

    fun detectPose(bitmap: Bitmap, videoRect: RectF) {
        if (!isInitialized || objectDetector == null) return

        executor.execute {
            try {
                val maskedBitmap = if (config.maskEnabled) {
                    applyMask(bitmap)
                } else {
                    bitmap
                }

                val tensorImage = TensorImage.fromBitmap(maskedBitmap)
                val results = objectDetector?.detect(tensorImage)

                val bestPose = findBestFocusPoint(results, bitmap, videoRect)
                listener.onPoseDetected(bestPose)
            } catch (e: Exception) {
                listener.onError("Pose detection error: ${e.message}")
            }
        }
    }

    private fun applyMask(bitmap: Bitmap): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(mutableBitmap)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            style = android.graphics.Paint.Style.FILL
        }

        val maskRect = android.graphics.RectF(
            bitmap.width * config.maskX,
            bitmap.height * config.maskY,
            bitmap.width * (config.maskX + config.maskWidth),
            bitmap.height * (config.maskY + config.maskHeight)
        )
        canvas.drawRect(maskRect, paint)

        return mutableBitmap
    }

    private fun findBestFocusPoint(
        detections: List<Detection>?,
        bitmap: Bitmap,
        videoRect: RectF
    ): DetectedPose? {
        if (detections.isNullOrEmpty()) return null

        val centerX = videoRect.centerX()
        val centerY = videoRect.centerY()
        var bestPose: DetectedPose? = null
        var minDistance = Float.MAX_VALUE

        for (detection in detections) {
            if (detection.categories.isEmpty()) continue
            
            val category = detection.categories[0]
            if (category.score < config.confidence) continue
            if (category.label != "person") continue

            val box = detection.boundingBox
            
            val scaleX = videoRect.width() / bitmap.width
            val scaleY = videoRect.height() / bitmap.height

            val screenBox = RectF(
                videoRect.left + box.left * scaleX,
                videoRect.top + box.top * scaleY,
                videoRect.left + box.right * scaleX,
                videoRect.top + box.bottom * scaleY
            )

            val focusX = screenBox.centerX()
            val focusY = screenBox.top + screenBox.height() / 14f

            val distance = hypot(focusX - centerX, focusY - centerY)

            if (distance < config.fovRadius && distance < minDistance) {
                minDistance = distance
                bestPose = DetectedPose(
                    boundingBox = screenBox,
                    focusPoint = PointF(focusX, focusY),
                    confidence = category.score
                )
            }
        }

        return bestPose
    }

    fun close() {
        executor.execute {
            objectDetector?.close()
            objectDetector = null
            isInitialized = false
        }
        executor.shutdown()
    }

    companion object {
        private const val MODEL_FILE = "movenet_singlepose_lightning.tflite"
    }
}
