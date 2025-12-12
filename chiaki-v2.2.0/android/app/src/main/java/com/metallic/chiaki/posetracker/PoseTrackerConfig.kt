// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.posetracker

data class PoseTrackerConfig(
    // Core settings
    var isEnabled: Boolean = false,
    var detectorType: DetectorType = DetectorType.YOLO_OBJECT,
    var confidence: Float = 0.28f,
    var fovRadius: Float = 300f,
    var debugMode: Boolean = false,
    
    // Visual settings
    var enableVisualAssist: Boolean = true,
    var showFocusCircle: Boolean = true,
    var showBoundingBox: Boolean = true,
    var showFocusLabel: Boolean = true,
    
    // TriggerBot settings
    var triggerBotEnabled: Boolean = false,
    var triggerBotDelay: Int = 50,
    var triggerBotHoldTime: Int = 100,
    var autoFireEnabled: Boolean = false,
    var autoFireRate: Int = 100,
    
    // Aim settings
    var aimSmoothing: Float = 0.5f,
    var aimSpeed: Float = 1.0f,
    var aimAssistStrength: Float = 0.8f,
    var snapToTarget: Boolean = false,
    var snapThreshold: Float = 50f,
    
    // Activation settings
    var activationMode: ActivationMode = ActivationMode.TOGGLE,
    var controllerActivationButton: ControllerButton = ControllerButton.LT,
    
    // Target priority
    var targetPriority: TargetPriority = TargetPriority.CLOSEST_TO_CENTER,
    var headShotMode: Boolean = true,
    var headOffsetY: Float = 0.07f,
    
    // HUD Mask settings (ignore HUD elements)
    var maskEnabled: Boolean = true,
    var showMask: Boolean = false,
    var maskX: Float = 0.0f,
    var maskY: Float = 0.27f,
    var maskWidth: Float = 0.43f,
    var maskHeight: Float = 0.74f,
    
    // Advanced settings
    var processingInterval: Int = 33,
    var maxTargets: Int = 5,
    var predictiveAiming: Boolean = false,
    var predictionStrength: Float = 0.3f
)

enum class ActivationMode {
    TOGGLE,
    HOLD,
    ALWAYS_ON
}

enum class ControllerButton {
    LT, RT, LB, RB, L3, R3, A, B, X, Y
}

enum class TargetPriority {
    CLOSEST_TO_CENTER,
    CLOSEST_TO_CROSSHAIR,
    LARGEST_TARGET,
    HIGHEST_CONFIDENCE
}
