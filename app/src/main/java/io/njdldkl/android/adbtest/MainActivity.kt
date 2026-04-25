package io.njdldkl.android.adbtest

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import io.njdldkl.android.adbtest.adb.AdbService
import io.njdldkl.android.adbtest.system.SystemConfig
import io.njdldkl.android.adbtest.system.SystemService
import io.njdldkl.android.adbtest.termux.TermuxConstants
import io.njdldkl.android.adbtest.ui.theme.ADBTestTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<MainViewModel>()
    private val termuxCommandViewModel by viewModels<TermuxCommandViewModel>()
    private val systemApiViewModel by viewModels<SystemApiViewModel>()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refresh(this)
        systemApiViewModel.refresh(this)
    }

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

    private val calendarPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        systemApiViewModel.refresh(this)
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        systemApiViewModel.refresh(this)
    }

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == SHIZUKU_REQUEST_CODE) {
                viewModel.refresh(this)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        refreshViewModels()
        setContent {
            ADBTestTheme {
                Surface {
                    MainScreen(
                        viewModel = viewModel,
                        termuxCommandViewModel = termuxCommandViewModel,
                        systemApiViewModel = systemApiViewModel,
                        onOpenOverlayPermission = ::openOverlayPermission,
                        onOpenAccessibilitySettings = ::openAccessibilitySettings,
                        onRequestShizuku = ::requestShizukuPermission,
                        onRequestNotificationPermission = ::requestNotificationPermission,
                        onRequestCalendarPermission = ::requestCalendarPermission,
                        onRequestLocationPermission = ::requestLocationPermission,
                        onStart = ::startAdbService,
                        onStop = ::stopAdbService,
                        onStartSystem = ::startSystemService,
                        onStopSystem = ::stopSystemService,
                        onExecuteTermuxCommand = ::executeTermuxCommand
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshViewModels()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        super.onDestroy()
    }

    private fun refreshViewModels() {
        viewModel.refresh(this)
        termuxCommandViewModel.refresh(this)
        systemApiViewModel.refresh(this)
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

    private fun requestCalendarPermission() {
        calendarPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            )
        )
    }

    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun requestShizukuPermission() {
        val preV11 = runCatching { Shizuku.isPreV11() }.getOrElse {
            viewModel.error = "Shizuku 服务未就绪。"
            viewModel.refresh(this)
            return
        }
        if (preV11) {
            viewModel.error = "Shizuku 版本过低。"
            return
        }
        runCatching {
            if (!isShizukuPermissionGranted()) {
                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
            }
        }.onFailure {
            viewModel.error = "Shizuku 服务未就绪。"
            viewModel.refresh(this)
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

    private fun startSystemService(config: SystemConfig) {
        val intent = SystemService.createStartIntent(this, config)
        ContextCompat.startForegroundService(this, intent)
        systemApiViewModel.serviceRunning = true
    }

    private fun stopSystemService() {
        startService(SystemService.createStopIntent(this))
        systemApiViewModel.serviceRunning = false
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
