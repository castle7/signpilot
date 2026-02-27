package com.signpilot

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.signpilot.data.firebase.FirebaseLearningRepository
import com.signpilot.databinding.ActivityMainBinding
import com.signpilot.gesture.GestureTranslator
import com.signpilot.gesture.HandGestureDetector
import com.signpilot.network.WebSocketManager
import com.signpilot.service.SignPilotAccessibilityService
import com.signpilot.ui.FloatingUIHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var gestureDetector: HandGestureDetector
    private lateinit var gestureTranslator: GestureTranslator
    private lateinit var floatingUI: FloatingUIHelper
    private lateinit var webSocketManager: WebSocketManager
    private lateinit var firebaseRepo: FirebaseLearningRepository

    private var currentSessionId: String? = null
    private var isConnected = false
    private val scope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        // 替换为你的 Cloud Run URL
        private const val WS_URL = "wss://your-cloud-run-url/ws"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        floatingUI = FloatingUIHelper(this)
        firebaseRepo = FirebaseLearningRepository.getInstance()
        gestureTranslator = GestureTranslator()

        checkPermissions()
        setupUI()
        initGestureRecognition()
        registerReceivers()
    }

    private fun setupUI() {
        binding.btnConnect.setOnClickListener {
            if (!isConnected) connectToAgent() else disconnectFromAgent()
        }

        binding.btnTestHighlight.setOnClickListener {
            testHighlightFeature()
        }

        binding.btnTestWarning.setOnClickListener {
            testSafetyFeature()
        }

        if (!isAccessibilityServiceEnabled()) {
            showAccessibilityDialog()
        }
    }

    private fun connectToAgent() {
        webSocketManager = WebSocketManager(
            url = WS_URL,
            onConnected = {
                runOnUiThread {
                    isConnected = true
                    binding.btnConnect.text = "断开连接"
                    binding.statusIndicator.setBackgroundColor(getColor(android.R.color.holo_green_dark))
                    Toast.makeText(this, "已连接到 SignPilot", Toast.LENGTH_SHORT).show()
                    floatingUI.showSignAvatar()
                }
            },
            onMessage = { message -> handleAgentMessage(message) },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(this, "错误: $error", Toast.LENGTH_SHORT).show()
                }
            }
        )
        webSocketManager.connect()
    }

    private fun disconnectFromAgent() {
        webSocketManager.disconnect()
        isConnected = false
        binding.btnConnect.text = "连接 SignPilot"
        binding.statusIndicator.setBackgroundColor(getColor(android.R.color.darker_gray))
        floatingUI.removeAllViews()
    }

    private fun initGestureRecognition() {
        gestureDetector = HandGestureDetector(
            context = this,
            onGestureRecognized = { result -> handleGestureResult(result) },
            onDebugInfo = { info -> Log.d(TAG, info) }
        )
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(binding.viewFinder.surfaceProvider) }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, gestureDetector.getAnalyzer()) }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (exc: Exception) {
                Log.e(TAG, "Camera error", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleGestureResult(result: HandGestureDetector.GestureResult) {
        if (!isConnected) return

        val intent = gestureTranslator.processGesture(result.gesture)
        intent?.let {
            showGestureFeedback(it.text)

            val message = JSONObject().apply {
                put("type", "gesture_input")
                put("text", it.text)
                put("intent", it.intentType)
                put("confidence", it.confidence)
                put("hand_side", result.handSide)
                put("timestamp", System.currentTimeMillis())
                put("user_id", getUserId())
            }.toString()

            webSocketManager.send(message)

            currentSessionId?.let { sessionId ->
                scope.launch {
                    firebaseRepo.recordInteraction(getUserId(), sessionId, it.intentType, true)
                }
            }
        }
    }

    private fun handleAgentMessage(message: String) {
        runOnUiThread {
            try {
                val json = JSONObject(message)
                when (json.getString("action")) {
                    "HIGHLIGHT" -> {
                        val payload = json.getJSONObject("payload")
                        val coords = payload.getJSONObject("coordinates")
                        floatingUI.showHighlight(
                            coords.getInt("x"), coords.getInt("y"),
                            coords.getInt("width"), coords.getInt("height")
                        )
                    }
                    "CLICK" -> performClick(json.getJSONArray("coordinates"))
                    "SIGN_LANGUAGE" -> playSignAnimation(json.getJSONArray("gestures"))
                    "SUBTITLE" -> floatingUI.showSubtitle(json.getString("text"))
                    "WARNING" -> {
                        floatingUI.showSecurityWarning(json.getString("message"))
                        floatingUI.getAvatarView()?.setEmotion(
                            com.signpilot.ui.views.SignAvatarView.Emotion.WARNING
                        )
                    }
                    "SESSION_START" -> currentSessionId = json.getString("session_id")
                }
            } catch (e: Exception) { Log.e(TAG, "Parse error", e) }
        }
    }

    private fun performClick(coords: org.json.JSONArray) {
        val intent = Intent(SignPilotAccessibilityService.ACTION_PERFORM_CLICK).apply {
            putExtra("x", coords.getDouble(0).toFloat())
            putExtra("y", coords.getDouble(1).toFloat())
        }
        sendBroadcast(intent)
    }

    private fun playSignAnimation(gestures: JSONArray) {
        val sequence = mutableListOf<com.signpilot.ui.views.SignAvatarView.SignGesture>()
        for (i in 0 until gestures.length()) {
            val g = gestures.getJSONObject(i)
            sequence.add(com.signpilot.ui.views.SignAvatarView.SignGesture(
                name = g.getString("name"),
                jointTargets = emptyMap(),
                durationMs = g.optLong("duration", 1000)
            ))
        }
        floatingUI.getAvatarView()?.playSignSequence(sequence)
    }

    private fun testHighlightFeature() {
        floatingUI.showHighlight(500, 800, 300, 150)
        floatingUI.showSubtitle("请点击这个蓝色按钮")
        floatingUI.getAvatarView()?.pointTo(650f, 875f)
    }

    private fun testSafetyFeature() {
        floatingUI.showSecurityWarning("检测到可疑链接！")
        floatingUI.getAvatarView()?.setEmotion(
            com.signpilot.ui.views.SignAvatarView.Emotion.WARNING
        )
    }

    private fun showGestureFeedback(text: String) {
        binding.tvGestureDebug.text = "识别: $text"
        binding.tvGestureDebug.alpha = 1f
        binding.tvGestureDebug.animate().alpha(0f).setDuration(2000).start()
    }

    private fun getUserId(): String {
        return "user_${Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)}"
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "$packageName/.service.SignPilotAccessibilityService"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.contains(service)
    }

    private fun showAccessibilityDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要无障碍权限")
            .setMessage("SignPilot 需要无障碍权限来帮助您操作手机。")
            .setPositiveButton("去设置") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun registerReceivers() {
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // 处理服务返回
            }
        }, IntentFilter(SignPilotAccessibilityService.ACTION_SERVICE_BOUND))
    }

    private fun checkPermissions() {
        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        if (::webSocketManager.isInitialized) webSocketManager.disconnect()
        floatingUI.removeAllViews()
    }
}
