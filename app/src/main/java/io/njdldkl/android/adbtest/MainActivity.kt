package io.njdldkl.android.adbtest

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import io.njdldkl.android.adbtest.agent.AgentService
import io.njdldkl.android.adbtest.ui.theme.ADBTestTheme
import rikka.shizuku.Shizuku
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<MainViewModel>()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_REQUEST_CODE) {
                viewModel.refresh(this)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        viewModel.refresh(this)
        setContent {
            ADBTestTheme {
                Surface {
                    MainScreen(
                        viewModel = viewModel,
                        onOpenOverlayPermission = ::openOverlayPermission,
                        onOpenAccessibilitySettings = ::openAccessibilitySettings,
                        onRequestShizuku = ::requestShizukuPermission,
                        onRequestNotificationPermission = ::requestNotificationPermission,
                        onStart = { startAgentService(it) },
                        onStop = ::stopAgentService
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh(this)
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        super.onDestroy()
    }

    private fun openOverlayPermission() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
        )
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestShizukuPermission() {
        if (Shizuku.isPreV11()) {
            return
        }
        if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        }
    }

    private fun startAgentService(config: AgentConfig) {
        val intent = AgentService.createStartIntent(this, config)
        ContextCompat.startForegroundService(this, intent)
        viewModel.serviceRunning = true
    }

    private fun stopAgentService() {
        startService(AgentService.createStopIntent(this))
        viewModel.serviceRunning = false
    }

    companion object {
        private const val SHIZUKU_REQUEST_CODE = 7001
    }
}

@Composable
private fun MainScreen(
    viewModel: MainViewModel,
    onOpenOverlayPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestShizuku: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onStart: (AgentConfig) -> Unit,
    onStop: () -> Unit
) {
    val context = LocalContext.current
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "ADB Agent",
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
                label = { Text("Server Base URL") },
                supportingText = { Text("例如 wss://server") },
                singleLine = true
            )
            OutlinedTextField(
                value = viewModel.deviceId,
                onValueChange = { viewModel.deviceId = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Device ID") },
                singleLine = true
            )
            OutlinedTextField(
                value = viewModel.token,
                onValueChange = { viewModel.token = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("JWT Token") }
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
                        onStart(
                            AgentConfig(
                                serverBaseUrl = viewModel.serverUrl.trim(),
                                deviceId = viewModel.deviceId.trim(),
                                token = viewModel.token.trim()
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("启动 Agent 服务")
            }
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                Text("停止 Agent 服务")
            }

            if (viewModel.error != null) {
                Text(
                    text = viewModel.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "说明：启动后会建立 `wss://server/ws/devices/{deviceId}` 连接；悬浮窗展示服务端状态消息，动作执行结果通过 `actionResult/error` 回传。"
            )
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Text(text = "$label: $value")
}

private fun validateConfig(viewModel: MainViewModel, context: Context): String? {
    if (viewModel.serverUrl.isBlank()) return "Server Base URL 不能为空。"
    if (viewModel.deviceId.isBlank()) return "Device ID 不能为空。"
    if (viewModel.token.isBlank()) return "JWT Token 不能为空。"
    if (!Settings.canDrawOverlays(context)) return "请先授予悬浮窗权限。"
    if (!isAccessibilityEnabled(context)) return "请先启用无障碍服务。"
    if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        return "请先授予 Shizuku 权限。"
    }
    return null
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
    val expectedClassName = "io.njdldkl.android.adbtest.agent.AgentAccessibilityService"
    val enabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    return enabled.any {
        it.resolveInfo.serviceInfo.packageName == context.packageName &&
            it.resolveInfo.serviceInfo.name == expectedClassName
    }
}

data class AgentConfig(
    val serverBaseUrl: String,
    val deviceId: String,
    val token: String
)

class MainViewModel : ViewModel() {
    var serverUrl by mutableStateOf("wss://server")
    var deviceId by mutableStateOf("")
    var token by mutableStateOf("")
    var overlayGranted by mutableStateOf(false)
    var accessibilityEnabled by mutableStateOf(false)
    var shizukuGranted by mutableStateOf(false)
    var notificationGranted by mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
    var serviceRunning by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun refresh(context: Context) {
        overlayGranted = Settings.canDrawOverlays(context)
        accessibilityEnabled = isAccessibilityEnabled(context)
        shizukuGranted = Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        notificationGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
