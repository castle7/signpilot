package com.signpilot.ui.views

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class SubtitleView(context: Context) : LinearLayout(context) {

    private val textView: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(40, 20, 40, 20)

        background = GradientDrawable().apply {
            cornerRadius = 50f
            setColor(Color.parseColor("#AA000000"))
        }

        textView = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
        }

        addView(textView)
    }

    fun setText(text: String) {
        textView.text = text
    }
}
