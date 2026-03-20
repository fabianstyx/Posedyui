// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.posetracker

import android.content.Context
import androidx.preference.PreferenceManager

class PoseTrackerSettings(context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    companion object {
        // Core
        const val KEY_ENABLED = "pose_tracker_enabled"
        const val KEY_DETECTOR_TYPE = "pose_tracker_detector_type"
        const val KEY_CONFIDENCE = "pose_tracker_confidence"
        const val KEY_FOV_RADIUS = "pose_tracker_fov"
        const val KEY_DEBUG_MODE = "pose_tracker_debug_mode"

        // Visual
        const val KEY_VISUAL_ASSIST = "pose_tracker_visual_assist"
        const val KEY_SHOW_FOCUS_CIRCLE = "pose_tracker_show_focus_circle"
        const val KEY_SHOW_BOUNDING_BOX = "pose_tracker_show_bounding_box"
        const val KEY_SHOW_FOCUS_LABEL = "pose_tracker_show_focus_label"

        // TriggerBot
        const val KEY_TRIGGERBOT_ENABLED = "pose_tracker_triggerbot_enabled"
        const val KEY_TRIGGERBOT_DELAY = "pose_tracker_triggerbot_delay"
        const val KEY_TRIGGERBOT_HOLD_TIME = "pose_tracker_triggerbot_hold"
        const val KEY_AUTO_FIRE_ENABLED = "pose_tracker_autofire_enabled"
        const val KEY_AUTO_FIRE_RATE = "pose_tracker_autofire_rate"

        // Aim
        const val KEY_AIM_SMOOTHING = "pose_tracker_aim_smoothing"
        const val KEY_AIM_SPEED = "pose_tracker_aim_speed"
        const val KEY_AIM_ASSIST_STRENGTH = "pose_tracker_aim_assist_strength"
        const val KEY_SNAP_TO_TARGET = "pose_tracker_snap_to_target"
        const val KEY_SNAP_THRESHOLD = "pose_tracker_snap_threshold"
        const val KEY_SMOOTHING_MODE = "pose_tracker_smoothing_mode"

        // Activation
        const val KEY_ACTIVATION_MODE = "pose_tracker_activation_mode"
        const val KEY_CONTROLLER_BUTTON = "pose_tracker_controller_button"

        // Target
        const val KEY_TARGET_PRIORITY = "pose_tracker_target_priority"
        const val KEY_HEADSHOT_MODE = "pose_tracker_headshot_mode"
        const val KEY_HEAD_OFFSET_Y = "pose_tracker_head_offset"

        // Mask
        const val KEY_MASK_ENABLED = "pose_tracker_mask_enabled"
        const val KEY_SHOW_MASK = "pose_tracker_show_mask"
        const val KEY_MASK_X = "pose_tracker_mask_x"
        const val KEY_MASK_Y = "pose_tracker_mask_y"
        const val KEY_MASK_WIDTH = "pose_tracker_mask_width"
        const val KEY_MASK_HEIGHT = "pose_tracker_mask_height"

        // Advanced
        const val KEY_PROCESSING_INTERVAL = "pose_tracker_processing_interval"
        const val KEY_MAX_TARGETS = "pose_tracker_max_targets"
        const val KEY_PREDICTIVE_AIMING = "pose_tracker_predictive_aiming"
        const val KEY_PREDICTION_STRENGTH = "pose_tracker_prediction_strength"

        // Adaptive confidence
        const val KEY_ADAPTIVE_CONFIDENCE = "pose_tracker_adaptive_confidence"
        const val KEY_ADAPTIVE_CONFIDENCE_MIN = "pose_tracker_adaptive_confidence_min"

        // Custom model
        const val KEY_CUSTOM_MODEL_PATH = "pose_tracker_custom_model_path"
        const val KEY_CUSTOM_MODEL_INPUT_SIZE = "pose_tracker_custom_model_input_size"

        private const val DEFAULT_DETECTOR_TYPE_ORDINAL = 1
    }

    fun loadConfig(): PoseTrackerConfig {
        return PoseTrackerConfig(
            isEnabled = prefs.getBoolean(KEY_ENABLED, false),
            detectorType = safeEnumFromOrdinal<DetectorType>(
                prefs.getString(KEY_DETECTOR_TYPE, DEFAULT_DETECTOR_TYPE_ORDINAL.toString()),
                DEFAULT_DETECTOR_TYPE_ORDINAL
            ),
            confidence = prefs.getInt(KEY_CONFIDENCE, 28) / 100f,
            fovRadius = prefs.getInt(KEY_FOV_RADIUS, 300).toFloat(),
            debugMode = prefs.getBoolean(KEY_DEBUG_MODE, false),

            enableVisualAssist = prefs.getBoolean(KEY_VISUAL_ASSIST, true),
            showFocusCircle = prefs.getBoolean(KEY_SHOW_FOCUS_CIRCLE, true),
            showBoundingBox = prefs.getBoolean(KEY_SHOW_BOUNDING_BOX, true),
            showFocusLabel = prefs.getBoolean(KEY_SHOW_FOCUS_LABEL, true),

            triggerBotEnabled = prefs.getBoolean(KEY_TRIGGERBOT_ENABLED, false),
            triggerBotDelay = prefs.getInt(KEY_TRIGGERBOT_DELAY, 50),
            triggerBotHoldTime = prefs.getInt(KEY_TRIGGERBOT_HOLD_TIME, 100),
            autoFireEnabled = prefs.getBoolean(KEY_AUTO_FIRE_ENABLED, false),
            autoFireRate = prefs.getInt(KEY_AUTO_FIRE_RATE, 100),

            aimSmoothing = prefs.getInt(KEY_AIM_SMOOTHING, 50) / 100f,
            aimSpeed = prefs.getInt(KEY_AIM_SPEED, 100) / 100f,
            aimAssistStrength = prefs.getInt(KEY_AIM_ASSIST_STRENGTH, 80) / 100f,
            snapToTarget = prefs.getBoolean(KEY_SNAP_TO_TARGET, false),
            snapThreshold = prefs.getInt(KEY_SNAP_THRESHOLD, 50).toFloat(),
            smoothingMode = safeEnumFromOrdinal<SmoothingMode>(
                prefs.getString(KEY_SMOOTHING_MODE, "1"), // EASE_OUT by default
                1
            ),

            activationMode = safeEnumFromOrdinal<ActivationMode>(
                prefs.getString(KEY_ACTIVATION_MODE, "0"),
                0
            ),
            controllerActivationButton = safeEnumFromOrdinal<ControllerButton>(
                prefs.getString(KEY_CONTROLLER_BUTTON, "0"),
                0
            ),

            targetPriority = safeEnumFromOrdinal<TargetPriority>(
                prefs.getString(KEY_TARGET_PRIORITY, "0"),
                0
            ),
            headShotMode = prefs.getBoolean(KEY_HEADSHOT_MODE, true),
            headOffsetY = prefs.getInt(KEY_HEAD_OFFSET_Y, 7) / 100f,

            maskEnabled = prefs.getBoolean(KEY_MASK_ENABLED, true),
            showMask = prefs.getBoolean(KEY_SHOW_MASK, false),
            maskX = prefs.getInt(KEY_MASK_X, 0) / 100f,
            maskY = prefs.getInt(KEY_MASK_Y, 27) / 100f,
            maskWidth = prefs.getInt(KEY_MASK_WIDTH, 43) / 100f,
            maskHeight = prefs.getInt(KEY_MASK_HEIGHT, 74) / 100f,

            processingInterval = prefs.getInt(KEY_PROCESSING_INTERVAL, 33),
            maxTargets = prefs.getInt(KEY_MAX_TARGETS, 5),
            predictiveAiming = prefs.getBoolean(KEY_PREDICTIVE_AIMING, false),
            predictionStrength = prefs.getInt(KEY_PREDICTION_STRENGTH, 30) / 100f,

            adaptiveConfidence = prefs.getBoolean(KEY_ADAPTIVE_CONFIDENCE, true),
            adaptiveConfidenceMin = prefs.getInt(KEY_ADAPTIVE_CONFIDENCE_MIN, 18) / 100f,

            customModelPath = prefs.getString(KEY_CUSTOM_MODEL_PATH, null)
                ?.takeIf { it.isNotBlank() },
            customModelInputSize = prefs.getInt(KEY_CUSTOM_MODEL_INPUT_SIZE, 640)
        )
    }

    fun saveConfig(config: PoseTrackerConfig) {
        prefs.edit().apply {
            putBoolean(KEY_ENABLED, config.isEnabled)
            putString(KEY_DETECTOR_TYPE, config.detectorType.ordinal.toString())
            putInt(KEY_CONFIDENCE, (config.confidence * 100).toInt())
            putInt(KEY_FOV_RADIUS, config.fovRadius.toInt())
            putBoolean(KEY_DEBUG_MODE, config.debugMode)

            putBoolean(KEY_VISUAL_ASSIST, config.enableVisualAssist)
            putBoolean(KEY_SHOW_FOCUS_CIRCLE, config.showFocusCircle)
            putBoolean(KEY_SHOW_BOUNDING_BOX, config.showBoundingBox)
            putBoolean(KEY_SHOW_FOCUS_LABEL, config.showFocusLabel)

            putBoolean(KEY_TRIGGERBOT_ENABLED, config.triggerBotEnabled)
            putInt(KEY_TRIGGERBOT_DELAY, config.triggerBotDelay)
            putInt(KEY_TRIGGERBOT_HOLD_TIME, config.triggerBotHoldTime)
            putBoolean(KEY_AUTO_FIRE_ENABLED, config.autoFireEnabled)
            putInt(KEY_AUTO_FIRE_RATE, config.autoFireRate)

            putInt(KEY_AIM_SMOOTHING, (config.aimSmoothing * 100).toInt())
            putInt(KEY_AIM_SPEED, (config.aimSpeed * 100).toInt())
            putInt(KEY_AIM_ASSIST_STRENGTH, (config.aimAssistStrength * 100).toInt())
            putBoolean(KEY_SNAP_TO_TARGET, config.snapToTarget)
            putInt(KEY_SNAP_THRESHOLD, config.snapThreshold.toInt())
            putString(KEY_SMOOTHING_MODE, config.smoothingMode.ordinal.toString())

            putString(KEY_ACTIVATION_MODE, config.activationMode.ordinal.toString())
            putString(KEY_CONTROLLER_BUTTON, config.controllerActivationButton.ordinal.toString())

            putString(KEY_TARGET_PRIORITY, config.targetPriority.ordinal.toString())
            putBoolean(KEY_HEADSHOT_MODE, config.headShotMode)
            putInt(KEY_HEAD_OFFSET_Y, (config.headOffsetY * 100).toInt())

            putBoolean(KEY_MASK_ENABLED, config.maskEnabled)
            putBoolean(KEY_SHOW_MASK, config.showMask)
            putInt(KEY_MASK_X, (config.maskX * 100).toInt())
            putInt(KEY_MASK_Y, (config.maskY * 100).toInt())
            putInt(KEY_MASK_WIDTH, (config.maskWidth * 100).toInt())
            putInt(KEY_MASK_HEIGHT, (config.maskHeight * 100).toInt())

            putInt(KEY_PROCESSING_INTERVAL, config.processingInterval)
            putInt(KEY_MAX_TARGETS, config.maxTargets)
            putBoolean(KEY_PREDICTIVE_AIMING, config.predictiveAiming)
            putInt(KEY_PREDICTION_STRENGTH, (config.predictionStrength * 100).toInt())

            putBoolean(KEY_ADAPTIVE_CONFIDENCE, config.adaptiveConfidence)
            putInt(KEY_ADAPTIVE_CONFIDENCE_MIN, (config.adaptiveConfidenceMin * 100).toInt())

            putString(KEY_CUSTOM_MODEL_PATH, config.customModelPath ?: "")
            putInt(KEY_CUSTOM_MODEL_INPUT_SIZE, config.customModelInputSize)

            apply()
        }
    }

    /** Save only the custom model path without touching other settings. */
    fun saveCustomModelPath(path: String?, inputSize: Int = 640) {
        prefs.edit()
            .putString(KEY_CUSTOM_MODEL_PATH, path ?: "")
            .putInt(KEY_CUSTOM_MODEL_INPUT_SIZE, inputSize)
            .apply()
    }

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    private inline fun <reified T : Enum<T>> safeEnumFromOrdinal(
        ordinalStr: String?,
        default: Int
    ): T {
        val ordinal = ordinalStr?.toIntOrNull() ?: default
        val values = enumValues<T>()
        return if (ordinal in values.indices) values[ordinal] else values[default]
    }
}
