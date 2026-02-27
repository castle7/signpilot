package com.signpilot.ui

import android.app.Service
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import com.signpilot.ui.views.HighlightOverlayView
import com.signpilot.ui.views.SignAvatarView
import com.signpilot.ui.views.SubtitleView

class FloatingUIHelper(private val context: Context) {

    private val windowManager = context.getSystemService(Service.WINDOW_SERVICE) as WindowManager
    private val floatingViews = mutableMapOf<String, View>()
    private var avatarView: SignAvatarView? = null

    fun showHighlight(x: Int, y: Int, width: Int, height: Int, color: Int = Color.parseColor("#4285F4"), durationMs: Long = 3000) {
        removeView("highlight")

        val highlightView = HighlightOverlayView(context).apply {
            setBounds(x, y, width, height)
            setColor(color)
            startPulseAnimation()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(highlightView, params)
        floatingViews["highlight"] = highlightView
        highlightView.postDelayed({ removeView("highlight") }, durationMs)
    }

    fun showSignAvatar(): SignAvatarView {
        removeView("avatar")

        avatarView = SignAvatarView(context).apply {
            layoutParams = FrameLayout.LayoutParams(400, 600).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                rightMargin = 50
                bottomMargin = 200
            }
        }

        val params = WindowManager.LayoutParams(
            400, 600,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 50
            y = 200
        }

        windowManager.addView(avatarView, params)
        floatingViews["avatar"] = avatarView!!

        val slideUp = TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 1f,
            Animation.RELATIVE_TO_SELF, 0f
        ).apply {
            duration = 500
            interpolator = DecelerateInterpolator()
        }
        avatarView?.startAnimation(slideUp)

        return avatarView!!
    }

    fun getAvatarView(): SignAvatarView? = avatarView

    fun showSubtitle(text: String, durationMs: Long = 4000) {
        removeView("subtitle")

        val subtitleView = SubtitleView(context).apply { setText(text) }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 300
        }

        windowManager.addView(subtitleView, params)
        floatingViews["subtitle"] = subtitleView
        subtitleView.postDelayed({ removeView("subtitle") }, durationMs)
    }

    fun showSecurityWarning(warningText: String) {
        removeView("warning")

        val warningView = View(context).apply {
            setBackgroundColor(Color.parseColor("#AAFF0000"))
            alpha = 0f
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(warningView, params)
        floatingViews["warning"] = warningView

        val alphaAnim = android.animation.ObjectAnimator.ofFloat(warningView, "alpha", 0f, 0.7f, 0f, 0.7f)
        alphaAnim.duration = 1000
        alphaAnim.repeatCount = 3
        alphaAnim.start()

        showSubtitle("⚠️ $warningText", 5000)
    }

    fun removeView(tag: String) {
        floatingViews[tag]?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            floatingViews.remove(tag)
        }
    }

    fun removeAllViews() {
        floatingViews.keys.toList().forEach { removeView(it) }
        avatarView = null
    }
}
