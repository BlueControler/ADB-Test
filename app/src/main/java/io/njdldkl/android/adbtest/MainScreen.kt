package io.njdldkl.android.adbtest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.njdldkl.android.adbtest.system.SystemConfig

@Composable
internal fun MainScreen(
    viewModel: MainViewModel,
    termuxCommandViewModel: TermuxCommandViewModel,
    systemApiViewModel: SystemApiViewModel,
    onOpenOverlayPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestShizuku: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestCalendarPermission: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onStart: (AdbConfig) -> Unit,
    onStop: () -> Unit,
    onStartSystem: (SystemConfig) -> Unit,
    onStopSystem: () -> Unit,
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
                Button(
                    onClick = { viewModel.currentPage = MainPage.System },
                    modifier = Modifier.weight(1f),
                    enabled = viewModel.currentPage != MainPage.System
                ) {
                    Text("System")
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

                MainPage.System -> SystemApiPage(
                    viewModel = systemApiViewModel,
                    context = context,
                    onRequestCalendarPermission = onRequestCalendarPermission,
                    onRequestLocationPermission = onRequestLocationPermission,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onStartSystem = onStartSystem,
                    onStopSystem = onStopSystem
                )
            }
        }
    }
}
