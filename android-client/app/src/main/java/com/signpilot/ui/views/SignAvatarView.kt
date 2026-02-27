package com.signpilot.ui.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.animation.ValueAnimator

class SignAvatarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 40f; textAlign = Paint.Align.CENTER
    }

    data class Joint(var x: Float, var y: Float, var targetX: Float = x, var targetY: Float = y)
    private val joints = mutableMapOf<String, Joint>()
    private var currentAnimation: ValueAnimator? = null
    private var currentSign = ""

    private val skinColor = Color.parseColor("#F5C6A5")
    private val jointColor = Color.parseColor("#8B4513")
    private val boneColor = Color.parseColor("#D2691E")

    init { initializeNeutralPose() }

    private fun initializeNeutralPose() {
        val cx = width / 2f; val cy = height / 2f
        joints["head"] = Joint(cx, cy - 150f)
        joints["neck"] = Joint(cx, cy - 100f)
        joints["shoulder_l"] = Joint(cx - 60f, cy - 80f)
        joints["shoulder_r"] = Joint(cx + 60f, cy - 80f)
        joints["elbow_l"] = Joint(cx - 80f, cy + 20f)
        joints["elbow_r"] = Joint(cx + 80f, cy + 20f)
        joints["wrist_l"] = Joint(cx - 100f, cy + 100f)
        joints["wrist_r"] = Joint(cx + 100f, cy + 100f)
        joints["hand_l"] = Joint(cx - 100f, cy + 140f)
        joints["hand_r"] = Joint(cx + 100f, cy + 140f)
    }

    data class SignGesture(val name: String, val jointTargets: Map<String, Pair<Float, Float>>, val durationMs: Long = 1000)

    fun playSignSequence(sequences: List<SignGesture>, onComplete: (() -> Unit)? = null) {
        var index = 0
        fun playNext() {
            if (index >= sequences.size) { onComplete?.invoke(); return }
            animateToGesture(sequences[index]) { index++; postDelayed({ playNext() }, 500) }
        }
        playNext()
    }

    private fun animateToGesture(gesture: SignGesture, onEnd: (() -> Unit)? = null) {
        currentSign = gesture.name
        gesture.jointTargets.forEach { (name, target) ->
            joints[name]?.let { it.targetX = target.first; it.targetY = target.second }
        }
        currentAnimation?.cancel()
        currentAnimation = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = gesture.durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                joints.values.forEach { joint ->
                    joint.x += (joint.targetX - joint.x) * 0.1f
                    joint.y += (joint.targetY - joint.y) * 0.1f
                }
                invalidate()
            }
            doOnEnd { onEnd?.invoke() }
            start()
        }
    }

    fun pointTo(screenX: Float, screenY: Float, duration: Long = 1000) {
        val rx = screenX * (width / 1080f)
        val ry = screenY * (height / 1920f)
        animateToGesture(SignGesture("point", mapOf(
            "shoulder_r" to Pair(width * 0.6f, height * 0.3f),
            "elbow_r" to Pair(width * 0.7f, height * 0.4f),
            "wrist_r" to Pair(rx, ry),
            "hand_r" to Pair(rx + 20f, ry + 20f)
        ), duration))
    }

    fun setEmotion(emotion: Emotion) {
        when(emotion) {
            Emotion.THUMBS_UP -> animateToGesture(SignGesture("thumbs_up", mapOf(
                "elbow_r" to Pair(width * 0.7f, height * 0.4f),
                "wrist_r" to Pair(width * 0.8f, height * 0.3f),
                "hand_r" to Pair(width * 0.8f, height * 0.2f)
            ), 500))
            Emotion.WARNING -> animateToGesture(SignGesture("stop", mapOf(
                "wrist_l" to Pair(width * 0.4f, height * 0.5f),
                "wrist_r" to Pair(width * 0.6f, height * 0.5f)
            ), 600))
            else -> {}
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (joints.isEmpty()) initializeNeutralPose()

        paint.color = Color.parseColor("#AA000000")
        canvas.drawCircle(width / 2f, height / 2f, width * 0.45f, paint)

        paint.color = boneColor; paint.strokeWidth = 12f
        drawBone(canvas, "shoulder_l", "elbow_l")
        drawBone(canvas, "elbow_l", "wrist_l")
        drawBone(canvas, "shoulder_r", "elbow_r")
        drawBone(canvas, "elbow_r", "wrist_r")
        drawBone(canvas, "neck", "shoulder_l")
        drawBone(canvas, "neck", "shoulder_r")

        paint.color = jointColor
        joints.values.forEach { canvas.drawCircle(it.x, it.y, 15f, paint) }

        paint.color = skinColor
        joints["hand_l"]?.let { canvas.drawCircle(it.x, it.y, 25f, paint) }
        joints["hand_r"]?.let { canvas.drawCircle(it.x, it.y, 25f, paint) }

        joints["head"]?.let { head ->
            canvas.drawCircle(head.x, head.y, 40f, paint)
            paint.color = Color.BLACK; paint.strokeWidth = 3f
            canvas.drawCircle(head.x - 15f, head.y - 5f, 5f, paint)
            canvas.drawCircle(head.x + 15f, head.y - 5f, 5f, paint)
            canvas.drawLine(head.x - 15f, head.y + 15f, head.x + 15f, head.y + 15f, paint)
        }

        if (currentSign.isNotEmpty()) {
            canvas.drawText(currentSign, width / 2f, height - 50f, textPaint)
        }
    }

    private fun drawBone(canvas: Canvas, start: String, end: String) {
        val s = joints[start] ?: return; val e = joints[end] ?: return
        canvas.drawLine(s.x, s.y, e.x, e.y, paint)
    }

    enum class Emotion { THUMBS_UP, WARNING, THINKING, NEUTRAL }
}
