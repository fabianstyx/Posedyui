// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.posetracker

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.Executors
import kotlin.math.hypot

interface PoseDetectorListener {
    fun onPoseDetected(pose: DetectedPose?)
    fun onError(error: String)
}

class PoseDetectorHelper(
    private val config: PoseTrackerConfig,
    private val listener: PoseDetectorListener
) {
    private var poseDetector: PoseDetector? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var isInitialized = false
    private var isProcessing = false

    fun initialize() {
        executor.execute {
            try {
                val options = PoseDetectorOptions.Builder()
                    .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                    .build()

                poseDetector = PoseDetection.getClient(options)
                isInitialized = true
            } catch (e: Exception) {
                listener.onError("Failed to initialize pose detector: ${e.message}")
            }
        }
    }

    fun detectPose(bitmap: Bitmap, videoRect: RectF) {
        if (!isInitialized || poseDetector == null || isProcessing) {
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
                val maskedBitmap = if (config.maskEnabled) {
                    applyMask(bitmapCopy)
                } else {
                    bitmapCopy
                }

                val inputImage = InputImage.fromBitmap(maskedBitmap, 0)
                
                poseDetector?.process(inputImage)
                    ?.addOnSuccessListener { pose ->
                        val bestPose = findBestFocusPoint(pose, bitmapCopy, videoRect)
                        listener.onPoseDetected(bestPose)
                        if (maskedBitmap != bitmapCopy) maskedBitmap.recycle()
                        bitmapCopy.recycle()
                        isProcessing = false
                    }
                    ?.addOnFailureListener { e ->
                        listener.onError("Pose detection error: ${e.message}")
                        if (maskedBitmap != bitmapCopy) maskedBitmap.recycle()
                        bitmapCopy.recycle()
                        isProcessing = false
                    }
            } catch (e: Exception) {
                listener.onError("Pose detection error: ${e.message}")
                bitmapCopy.recycle()
                isProcessing = false
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
        pose: Pose,
        bitmap: Bitmap,
        videoRect: RectF
    ): DetectedPose? {
        val landmarks = pose.allPoseLandmarks
        if (landmarks.isEmpty()) return null

        val centerX = videoRect.centerX()
        val centerY = videoRect.centerY()

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        var validLandmarks = 0

        val scaleX = videoRect.width() / bitmap.width
        val scaleY = videoRect.height() / bitmap.height

        for (landmark in landmarks) {
            if (landmark.inFrameLikelihood >= config.confidence) {
                val screenX = videoRect.left + landmark.position.x * scaleX
                val screenY = videoRect.top + landmark.position.y * scaleY
                
                minX = minOf(minX, screenX)
                minY = minOf(minY, screenY)
                maxX = maxOf(maxX, screenX)
                maxY = maxOf(maxY, screenY)
                validLandmarks++
            }
        }

        if (validLandmarks < 5) return null

        val boundingBox = RectF(minX, minY, maxX, maxY)
        
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
        val focusPoint = if (nose != null && nose.inFrameLikelihood >= config.confidence) {
            PointF(
                videoRect.left + nose.position.x * scaleX,
                videoRect.top + nose.position.y * scaleY
            )
        } else {
            PointF(
                boundingBox.centerX(),
                boundingBox.top + boundingBox.height() / 14f
            )
        }

        val distance = hypot(focusPoint.x - centerX, focusPoint.y - centerY)

        if (distance > config.fovRadius) return null

        return DetectedPose(
            boundingBox = boundingBox,
            focusPoint = focusPoint,
            confidence = landmarks.map { it.inFrameLikelihood }.average().toFloat()
        )
    }

    fun close() {
        executor.execute {
            poseDetector?.close()
            poseDetector = null
            isInitialized = false
        }
        executor.shutdown()
    }
}
