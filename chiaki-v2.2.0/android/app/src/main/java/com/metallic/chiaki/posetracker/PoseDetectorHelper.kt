// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.posetracker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.hypot

interface PoseDetectorListener {
    fun onPoseDetected(pose: DetectedPose?)
    fun onError(error: String)
}

class PoseDetectorHelper(
    private var config: PoseTrackerConfig,
    private val listener: PoseDetectorListener
) {
    private var poseDetector: PoseDetector? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var isInitialized = false

    // FIX: @Volatile makes isProcessing writes visible to all threads immediately
    @Volatile
    private var isProcessing = false

    companion object {
        private const val EXECUTOR_SHUTDOWN_TIMEOUT_MS = 500L
    }

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

    fun updateConfig(newConfig: PoseTrackerConfig) {
        config = newConfig
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
                        // FIX: pass maskedBitmap to findBestFocusPoint so landmark coordinates
                        // match the image that was actually fed to the detector.
                        // Also avoid using bitmapCopy after it may have been recycled below.
                        val bestPose = findBestFocusPoint(pose, maskedBitmap, videoRect)
                        listener.onPoseDetected(bestPose)

                        // FIX: only recycle maskedBitmap separately when it is a different object;
                        // bitmapCopy is recycled unconditionally afterwards exactly once.
                        if (maskedBitmap !== bitmapCopy) maskedBitmap.recycle()
                        bitmapCopy.recycle()
                        isProcessing = false
                    }
                    ?.addOnFailureListener { e ->
                        listener.onError("Pose detection error: ${e.message}")
                        if (maskedBitmap !== bitmapCopy) maskedBitmap.recycle()
                        bitmapCopy.recycle()
                        isProcessing = false
                    }
            } catch (e: Exception) {
                listener.onError("Pose detection error: ${e.message}")
                // FIX: bitmapCopy is guaranteed to still exist here (maskedBitmap is local and
                // may not have been assigned yet), so only recycle what we know is valid.
                if (!bitmapCopy.isRecycled) bitmapCopy.recycle()
                isProcessing = false
            }
        }
    }

    private fun applyMask(bitmap: Bitmap): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        val maskRect = RectF(
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

        val focusPoint = if (config.headShotMode) {
            val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
            if (nose != null && nose.inFrameLikelihood >= config.confidence) {
                PointF(
                    videoRect.left + nose.position.x * scaleX,
                    videoRect.top + nose.position.y * scaleY
                )
            } else {
                PointF(
                    boundingBox.centerX(),
                    boundingBox.top + boundingBox.height() * config.headOffsetY
                )
            }
        } else {
            PointF(boundingBox.centerX(), boundingBox.centerY())
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
        // FIX: shutdownNow + awaitTermination for a clean, timely shutdown
        executor.shutdownNow()
        try {
            executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        poseDetector?.close()
        poseDetector = null
        isInitialized = false
        isProcessing = false
    }
}
