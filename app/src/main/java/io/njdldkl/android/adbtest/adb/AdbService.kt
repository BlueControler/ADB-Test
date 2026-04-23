package io.njdldkl.android.adbtest.adb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import io.njdldkl.android.adbtest.AdbConfig
import io.njdldkl.android.adbtest.R
import kotlinx.coroutines.*
import org.json.JSONObject
import kotlin.time.Duration.Companion.milliseconds

class AdbService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var overlayController: AdbOverlayController
    private lateinit var executor: ShizukuAdbExecutor
    private lateinit var collector: AdbSnapshotCollector
    private var client: AdbWebSocketClient? = null

    override fun onCreate() {
        super.onCreate()
        overlayController = AdbOverlayController(this)
        executor = ShizukuAdbExecutor(packageName)
        collector = AdbSnapshotCollector(executor)
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

        val config = AdbConfig(
            serverBaseUrl = intent?.getStringExtra(EXTRA_SERVER_URL).orEmpty()
        )

        if (config.serverBaseUrl.isBlank()) {
            overlayController.update("启动失败", "连接参数缺失")
            stopSelf()
            return START_NOT_STICKY
        }

        client?.disconnect()
        client = AdbWebSocketClient(
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
        Log.i("测试", "执行请求！")

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
                    typeWithAdbKeyboard(text)
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

                "keyevent" -> {
                    runShell("input keyevent ${data.requiredInt("keyevent")}")
                }

                "interact" -> {
                    waitForInteraction(data?.optString("message"))
                }

                else -> error("未知消息类型: $message")
            }
        }.fold(
            onSuccess = {
                val snapshot = collector.collect()
                client?.sendResponse("actionResult", requestId, snapshotJson(snapshot))
                overlayController.update("已完成", message)
            },
            onFailure = { error ->
                val snapshot = collector.collect()
                client?.sendResponse(
                    "error",
                    requestId,
                    snapshotJson(snapshot).apply {
                        put("message", error.message ?: "执行失败")
                    }
                )
                overlayController.update("执行失败", error.message)
            }
        )
    }

    private fun snapshotJson(snapshot: AdbSnapshot): JSONObject = snapshot.toJson()

    private suspend fun waitForInteraction(message: String?) {
        val completed = CompletableDeferred<Unit>()
        overlayController.waitForInteraction(message) {
            completed.complete(Unit)
        }
        completed.await()
    }

    private suspend fun runShell(command: String) {
        val result = executor.execute(command)
        check(result.isSuccess) { result.stderr.ifBlank { "命令执行失败: $command" } }
    }

    private suspend fun typeWithAdbKeyboard(text: String) {
        val currentIme = currentInputMethod()
        val restoreIme = if (currentIme.isNullOrBlank() || currentIme == ADB_KEYBOARD_IME) {
            firstNonAdbInputMethod()
        } else {
            currentIme
        }
        require(!restoreIme.isNullOrBlank()) { "未找到可恢复的非 ADB 输入法。" }

        try {
            runShell("ime set $ADB_KEYBOARD_IME")
            val encodedText = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            runShell("am broadcast -a ADB_INPUT_B64 --es msg '$encodedText'")
        } finally {
            runCatching { runShell("ime set $restoreIme") }
                .onFailure { Log.w("测试", "恢复输入法失败: ${it.message}") }
        }
    }

    private suspend fun currentInputMethod(): String? {
        val result = executor.execute("settings get secure default_input_method")
        check(result.isSuccess) { result.stderr.ifBlank { "读取当前输入法失败" } }
        return result.stdout.trim().takeUnless { it.isBlank() || it == "null" }
    }

    private suspend fun firstNonAdbInputMethod(): String? {
        val result = executor.execute("ime list -s")
        check(result.isSuccess) { result.stderr.ifBlank { "读取输入法列表失败" } }
        return result.stdout.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && it != ADB_KEYBOARD_IME }
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
        private const val ACTION_START = "io.njdldkl.android.adbtest.adb.START"
        private const val ACTION_STOP = "io.njdldkl.android.adbtest.adb.STOP"
        private const val EXTRA_SERVER_URL = "server_url"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_CHANNEL_ID = "adb_connection"
        private const val ADB_KEYBOARD_IME = "com.android.adbkeyboard/.AdbIME"

        fun createStartIntent(context: Context, config: AdbConfig): Intent {
            return Intent(context, AdbService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SERVER_URL, config.serverBaseUrl)
            }
        }

        fun createStopIntent(context: Context): Intent {
            return Intent(context, AdbService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }
}

private fun JSONObject?.requiredInt(key: String): Int {
    require(this != null && has(key)) { "$key 缺失。" }
    return getInt(key)
}
