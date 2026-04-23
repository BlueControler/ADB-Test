package io.njdldkl.android.adbtest

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import io.njdldkl.android.adbtest.adb.AdbService
import io.njdldkl.android.adbtest.termux.TermuxCommandEntry
import io.njdldkl.android.adbtest.termux.TermuxCommandRunner
import io.njdldkl.android.adbtest.termux.TermuxConstants
import io.njdldkl.android.adbtest.ui.theme.ADBTestTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<MainViewModel>()
    private val termuxCommandViewModel by viewModels<TermuxCommandViewModel>()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val termuxRunCommandPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        termuxCommandViewModel.refresh(this)
        if (granted) {
            termuxCommandViewModel.execute(this)
        } else {
            termuxCommandViewModel.error = "未授予 Termux RUN_COMMAND 权限。"
        }
    }

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
        termuxCommandViewModel.refresh(this)
        setContent {
            ADBTestTheme {
                Surface {
                    MainScreen(
                        viewModel = viewModel,
                        termuxCommandViewModel = termuxCommandViewModel,
                        onOpenOverlayPermission = ::openOverlayPermission,
                        onOpenAccessibilitySettings = ::openAccessibilitySettings,
                        onRequestShizuku = ::requestShizukuPermission,
                        onRequestNotificationPermission = ::requestNotificationPermission,
                        onStart = { startAdbService(it) },
                        onStop = ::stopAdbService,
                        onExecuteTermuxCommand = ::executeTermuxCommand
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh(this)
        termuxCommandViewModel.refresh(this)
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

    private fun startAdbService(config: AdbConfig) {
        val intent = AdbService.createStartIntent(this, config)
        ContextCompat.startForegroundService(this, intent)
        viewModel.serviceRunning = true
    }

    private fun stopAdbService() {
        startService(AdbService.createStopIntent(this))
        viewModel.serviceRunning = false
    }

    private fun executeTermuxCommand() {
        termuxCommandViewModel.refresh(this)
        if (termuxCommandViewModel.termuxRunCommandGranted) {
            termuxCommandViewModel.execute(this)
        } else {
            termuxCommandViewModel.error = null
            termuxRunCommandPermissionLauncher.launch(TermuxConstants.PERMISSION_RUN_COMMAND)
        }
    }

    companion object {
        private const val SHIZUKU_REQUEST_CODE = 7001
    }
}

@Composable
private fun MainScreen(
    viewModel: MainViewModel,
    termuxCommandViewModel: TermuxCommandViewModel,
    onOpenOverlayPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestShizuku: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onStart: (AdbConfig) -> Unit,
    onStop: () -> Unit,
    onExecuteTermuxCommand: () -> Unit
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.currentPage = MainPage.Adb },
                    modifier = Modifier.weight(1f),
                    enabled = viewModel.currentPage != MainPage.Adb
                ) {
                    Text("ADB")
                }
                Button(
                    onClick = { viewModel.currentPage = MainPage.Termux },
                    modifier = Modifier.weight(1f),
                    enabled = viewModel.currentPage != MainPage.Termux
                ) {
                    Text("Termux")
                }
            }
            HorizontalDivider()
            when (viewModel.currentPage) {
                MainPage.Adb -> AdbPage(
                    viewModel = viewModel,
                    context = context,
                    onOpenOverlayPermission = onOpenOverlayPermission,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onRequestShizuku = onRequestShizuku,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onStart = onStart,
                    onStop = onStop
                )
                MainPage.Termux -> TermuxCommandPage(
                    viewModel = termuxCommandViewModel,
                    onExecute = onExecuteTermuxCommand,
                    onOpenPermissionSettings = {
                        termuxCommandViewModel.openAppSettings(context)
                    },
                    onClear = termuxCommandViewModel::clearResults
                )
            }
        }
    }
}

@Composable
private fun AdbPage(
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
                onStart(
                    AdbConfig(
                        serverBaseUrl = viewModel.serverUrl.trim()
                    )
                )
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

@Composable
private fun TermuxCommandPage(
    viewModel: TermuxCommandViewModel,
    onExecute: () -> Unit,
    onOpenPermissionSettings: () -> Unit,
    onClear: () -> Unit
) {
    Text(
        text = "Termux Command",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold
    )
    StatusLine("RUN_COMMAND 权限", if (viewModel.termuxRunCommandGranted) "已授权" else "未授权")
    StatusLine("WORKDIR", TermuxCommandRunner.HOME)

    OutlinedTextField(
        value = viewModel.command,
        onValueChange = { viewModel.command = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Command") },
        minLines = 3
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onExecute,
            modifier = Modifier.weight(1f)
        ) {
            Text("执行")
        }
        TextButton(
            onClick = onOpenPermissionSettings,
            modifier = Modifier.weight(1f)
        ) {
            Text("权限设置")
        }
        TextButton(onClick = onClear) {
            Text("清空")
        }
    }

    if (viewModel.error != null) {
        Text(
            text = viewModel.error.orEmpty(),
            color = MaterialTheme.colorScheme.error
        )
    }

    if (viewModel.entries.isEmpty()) {
        Text("暂无执行结果。")
    } else {
        viewModel.entries.forEach { entry ->
            TermuxCommandResultCard(entry)
        }
    }
}

@Composable
private fun TermuxCommandResultCard(entry: TermuxCommandEntry) {
    val result = entry.result
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "$ ${entry.command}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (result == null) {
                Text("运行中...")
                return@Column
            }
            StatusLine("Exit Code", result.exitCode?.toString() ?: "N/A")
            if (result.error != null) {
                Text(result.error, color = MaterialTheme.colorScheme.error)
            }
            CommandOutputBlock("STDOUT", result.stdout)
            CommandOutputBlock("STDERR", result.stderr)
        }
    }
}

@Composable
private fun CommandOutputBlock(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        SelectionContainer {
            Text(
                text = value.ifBlank { "(empty)" },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Text(text = "$label: $value")
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

private fun isAccessibilityEnabled(context: Context): Boolean {
    val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
    val expectedClassName = "io.njdldkl.android.adbtest.adb.AdbAccessibilityService"
    val enabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    return enabled.any {
        it.resolveInfo.serviceInfo.packageName == context.packageName &&
            it.resolveInfo.serviceInfo.name == expectedClassName
    }
}

data class AdbConfig(
    val serverBaseUrl: String
)

enum class MainPage {
    Adb,
    Termux
}

class MainViewModel : ViewModel() {
    var currentPage by mutableStateOf(MainPage.Adb)
    var serverUrl by mutableStateOf("ws://localhost:8080")
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
