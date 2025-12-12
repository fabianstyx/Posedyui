// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.posetracker

import android.graphics.Bitmap
import android.graphics.RectF

interface DetectorListener {
    fun onTargetDetected(target: DetectedPose?)
    fun onError(error: String)
    fun onDebugInfo(info: String)
}

interface BaseDetector {
    fun initialize()
    fun detect(bitmap: Bitmap, videoRect: RectF)
    fun updateConfig(config: PoseTrackerConfig)
    fun close()
}
