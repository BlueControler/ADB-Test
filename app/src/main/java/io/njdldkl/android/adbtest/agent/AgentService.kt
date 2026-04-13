package io.njdldkl.android.adbtest.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.njdldkl.android.adbtest.AgentConfig
import io.njdldkl.android.adbtest.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.time.Duration.Companion.milliseconds

class AgentService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var overlayController: OverlayController
    private lateinit var executor: ShizukuAdbExecutor
    private lateinit var collector: DeviceSnapshotCollector
    private var client: DeviceWebSocketClient? = null

    override fun onCreate() {
        super.onCreate()
        overlayController = OverlayController(this)
        executor = ShizukuAdbExecutor(packageName)
        collector = DeviceSnapshotCollector(executor)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_content)))
        overlayController.show()
        overlayController.update(getString(R.string.overlay_waiting), null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val config = AgentConfig(
            serverBaseUrl = intent?.getStringExtra(EXTRA_SERVER_URL).orEmpty(),
            deviceId = intent?.getStringExtra(EXTRA_DEVICE_ID).orEmpty(),
            token = intent?.getStringExtra(EXTRA_TOKEN).orEmpty()
        )

        if (config.serverBaseUrl.isBlank() || config.deviceId.isBlank() || config.token.isBlank()) {
            overlayController.update("启动失败", "连接参数缺失")
            stopSelf()
            return START_NOT_STICKY
        }

        client?.disconnect()
        client = DeviceWebSocketClient(
            scope = serviceScope,
            config = config,
            collectSnapshot = { collector.collect() },
            onServerRequest = { handleServerRequest(it) },
            onStatus = { status, detail ->
                overlayController.update(status, detail)
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, buildNotification("$status ${detail.orEmpty()}".trim()))
            }
        ).also {
            val metrics = resources.displayMetrics
            it.snapshotWidth = metrics.widthPixels
            it.snapshotHeight = metrics.heightPixels
            it.connect()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        client?.disconnect()
        client = null
        runCatching { kotlinx.coroutines.runBlocking { executor.destroy() } }
        overlayController.hide()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun handleServerRequest(envelope: JSONObject) {
        val requestId = envelope.optInt("requestId")
        val message = envelope.optString("message")
        val data = envelope.optJSONObject("data")
        overlayController.update("执行中", message)
        // 打印日志
        Log.i("测试","执行请求！")

        runCatching {
            when (message) {
                "observe" -> Unit
                "launch" -> {
                    val pkg = data?.optString("package").orEmpty()
                    require(pkg.isNotBlank()) { "package 不能为空。" }
                    runShell("monkey -p $pkg -c android.intent.category.LAUNCHER 1")
                }
                "tap" -> {
                    runShell("input tap ${data.requiredInt("x")} ${data.requiredInt("y")}")
                }
                "type" -> {
                    val text = data?.optString("text").orEmpty()
                    require(text.isNotBlank()) { "text 不能为空。" }
                    runShell("input text ${shellQuote(text)}")
                }
                "swipe" -> {
                    runShell(
                        "input swipe ${data.requiredInt("startX")} ${data.requiredInt("startY")} " +
                            "${data.requiredInt("endX")} ${data.requiredInt("endY")} 250"
                    )
                }
                "longPress" -> {
                    val x = data.requiredInt("x")
                    val y = data.requiredInt("y")
                    runShell("input swipe $x $y $x $y 800")
                }
                "doubleTap" -> {
                    val x = data.requiredInt("x")
                    val y = data.requiredInt("y")
                    runShell("input tap $x $y")
                    delay(120.milliseconds)
                    runShell("input tap $x $y")
                }
                "back" -> runShell("input keyevent KEYCODE_BACK")
                "home" -> runShell("input keyevent KEYCODE_HOME")
                "wait" -> {
                    val seconds = data?.optDouble("duration", 0.0) ?: 0.0
                    require(seconds >= 0.0) { "duration 不能为负数。" }
                    delay((seconds * 1000).toLong().milliseconds)
                }
                "interact", "takeOver", "finish" -> {
                    overlayController.update(message, data?.optString("message"))
                }
                else -> error("未知消息类型: $message")
            }
        }.fold(
            onSuccess = {
                val snapshot = collector.collect()
                client?.sendResponse("actionResult", requestId, snapshot.toJson())
                overlayController.update("已完成", message)
            },
            onFailure = { error ->
                val snapshot = collector.collect()
                client?.sendResponse(
                    "error",
                    requestId,
                    snapshot.toJson().apply {
                        put("message", error.message ?: "执行失败")
                    }
                )
                overlayController.update("执行失败", error.message)
            }
        )
    }

    private suspend fun runShell(command: String) {
        val result = executor.execute(command)
        check(result.isSuccess) { result.stderr.ifBlank { "命令执行失败: $command" } }
    }

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val ACTION_START = "io.njdldkl.android.adbtest.agent.START"
        private const val ACTION_STOP = "io.njdldkl.android.adbtest.agent.STOP"
        private const val EXTRA_SERVER_URL = "server_url"
        private const val EXTRA_DEVICE_ID = "device_id"
        private const val EXTRA_TOKEN = "token"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_CHANNEL_ID = "agent_connection"

        fun createStartIntent(context: Context, config: AgentConfig): Intent {
            return Intent(context, AgentService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SERVER_URL, config.serverBaseUrl)
                putExtra(EXTRA_DEVICE_ID, config.deviceId)
                putExtra(EXTRA_TOKEN, config.token)
            }
        }

        fun createStopIntent(context: Context): Intent {
            return Intent(context, AgentService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }
}

private fun JSONObject?.requiredInt(key: String): Int {
    require(this != null && has(key)) { "$key 缺失。" }
    return getInt(key)
}

private fun shellQuote(text: String): String {
    return "'" + text.replace("'", "'\\''").replace(" ", "%s") + "'"
}
