package com.example.chessassistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Data class representing a detected chess piece.
 */
data class Detection(
    val className: String,      // e.g., "white_pawn"
    val confidence: Float,       // 0.0 - 1.0
    val boundingBox: RectF,      // Normalized coordinates (0-1)
    var boardSquare: String? = null  // e.g., "e4" (calculated later)
)

/**
 * YOLO model detector for chess pieces using TensorFlow Lite.
 * Handles model loading, inference, and post-processing.
 */
class YoloDetector(private val context: Context) {
    
    companion object {
        private const val TAG = "YoloDetector"
        private const val MODEL_FILE = "chess_yolo.tflite"
        private const val LABELS_FILE = "labels.txt"
        private const val INPUT_SIZE = 640  // YOLOv11 default input size
        private const val CONFIDENCE_THRESHOLD = 0.5f
        private const val IOU_THRESHOLD = 0.45f
    }
    
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var labels: List<String> = emptyList()
    private var isInitialized = false
    
    /**
     * Load the TFLite model and labels from assets.
     */
    fun loadModel(): Boolean {
        return try {
            Log.i(TAG, "Loading YOLO model...")
            
            // Load labels
            labels = loadLabels()
            Log.i(TAG, "Loaded ${labels.size} labels")
            
            // Load model
            val modelBuffer = loadModelFile()
            
            // Configure interpreter options
            val options = Interpreter.Options()
            
            // Try to use GPU delegate if available
            val compatList = CompatibilityList()
            if (compatList.isDelegateSupportedOnThisDevice) {
                Log.i(TAG, "GPU delegate is supported, enabling GPU acceleration")
                gpuDelegate = GpuDelegate(compatList.bestOptionsForThisDevice)
                options.addDelegate(gpuDelegate)
            } else {
                Log.i(TAG, "GPU delegate not supported, using CPU")
                options.setNumThreads(4)  // Use 4 threads for CPU inference
            }
            
            // Create interpreter
            interpreter = Interpreter(modelBuffer, options)
            
            // Get input/output tensor info
            val inputShape = interpreter?.getInputTensor(0)?.shape()
            val outputShape = interpreter?.getOutputTensor(0)?.shape()
            Log.i(TAG, "Input shape: ${inputShape?.contentToString()}")
            Log.i(TAG, "Output shape: ${outputShape?.contentToString()}")
            
            isInitialized = true
            Log.i(TAG, "✓ YOLO model loaded successfully")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to load model", e)
            false
        }
    }
    
    /**
     * Run inference on a bitmap image.
     * @param bitmap Input image
     * @return List of detected pieces
     */
    fun detect(bitmap: Bitmap): List<Detection> {
        if (!isInitialized || interpreter == null) {
            Log.e(TAG, "Model not initialized")
            return emptyList()
        }
        
        return try {
            val startTime = System.currentTimeMillis()
            
            // Preprocess image
            val inputBuffer = preprocessImage(bitmap)
            
            // Prepare output buffer
            val outputShape = interpreter!!.getOutputTensor(0).shape()
            val outputBuffer = Array(1) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }
            
            // Run inference
            interpreter!!.run(inputBuffer, outputBuffer)
            
            // Post-process results
            val detections = postprocess(outputBuffer[0], bitmap.width, bitmap.height)
            
            val inferenceTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "Inference completed in ${inferenceTime}ms, found ${detections.size} pieces")
            
            detections
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during inference", e)
            emptyList()
        }
    }
    
    /**
     * Preprocess bitmap for model input.
     */
    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        // Resize bitmap to model input size
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        
        // Allocate ByteBuffer for input
        val byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        
        // Convert bitmap to ByteBuffer (normalized to 0-1)
        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        resizedBitmap.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        
        var pixel = 0
        for (i in 0 until INPUT_SIZE) {
            for (j in 0 until INPUT_SIZE) {
                val value = intValues[pixel++]
                
                // Extract RGB and normalize to [0, 1]
                byteBuffer.putFloat(((value shr 16) and 0xFF) / 255.0f)  // R
                byteBuffer.putFloat(((value shr 8) and 0xFF) / 255.0f)   // G
                byteBuffer.putFloat((value and 0xFF) / 255.0f)            // B
            }
        }
        
        return byteBuffer
    }
    
    /**
     * Post-process model output to extract detections.
     */
    private fun postprocess(output: Array<FloatArray>, imageWidth: Int, imageHeight: Int): List<Detection> {
        val detections = mutableListOf<Detection>()
        
        // YOLO output format: [num_detections, 5 + num_classes]
        // [x_center, y_center, width, height, confidence, class_scores...]
        
        for (detection in output) {
            if (detection.size < 5 + labels.size) continue
            
            val confidence = detection[4]
            if (confidence < CONFIDENCE_THRESHOLD) continue
            
            // Find class with highest score
            var maxClassScore = 0f
            var maxClassIndex = 0
            for (i in 5 until detection.size) {
                if (detection[i] > maxClassScore) {
                    maxClassScore = detection[i]
                    maxClassIndex = i - 5
                }
            }
            
            val finalConfidence = confidence * maxClassScore
            if (finalConfidence < CONFIDENCE_THRESHOLD) continue
            
            // Extract bounding box (normalized coordinates)
            val xCenter = detection[0]
            val yCenter = detection[1]
            val width = detection[2]
            val height = detection[3]
            
            val left = (xCenter - width / 2)
            val top = (yCenter - height / 2)
            val right = (xCenter + width / 2)
            val bottom = (yCenter + height / 2)
            
            val boundingBox = RectF(left, top, right, bottom)
            
            val className = if (maxClassIndex < labels.size) {
                labels[maxClassIndex]
            } else {
                "unknown"
            }
            
            detections.add(Detection(className, finalConfidence, boundingBox))
        }
        
        // Apply Non-Maximum Suppression (NMS)
        return applyNMS(detections)
    }
    
    /**
     * Apply Non-Maximum Suppression to remove overlapping detections.
     */
    private fun applyNMS(detections: List<Detection>): List<Detection> {
        if (detections.isEmpty()) return emptyList()
        
        // Sort by confidence (descending)
        val sorted = detections.sortedByDescending { it.confidence }
        val selected = mutableListOf<Detection>()
        val suppressed = BooleanArray(sorted.size) { false }
        
        for (i in sorted.indices) {
            if (suppressed[i]) continue
            
            selected.add(sorted[i])
            
            // Suppress overlapping boxes
            for (j in i + 1 until sorted.size) {
                if (suppressed[j]) continue
                
                val iou = calculateIoU(sorted[i].boundingBox, sorted[j].boundingBox)
                if (iou > IOU_THRESHOLD) {
                    suppressed[j] = true
                }
            }
        }
        
        return selected
    }
    
    /**
     * Calculate Intersection over Union (IoU) between two bounding boxes.
     */
    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val intersectLeft = maxOf(box1.left, box2.left)
        val intersectTop = maxOf(box1.top, box2.top)
        val intersectRight = minOf(box1.right, box2.right)
        val intersectBottom = minOf(box1.bottom, box2.bottom)
        
        if (intersectRight < intersectLeft || intersectBottom < intersectTop) {
            return 0f
        }
        
        val intersectArea = (intersectRight - intersectLeft) * (intersectBottom - intersectTop)
        val box1Area = (box1.right - box1.left) * (box1.bottom - box1.top)
        val box2Area = (box2.right - box2.left) * (box2.bottom - box2.top)
        
        return intersectArea / (box1Area + box2Area - intersectArea)
    }
    
    /**
     * Load model file from assets.
     */
    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    /**
     * Load labels from assets.
     */
    private fun loadLabels(): List<String> {
        return context.assets.open(LABELS_FILE).bufferedReader().useLines { lines ->
            lines.map { it.trim() }.toList()
        }
    }
    
    /**
     * Release resources.
     */
    fun close() {
        interpreter?.close()
        gpuDelegate?.close()
        interpreter = null
        gpuDelegate = null
        isInitialized = false
        Log.i(TAG, "YoloDetector closed")
    }
}
