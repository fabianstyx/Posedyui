// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.posetracker

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.preference.PreferenceManager
import kotlin.math.hypot

class PoseTrackerManager(
    private val context: Context,
    private val overlayView: PoseTrackerOverlayView,
    private val onCursorMove: ((Float, Float) -> Unit)? = null,
    private val onTriggerBot: ((Boolean) -> Unit)? = null
) : PoseDetectorListener, DetectorListener, SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val TAG = "PoseTrackerManager"
    }

    private val settings = PoseTrackerSettings(context)
    private var config = settings.loadConfig()
    
    private var mlKitDetector: PoseDetectorHelper? = null
    private var yoloDetector: YoloDetectorHelper? = null
    private var colorDetector: ColorDetectorHelper? = null
    private var currentDetectorType: DetectorType = config.detectorType
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private var videoRect = RectF()
    private var lastMouseX = 0f
    private var lastMouseY = 0f
    private var isActive = false
    
    private var smoothedX = 0f
    private var smoothedY = 0f
    
    private var triggerBotArmed = false
    private var triggerBotFiring = false
    private var lastTriggerTime = 0L
    private var lastFireTime = 0L
    
    private var previousFocusPoint: PointF? = null
    private var previousTime = 0L
    
    private var lastProcessTime = 0L
    
    private var detectionCount = 0
    private var lastDebugLogTime = 0L

    fun initialize() {
        config = settings.loadConfig()
        currentDetectorType = config.detectorType
        
        when (config.detectorType) {
            DetectorType.ML_KIT_POSE -> {
                mlKitDetector = PoseDetectorHelper(config, this)
                mlKitDetector?.initialize()
                logDebug("Initialized ML Kit Pose detector")
            }
            DetectorType.YOLO_OBJECT -> {
                yoloDetector = YoloDetectorHelper(context, config, this)
                yoloDetector?.initialize()
                logDebug("Initialized YOLO Object detector")
            }
            DetectorType.COLOR_DETECTION -> {
                colorDetector = ColorDetectorHelper(config, this)
                colorDetector?.initialize()
                logDebug("Initialized Color Detection")
            }
        }
        
        overlayView.setConfig(config)
        
        PreferenceManager.getDefaultSharedPreferences(context)
            .registerOnSharedPreferenceChangeListener(this)
    }
    
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key?.startsWith("pose_tracker") == true) {
            val oldDetectorType = currentDetectorType
            reloadConfig()
            
            if (config.detectorType != oldDetectorType) {
                logDebug("Detector type changed from $oldDetectorType to ${config.detectorType}")
                reinitializeDetector()
            }
        }
    }
    
    private fun reinitializeDetector() {
        mlKitDetector?.close()
        mlKitDetector = null
        yoloDetector?.close()
        yoloDetector = null
        colorDetector?.close()
        colorDetector = null
        
        currentDetectorType = config.detectorType
        
        when (config.detectorType) {
            DetectorType.ML_KIT_POSE -> {
                mlKitDetector = PoseDetectorHelper(config, this)
                mlKitDetector?.initialize()
            }
            DetectorType.YOLO_OBJECT -> {
                yoloDetector = YoloDetectorHelper(context, config, this)
                yoloDetector?.initialize()
            }
            DetectorType.COLOR_DETECTION -> {
                colorDetector = ColorDetectorHelper(config, this)
                colorDetector?.initialize()
            }
        }
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
        logDebug("Tracking ${if (isActive) "enabled" else "disabled"}")
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
        mlKitDetector?.updateConfig(config)
        yoloDetector?.updateConfig(config)
        colorDetector?.updateConfig(config)
        overlayView.setConfig(config)
    }

    fun processFrame(bitmap: Bitmap) {
        if (!isActive || !config.isEnabled) {
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessTime < config.processingInterval) {
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }
        lastProcessTime = currentTime
        
        when (config.detectorType) {
            DetectorType.ML_KIT_POSE -> {
                mlKitDetector?.detectPose(bitmap, videoRect) ?: run {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }
            DetectorType.YOLO_OBJECT -> {
                yoloDetector?.detect(bitmap, videoRect) ?: run {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }
            DetectorType.COLOR_DETECTION -> {
                colorDetector?.detect(bitmap, videoRect) ?: run {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }
        }
    }

    override fun onPoseDetected(pose: DetectedPose?) {
        handleDetection(pose)
    }
    
    override fun onTargetDetected(target: DetectedPose?) {
        handleDetection(target)
    }
    
    override fun onDebugInfo(info: String) {
        logDebug(info)
    }

    private fun handleDetection(pose: DetectedPose?) {
        mainHandler.post {
            overlayView.setDetectedPose(pose)
            
            if (pose != null && isActive) {
                detectionCount++
                val currentTime = System.currentTimeMillis()
                
                if (config.debugMode && currentTime - lastDebugLogTime > 1000) {
                    logDebug("Detections/sec: $detectionCount, Target: (${pose.focusPoint.x.toInt()}, ${pose.focusPoint.y.toInt()}), Conf: ${String.format("%.2f", pose.confidence)}")
                    detectionCount = 0
                    lastDebugLogTime = currentTime
                }
                
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
                
                val smoothingFactor = config.aimSmoothing
                if (smoothingFactor > 0.01f) {
                    val lerpFactor = 1f - smoothingFactor
                    smoothedX = smoothedX + (targetX - smoothedX) * lerpFactor
                    smoothedY = smoothedY + (targetY - smoothedY) * lerpFactor
                } else {
                    smoothedX = targetX
                    smoothedY = targetY
                }
                
                val distanceToTarget = hypot(smoothedX - targetX, smoothedY - targetY)
                if (config.snapToTarget && distanceToTarget < config.snapThreshold) {
                    smoothedX = targetX
                    smoothedY = targetY
                }
                
                moveCursorTo(smoothedX, smoothedY)
                
                if (config.triggerBotEnabled) {
                    handleTriggerBot(pose, currentTime)
                }
            } else {
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
        
        val isOnTarget = pose.boundingBox.contains(centerX, centerY)
        
        if (isOnTarget) {
            if (!triggerBotArmed) {
                triggerBotArmed = true
                lastTriggerTime = currentTime
                logDebug("TriggerBot: Target acquired")
            }
            
            val delayPassed = (currentTime - lastTriggerTime) >= config.triggerBotDelay
            
            if (delayPassed && !triggerBotFiring) {
                triggerBotFiring = true
                lastFireTime = currentTime
                onTriggerBot?.invoke(true)
                logDebug("TriggerBot: FIRE!")
            }
            
            if (triggerBotFiring) {
                val holdTimeElapsed = (currentTime - lastFireTime) >= config.triggerBotHoldTime
                
                if (holdTimeElapsed) {
                    onTriggerBot?.invoke(false)
                    
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
            logDebug("TriggerBot: Target lost")
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
        
        movementX *= config.aimAssistStrength
        movementY *= config.aimAssistStrength
        
        movementX *= config.aimSpeed
        movementY *= config.aimSpeed

        lastMouseX += movementX
        lastMouseY += movementY

        if (config.debugMode && (movementX != 0f || movementY != 0f)) {
            logDebug("Movement: (${String.format("%.1f", movementX)}, ${String.format("%.1f", movementY)})")
        }

        onCursorMove?.invoke(movementX, movementY)
    }

    override fun onError(error: String) {
        Log.e(TAG, error)
    }

    fun updateConfig(newConfig: PoseTrackerConfig) {
        config = newConfig
        settings.saveConfig(newConfig)
        mlKitDetector?.updateConfig(config)
        yoloDetector?.updateConfig(config)
        colorDetector?.updateConfig(config)
        overlayView.setConfig(config)
    }
    
    private fun logDebug(message: String) {
        if (config.debugMode) {
            Log.d(TAG, message)
        }
    }

    fun release() {
        PreferenceManager.getDefaultSharedPreferences(context)
            .unregisterOnSharedPreferenceChangeListener(this)
        
        resetTriggerBot()
        mlKitDetector?.close()
        mlKitDetector = null
        yoloDetector?.close()
        yoloDetector = null
        colorDetector?.close()
        colorDetector = null
    }
}
