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
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * PoseTrackerManager orchestrates detection + aim-assist movement.
 *
 * Smoothing modes (set in config.smoothingMode):
 *
 *   LINEAR        — constant lerp each frame (original).  Predictable but robotic-looking.
 *
 *   EASE_OUT      — speed proportional to remaining distance.  Fast when far from the target,
 *                   decelerates as it closes in.  Mimics human hand deceleration — the most
 *                   natural feel for FPS aim assist.  Formula: delta * k where k = (1-smoothing).
 *                   The exponential decay means it asymptotically approaches the target.
 *
 *   MAGNETIC      — two-phase:
 *                   1. When distance > snapThreshold: move at full speed (instant convergence).
 *                   2. When distance ≤ snapThreshold: switch to EASE_OUT for fine precision.
 *                   Best for games where the target needs to be hit precisely after a fast flick.
 *
 * Adaptive confidence: the smoothed cursor velocity (px/frame) is fed back into
 * YoloDetectorHelper so it can lower the detection threshold during fast camera movement.
 */
class PoseTrackerManager(
    private val context: Context,
    private val overlayView: PoseTrackerOverlayView,
    private val onCursorMove: ((Float, Float) -> Unit)? = null,
    private val onTriggerBot: ((Boolean) -> Unit)? = null,
    private val onResetMovement: (() -> Unit)? = null
) : PoseDetectorListener, DetectorListener, SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val TAG = "PoseTrackerManager"
        // Speed (px/frame) above which MAGNETIC mode skips the smooth phase.
        private const val MAGNETIC_FAST_THRESHOLD = 8f
        // Exponential smoothing factor for camera-speed estimation fed to adaptive confidence.
        private const val SPEED_SMOOTH = 0.25f
    }

    private val settings = PoseTrackerSettings(context)
    private var config   = settings.loadConfig()

    private var mlKitDetector: PoseDetectorHelper?  = null
    private var yoloDetector: YoloDetectorHelper?   = null
    private var colorDetector: ColorDetectorHelper? = null
    private var currentDetectorType: DetectorType   = config.detectorType

    private val mainHandler = Handler(Looper.getMainLooper())
    private var videoRect   = RectF()
    private var isActive    = false

    // Smoothed cursor position (screen-space).
    private var smoothedX = 0f
    private var smoothedY = 0f

    // Exponentially smoothed cursor speed used to drive adaptive confidence.
    private var smoothedSpeedPx = 0f

    private var triggerBotArmed   = false
    private var triggerBotFiring  = false
    private var lastTriggerTime   = 0L
    private var lastFireTime      = 0L

    private var previousFocusPoint: PointF? = null
    private var previousTime                = 0L
    private var lastProcessTime             = 0L
    private var detectionCount              = 0
    private var lastDebugLogTime            = 0L

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun initialize() {
        config              = settings.loadConfig()
        currentDetectorType = config.detectorType
        startDetector()
        overlayView.setConfig(config)
        PreferenceManager.getDefaultSharedPreferences(context)
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key?.startsWith("pose_tracker") == true) {
            val oldType = currentDetectorType
            reloadConfig()
            if (config.detectorType != oldType) reinitializeDetector()
        }
    }

    private fun startDetector() {
        when (config.detectorType) {
            DetectorType.ML_KIT_POSE -> {
                mlKitDetector = PoseDetectorHelper(config, this).also { it.initialize() }
                logDebug("Initialized ML Kit Pose detector")
            }
            DetectorType.YOLO_OBJECT -> {
                yoloDetector = YoloDetectorHelper(context, config, this).also { it.initialize() }
                logDebug("Initialized YOLO Object detector")
            }
            DetectorType.COLOR_DETECTION -> {
                colorDetector = ColorDetectorHelper(config, this).also { it.initialize() }
                logDebug("Initialized Color Detection")
            }
        }
    }

    private fun reinitializeDetector() {
        mlKitDetector?.close();  mlKitDetector  = null
        yoloDetector?.close();   yoloDetector   = null
        colorDetector?.close();  colorDetector  = null
        currentDetectorType = config.detectorType
        startDetector()
    }

    fun release() {
        PreferenceManager.getDefaultSharedPreferences(context)
            .unregisterOnSharedPreferenceChangeListener(this)
        resetTriggerBot()
        mlKitDetector?.close();  mlKitDetector  = null
        yoloDetector?.close();   yoloDetector   = null
        colorDetector?.close();  colorDetector  = null
    }

    // ── State ─────────────────────────────────────────────────────────────────

    fun setVideoRect(rect: RectF) {
        videoRect = rect
        overlayView.setVideoRect(rect)
        smoothedX = rect.centerX()
        smoothedY = rect.centerY()
    }

    fun toggleTracking(): Boolean {
        isActive = !isActive
        overlayView.setTrackingEnabled(isActive)
        if (!isActive) { resetTriggerBot(); previousFocusPoint = null }
        logDebug("Tracking ${if (isActive) "ON" else "OFF"}")
        return isActive
    }

    fun setTrackingEnabled(enabled: Boolean) {
        isActive = enabled
        overlayView.setTrackingEnabled(enabled)
        if (!enabled) { resetTriggerBot(); previousFocusPoint = null }
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

    fun updateConfig(newConfig: PoseTrackerConfig) {
        config = newConfig
        settings.saveConfig(newConfig)
        mlKitDetector?.updateConfig(config)
        yoloDetector?.updateConfig(config)
        colorDetector?.updateConfig(config)
        overlayView.setConfig(config)
    }

    // ── Frame processing ──────────────────────────────────────────────────────

    fun processFrame(bitmap: Bitmap) {
        if (!isActive || !config.isEnabled) {
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastProcessTime < config.processingInterval) {
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }
        lastProcessTime = now

        when (config.detectorType) {
            DetectorType.ML_KIT_POSE    -> mlKitDetector?.detectPose(bitmap, videoRect)    ?: bitmap.recycle()
            DetectorType.YOLO_OBJECT    -> yoloDetector?.detect(bitmap, videoRect)          ?: bitmap.recycle()
            DetectorType.COLOR_DETECTION -> colorDetector?.detect(bitmap, videoRect)        ?: bitmap.recycle()
        }
    }

    // ── Detection callbacks ───────────────────────────────────────────────────

    override fun onPoseDetected(pose: DetectedPose?)   { handleDetection(pose) }
    override fun onTargetDetected(target: DetectedPose?) { handleDetection(target) }
    override fun onDebugInfo(info: String)              { logDebug(info) }
    override fun onError(error: String)                 { Log.e(TAG, error) }

    private fun handleDetection(pose: DetectedPose?) {
        mainHandler.post {
            overlayView.setDetectedPose(pose)

            if (pose != null && isActive) {
                detectionCount++
                val now = System.currentTimeMillis()

                if (config.debugMode && now - lastDebugLogTime > 1000) {
                    logDebug(
                        "det/s=$detectionCount " +
                        "target=(${pose.focusPoint.x.toInt()},${pose.focusPoint.y.toInt()}) " +
                        "conf=${String.format("%.2f", pose.confidence)} " +
                        "speed=${smoothedSpeedPx.toInt()}px/f"
                    )
                    detectionCount    = 0
                    lastDebugLogTime  = now
                }

                // ── Motion prediction ──────────────────────────────────────
                var targetX = pose.focusPoint.x
                var targetY = pose.focusPoint.y

                if (config.predictiveAiming) {
                    val prev = previousFocusPoint
                    if (prev != null) {
                        val dt = (now - previousTime).coerceAtLeast(1L)
                        val vx = (pose.focusPoint.x - prev.x) / dt * 16f
                        val vy = (pose.focusPoint.y - prev.y) / dt * 16f
                        targetX += vx * config.predictionStrength
                        targetY += vy * config.predictionStrength
                    }
                }

                previousFocusPoint = PointF(pose.focusPoint.x, pose.focusPoint.y)
                previousTime       = now

                // ── Smoothing ─────────────────────────────────────────────
                applySmoothing(targetX, targetY)

                // ── Adaptive confidence feedback ──────────────────────────
                val frameMovement = hypot(smoothedX - targetX, smoothedY - targetY)
                smoothedSpeedPx   = smoothedSpeedPx * (1f - SPEED_SMOOTH) + frameMovement * SPEED_SMOOTH
                yoloDetector?.cameraSpeedPx = smoothedSpeedPx

                // ── Send movement ─────────────────────────────────────────
                moveCursorTo(smoothedX, smoothedY)

                if (config.triggerBotEnabled) handleTriggerBot(pose, now)

            } else {
                if (config.triggerBotEnabled) resetTriggerBot()
                previousFocusPoint = null
                smoothedSpeedPx    = 0f
                yoloDetector?.cameraSpeedPx = 0f
                onResetMovement?.invoke()
            }
        }
    }

    /**
     * Apply the selected smoothing curve and update smoothedX / smoothedY.
     *
     * All modes respect config.aimSmoothing (0 = instant, 1 = never moves).
     */
    private fun applySmoothing(targetX: Float, targetY: Float) {
        val dx       = targetX - smoothedX
        val dy       = targetY - smoothedY
        val distance = hypot(dx, dy)

        if (distance < 0.5f) return // already there — skip to avoid floating-point jitter

        when (config.smoothingMode) {

            SmoothingMode.LINEAR -> {
                // Constant lerp: cursor moves the same fraction of remaining distance every frame.
                val k = (1f - config.aimSmoothing).coerceIn(0.05f, 1f)
                smoothedX += dx * k
                smoothedY += dy * k
            }

            SmoothingMode.EASE_OUT -> {
                // Speed is proportional to remaining distance — fast start, smooth finish.
                // Equivalent to exponential decay: position = target * (1 - e^-kt).
                // k is derived from aimSmoothing so the user's slider has the same feel as LINEAR.
                val k = (1f - config.aimSmoothing).coerceIn(0.05f, 1f)
                // Scale k by sqrt(distance/fovRadius) so large distances move even faster.
                val distRatio = (distance / config.fovRadius.coerceAtLeast(1f)).coerceIn(0f, 1f)
                val kScaled   = k * (0.5f + 0.5f * sqrt(distRatio))  // range [k*0.5, k]
                smoothedX += dx * kScaled
                smoothedY += dy * kScaled
            }

            SmoothingMode.MAGNETIC -> {
                // Phase 1 — outside snap zone OR moving fast: instant convergence.
                // Phase 2 — inside snap zone AND slow: EASE_OUT for fine precision.
                val snap = config.snapThreshold.coerceAtLeast(10f)
                if (distance > snap || smoothedSpeedPx > MAGNETIC_FAST_THRESHOLD) {
                    // Full speed — jump the majority of the remaining distance in one frame.
                    val k = (1f - config.aimSmoothing * 0.3f).coerceIn(0.5f, 1f)
                    smoothedX += dx * k
                    smoothedY += dy * k
                } else {
                    // Fine precision phase — ease out.
                    val k = (1f - config.aimSmoothing).coerceIn(0.05f, 0.5f)
                    smoothedX += dx * k
                    smoothedY += dy * k
                }
            }
        }

        // Hard snap when already very close (avoids infinite asymptote).
        if (config.snapToTarget && hypot(smoothedX - targetX, smoothedY - targetY) < config.snapThreshold) {
            smoothedX = targetX
            smoothedY = targetY
        }
    }

    // ── Cursor movement ───────────────────────────────────────────────────────

    private fun moveCursorTo(targetX: Float, targetY: Float) {
        if (videoRect.width() == 0f) return

        val movementX = (targetX - videoRect.centerX()) * config.aimAssistStrength * config.aimSpeed
        val movementY = (targetY - videoRect.centerY()) * config.aimAssistStrength * config.aimSpeed

        if (config.debugMode && (abs(movementX) > 0.1f || abs(movementY) > 0.1f)) {
            logDebug("move=(${String.format("%.1f", movementX)}, ${String.format("%.1f", movementY)})")
        }

        onCursorMove?.invoke(movementX, movementY)
    }

    // ── TriggerBot ────────────────────────────────────────────────────────────

    private fun handleTriggerBot(pose: DetectedPose, now: Long) {
        val onTarget = pose.boundingBox.contains(videoRect.centerX(), videoRect.centerY())

        if (onTarget) {
            if (!triggerBotArmed) {
                triggerBotArmed   = true
                lastTriggerTime   = now
                logDebug("TriggerBot: armed")
            }
            if ((now - lastTriggerTime) >= config.triggerBotDelay && !triggerBotFiring) {
                triggerBotFiring  = true
                lastFireTime      = now
                onTriggerBot?.invoke(true)
                logDebug("TriggerBot: FIRE")
            }
            if (triggerBotFiring && (now - lastFireTime) >= config.triggerBotHoldTime) {
                onTriggerBot?.invoke(false)
                triggerBotFiring = false
                if (config.autoFireEnabled && (now - lastFireTime) >= config.autoFireRate) {
                    triggerBotFiring = true
                    lastFireTime     = now
                    onTriggerBot?.invoke(true)
                }
            }
        } else {
            resetTriggerBot()
        }
    }

    private fun resetTriggerBot() {
        if (triggerBotFiring) {
            onTriggerBot?.invoke(false)
            logDebug("TriggerBot: released")
        }
        triggerBotArmed  = false
        triggerBotFiring = false
    }

    private fun logDebug(msg: String) {
        if (config.debugMode) Log.d(TAG, msg)
    }
}
