package io.njdldkl.android.adbtest

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import io.njdldkl.android.adbtest.termux.*

class TermuxCommandViewModel : ViewModel() {
    var command by mutableStateOf("pwd && ls -la")
    var error by mutableStateOf<String?>(null)
    var termuxRunCommandGranted by mutableStateOf(false)
    val entries = mutableStateListOf<TermuxCommandEntry>()

    private val resultListener = TermuxCommandResultBus.addListener(::receiveResult)

    fun refresh(context: Context) {
        termuxRunCommandGranted =
            context.checkSelfPermission(TermuxConstants.PERMISSION_RUN_COMMAND) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun execute(context: Context) {
        val submittedCommand = command.trim()
        if (submittedCommand.isBlank()) {
            error = "命令不能为空。"
            return
        }

        runCatching {
            TermuxCommandRunner.execute(context, submittedCommand)
        }.onSuccess { executionId ->
            error = null
            entries.add(0, TermuxCommandEntry(executionId = executionId, command = submittedCommand))
        }.onFailure { throwable ->
            error = throwable.message ?: "启动 Termux 命令失败。"
        }
    }

    fun openAppSettings(context: Context) {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${context.packageName}".toUri()
            )
        )
    }

    fun clearResults() {
        entries.clear()
        error = null
    }

    private fun receiveResult(result: TermuxCommandResult) {
        val index = entries.indexOfFirst { it.executionId == result.executionId }
        if (index >= 0) {
            entries[index] = entries[index].copy(result = result)
        } else {
            entries.add(
                0,
                TermuxCommandEntry(
                    executionId = result.executionId,
                    command = result.command,
                    result = result
                )
            )
        }
    }

    override fun onCleared() {
        resultListener.close()
        super.onCleared()
    }
}
