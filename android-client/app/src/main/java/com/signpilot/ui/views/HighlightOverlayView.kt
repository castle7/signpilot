package com.signpilot.ui.views

import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.Animation
import android.view.animation.Transformation

class HighlightOverlayView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        alpha = 50
    }

    private var boundsRect = RectF()
    private var cornerRadius = 20f

    fun setBounds(x: Int, y: Int, width: Int, height: Int) {
        boundsRect.set(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat())
        invalidate()
    }

    fun setColor(color: Int) {
        paint.color = color
        fillPaint.color = color
        invalidate()
    }

    fun startPulseAnimation() {
        val anim = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
                paint.alpha = (100 + 155 * interpolatedTime).toInt()
                fillPaint.alpha = (30 + 70 * interpolatedTime).toInt()
                invalidate()
            }
        }
        anim.duration = 800
        anim.repeatMode = Animation.REVERSE
        anim.repeatCount = Animation.INFINITE
        startAnimation(anim)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRoundRect(boundsRect, cornerRadius, cornerRadius, fillPaint)
        canvas.drawRoundRect(boundsRect, cornerRadius, cornerRadius, paint)

        val markLength = 40f
        val markPaint = Paint(paint).apply { pathEffect = null; strokeWidth = 8f }

        canvas.drawLine(boundsRect.left, boundsRect.top + markLength, boundsRect.left, boundsRect.top, markPaint)
        canvas.drawLine(boundsRect.left, boundsRect.top, boundsRect.left + markLength, boundsRect.top, markPaint)

        canvas.drawLine(boundsRect.right - markLength, boundsRect.top, boundsRect.right, boundsRect.top, markPaint)
        canvas.drawLine(boundsRect.right, boundsRect.top, boundsRect.right, boundsRect.top + markLength, markPaint)

        canvas.drawLine(boundsRect.left, boundsRect.bottom - markLength, boundsRect.left, boundsRect.bottom, markPaint)
        canvas.drawLine(boundsRect.left, boundsRect.bottom, boundsRect.left + markLength, boundsRect.bottom, markPaint)

        canvas.drawLine(boundsRect.right - markLength, boundsRect.bottom, boundsRect.right, boundsRect.bottom, markPaint)
        canvas.drawLine(boundsRect.right, boundsRect.bottom - markLength, boundsRect.right, boundsRect.bottom, markPaint)
    }
}
