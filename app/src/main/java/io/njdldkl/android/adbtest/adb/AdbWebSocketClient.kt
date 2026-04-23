package io.njdldkl.android.adbtest.adb

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

class AdbWebSocketClient(
    private val scope: CoroutineScope,
    private val config: io.njdldkl.android.adbtest.AdbConfig,
    private val collectSnapshot: suspend () -> AdbSnapshot,
    private val onServerRequest: suspend (JSONObject) -> Unit,
    private val onStatus: (String, String?) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null
    private val incomingBuffer = StringBuilder()

    @Volatile
    private var closedByClient = false

    fun connect() {
        closedByClient = false
        val targetUrl = buildSocketRequestUrl()
        val request = Request.Builder()
            .url(targetUrl)
            .build()
        onStatus("连接中", request.url.toString())
        webSocket = client.newWebSocket(request, listener)
    }

    fun disconnect() {
        closedByClient = true
        pingJob?.cancel()
        webSocket?.close(1000, "client stop")
        webSocket = null
        client.dispatcher.executorService.shutdown()
    }

    fun sendResponse(message: String, requestId: Int?, data: JSONObject?) {
        sendEnvelope("response", message, requestId, data)
    }

    fun sendRequest(message: String, requestId: Int?, data: JSONObject?) {
        sendEnvelope("request", message, requestId, data)
    }

    private fun sendEnvelope(type: String, message: String, requestId: Int?, data: JSONObject?) {
        val body = JSONObject().apply {
            put("type", type)
            put("message", message)
            put("data", data ?: JSONObject.NULL)
            if (requestId != null) {
                put("requestId", requestId)
            }
        }
        webSocket?.send("$body\n")
    }

    private suspend fun sendConnect() {
        val snapshot = collectSnapshot()
        sendRequest(
            message = "connect",
            requestId = CONNECT_REQUEST_ID,
            data = snapshot.toJson().apply {
                put("width", snapshotWidth)
                put("height", snapshotHeight)
            }
        )
        onStatus("已连接", "首帧已上报")
    }

    var snapshotWidth: Int = 0
    var snapshotHeight: Int = 0

    private fun startHeartbeat() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (true) {
                delay(20_000.milliseconds)
                sendRequest("ping", null, null)
            }
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            scope.launch {
                runCatching { sendConnect() }
                    .onFailure { onStatus("连接失败", it.message) }
            }
            startHeartbeat()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            incomingBuffer.append(text)
            while (true) {
                val newlineIndex = incomingBuffer.indexOf("\n")
                if (newlineIndex < 0) break
                val raw = incomingBuffer.substring(0, newlineIndex).trim()
                incomingBuffer.delete(0, newlineIndex + 1)
                if (raw.isBlank()) continue
                scope.launch {
                    handleLine(raw)
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            onStatus("连接关闭", "$code $reason")
            reconnectLater()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            onStatus("连接异常", t.message)
            reconnectLater()
        }
    }

    private suspend fun handleLine(raw: String) {
        val envelope = JSONObject(raw)
        val type = envelope.optString("type")
        val message = envelope.optString("message")
        if (type == "request" && message == "ping") {
            sendResponse("pong", null, null)
            return
        }
        if (type == "response" && message == "pong") {
            onStatus("心跳正常", null)
            return
        }
        if (type == "request") {
            onServerRequest(envelope)
        }
    }

    private fun reconnectLater() {
        if (closedByClient) return
        scope.launch {
            delay(3_000.milliseconds)
            connect()
        }
    }

    private fun buildSocketRequestUrl(): String {
        val base = config.serverBaseUrl.trim()
        require(base.isNotBlank()) { "Server URL 不能为空。" }
        val normalizedBase = when {
            base.startsWith("ws://") -> "http://${base.removePrefix("ws://")}"
            base.startsWith("wss://") -> "https://${base.removePrefix("wss://")}"
            base.startsWith("http://") || base.startsWith("https://") -> base
            else -> "http://$base"
        }
        val httpUrl = normalizedBase.toHttpUrl()
        return httpUrl.newBuilder()
            .encodedPath(PROTOCOL_PATH)
            .build()
            .toString()
    }

    private companion object {
        private const val CONNECT_REQUEST_ID = 1
        private const val PROTOCOL_PATH = "/adb"
    }
}
