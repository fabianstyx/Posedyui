// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.posetracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class YoloDetectorHelper(
    private val context: Context,
    private var config: PoseTrackerConfig,
    private val listener: DetectorListener
) : BaseDetector {

    private var interpreter: Interpreter? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var isInitialized = false

    // FIX: @Volatile ensures visibility of isProcessing across threads
    @Volatile
    private var isProcessing = false

    companion object {
        private const val MODEL_FILE = "yolov8n.tflite"
        private const val INPUT_SIZE = 640
        private const val NUM_CLASSES = 80
        private const val PERSON_CLASS_ID = 0
        private const val IOU_THRESHOLD = 0.45f
        private const val EXECUTOR_SHUTDOWN_TIMEOUT_MS = 500L
    }

    override fun initialize() {
        executor.execute {
            try {
                val modelBuffer = loadModelFile()
                if (modelBuffer != null) {
                    val options = Interpreter.Options()

                    // FIX: GpuDelegate() no-arg constructor avoids any reference to
                    // GpuDelegateFactory.Options or GpuDelegate.Options.  Both of those types
                    // require tensorflow-lite-gpu-delegate-plugin in the classpath; without it
                    // the Kotlin compiler fails to resolve the class hierarchy and rejects even
                    // a subtype argument.  The no-arg constructor uses sensible defaults and
                    // compiles cleanly against tensorflow-lite-gpu:2.13.0 alone.
                    val compatList = CompatibilityList()
                    if (compatList.isDelegateSupportedOnThisDevice) {
                        @Suppress("DEPRECATION")
                        options.addDelegate(GpuDelegate())
                        listener.onDebugInfo("YOLO: Using GPU acceleration")
                    } else {
                        options.setNumThreads(4)
                        listener.onDebugInfo("YOLO: Using CPU with 4 threads")
                    }

                    interpreter = Interpreter(modelBuffer, options)
                    isInitialized = true
                    listener.onDebugInfo("YOLO: Initialized successfully")
                } else {
                    listener.onDebugInfo("YOLO: Model file not found, using fallback detection")
                    isInitialized = true
                }
            } catch (e: Exception) {
                listener.onError("YOLO init failed: ${e.message}")
                listener.onDebugInfo("YOLO: Fallback to simple detection")
                isInitialized = true
            }
        }
    }

    // FIX: use try-with-resources (use{}) to guarantee FileInputStream and
    // AssetFileDescriptor are always closed — prevents file descriptor leaks
    private fun loadModelFile(): MappedByteBuffer? {
        return try {
            context.assets.openFd(MODEL_FILE).use { afd ->
                FileInputStream(afd.fileDescriptor).use { inputStream ->
                    inputStream.channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        afd.startOffset,
                        afd.declaredLength
                    )
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun detect(bitmap: Bitmap, videoRect: RectF) {
        if (!isInitialized || isProcessing) {
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }
        if (bitmap.isRecycled) return
        isProcessing = true

        // Copy bitmap before recycling; only the copy is safe to use on the background thread
        val bitmapCopy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        bitmap.recycle()

        if (bitmapCopy == null) {
            isProcessing = false
            return
        }

        executor.execute {
            try {
                // FIX: pass bitmapCopy — not the already-recycled original — to inference
                val detections = if (interpreter != null) {
                    runYoloInference(bitmapCopy, videoRect)
                } else {
                    runFallbackDetection(bitmapCopy, videoRect)
                }

                val bestTarget = findBestTarget(detections, videoRect)
                listener.onTargetDetected(bestTarget)

                if (bestTarget != null) {
                    listener.onDebugInfo(
                        "YOLO: Target found at (${bestTarget.focusPoint.x.toInt()}, " +
                            "${bestTarget.focusPoint.y.toInt()})"
                    )
                }

                bitmapCopy.recycle()
                isProcessing = false
            } catch (e: Exception) {
                listener.onError("YOLO detection error: ${e.message}")
                bitmapCopy.recycle()
                isProcessing = false
            }
        }
    }

    private fun runYoloInference(bitmap: Bitmap, videoRect: RectF): List<Detection> {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val inputBuffer = convertBitmapToByteBuffer(resizedBitmap)
        resizedBitmap.recycle()

        val outputShape = interpreter?.getOutputTensor(0)?.shape() ?: return emptyList()
        val outputBuffer = Array(1) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }

        interpreter?.run(inputBuffer, outputBuffer)

        return parseYoloOutput(outputBuffer[0], bitmap.width, bitmap.height, videoRect)
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            byteBuffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
            byteBuffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
            byteBuffer.putFloat((pixel and 0xFF) / 255.0f)
        }

        return byteBuffer
    }

    private fun parseYoloOutput(
        output: Array<FloatArray>,
        imgWidth: Int,
        imgHeight: Int,
        videoRect: RectF
    ): List<Detection> {
        val detections = mutableListOf<Detection>()
        if (output.isEmpty() || output[0].isEmpty()) return detections

        // FIX: YOLOv8 output shape is [num_attributes, num_detections].
        // output[0].size gives the number of detections (columns), not output.size (rows = attributes).
        val numDetections = output[0].size

        for (i in 0 until numDetections) {
            val cx = output[0][i]
            val cy = output[1][i]
            val w = output[2][i]
            val h = output[3][i]

            var maxClassScore = 0f
            var classId = -1
            for (c in 0 until NUM_CLASSES) {
                val score = output[4 + c][i]
                if (score > maxClassScore) {
                    maxClassScore = score
                    classId = c
                }
            }

            if (classId == PERSON_CLASS_ID && maxClassScore >= config.confidence) {
                val scaleX = videoRect.width() / INPUT_SIZE
                val scaleY = videoRect.height() / INPUT_SIZE

                val left = videoRect.left + (cx - w / 2) * scaleX
                val top = videoRect.top + (cy - h / 2) * scaleY
                val right = videoRect.left + (cx + w / 2) * scaleX
                val bottom = videoRect.top + (cy + h / 2) * scaleY

                detections.add(
                    Detection(
                        boundingBox = RectF(left, top, right, bottom),
                        confidence = maxClassScore,
                        classId = classId
                    )
                )
            }
        }

        return applyNMS(detections)
    }

    private fun runFallbackDetection(bitmap: Bitmap, videoRect: RectF): List<Detection> {
        val detections = mutableListOf<Detection>()
        val width = bitmap.width
        val height = bitmap.height
        val centerX = width / 2
        val centerY = height / 2

        val scaleX = videoRect.width() / width
        val scaleY = videoRect.height() / height

        val gridSize = 64
        // 100 / 16 ≈ 6.25 → threshold of 7 is consistent with the original minClusterSize check
        val minClusterScore = 7

        val brightnessMap = Array(height / gridSize + 1) { IntArray(width / gridSize + 1) }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 0 until height step gridSize) {
            for (x in 0 until width step gridSize) {
                var brightPixels = 0
                val endY = min(y + gridSize, height)
                val endX = min(x + gridSize, width)

                for (py in y until endY step 4) {
                    for (px in x until endX step 4) {
                        val pixel = pixels[py * width + px]
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF

                        val brightness = (r + g + b) / 3
                        val saturation = max(max(r, g), b) - min(min(r, g), b)

                        if (brightness in 80..220 && saturation > 20) {
                            brightPixels++
                        }
                    }
                }
                brightnessMap[y / gridSize][x / gridSize] = brightPixels
            }
        }

        var bestX = -1
        var bestY = -1
        var bestScore = 0
        val fovRadiusGrid = (config.fovRadius / gridSize).toInt()
        val centerGridX = centerX / gridSize
        val centerGridY = centerY / gridSize

        for (gy in brightnessMap.indices) {
            for (gx in brightnessMap[gy].indices) {
                val distFromCenter =
                    hypot((gx - centerGridX).toFloat(), (gy - centerGridY).toFloat())
                if (distFromCenter <= fovRadiusGrid && brightnessMap[gy][gx] > bestScore) {
                    bestScore = brightnessMap[gy][gx]
                    bestX = gx
                    bestY = gy
                }
            }
        }

        if (bestScore >= minClusterScore) {
            val boxCenterX = (bestX * gridSize + gridSize / 2).toFloat()
            val boxCenterY = (bestY * gridSize + gridSize / 2).toFloat()
            val boxWidth = gridSize * 3f
            val boxHeight = gridSize * 5f

            val screenLeft = videoRect.left + (boxCenterX - boxWidth / 2) * scaleX
            val screenTop = videoRect.top + (boxCenterY - boxHeight / 2) * scaleY
            val screenRight = videoRect.left + (boxCenterX + boxWidth / 2) * scaleX
            val screenBottom = videoRect.top + (boxCenterY + boxHeight / 2) * scaleY

            detections.add(
                Detection(
                    boundingBox = RectF(screenLeft, screenTop, screenRight, screenBottom),
                    confidence = (bestScore.toFloat() / 256f).coerceIn(0.3f, 0.9f),
                    classId = PERSON_CLASS_ID
                )
            )
        }

        return detections
    }

    private fun applyNMS(detections: List<Detection>): List<Detection> {
        if (detections.isEmpty()) return emptyList()

        val sorted = detections.sortedByDescending { it.confidence }
        val selected = mutableListOf<Detection>()

        for (detection in sorted) {
            var shouldSelect = true
            for (selectedBox in selected) {
                if (calculateIoU(detection.boundingBox, selectedBox.boundingBox) > IOU_THRESHOLD) {
                    shouldSelect = false
                    break
                }
            }
            if (shouldSelect) {
                selected.add(detection)
            }
        }

        return selected
    }

    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val intersectionLeft = max(box1.left, box2.left)
        val intersectionTop = max(box1.top, box2.top)
        val intersectionRight = min(box1.right, box2.right)
        val intersectionBottom = min(box1.bottom, box2.bottom)

        if (intersectionLeft >= intersectionRight || intersectionTop >= intersectionBottom) {
            return 0f
        }

        val intersectionArea =
            (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop)
        val box1Area = box1.width() * box1.height()
        val box2Area = box2.width() * box2.height()
        val unionArea = box1Area + box2Area - intersectionArea

        // FIX: guard against division by zero
        return if (unionArea <= 0f) 0f else intersectionArea / unionArea
    }

    private fun findBestTarget(detections: List<Detection>, videoRect: RectF): DetectedPose? {
        if (detections.isEmpty()) return null

        val centerX = videoRect.centerX()
        val centerY = videoRect.centerY()

        val validDetections = detections.filter { detection ->
            val focusX = detection.boundingBox.centerX()
            val focusY = if (config.headShotMode) {
                detection.boundingBox.top + detection.boundingBox.height() * config.headOffsetY
            } else {
                detection.boundingBox.centerY()
            }
            hypot(focusX - centerX, focusY - centerY) <= config.fovRadius
        }

        if (validDetections.isEmpty()) return null

        // FIX: CLOSEST_TO_CROSSHAIR now handled explicitly — removed stale `else` branch
        val bestDetection = when (config.targetPriority) {
            TargetPriority.CLOSEST_TO_CENTER,
            TargetPriority.CLOSEST_TO_CROSSHAIR -> {
                validDetections.minByOrNull { detection ->
                    hypot(
                        detection.boundingBox.centerX() - centerX,
                        detection.boundingBox.centerY() - centerY
                    )
                }
            }
            TargetPriority.LARGEST_TARGET -> {
                validDetections.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            }
            TargetPriority.HIGHEST_CONFIDENCE -> {
                validDetections.maxByOrNull { it.confidence }
            }
        } ?: return null

        val focusPoint = if (config.headShotMode) {
            PointF(
                bestDetection.boundingBox.centerX(),
                bestDetection.boundingBox.top +
                    bestDetection.boundingBox.height() * config.headOffsetY
            )
        } else {
            PointF(bestDetection.boundingBox.centerX(), bestDetection.boundingBox.centerY())
        }

        return DetectedPose(
            boundingBox = bestDetection.boundingBox,
            focusPoint = focusPoint,
            confidence = bestDetection.confidence
        )
    }

    override fun updateConfig(newConfig: PoseTrackerConfig) {
        config = newConfig
    }

    override fun close() {
        // FIX: shutdownNow cancels queued tasks; awaitTermination waits for the running task
        executor.shutdownNow()
        try {
            executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        interpreter?.close()
        interpreter = null
        isInitialized = false
        isProcessing = false
    }

    data class Detection(
        val boundingBox: RectF,
        val confidence: Float,
        val classId: Int
    )
}
