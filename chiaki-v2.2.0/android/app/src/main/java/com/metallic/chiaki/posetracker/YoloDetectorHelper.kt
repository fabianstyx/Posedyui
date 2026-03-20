// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.posetracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File
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

/**
 * YOLOv8-based person detector.
 *
 * Improvements over the original:
 *  - GPU delegate via no-arg GpuDelegate() — avoids GpuDelegateFactory.Options classpath issues.
 *  - INT8 / UINT8 quantized model support: input buffer format is detected automatically from
 *    the model's input tensor DataType.  PC-trained quantized models work as-is.
 *  - Dynamic input size: read from the model's input tensor shape, not hardcoded to 640.
 *  - Custom model loading from device storage: set config.customModelPath to a .tflite file path.
 *  - Adaptive confidence: threshold lowers while the camera is moving so detections are not
 *    dropped during rapid panning, and tightens when still for fewer false positives.
 */
class YoloDetectorHelper(
    private val context: Context,
    private var config: PoseTrackerConfig,
    private val listener: DetectorListener
) : BaseDetector {

    private var interpreter: Interpreter? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var isInitialized = false

    @Volatile
    private var isProcessing = false

    // Resolved at init time from the model's input tensor.
    private var inputSize = DEFAULT_INPUT_SIZE
    // DataType.FLOAT32 or DataType.UINT8 — determines buffer packing.
    private var inputDataType = DataType.FLOAT32

    // Adaptive confidence: smoothed camera-motion speed (pixels/frame), updated by PoseTrackerManager.
    @Volatile
    var cameraSpeedPx: Float = 0f

    companion object {
        private const val BUNDLED_MODEL = "yolov8n.tflite"
        private const val DEFAULT_INPUT_SIZE = 640
        private const val NUM_CLASSES = 80
        private const val PERSON_CLASS_ID = 0
        private const val IOU_THRESHOLD = 0.45f
        private const val EXECUTOR_SHUTDOWN_TIMEOUT_MS = 500L
    }

    override fun initialize() {
        executor.execute {
            try {
                val modelBuffer = loadModel()
                if (modelBuffer != null) {
                    val options = Interpreter.Options()

                    // Use GPU when available. GpuDelegate() with no args avoids any reference to
                    // GpuDelegateFactory.Options whose supertype is only available in the separate
                    // tensorflow-lite-gpu-delegate-plugin artefact; without that artefact the
                    // Kotlin compiler rejects even the subtype argument.
                    val compatList = CompatibilityList()
                    if (compatList.isDelegateSupportedOnThisDevice) {
                        @Suppress("DEPRECATION")
                        options.addDelegate(GpuDelegate())
                        listener.onDebugInfo("YOLO: GPU acceleration enabled")
                    } else {
                        options.setNumThreads(4)
                        listener.onDebugInfo("YOLO: CPU mode (4 threads)")
                    }

                    val interp = Interpreter(modelBuffer, options)

                    // Introspect input tensor: shape = [1, H, W, 3], type = FLOAT32 or UINT8
                    val inputTensor = interp.getInputTensor(0)
                    val shape = inputTensor.shape()        // [batch, height, width, channels]
                    inputDataType = inputTensor.dataType()
                    // shape[1] == shape[2] for square models; fall back to default if unexpected
                    inputSize = if (shape.size >= 3 && shape[1] > 0) shape[1] else DEFAULT_INPUT_SIZE

                    listener.onDebugInfo(
                        "YOLO: model input ${inputSize}x${inputSize} $inputDataType"
                    )

                    interpreter = interp
                    isInitialized = true
                    listener.onDebugInfo("YOLO: Initialized successfully")
                } else {
                    listener.onDebugInfo("YOLO: No model found, using fallback brightness detection")
                    isInitialized = true
                }
            } catch (e: Exception) {
                listener.onError("YOLO init failed: ${e.message}")
                listener.onDebugInfo("YOLO: Falling back to brightness detection")
                isInitialized = true
            }
        }
    }

    /**
     * Load the model: prefer the user-supplied file, fall back to the bundled asset.
     */
    private fun loadModel(): MappedByteBuffer? {
        val customPath = config.customModelPath
        if (!customPath.isNullOrBlank()) {
            val file = File(customPath)
            if (file.exists() && file.canRead()) {
                return try {
                    FileInputStream(file).use { stream ->
                        stream.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
                    }.also {
                        listener.onDebugInfo("YOLO: Loaded custom model from $customPath")
                    }
                } catch (e: Exception) {
                    listener.onDebugInfo("YOLO: Custom model load failed (${e.message}), using bundled")
                    loadBundledModel()
                }
            } else {
                listener.onDebugInfo("YOLO: Custom model path not found: $customPath — using bundled")
            }
        }
        return loadBundledModel()
    }

    private fun loadBundledModel(): MappedByteBuffer? {
        return try {
            context.assets.openFd(BUNDLED_MODEL).use { afd ->
                FileInputStream(afd.fileDescriptor).use { inputStream ->
                    inputStream.channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        afd.startOffset,
                        afd.declaredLength
                    )
                }
            }
        } catch (e: Exception) {
            null // asset not present — caller will use fallback detection
        }
    }

    override fun detect(bitmap: Bitmap, videoRect: RectF) {
        if (!isInitialized || isProcessing) {
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }
        if (bitmap.isRecycled) return
        isProcessing = true

        val bitmapCopy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        bitmap.recycle()

        if (bitmapCopy == null) {
            isProcessing = false
            return
        }

        executor.execute {
            try {
                val detections = if (interpreter != null) {
                    runYoloInference(bitmapCopy, videoRect)
                } else {
                    runFallbackDetection(bitmapCopy, videoRect)
                }

                val bestTarget = findBestTarget(detections, videoRect)
                listener.onTargetDetected(bestTarget)

                if (bestTarget != null) {
                    listener.onDebugInfo(
                        "YOLO: Target @ (${bestTarget.focusPoint.x.toInt()}, " +
                            "${bestTarget.focusPoint.y.toInt()}) " +
                            "conf=${String.format("%.2f", bestTarget.confidence)}"
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
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val inputBuffer = convertBitmapToByteBuffer(resized)
        resized.recycle()

        val outputShape = interpreter?.getOutputTensor(0)?.shape() ?: return emptyList()
        val outputBuffer = Array(1) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }

        interpreter?.run(inputBuffer, outputBuffer)

        return parseYoloOutput(outputBuffer[0], bitmap.width, bitmap.height, videoRect)
    }

    /**
     * Build the input buffer in the correct format for the model.
     *
     * FLOAT32 models  : 4 bytes/channel, values in [0, 1]
     * UINT8/INT8 models: 1 byte/channel, values in [0, 255]
     *
     * Using the wrong format is a silent error — the model runs but produces garbage detections.
     */
    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val bytesPerChannel = if (inputDataType == DataType.FLOAT32) 4 else 1
        val buffer = ByteBuffer.allocateDirect(bytesPerChannel * inputSize * inputSize * 3)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            if (inputDataType == DataType.FLOAT32) {
                buffer.putFloat(r / 255.0f)
                buffer.putFloat(g / 255.0f)
                buffer.putFloat(b / 255.0f)
            } else {
                // INT8 / UINT8 quantized: write raw byte values
                buffer.put(r.toByte())
                buffer.put(g.toByte())
                buffer.put(b.toByte())
            }
        }

        return buffer
    }

    /**
     * Return the effective confidence threshold.
     *
     * When adaptiveConfidence is on:
     *   - Camera moving fast  → drop threshold toward adaptiveConfidenceMin (catch more targets)
     *   - Camera still        → use full config.confidence (fewer false positives)
     *
     * The transition is linear over 0–200 px/frame speed range.
     */
    private fun effectiveConfidence(): Float {
        if (!config.adaptiveConfidence) return config.confidence
        val maxSpeedPx = 200f
        val t = (cameraSpeedPx / maxSpeedPx).coerceIn(0f, 1f)
        return config.confidence - t * (config.confidence - config.adaptiveConfidenceMin)
    }

    private fun parseYoloOutput(
        output: Array<FloatArray>,
        imgWidth: Int,
        imgHeight: Int,
        videoRect: RectF
    ): List<Detection> {
        val detections = mutableListOf<Detection>()
        if (output.isEmpty() || output[0].isEmpty()) return detections

        val threshold = effectiveConfidence()
        val numDetections = output[0].size

        for (i in 0 until numDetections) {
            val cx = output[0][i]
            val cy = output[1][i]
            val w  = output[2][i]
            val h  = output[3][i]

            var maxScore = 0f
            var classId  = -1
            for (c in 0 until NUM_CLASSES) {
                val score = output[4 + c][i]
                if (score > maxScore) {
                    maxScore = score
                    classId  = c
                }
            }

            if (classId == PERSON_CLASS_ID && maxScore >= threshold) {
                val scaleX = videoRect.width()  / inputSize
                val scaleY = videoRect.height() / inputSize

                val left   = videoRect.left + (cx - w / 2) * scaleX
                val top    = videoRect.top  + (cy - h / 2) * scaleY
                val right  = videoRect.left + (cx + w / 2) * scaleX
                val bottom = videoRect.top  + (cy + h / 2) * scaleY

                detections.add(
                    Detection(
                        boundingBox = RectF(left, top, right, bottom),
                        confidence  = maxScore,
                        classId     = classId
                    )
                )
            }
        }

        return applyNMS(detections)
    }

    // ── Fallback brightness-cluster detection (no model) ─────────────────────

    private fun runFallbackDetection(bitmap: Bitmap, videoRect: RectF): List<Detection> {
        val detections = mutableListOf<Detection>()
        val width  = bitmap.width
        val height = bitmap.height
        val centerX = width  / 2
        val centerY = height / 2

        val scaleX = videoRect.width()  / width
        val scaleY = videoRect.height() / height

        val gridSize = 64
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
                        val g = (pixel shr 8)  and 0xFF
                        val b =  pixel         and 0xFF
                        val brightness  = (r + g + b) / 3
                        val saturation  = max(max(r, g), b) - min(min(r, g), b)
                        if (brightness in 80..220 && saturation > 20) brightPixels++
                    }
                }
                brightnessMap[y / gridSize][x / gridSize] = brightPixels
            }
        }

        var bestX = -1; var bestY = -1; var bestScore = 0
        val fovRadiusGrid = (config.fovRadius / gridSize).toInt()
        val centerGridX   = centerX / gridSize
        val centerGridY   = centerY / gridSize

        for (gy in brightnessMap.indices) {
            for (gx in brightnessMap[gy].indices) {
                val dist = hypot((gx - centerGridX).toFloat(), (gy - centerGridY).toFloat())
                if (dist <= fovRadiusGrid && brightnessMap[gy][gx] > bestScore) {
                    bestScore = brightnessMap[gy][gx]; bestX = gx; bestY = gy
                }
            }
        }

        if (bestScore >= minClusterScore) {
            val boxCX = (bestX * gridSize + gridSize / 2).toFloat()
            val boxCY = (bestY * gridSize + gridSize / 2).toFloat()
            val boxW  = gridSize * 3f
            val boxH  = gridSize * 5f

            detections.add(
                Detection(
                    boundingBox = RectF(
                        videoRect.left + (boxCX - boxW / 2) * scaleX,
                        videoRect.top  + (boxCY - boxH / 2) * scaleY,
                        videoRect.left + (boxCX + boxW / 2) * scaleX,
                        videoRect.top  + (boxCY + boxH / 2) * scaleY
                    ),
                    confidence = (bestScore.toFloat() / 256f).coerceIn(0.3f, 0.9f),
                    classId    = PERSON_CLASS_ID
                )
            )
        }

        return detections
    }

    // ── NMS ──────────────────────────────────────────────────────────────────

    private fun applyNMS(detections: List<Detection>): List<Detection> {
        if (detections.isEmpty()) return emptyList()
        val sorted   = detections.sortedByDescending { it.confidence }
        val selected = mutableListOf<Detection>()
        for (d in sorted) {
            if (selected.none { calculateIoU(d.boundingBox, it.boundingBox) > IOU_THRESHOLD }) {
                selected.add(d)
            }
        }
        return selected
    }

    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val iL = max(box1.left,   box2.left)
        val iT = max(box1.top,    box2.top)
        val iR = min(box1.right,  box2.right)
        val iB = min(box1.bottom, box2.bottom)
        if (iL >= iR || iT >= iB) return 0f
        val iArea = (iR - iL) * (iB - iT)
        val union = box1.width() * box1.height() + box2.width() * box2.height() - iArea
        return if (union <= 0f) 0f else iArea / union
    }

    // ── Target selection ─────────────────────────────────────────────────────

    private fun findBestTarget(detections: List<Detection>, videoRect: RectF): DetectedPose? {
        if (detections.isEmpty()) return null

        val centerX = videoRect.centerX()
        val centerY = videoRect.centerY()

        val valid = detections.filter { d ->
            val focusX = d.boundingBox.centerX()
            val focusY = headY(d.boundingBox)
            hypot(focusX - centerX, focusY - centerY) <= config.fovRadius
        }
        if (valid.isEmpty()) return null

        val best = when (config.targetPriority) {
            TargetPriority.CLOSEST_TO_CENTER,
            TargetPriority.CLOSEST_TO_CROSSHAIR ->
                valid.minByOrNull { hypot(it.boundingBox.centerX() - centerX, it.boundingBox.centerY() - centerY) }
            TargetPriority.LARGEST_TARGET       ->
                valid.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            TargetPriority.HIGHEST_CONFIDENCE   ->
                valid.maxByOrNull { it.confidence }
        } ?: return null

        return DetectedPose(
            boundingBox = best.boundingBox,
            focusPoint  = PointF(best.boundingBox.centerX(), headY(best.boundingBox)),
            confidence  = best.confidence
        )
    }

    /** Y coordinate of the aim point: top of box + headOffsetY fraction of box height. */
    private fun headY(box: RectF): Float =
        if (config.headShotMode) box.top + box.height() * config.headOffsetY
        else box.centerY()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun updateConfig(newConfig: PoseTrackerConfig) {
        config = newConfig
    }

    override fun close() {
        executor.shutdownNow()
        try {
            executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        interpreter?.close()
        interpreter    = null
        isInitialized  = false
        isProcessing   = false
    }

    data class Detection(
        val boundingBox: RectF,
        val confidence: Float,
        val classId: Int
    )
}
