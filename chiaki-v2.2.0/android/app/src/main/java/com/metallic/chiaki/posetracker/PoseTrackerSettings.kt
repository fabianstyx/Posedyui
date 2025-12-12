// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.posetracker

import android.content.Context
import android.content.SharedPreferences

class PoseTrackerSettings(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "pose_tracker_settings"
        
        // Core settings keys
        private const val KEY_ENABLED = "pose_tracker_enabled"
        private const val KEY_CONFIDENCE = "pose_tracker_confidence"
        private const val KEY_FOV_RADIUS = "pose_tracker_fov_radius"
        
        // Visual settings keys
        private const val KEY_VISUAL_ASSIST = "pose_tracker_visual_assist"
        private const val KEY_SHOW_FOCUS_CIRCLE = "pose_tracker_show_focus_circle"
        private const val KEY_SHOW_BOUNDING_BOX = "pose_tracker_show_bounding_box"
        private const val KEY_SHOW_FOCUS_LABEL = "pose_tracker_show_focus_label"
        
        // TriggerBot settings keys
        private const val KEY_TRIGGERBOT_ENABLED = "pose_tracker_triggerbot_enabled"
        private const val KEY_TRIGGERBOT_DELAY = "pose_tracker_triggerbot_delay"
        private const val KEY_TRIGGERBOT_HOLD_TIME = "pose_tracker_triggerbot_hold_time"
        private const val KEY_AUTO_FIRE_ENABLED = "pose_tracker_auto_fire_enabled"
        private const val KEY_AUTO_FIRE_RATE = "pose_tracker_auto_fire_rate"
        
        // Aim settings keys
        private const val KEY_AIM_SMOOTHING = "pose_tracker_aim_smoothing"
        private const val KEY_AIM_SPEED = "pose_tracker_aim_speed"
        private const val KEY_AIM_ASSIST_STRENGTH = "pose_tracker_aim_assist_strength"
        private const val KEY_SNAP_TO_TARGET = "pose_tracker_snap_to_target"
        private const val KEY_SNAP_THRESHOLD = "pose_tracker_snap_threshold"
        
        // Activation settings keys
        private const val KEY_ACTIVATION_MODE = "pose_tracker_activation_mode"
        private const val KEY_CONTROLLER_BUTTON = "pose_tracker_controller_button"
        
        // Target priority keys
        private const val KEY_TARGET_PRIORITY = "pose_tracker_target_priority"
        private const val KEY_HEADSHOT_MODE = "pose_tracker_headshot_mode"
        private const val KEY_HEAD_OFFSET_Y = "pose_tracker_head_offset_y"
        
        // Mask settings keys
        private const val KEY_MASK_ENABLED = "pose_tracker_mask_enabled"
        private const val KEY_SHOW_MASK = "pose_tracker_show_mask"
        private const val KEY_MASK_X = "pose_tracker_mask_x"
        private const val KEY_MASK_Y = "pose_tracker_mask_y"
        private const val KEY_MASK_WIDTH = "pose_tracker_mask_width"
        private const val KEY_MASK_HEIGHT = "pose_tracker_mask_height"
        
        // Advanced settings keys
        private const val KEY_PROCESSING_INTERVAL = "pose_tracker_processing_interval"
        private const val KEY_MAX_TARGETS = "pose_tracker_max_targets"
        private const val KEY_PREDICTIVE_AIMING = "pose_tracker_predictive_aiming"
        private const val KEY_PREDICTION_STRENGTH = "pose_tracker_prediction_strength"
    }
    
    fun loadConfig(): PoseTrackerConfig {
        return PoseTrackerConfig(
            // Core settings
            isEnabled = prefs.getBoolean(KEY_ENABLED, false),
            confidence = prefs.getFloat(KEY_CONFIDENCE, 0.28f),
            fovRadius = prefs.getFloat(KEY_FOV_RADIUS, 300f),
            
            // Visual settings
            enableVisualAssist = prefs.getBoolean(KEY_VISUAL_ASSIST, true),
            showFocusCircle = prefs.getBoolean(KEY_SHOW_FOCUS_CIRCLE, true),
            showBoundingBox = prefs.getBoolean(KEY_SHOW_BOUNDING_BOX, true),
            showFocusLabel = prefs.getBoolean(KEY_SHOW_FOCUS_LABEL, true),
            
            // TriggerBot settings
            triggerBotEnabled = prefs.getBoolean(KEY_TRIGGERBOT_ENABLED, false),
            triggerBotDelay = prefs.getInt(KEY_TRIGGERBOT_DELAY, 50),
            triggerBotHoldTime = prefs.getInt(KEY_TRIGGERBOT_HOLD_TIME, 100),
            autoFireEnabled = prefs.getBoolean(KEY_AUTO_FIRE_ENABLED, false),
            autoFireRate = prefs.getInt(KEY_AUTO_FIRE_RATE, 100),
            
            // Aim settings
            aimSmoothing = prefs.getFloat(KEY_AIM_SMOOTHING, 0.5f),
            aimSpeed = prefs.getFloat(KEY_AIM_SPEED, 1.0f),
            aimAssistStrength = prefs.getFloat(KEY_AIM_ASSIST_STRENGTH, 0.8f),
            snapToTarget = prefs.getBoolean(KEY_SNAP_TO_TARGET, false),
            snapThreshold = prefs.getFloat(KEY_SNAP_THRESHOLD, 50f),
            
            // Activation settings
            activationMode = ActivationMode.values().getOrElse(
                prefs.getInt(KEY_ACTIVATION_MODE, 0)
            ) { ActivationMode.TOGGLE },
            controllerActivationButton = ControllerButton.values().getOrElse(
                prefs.getInt(KEY_CONTROLLER_BUTTON, 0)
            ) { ControllerButton.LT },
            
            // Target priority
            targetPriority = TargetPriority.values().getOrElse(
                prefs.getInt(KEY_TARGET_PRIORITY, 0)
            ) { TargetPriority.CLOSEST_TO_CENTER },
            headShotMode = prefs.getBoolean(KEY_HEADSHOT_MODE, true),
            headOffsetY = prefs.getFloat(KEY_HEAD_OFFSET_Y, 0.07f),
            
            // Mask settings
            maskEnabled = prefs.getBoolean(KEY_MASK_ENABLED, true),
            showMask = prefs.getBoolean(KEY_SHOW_MASK, false),
            maskX = prefs.getFloat(KEY_MASK_X, 0.0f),
            maskY = prefs.getFloat(KEY_MASK_Y, 0.27f),
            maskWidth = prefs.getFloat(KEY_MASK_WIDTH, 0.43f),
            maskHeight = prefs.getFloat(KEY_MASK_HEIGHT, 0.74f),
            
            // Advanced settings
            processingInterval = prefs.getInt(KEY_PROCESSING_INTERVAL, 33),
            maxTargets = prefs.getInt(KEY_MAX_TARGETS, 5),
            predictiveAiming = prefs.getBoolean(KEY_PREDICTIVE_AIMING, false),
            predictionStrength = prefs.getFloat(KEY_PREDICTION_STRENGTH, 0.3f)
        )
    }
    
    fun saveConfig(config: PoseTrackerConfig) {
        prefs.edit().apply {
            // Core settings
            putBoolean(KEY_ENABLED, config.isEnabled)
            putFloat(KEY_CONFIDENCE, config.confidence)
            putFloat(KEY_FOV_RADIUS, config.fovRadius)
            
            // Visual settings
            putBoolean(KEY_VISUAL_ASSIST, config.enableVisualAssist)
            putBoolean(KEY_SHOW_FOCUS_CIRCLE, config.showFocusCircle)
            putBoolean(KEY_SHOW_BOUNDING_BOX, config.showBoundingBox)
            putBoolean(KEY_SHOW_FOCUS_LABEL, config.showFocusLabel)
            
            // TriggerBot settings
            putBoolean(KEY_TRIGGERBOT_ENABLED, config.triggerBotEnabled)
            putInt(KEY_TRIGGERBOT_DELAY, config.triggerBotDelay)
            putInt(KEY_TRIGGERBOT_HOLD_TIME, config.triggerBotHoldTime)
            putBoolean(KEY_AUTO_FIRE_ENABLED, config.autoFireEnabled)
            putInt(KEY_AUTO_FIRE_RATE, config.autoFireRate)
            
            // Aim settings
            putFloat(KEY_AIM_SMOOTHING, config.aimSmoothing)
            putFloat(KEY_AIM_SPEED, config.aimSpeed)
            putFloat(KEY_AIM_ASSIST_STRENGTH, config.aimAssistStrength)
            putBoolean(KEY_SNAP_TO_TARGET, config.snapToTarget)
            putFloat(KEY_SNAP_THRESHOLD, config.snapThreshold)
            
            // Activation settings
            putInt(KEY_ACTIVATION_MODE, config.activationMode.ordinal)
            putInt(KEY_CONTROLLER_BUTTON, config.controllerActivationButton.ordinal)
            
            // Target priority
            putInt(KEY_TARGET_PRIORITY, config.targetPriority.ordinal)
            putBoolean(KEY_HEADSHOT_MODE, config.headShotMode)
            putFloat(KEY_HEAD_OFFSET_Y, config.headOffsetY)
            
            // Mask settings
            putBoolean(KEY_MASK_ENABLED, config.maskEnabled)
            putBoolean(KEY_SHOW_MASK, config.showMask)
            putFloat(KEY_MASK_X, config.maskX)
            putFloat(KEY_MASK_Y, config.maskY)
            putFloat(KEY_MASK_WIDTH, config.maskWidth)
            putFloat(KEY_MASK_HEIGHT, config.maskHeight)
            
            // Advanced settings
            putInt(KEY_PROCESSING_INTERVAL, config.processingInterval)
            putInt(KEY_MAX_TARGETS, config.maxTargets)
            putBoolean(KEY_PREDICTIVE_AIMING, config.predictiveAiming)
            putFloat(KEY_PREDICTION_STRENGTH, config.predictionStrength)
            
            apply()
        }
    }
    
    // Individual setters for preference changes
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
    
    fun setConfidence(confidence: Float) {
        prefs.edit().putFloat(KEY_CONFIDENCE, confidence).apply()
    }
    
    fun setFovRadius(radius: Float) {
        prefs.edit().putFloat(KEY_FOV_RADIUS, radius).apply()
    }
    
    fun setVisualAssist(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VISUAL_ASSIST, enabled).apply()
    }
    
    fun setTriggerBotEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TRIGGERBOT_ENABLED, enabled).apply()
    }
    
    fun setTriggerBotDelay(delay: Int) {
        prefs.edit().putInt(KEY_TRIGGERBOT_DELAY, delay).apply()
    }
    
    fun setAimSmoothing(smoothing: Float) {
        prefs.edit().putFloat(KEY_AIM_SMOOTHING, smoothing).apply()
    }
    
    fun setAimSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_AIM_SPEED, speed).apply()
    }
    
    fun setActivationMode(mode: ActivationMode) {
        prefs.edit().putInt(KEY_ACTIVATION_MODE, mode.ordinal).apply()
    }
    
    fun setControllerButton(button: ControllerButton) {
        prefs.edit().putInt(KEY_CONTROLLER_BUTTON, button.ordinal).apply()
    }
    
    fun setHeadshotMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HEADSHOT_MODE, enabled).apply()
    }
    
    fun setMaskEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MASK_ENABLED, enabled).apply()
    }
    
    fun setShowMask(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_MASK, show).apply()
    }
}
