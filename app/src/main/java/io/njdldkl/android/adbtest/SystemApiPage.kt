package io.njdldkl.android.adbtest

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.njdldkl.android.adbtest.system.SystemConfig

@Composable
internal fun SystemApiPage(
    viewModel: SystemApiViewModel,
    context: Context,
    onRequestCalendarPermission: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onStartSystem: (SystemConfig) -> Unit,
    onStopSystem: () -> Unit
) {
    Text(
        text = "System API",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold
    )
    StatusLine("日历权限", if (viewModel.calendarGranted) "已授权" else "未授权")
    StatusLine("定位权限", if (viewModel.locationGranted) "已授权" else "未授权")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        StatusLine("通知权限", if (viewModel.notificationGranted) "已授权" else "未授权")
    }
    StatusLine("System WebSocket", if (viewModel.serviceRunning) "运行中" else "未运行")

    OutlinedTextField(
        value = viewModel.serverUrl,
        onValueChange = { viewModel.serverUrl = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("System WebSocket URL") },
        supportingText = { Text("例如 ws://localhost:8080；System API 会连接到 /system") },
        singleLine = true
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onRequestCalendarPermission,
            modifier = Modifier.weight(1f)
        ) {
            Text("日历权限")
        }
        Button(
            onClick = onRequestLocationPermission,
            modifier = Modifier.weight(1f)
        ) {
            Text("定位权限")
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Button(onClick = onRequestNotificationPermission, modifier = Modifier.fillMaxWidth()) {
            Text("通知权限")
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {
                viewModel.error = validateSystemConfig(viewModel)
                if (viewModel.error == null) {
                    onStartSystem(SystemConfig(viewModel.serverUrl.trim()))
                }
            },
            modifier = Modifier.weight(1f)
        ) {
            Text("启动服务")
        }
        Button(
            onClick = onStopSystem,
            modifier = Modifier.weight(1f)
        ) {
            Text("停止服务")
        }
    }

    Text("本地调用", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { viewModel.callListApps(context, "all") },
            modifier = Modifier.weight(1f)
        ) {
            Text("全部应用")
        }
        Button(
            onClick = { viewModel.callListApps(context, "third") },
            modifier = Modifier.weight(1f)
        ) {
            Text("第三方")
        }
        Button(
            onClick = { viewModel.callListApps(context, "system") },
            modifier = Modifier.weight(1f)
        ) {
            Text("系统")
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { viewModel.callCreateEvent(context) },
            modifier = Modifier.weight(1f)
        ) {
            Text("创建日程")
        }
        Button(
            onClick = { viewModel.callListEvents(context) },
            modifier = Modifier.weight(1f)
        ) {
            Text("查询日程")
        }
        Button(
            onClick = { viewModel.callUpdateEvent(context) },
            modifier = Modifier.weight(1f)
        ) {
            Text("更新日程")
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { viewModel.callListReminders(context) },
            modifier = Modifier.weight(1f)
        ) {
            Text("查提醒")
        }
        Button(
            onClick = { viewModel.callUpdateReminders(context) },
            modifier = Modifier.weight(1f)
        ) {
            Text("改提醒")
        }
        Button(
            onClick = { viewModel.callGetLocation(context) },
            modifier = Modifier.weight(1f)
        ) {
            Text("定位")
        }
    }

    TextButton(onClick = viewModel::clearResult) {
        Text("清空响应")
    }

    if (viewModel.error != null) {
        Text(
            text = viewModel.error.orEmpty(),
            color = MaterialTheme.colorScheme.error
        )
    }
    if (viewModel.resultJson.isBlank()) {
        Text("暂无响应。")
    } else {
        SelectionContainer {
            Text(
                text = viewModel.resultJson,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun validateSystemConfig(viewModel: SystemApiViewModel): String? {
    if (viewModel.serverUrl.isBlank()) return "System WebSocket URL 不能为空。"
    return null
}
