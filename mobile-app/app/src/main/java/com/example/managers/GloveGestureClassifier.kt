package com.example.managers

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

data class GestureResult(
    val label: String,
    val confidence: Float,
    val classIndex: Int
)

class GloveGestureClassifier(private val context: Context) {

    companion object {
        private const val TAG = "GloveGestureClassifier"
        private const val MODEL_FILE = "glove_model.tflite"
        private const val LABELS_FILE = "labels.txt"
        private const val FEATURE_COUNT = 8
        private const val MIN_CONFIDENCE_THRESHOLD = 0.70f
        private const val CONSECUTIVE_FRAMES_REQUIRED = 2
        private const val DEBOUNCE_COOLDOWN_MS = 1800L
    }

    private var interpreter: Interpreter? = null
    private val labels = mutableListOf<String>()

    // Telemetry debounce / stabilization state
    private var lastRecognizedLabel: String? = null
    private var candidateLabel: String? = null
    private var candidateCount: Int = 0
    private var lastSpokenTimestamp: Long = 0L

    init {
        loadModelAndLabels()
    }

    private fun loadModelAndLabels() {
        try {
            // Load TFLite Model
            val modelBuffer = loadModelFile(MODEL_FILE)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.d(TAG, "TFLite glove model loaded successfully.")

            // Load Labels
            context.assets.open(LABELS_FILE).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        labels.add(trimmed)
                    }
                }
            }
            Log.d(TAG, "Loaded ${labels.size} gesture labels.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize GloveGestureClassifier", e)
        }
    }

    @Throws(IOException::class)
    private fun loadModelFile(modelPath: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun isReady(): Boolean = interpreter != null && labels.isNotEmpty()

    /**
     * Classifies raw 8-element telemetry:
     * [THUMB, INDEX, MIDDLE, RING, LITTLE, G1, G2, G3]
     */
    fun classify(features: FloatArray): GestureResult? {
        val activeInterpreter = interpreter ?: return null
        if (features.size != FEATURE_COUNT || labels.isEmpty()) return null

        val input = Array(1) { FloatArray(FEATURE_COUNT) }
        for (i in 0 until FEATURE_COUNT) {
            input[0][i] = features[i]
        }

        val output = Array(1) { FloatArray(labels.size) }
        activeInterpreter.run(input, output)

        var maxIdx = -1
        var maxProb = -1.0f
        for (i in output[0].indices) {
            if (output[0][i] > maxProb) {
                maxProb = output[0][i]
            }
        }
        for (i in output[0].indices) {
            if (output[0][i] == maxProb) {
                maxIdx = i
                break
            }
        }

        if (maxIdx in labels.indices) {
            return GestureResult(
                label = labels[maxIdx],
                confidence = maxProb,
                classIndex = maxIdx + 1
            )
        }
        return null
    }

    /**
     * Parses numeric CSV or whitespace-delimited sensor telemetry:
     * e.g. "512,520,530,510,500,1024,-2048,16000"
     */
    fun parseTelemetry(packet: String): FloatArray? {
        val trimmed = packet.trim()
        if (trimmed.isEmpty()) return null

        // Check if string contains numbers
        val tokens = trimmed.split(Regex("[,\\s]+"))
            .filter { it.isNotBlank() }
            .mapNotNull { it.toFloatOrNull() }

        if (tokens.size >= FEATURE_COUNT) {
            return tokens.take(FEATURE_COUNT).toFloatArray()
        }
        return null
    }

    /**
     * Processes live incoming telemetry stream with stabilization and debounce filtering.
     * Returns a valid GestureResult ONLY when a stable, high-confidence gesture is detected
     * and cooldown has elapsed.
     */
    fun processStreamingTelemetry(packet: String): GestureResult? {
        val features = parseTelemetry(packet) ?: return null
        val result = classify(features) ?: return null

        if (result.confidence < MIN_CONFIDENCE_THRESHOLD) {
            candidateLabel = null
            candidateCount = 0
            return null
        }

        // Temporal filter across frames
        if (result.label == candidateLabel) {
            candidateCount++
        } else {
            candidateLabel = result.label
            candidateCount = 1
        }

        if (candidateCount >= CONSECUTIVE_FRAMES_REQUIRED) {
            val now = System.currentTimeMillis()
            val isNewGesture = (result.label != lastRecognizedLabel)
            val cooldownElapsed = (now - lastSpokenTimestamp) >= DEBOUNCE_COOLDOWN_MS

            if (isNewGesture || cooldownElapsed) {
                lastRecognizedLabel = result.label
                lastSpokenTimestamp = now
                return result
            }
        }

        return null
    }

    fun close() {
        try {
            interpreter?.close()
            interpreter = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing TFLite interpreter", e)
        }
    }
}
