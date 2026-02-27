package com.signpilot.gesture

import android.content.Context
import android.media.Image
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.nio.ByteBuffer
import kotlin.math.acos
import kotlin.math.sqrt

class HandGestureDetector(
    private val context: Context,
    private val onGestureRecognized: (GestureResult) -> Unit,
    private val onDebugInfo: ((String) -> Unit)? = null
) {
    data class GestureResult(
        val gesture: HandGesture,
        val confidence: Float,
        val handSide: String,
        val landmarks: List<NormalizedLandmark>?
    )

    data class NormalizedLandmark(val x: Float, val y: Float, val z: Float)

    enum class HandGesture {
        UNKNOWN, OPEN_PALM, FIST, POINTING, THUMB_UP, THUMB_DOWN,
        NUMBER_0, NUMBER_1, NUMBER_2, NUMBER_3, NUMBER_4, NUMBER_5,
        NUMBER_6, NUMBER_7, NUMBER_8, NUMBER_9, NUMBER_10,
        HELLO, THANKS, CONFIRM, CANCEL, HELP, YES, NO, CLICK,
        ATTENTION, STOP
    }

    private var handLandmarker: HandLandmarker? = null
    private var lastGesture = HandGesture.UNKNOWN
    private var gestureFrameCount = 0
    private val GESTURE_THRESHOLD = 5

    init { setupHandLandmarker() }

    private fun setupHandLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setDelegate(Delegate.GPU)
                .setModelAssetPath("hand_landmarker.task")
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, _ -> processResult(result) }
                .setErrorListener { error -> onDebugInfo?.invoke("Error: $error") }
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            onDebugInfo?.invoke("GPU failed, using CPU")
            setupHandLandmarkerCPU()
        }
    }

    private fun setupHandLandmarkerCPU() {
        val baseOptions = BaseOptions.builder()
            .setDelegate(Delegate.CPU)
            .setModelAssetPath("hand_landmarker.task")
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(1)
            .setResultListener { result, _ -> processResult(result) }
            .build()

        handLandmarker = HandLandmarker.createFromOptions(context, options)
    }

    fun getAnalyzer(): ImageAnalysis.Analyzer {
        return ImageAnalysis.Analyzer { imageProxy ->
            processImage(imageProxy)
        }
    }

    private fun processImage(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        val mpImage = BitmapImageBuilder(bitmap).build()
        handLandmarker?.detectAsync(mpImage, System.currentTimeMillis())
        imageProxy.close()
        bitmap.recycle()
    }

    private fun processResult(result: HandLandmarkerResult) {
        if (result.handedness().isEmpty()) return

        val landmarks = result.landmarks()[0].map {
            NormalizedLandmark(it.x(), it.y(), it.z())
        }
        val handedness = result.handedness()[0][0].categoryName()

        val gesture = classifyGesture(landmarks)
        val confidence = result.handedness()[0][0].score()

        if (gesture == lastGesture) {
            gestureFrameCount++
            if (gestureFrameCount >= GESTURE_THRESHOLD) {
                onGestureRecognized(GestureResult(gesture, confidence, handedness, landmarks))
                gestureFrameCount = 0
            }
        } else {
            lastGesture = gesture
            gestureFrameCount = 1
        }
    }

    private fun classifyGesture(landmarks: List<NormalizedLandmark>): HandGesture {
        val WRIST = 0
        val INDEX_TIP = 8; val INDEX_PIP = 6
        val MIDDLE_TIP = 12; val MIDDLE_PIP = 10
        val RING_TIP = 16; val RING_PIP = 14
        val PINKY_TIP = 20; val PINKY_PIP = 18
        val THUMB_TIP = 4; val THUMB_IP = 3

        fun isExtended(tip: Int, pip: Int): Boolean {
            return distance(landmarks[tip], landmarks[WRIST]) >
                   distance(landmarks[pip], landmarks[WRIST]) * 1.2
        }

        val index = isExtended(INDEX_TIP, INDEX_PIP)
        val middle = isExtended(MIDDLE_TIP, MIDDLE_PIP)
        val ring = isExtended(RING_TIP, RING_PIP)
        val pinky = isExtended(PINKY_TIP, PINKY_PIP)
        val thumb = distance(landmarks[THUMB_TIP], landmarks[INDEX_TIP]) > 0.1

        val count = listOf(index, middle, ring, pinky).count { it }

        return when {
            count == 0 && !thumb -> HandGesture.FIST
            count == 1 && index -> HandGesture.NUMBER_1
            count == 2 && index && middle -> HandGesture.NUMBER_2
            count == 3 && index && middle && ring -> HandGesture.NUMBER_3
            count == 4 && !thumb -> HandGesture.NUMBER_4
            count == 4 && thumb -> HandGesture.OPEN_PALM
            count == 5 -> HandGesture.OPEN_PALM
            count == 1 && thumb -> HandGesture.THUMB_UP
            count == 2 && index && middle -> HandGesture.NUMBER_2
            else -> HandGesture.UNKNOWN
        }
    }

    private fun distance(a: NormalizedLandmark, b: NormalizedLandmark): Float {
        return sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
    }

    private fun ImageProxy.toBitmap(): android.graphics.Bitmap {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
        val imageBytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    fun close() { handLandmarker?.close() }
}
