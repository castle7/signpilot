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
                webSocket.send("""{"type":"init","device":"android"}""")
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
