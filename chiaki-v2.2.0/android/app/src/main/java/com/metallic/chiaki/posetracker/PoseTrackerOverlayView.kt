// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.posetracker

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

data class DetectedPose(
    val boundingBox: RectF,
    val focusPoint: PointF,
    val confidence: Float
)

class PoseTrackerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val config = PoseTrackerConfig()
    
    private var currentPose: DetectedPose? = null
    private var videoRect: RectF = RectF()
    
    private val boxPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    
    private val focusCirclePaint = Paint().apply {
        color = Color.argb(102, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }
    
    private val maskPaint = Paint().apply {
        color = Color.argb(38, 255, 255, 255)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val labelPaint = Paint().apply {
        color = Color.RED
        textSize = 40f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    fun setConfig(newConfig: PoseTrackerConfig) {
        config.confidence = newConfig.confidence
        config.enableVisualAssist = newConfig.enableVisualAssist
        config.fovRadius = newConfig.fovRadius
        config.isEnabled = newConfig.isEnabled
        config.showFocusCircle = newConfig.showFocusCircle
        config.maskEnabled = newConfig.maskEnabled
        config.showMask = newConfig.showMask
        config.maskX = newConfig.maskX
        config.maskY = newConfig.maskY
        config.maskWidth = newConfig.maskWidth
        config.maskHeight = newConfig.maskHeight
        invalidate()
    }
    
    fun setVideoRect(rect: RectF) {
        videoRect = rect
        invalidate()
    }
    
    fun setDetectedPose(pose: DetectedPose?) {
        currentPose = pose
        invalidate()
    }
    
    fun setTrackingEnabled(enabled: Boolean) {
        config.isEnabled = enabled
        invalidate()
    }
    
    fun isTrackingEnabled(): Boolean = config.isEnabled

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (videoRect.width() == 0f) return
        
        val centerX = videoRect.centerX()
        val centerY = videoRect.centerY()
        
        if (config.showFocusCircle && config.isEnabled) {
            canvas.drawCircle(centerX, centerY, config.fovRadius, focusCirclePaint)
        }
        
        if (config.maskEnabled && config.showMask && config.isEnabled) {
            val maskRect = RectF(
                videoRect.left + videoRect.width() * config.maskX,
                videoRect.top + videoRect.height() * config.maskY,
                videoRect.left + videoRect.width() * (config.maskX + config.maskWidth),
                videoRect.top + videoRect.height() * (config.maskY + config.maskHeight)
            )
            canvas.drawRect(maskRect, maskPaint)
        }
        
        if (config.enableVisualAssist && config.isEnabled && currentPose != null) {
            val pose = currentPose!!
            canvas.drawRect(pose.boundingBox, boxPaint)
            canvas.drawText(
                "focus",
                pose.boundingBox.centerX(),
                pose.boundingBox.top - 10f,
                labelPaint
            )
        }
    }
}
