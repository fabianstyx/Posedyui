// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.posetracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

class PoseTrackerManager(
    private val context: Context,
    private val overlayView: PoseTrackerOverlayView,
    private val onCursorMove: ((Float, Float) -> Unit)? = null
) : PoseDetectorListener {

    private val config = PoseTrackerConfig()
    private var poseDetector: PoseDetectorHelper? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var videoRect = RectF()
    private var lastMouseX = 0f
    private var lastMouseY = 0f
    private var isActive = false

    fun initialize() {
        poseDetector = PoseDetectorHelper(context, config, this)
        poseDetector?.initialize()
        overlayView.setConfig(config)
    }

    fun setVideoRect(rect: RectF) {
        videoRect = rect
        overlayView.setVideoRect(rect)
        lastMouseX = rect.centerX()
        lastMouseY = rect.centerY()
    }

    fun toggleTracking(): Boolean {
        isActive = !isActive
        overlayView.setTrackingEnabled(isActive)
        return isActive
    }

    fun setTrackingEnabled(enabled: Boolean) {
        isActive = enabled
        overlayView.setTrackingEnabled(enabled)
    }

    fun isTrackingActive(): Boolean = isActive

    fun processFrame(bitmap: Bitmap) {
        if (!isActive) return
        poseDetector?.detectPose(bitmap, videoRect)
    }

    override fun onPoseDetected(pose: DetectedPose?) {
        mainHandler.post {
            overlayView.setDetectedPose(pose)
            
            if (pose != null && isActive) {
                moveCursorTo(pose.focusPoint.x, pose.focusPoint.y)
            }
        }
    }

    private fun moveCursorTo(targetX: Float, targetY: Float) {
        if (videoRect.width() == 0f) return

        val gameCenterX = videoRect.centerX()
        val gameCenterY = videoRect.centerY()

        val movementX = targetX - gameCenterX
        val movementY = targetY - gameCenterY

        lastMouseX += movementX
        lastMouseY += movementY

        onCursorMove?.invoke(movementX, movementY)
    }

    override fun onError(error: String) {
        android.util.Log.e("PoseTrackerManager", error)
    }

    fun updateConfig(newConfig: PoseTrackerConfig) {
        config.confidence = newConfig.confidence
        config.enableVisualAssist = newConfig.enableVisualAssist
        config.fovRadius = newConfig.fovRadius
        config.showFocusCircle = newConfig.showFocusCircle
        config.maskEnabled = newConfig.maskEnabled
        config.showMask = newConfig.showMask
        config.maskX = newConfig.maskX
        config.maskY = newConfig.maskY
        config.maskWidth = newConfig.maskWidth
        config.maskHeight = newConfig.maskHeight
        overlayView.setConfig(config)
    }

    fun release() {
        poseDetector?.close()
        poseDetector = null
    }
}
