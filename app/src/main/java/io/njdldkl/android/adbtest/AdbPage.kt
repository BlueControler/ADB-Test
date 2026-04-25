package io.njdldkl.android.adbtest

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import rikka.shizuku.Shizuku

@Composable
internal fun AdbPage(
    viewModel: MainViewModel,
    context: Context,
    onOpenOverlayPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestShizuku: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onStart: (AdbConfig) -> Unit,
    onStop: () -> Unit
) {
    Text(
        text = "ADB",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = "手机端负责 WebSocket 连接、ADB 指令执行、截图采集和无障碍 UI 树上报。"
    )
    StatusLine("悬浮窗权限", if (viewModel.overlayGranted) "已授权" else "未授权")
    StatusLine("无障碍服务", if (viewModel.accessibilityEnabled) "已开启" else "未开启")
    StatusLine("Shizuku", if (viewModel.shizukuGranted) "已授权" else "未授权")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        StatusLine("通知权限", if (viewModel.notificationGranted) "已授权" else "未授权")
    }

    OutlinedTextField(
        value = viewModel.serverUrl,
        onValueChange = { viewModel.serverUrl = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("ADB WebSocket URL") },
        supportingText = { Text("例如 ws://localhost:8080；ADB 会连接到 /adb") },
        singleLine = true
    )

    Button(onClick = onOpenOverlayPermission, modifier = Modifier.fillMaxWidth()) {
        Text("打开悬浮窗权限设置")
    }
    Button(onClick = onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
        Text("打开无障碍设置")
    }
    Button(onClick = onRequestShizuku, modifier = Modifier.fillMaxWidth()) {
        Text("请求 Shizuku 权限")
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Button(onClick = onRequestNotificationPermission, modifier = Modifier.fillMaxWidth()) {
            Text("请求通知权限")
        }
    }

    Button(
        onClick = {
            viewModel.error = validateConfig(viewModel, context)
            if (viewModel.error == null) {
                onStart(AdbConfig(serverBaseUrl = viewModel.serverUrl.trim()))
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("启动 ADB 服务")
    }
    Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
        Text("停止 ADB 服务")
    }

    if (viewModel.error != null) {
        Text(
            text = viewModel.error.orEmpty(),
            color = MaterialTheme.colorScheme.error
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "说明：启动后会建立 `ws://host:port/adb` 连接；悬浮窗展示服务端状态消息，动作执行结果通过 `actionResult/error` 回传。"
    )
}

private fun validateConfig(viewModel: MainViewModel, context: Context): String? {
    if (viewModel.serverUrl.isBlank()) return "ADB WebSocket URL 不能为空。"
    if (!Settings.canDrawOverlays(context)) return "请先授予悬浮窗权限。"
    if (!isAccessibilityEnabled(context)) return "请先启用无障碍服务。"
    if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        return "请先授予 Shizuku 权限。"
    }
    return null
}
