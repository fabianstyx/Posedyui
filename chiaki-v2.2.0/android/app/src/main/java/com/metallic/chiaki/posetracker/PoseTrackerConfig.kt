// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.posetracker

data class PoseTrackerConfig(
    var confidence: Float = 0.28f,
    var enableVisualAssist: Boolean = true,
    var fovRadius: Float = 300f,
    var isEnabled: Boolean = false,
    var showFocusCircle: Boolean = true,
    var maskEnabled: Boolean = true,
    var showMask: Boolean = false,
    var maskX: Float = 0.0f,
    var maskY: Float = 0.27f,
    var maskWidth: Float = 0.43f,
    var maskHeight: Float = 0.74f
)
