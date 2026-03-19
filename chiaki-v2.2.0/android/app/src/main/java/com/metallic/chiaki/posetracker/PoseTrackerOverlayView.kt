// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.posetracker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
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

    // FIX: all Paint objects are allocated once as fields, NOT inside onDraw().
    // Creating Paint instances on every draw call causes excessive GC pressure and
    // jank on the UI thread — especially at 30-60 fps.

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val focusCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(102, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(38, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        textSize = 40f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 0, 255, 0)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val targetDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    // FIX: line paint also allocated once; DashPathEffect is immutable so it is safe to reuse
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 255, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 1f
        pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
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

        if (config.showFocusCircle && isTrackingEnabled && config.enableVisualAssist) {
            canvas.drawCircle(centerX, centerY, config.fovRadius, focusCirclePaint)
        }

        if (config.maskEnabled && config.showMask && isTrackingEnabled) {
            val maskRect = RectF(
                videoRect.left + videoRect.width() * config.maskX,
                videoRect.top + videoRect.height() * config.maskY,
                videoRect.left + videoRect.width() * (config.maskX + config.maskWidth),
                videoRect.top + videoRect.height() * (config.maskY + config.maskHeight)
            )
            canvas.drawRect(maskRect, maskPaint)
        }

        if (config.enableVisualAssist && isTrackingEnabled && currentPose != null) {
            val pose = currentPose!!

            if (config.showBoundingBox) {
                canvas.drawRect(pose.boundingBox, boxPaint)
            }

            if (config.showFocusLabel) {
                canvas.drawText(
                    "focus",
                    pose.boundingBox.centerX(),
                    pose.boundingBox.top - 10f,
                    labelPaint
                )
            }

            canvas.drawCircle(pose.focusPoint.x, pose.focusPoint.y, 6f, targetDotPaint)

            canvas.drawLine(centerX, centerY, pose.focusPoint.x, pose.focusPoint.y, linePaint)
        }

        if (isTrackingEnabled && config.enableVisualAssist) {
            val crosshairSize = 15f
            canvas.drawLine(
                centerX - crosshairSize, centerY,
                centerX + crosshairSize, centerY,
                crosshairPaint
            )
            canvas.drawLine(
                centerX, centerY - crosshairSize,
                centerX, centerY + crosshairSize,
                crosshairPaint
            )
        }
    }
}
