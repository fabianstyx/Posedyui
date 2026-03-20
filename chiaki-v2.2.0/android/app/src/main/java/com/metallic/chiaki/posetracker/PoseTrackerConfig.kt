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

    // Smoothing mode — controls how the cursor accelerates toward the target
    // LINEAR     : constant lerp (original behaviour)
    // EASE_OUT   : fast when far, decelerates near target — most natural for FPS
    // MAGNETIC   : instant snap into the FOV zone, then ease-out for fine precision
    var smoothingMode: SmoothingMode = SmoothingMode.EASE_OUT,

    // Activation settings
    var activationMode: ActivationMode = ActivationMode.TOGGLE,
    var controllerActivationButton: ControllerButton = ControllerButton.LT,

    // Target priority
    var targetPriority: TargetPriority = TargetPriority.CLOSEST_TO_CENTER,
    var headShotMode: Boolean = true,
    // Fraction of bounding-box height from the top where the aim point is placed.
    // 0.07 = top 7% = forehead area (best for FPS headshots)
    var headOffsetY: Float = 0.07f,

    // HUD Mask settings (ignore HUD elements in the detection area)
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
    var predictionStrength: Float = 0.3f,

    // Adaptive confidence: lowers the detection threshold when the camera is moving fast
    // (more detections during rapid movement) and raises it when the camera is still
    // (fewer false positives when aiming precisely).
    var adaptiveConfidence: Boolean = true,
    // Minimum confidence floor used when camera speed is at its maximum.
    var adaptiveConfidenceMin: Float = 0.18f,

    // Custom model: path to a user-supplied .tflite file on device storage.
    // Leave null/blank to use the bundled yolov8n.tflite.
    // The model must follow YOLOv8 output format [1, num_attrs, num_detections].
    // FLOAT32 and UINT8/INT8 quantized models are both supported — the input
    // buffer format is detected automatically from the model's input tensor.
    var customModelPath: String? = null,
    // Expected square input size of the custom model (e.g. 320, 416, 640).
    // Ignored when using the built-in model (always 640).
    var customModelInputSize: Int = 640
)

enum class SmoothingMode {
    LINEAR,
    EASE_OUT,
    MAGNETIC
}

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
