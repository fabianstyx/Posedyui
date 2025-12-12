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

    private var config = PoseTrackerConfig()
    
    private var currentPose: DetectedPose? = null
    private var videoRect: RectF = RectF()
    private var isTrackingEnabled = false
    
    // Bounding box paint
    private val boxPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    
    // FOV circle paint
    private val focusCirclePaint = Paint().apply {
        color = Color.argb(102, 255, 255, 255) // 40% opacity white
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }
    
    // HUD mask paint
    private val maskPaint = Paint().apply {
        color = Color.argb(38, 255, 255, 255) // 15% opacity white
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    // Focus label paint
    private val labelPaint = Paint().apply {
        color = Color.RED
        textSize = 40f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    
    // Crosshair paint
    private val crosshairPaint = Paint().apply {
        color = Color.argb(180, 0, 255, 0) // Green crosshair
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }
    
    // Target dot paint
    private val targetDotPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun setConfig(newConfig: PoseTrackerConfig) {
        config = newConfig.copy()
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
        isTrackingEnabled = enabled
        invalidate()
    }
    
    fun isTrackingEnabled(): Boolean = isTrackingEnabled

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (videoRect.width() == 0f) return
        
        val centerX = videoRect.centerX()
        val centerY = videoRect.centerY()
        
        // Draw FOV circle (always show when enabled for context)
        if (config.showFocusCircle && isTrackingEnabled && config.enableVisualAssist) {
            canvas.drawCircle(centerX, centerY, config.fovRadius, focusCirclePaint)
        }
        
        // Draw HUD mask area for debugging
        if (config.maskEnabled && config.showMask && isTrackingEnabled) {
            val maskRect = RectF(
                videoRect.left + videoRect.width() * config.maskX,
                videoRect.top + videoRect.height() * config.maskY,
                videoRect.left + videoRect.width() * (config.maskX + config.maskWidth),
                videoRect.top + videoRect.height() * (config.maskY + config.maskHeight)
            )
            canvas.drawRect(maskRect, maskPaint)
        }
        
        // Draw detected pose
        if (config.enableVisualAssist && isTrackingEnabled && currentPose != null) {
            val pose = currentPose!!
            
            // Draw bounding box
            if (config.showBoundingBox) {
                canvas.drawRect(pose.boundingBox, boxPaint)
            }
            
            // Draw focus label
            if (config.showFocusLabel) {
                canvas.drawText(
                    "focus",
                    pose.boundingBox.centerX(),
                    pose.boundingBox.top - 10f,
                    labelPaint
                )
            }
            
            // Draw target dot at focus point
            canvas.drawCircle(pose.focusPoint.x, pose.focusPoint.y, 6f, targetDotPaint)
            
            // Draw line from center to target
            val lineAlpha = 100
            val linePaint = Paint().apply {
                color = Color.argb(lineAlpha, 255, 0, 0)
                style = Paint.Style.STROKE
                strokeWidth = 1f
                isAntiAlias = true
                pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
            }
            canvas.drawLine(centerX, centerY, pose.focusPoint.x, pose.focusPoint.y, linePaint)
        }
        
        // Draw crosshair at center (always visible when tracking)
        if (isTrackingEnabled && config.enableVisualAssist) {
            val crosshairSize = 15f
            canvas.drawLine(centerX - crosshairSize, centerY, centerX + crosshairSize, centerY, crosshairPaint)
            canvas.drawLine(centerX, centerY - crosshairSize, centerX, centerY + crosshairSize, crosshairPaint)
        }
    }
}
