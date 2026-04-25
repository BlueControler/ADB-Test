package io.njdldkl.android.adbtest

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import rikka.shizuku.Shizuku

data class AdbConfig(
    val serverBaseUrl: String
)

enum class MainPage {
    Adb,
    Termux,
    System
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

internal fun isAccessibilityEnabled(context: Context): Boolean {
    val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
    val expectedClassName = "io.njdldkl.android.adbtest.adb.AdbAccessibilityService"
    val enabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    return enabled.any {
        it.resolveInfo.serviceInfo.packageName == context.packageName &&
                it.resolveInfo.serviceInfo.name == expectedClassName
    }
}
