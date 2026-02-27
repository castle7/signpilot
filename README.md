# 🤟 SignPilot: AI Digital Guardian for the Hearing-Impaired Elderly

**Honest Innovation**: Built on Gemini 2.0 + MediaPipe, but uniquely focused on *teaching* rather than *doing* for deaf elderly users.

## 🎯 What Makes Us Different

| Feature | Claude Computer Use | Traditional Tools | **SignPilot** |
|---------|-------------------|------------------|--------------|
| Input | Voice/Text | Screen readers | **Sign Language (CSL)** |
| Mode | Do for user | Read to user | **Teach user** |
| Target | General | Visual impaired | **Deaf + Elderly** |
| Safety | Audio alerts | Audio/Vibration | **Visual + Sign alerts** |

## 🏗️ Architecture
cat >> create_signpilot_complete.sh << 'EOF'

# ==================== FLOATING UI HELPER ====================

cat > android-client/app/src/main/java/com/signpilot/ui/FloatingUIHelper.kt << 'KOTLIN'
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
        floatingViews["avatar"] = avatarView

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
KOTLIN

# ==================== WEBSOCKET MANAGER ====================

cat > android-client/app/src/main/java/com/signpilot/network/WebSocketManager.kt << 'KOTLIN'
package com.signpilot.network

import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

class WebSocketManager(
    private val url: String,
    private val onConnected: (() -> Unit)? = null,
    private val onMessage: ((String) -> Unit)? = null,
    private val onError: ((String) -> Unit)? = null
) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var reconnectAttempt = 0
    private val maxReconnectDelay = 30000L

    fun connect() {
        val request = Request.Builder().url(url).build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempt = 0
                onConnected?.invoke()

                webSocket.send(okhttp3.internal.EMPTY_REQUEST.body?.toString() ?:
                    """{"type":"init","device":"android"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                onMessage?.invoke(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onMessage?.invoke(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onError?.invoke(t.message ?: "Connection failed")
                reconnect()
            }
        }

        webSocket = client.newWebSocket(request, listener)
    }

    fun send(message: String): Boolean {
        return webSocket?.send(message) ?: false
    }

    fun disconnect() {
        webSocket?.close(1000, "Closing")
        client.dispatcher.executorService.shutdown()
    }

    private fun reconnect() {
        val delay = minOf(1000L * (1 shl reconnectAttempt), maxReconnectDelay)
        reconnectAttempt++

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            connect()
        }, delay)
    }
}
KOTLIN

# ==================== ACCESSIBILITY SERVICE ====================

cat > android-client/app/src/main/java/com/signpilot/service/SignPilotAccessibilityService.kt << 'KOTLIN'
package com.signpilot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi

class SignPilotAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_PERFORM_CLICK = "com.signpilot.PERFORM_CLICK"
        const val ACTION_SERVICE_BOUND = "com.signpilot.SERVICE_BOUND"
        const val TAG = "SignPilotService"
    }

    private var windowManager: WindowManager? = null
    private val clickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_PERFORM_CLICK) {
                val x = intent.getFloatExtra("x", 0f)
                val y = intent.getFloatExtra("y", 0f)
                performClick(x, y)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service Connected")

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_VISUAL
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
            notificationTimeout = 100
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        registerReceiver(clickReceiver, IntentFilter(ACTION_PERFORM_CLICK))

        sendBroadcast(Intent(ACTION_SERVICE_BOUND))
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun performClick(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Click completed at ($x, $y)")
            }
        }, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 可在此处监听窗口变化自动发送屏幕状态
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(clickReceiver)
    }
}
KOTLIN

# ==================== FIREBASE REPOSITORY ====================

cat > android-client/app/src/main/java/com/signpilot/data/firebase/FirebaseLearningRepository.kt << 'KOTLIN'
package com.signpilot.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

class FirebaseLearningRepository private constructor() {

    private val db = FirebaseFirestore.getInstance()

    companion object {
        @Volatile
        private var instance: FirebaseLearningRepository? = null

        fun getInstance() = instance ?: synchronized(this) {
            instance ?: FirebaseLearningRepository().also { instance = it }
        }
    }

    data class LearningSession(
        val userId: String,
        val taskId: String,
        val taskName: String,
        val startTime: Date = Date(),
        var endTime: Date? = null,
        var stepsCompleted: Int = 0,
        var totalSteps: Int = 0,
        var mistakes: List<MistakeRecord> = emptyList(),
        var finalSuccess: Boolean = false
    )

    data class MistakeRecord(
        val stepIndex: Int,
        val mistakeType: String,
        val timestamp: Date = Date(),
        val context: String = ""
    )

    suspend fun startSession(session: LearningSession): String =
        withContext(Dispatchers.IO) {
            val docRef = db.collection("users")
                .document(session.userId)
                .collection("learning_history")
                .document()

            val data = hashMapOf(
                "task_id" to session.taskId,
                "task_name" to session.taskName,
                "start_time" to session.startTime,
                "status" to "in_progress",
                "steps_completed" to 0,
                "mistakes" to arrayListOf<String>()
            )

            docRef.set(data).await()
            docRef.id
        }

    suspend fun updateProgress(
        userId: String,
        sessionId: String,
        step: Int,
        success: Boolean,
        mistake: MistakeRecord? = null
    ) = withContext(Dispatchers.IO) {
        val ref = db.collection("users")
            .document(userId)
            .collection("learning_history")
            .document(sessionId)

        val updates = hashMapOf<String, Any>(
            "steps_completed" to FieldValue.increment(1),
            "last_update" to Date()
        )

        if (mistake != null) {
            updates["mistakes"] = FieldValue.arrayUnion(
                hashMapOf(
                    "step" to mistake.stepIndex,
                    "type" to mistake.mistakeType,
                    "time" to Date()
                )
            )
        }

        ref.update(updates).await()
    }

    suspend fun recordInteraction(userId: String, sessionId: String, intentType: String, success: Boolean) =
        withContext(Dispatchers.IO) {
            // 简化记录
        }

    suspend fun completeSession(userId: String, sessionId: String, success: Boolean) =
        withContext(Dispatchers.IO) {
            db.collection("users")
                .document(userId)
                .collection("learning_history")
                .document(sessionId)
                .update(
                    mapOf(
                        "status" to if (success) "completed" else "failed",
                        "end_time" to Date(),
                        "final_success" to success
                    )
                ).await()
        }
}
KOTLIN

# ==================== GESTURE TRANSLATOR ====================

cat > android-client/app/src/main/java/com/signpilot/gesture/GestureTranslator.kt << 'KOTLIN'
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
KOTLIN

# ==================== XML LAYOUT FILES ====================

cat > android-client/app/src/main/res/layout/activity_main.xml << 'XML'
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#1A1A1A">

    <androidx.camera.view.PreviewView
        android:id="@+id/view_finder"
        android:layout_width="200dp"
        android:layout_height="250dp"
        android:layout_margin="16dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent" />

    <TextView
        android:id="@+id/tv_status"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="SignPilot 就绪"
        android:textColor="#4CAF50"
        android:textSize="18sp"
        android:layout_marginTop="16dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        android:layout_marginEnd="16dp" />

    <TextView
        android:id="@+id/tv_gesture_debug"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text=""
        android:textColor="#FFFFFF"
        android:textSize="14sp"
        android:layout_marginTop="8dp"
        app:layout_constraintTop_toBottomOf="@id/view_finder"
        app:layout_constraintStart_toStartOf="@id/view_finder" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="24dp"
        android:gravity="center"
        app:layout_constraintBottom_toBottomOf="parent"
        android:layout_marginBottom="100dp">

        <Button
            android:id="@+id/btn_connect"
            android:layout_width="match_parent"
            android:layout_height="60dp"
            android:text="连接 SignPilot"
            android:textSize="18sp"
            android:backgroundTint="#4285F4"
            android:layout_marginBottom="16dp" />

        <Button
            android:id="@+id/btn_test_warning"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="测试安全警告"
            android:backgroundTint="#EA4335"
            android:layout_marginBottom="16dp" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="提示：开启无障碍服务后，SignPilot 可以在其他应用上显示教学指引"
            android:textColor="#AAAAAA"
            android:textSize="14sp"
            android:gravity="center" />
    </LinearLayout>

    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/btn_test_highlight"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_margin="16dp"
        android:src="@android:drawable/ic_menu_info_details"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
XML

cat > android-client/app/src/main/res/xml/accessibility_service_config.xml << 'XML'
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/accessibility_service_description"
    android:packageNames="com.tencent.mm,com.eg.android.AlipayGphone,com.android.settings"
    android:accessibilityEventTypes="typeWindowStateChanged|typeViewClicked"
    android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows|flagRequestTouchExplorationMode"
    android:accessibilityFeedbackType="feedbackVisual"
    android:notificationTimeout="100"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    android:settingsActivity="com.signpilot.MainActivity" />
XML

cat > android-client/app/src/main/res/values/strings.xml << 'XML'
<resources>
    <string name="app_name">SignPilot</string>
    <string name="accessibility_service_description">SignPilot 辅助服务，用于帮助听障用户操作手机并提供视觉教学指引</string>
</resources>
XML

# ==================== APPLICATION CLASS ====================

cat > android-client/app/src/main/java/com/signpilot/SignPilotApp.kt << 'KOTLIN'
package com.signpilot

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class SignPilotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        val settings = firestoreSettings {
            isPersistenceEnabled = true
        }
        Firebase.firestore.firestoreSettings = settings
    }
}
KOTLIN

# ==================== HIGHLIGHT OVERLAY VIEW ====================

cat > android-client/app/src/main/java/com/signpilot/ui/views/HighlightOverlayView.kt << 'KOTLIN'
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
KOTLIN

# ==================== SUBTITLE VIEW ====================

cat > android-client/app/src/main/java/com/signpilot/ui/views/SubtitleView.kt << 'KOTLIN'
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
KOTLIN

echo ""
echo "✅ 所有代码生成完毕！"
echo ""
echo "📊 完整文件统计:"
echo "- Kotlin 文件: $(find android-client -name '*.kt' | wc -l)"
echo "- Python 文件: $(find agent-service -name '*.py' | wc -l)"
echo "- XML 文件: $(find android-client -name '*.xml' | wc -l)"
echo ""
echo "🎯 最终步骤:"
echo "1. bash create_signpilot_complete.sh"
echo "2. 下载 hand_landmarker.task 到 android-client/app/src/main/assets/"
echo "3. 获取 Firebase google-services.json"
echo "4. 更新 WS_URL 并构建"
echo ""
echo "打包命令:"
echo "zip -r signpilot-complete.zip $PROJECT_NAME"
