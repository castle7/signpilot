package com.signpilot.gesture

class GestureTranslator {

    data class TranslatedIntent(
        val text: String,
        val intentType: String,
        val confidence: Float,
        val rawGestures: List<String>
    )

    private val gestureBuffer = mutableListOf<Pair<HandGestureDetector.HandGesture, Long>>()
    private val BUFFER_TIMEOUT = 3000L

    fun processGesture(gesture: HandGestureDetector.HandGesture): TranslatedIntent? {
        val now = System.currentTimeMillis()
        gestureBuffer.removeAll { now - it.second > BUFFER_TIMEOUT }

        when (gesture) {
            HandGestureDetector.HandGesture.HELLO -> {
                gestureBuffer.clear()
                return TranslatedIntent("你好", "greeting", 1.0f, listOf("HELLO"))
            }
            HandGestureDetector.HandGesture.THUMB_UP -> {
                gestureBuffer.clear()
                return TranslatedIntent("是的，确认", "confirm", 1.0f, listOf("CONFIRM"))
            }
            HandGestureDetector.HandGesture.CANCEL,
            HandGestureDetector.HandGesture.NO -> {
                gestureBuffer.clear()
                return TranslatedIntent("不，取消", "cancel", 1.0f, listOf("CANCEL"))
            }
            HandGestureDetector.HandGesture.HELP -> {
                gestureBuffer.clear()
                return TranslatedIntent("我需要帮助", "help", 1.0f, listOf("HELP"))
            }
            else -> {}
        }

        gestureBuffer.add(gesture to now)
        return translateSequence()
    }

    private fun translateSequence(): TranslatedIntent? {
        val gestures = gestureBuffer.map { it.first }

        return when {
            gestures.size == 2 &&
            gestures[0] == HandGestureDetector.HandGesture.POINTING &&
            gestures[1] == HandGestureDetector.HandGesture.OPEN_PALM -> {
                gestureBuffer.clear()
                TranslatedIntent("这是什么？", "question_what", 0.9f, gestures.map { it.name })
            }
            else -> null
        }
    }
}
