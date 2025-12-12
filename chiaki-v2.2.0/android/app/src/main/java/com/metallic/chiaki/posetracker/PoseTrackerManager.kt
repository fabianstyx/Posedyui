// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.posetracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import kotlin.math.hypot

class PoseTrackerManager(
    context: Context,
    private val overlayView: PoseTrackerOverlayView,
    private val onCursorMove: ((Float, Float) -> Unit)? = null,
    private val onTriggerBot: ((Boolean) -> Unit)? = null
) : PoseDetectorListener {

    private val settings = PoseTrackerSettings(context)
    private var config = settings.loadConfig()
    private var poseDetector: PoseDetectorHelper? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var videoRect = RectF()
    private var lastMouseX = 0f
    private var lastMouseY = 0f
    private var isActive = false
    
    // Smoothing state
    private var smoothedX = 0f
    private var smoothedY = 0f
    private var lastTargetX = 0f
    private var lastTargetY = 0f
    
    // TriggerBot state
    private var triggerBotArmed = false
    private var triggerBotFiring = false
    private var lastTriggerTime = 0L
    private var lastFireTime = 0L
    
    // Predictive aiming state
    private var previousFocusPoint: PointF? = null
    private var previousTime = 0L

    fun initialize() {
        config = settings.loadConfig()
        poseDetector = PoseDetectorHelper(config, this)
        poseDetector?.initialize()
        overlayView.setConfig(config)
    }

    fun setVideoRect(rect: RectF) {
        videoRect = rect
        overlayView.setVideoRect(rect)
        lastMouseX = rect.centerX()
        lastMouseY = rect.centerY()
        smoothedX = rect.centerX()
        smoothedY = rect.centerY()
    }

    fun toggleTracking(): Boolean {
        isActive = !isActive
        overlayView.setTrackingEnabled(isActive)
        if (!isActive) {
            resetTriggerBot()
        }
        return isActive
    }

    fun setTrackingEnabled(enabled: Boolean) {
        isActive = enabled
        overlayView.setTrackingEnabled(enabled)
        if (!enabled) {
            resetTriggerBot()
        }
    }

    fun isTrackingActive(): Boolean = isActive
    
    fun getConfig(): PoseTrackerConfig = config
    
    fun reloadConfig() {
        config = settings.loadConfig()
        poseDetector?.updateConfig(config)
        overlayView.setConfig(config)
    }

    fun processFrame(bitmap: Bitmap) {
        if (!isActive || !config.isEnabled) {
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }
        poseDetector?.detectPose(bitmap, videoRect) ?: run {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    override fun onPoseDetected(pose: DetectedPose?) {
        mainHandler.post {
            overlayView.setDetectedPose(pose)
            
            if (pose != null && isActive) {
                val currentTime = System.currentTimeMillis()
                
                // Apply predictive aiming if enabled
                var targetX = pose.focusPoint.x
                var targetY = pose.focusPoint.y
                
                if (config.predictiveAiming && previousFocusPoint != null) {
                    val deltaTime = (currentTime - previousTime).coerceAtLeast(1L)
                    val velocityX = (pose.focusPoint.x - previousFocusPoint!!.x) / deltaTime * 16f
                    val velocityY = (pose.focusPoint.y - previousFocusPoint!!.y) / deltaTime * 16f
                    
                    targetX += velocityX * config.predictionStrength
                    targetY += velocityY * config.predictionStrength
                }
                
                previousFocusPoint = PointF(pose.focusPoint.x, pose.focusPoint.y)
                previousTime = currentTime
                
                // Apply smoothing
                val smoothingFactor = config.aimSmoothing
                if (smoothingFactor > 0f) {
                    smoothedX = smoothedX + (targetX - smoothedX) * (1f - smoothingFactor)
                    smoothedY = smoothedY + (targetY - smoothedY) * (1f - smoothingFactor)
                } else {
                    smoothedX = targetX
                    smoothedY = targetY
                }
                
                // Apply snap to target if enabled and within threshold
                val distanceToTarget = hypot(smoothedX - targetX, smoothedY - targetY)
                if (config.snapToTarget && distanceToTarget < config.snapThreshold) {
                    smoothedX = targetX
                    smoothedY = targetY
                }
                
                // Move cursor with aim speed
                moveCursorTo(smoothedX, smoothedY)
                
                // Handle TriggerBot
                if (config.triggerBotEnabled) {
                    handleTriggerBot(pose, currentTime)
                }
            } else {
                // No target - reset triggerbot
                if (config.triggerBotEnabled) {
                    resetTriggerBot()
                }
                previousFocusPoint = null
            }
        }
    }

    private fun handleTriggerBot(pose: DetectedPose, currentTime: Long) {
        val centerX = videoRect.centerX()
        val centerY = videoRect.centerY()
        
        // Check if crosshair is on target (within bounding box)
        val isOnTarget = pose.boundingBox.contains(centerX, centerY)
        
        if (isOnTarget) {
            if (!triggerBotArmed) {
                triggerBotArmed = true
                lastTriggerTime = currentTime
            }
            
            // Check if delay has passed
            val delayPassed = (currentTime - lastTriggerTime) >= config.triggerBotDelay
            
            if (delayPassed && !triggerBotFiring) {
                // Fire!
                triggerBotFiring = true
                lastFireTime = currentTime
                onTriggerBot?.invoke(true)
            }
            
            // Handle fire hold time
            if (triggerBotFiring) {
                val holdTimeElapsed = (currentTime - lastFireTime) >= config.triggerBotHoldTime
                
                if (holdTimeElapsed) {
                    onTriggerBot?.invoke(false)
                    
                    // Check auto-fire mode
                    if (config.autoFireEnabled) {
                        val autoFireIntervalElapsed = (currentTime - lastFireTime) >= config.autoFireRate
                        if (autoFireIntervalElapsed) {
                            lastFireTime = currentTime
                            onTriggerBot?.invoke(true)
                        }
                    } else {
                        triggerBotFiring = false
                    }
                }
            }
        } else {
            resetTriggerBot()
        }
    }

    private fun resetTriggerBot() {
        if (triggerBotFiring) {
            onTriggerBot?.invoke(false)
        }
        triggerBotArmed = false
        triggerBotFiring = false
    }

    private fun moveCursorTo(targetX: Float, targetY: Float) {
        if (videoRect.width() == 0f) return

        val gameCenterX = videoRect.centerX()
        val gameCenterY = videoRect.centerY()

        var movementX = targetX - gameCenterX
        var movementY = targetY - gameCenterY
        
        // Apply aim assist strength
        movementX *= config.aimAssistStrength
        movementY *= config.aimAssistStrength
        
        // Apply aim speed
        movementX *= config.aimSpeed
        movementY *= config.aimSpeed

        lastMouseX += movementX
        lastMouseY += movementY

        onCursorMove?.invoke(movementX, movementY)
    }

    override fun onError(error: String) {
        android.util.Log.e("PoseTrackerManager", error)
    }

    fun updateConfig(newConfig: PoseTrackerConfig) {
        config = newConfig
        settings.saveConfig(newConfig)
        poseDetector?.updateConfig(config)
        overlayView.setConfig(config)
    }

    fun release() {
        resetTriggerBot()
        poseDetector?.close()
        poseDetector = null
    }
}
