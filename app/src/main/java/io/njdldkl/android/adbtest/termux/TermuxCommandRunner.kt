package io.njdldkl.android.adbtest.termux

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.concurrent.atomic.AtomicInteger

object TermuxCommandRunner {
    private val nextExecutionId = AtomicInteger(1000)

    fun execute(context: Context, command: String): Int {
        require(command.isNotBlank()) { "命令不能为空。" }
        check(
            context.checkSelfPermission(TermuxConstants.PERMISSION_RUN_COMMAND) ==
                PackageManager.PERMISSION_GRANTED
        ) {
            "未授予 Termux RUN_COMMAND 权限。"
        }

        val executionId = nextExecutionId.incrementAndGet()
        val resultIntent = Intent(context, TermuxCommandResultService::class.java).apply {
            putExtra(TermuxCommandResultService.EXTRA_EXECUTION_ID, executionId)
            putExtra(TermuxCommandResultService.EXTRA_COMMAND, command)
        }
        val pendingIntentFlags = PendingIntent.FLAG_ONE_SHOT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
        val pendingIntent = PendingIntent.getService(
            context,
            executionId,
            resultIntent,
            pendingIntentFlags
        )

        val intent = Intent().apply {
            setClassName(TermuxConstants.PACKAGE_NAME, TermuxConstants.RUN_COMMAND_SERVICE_NAME)
            action = TermuxConstants.ACTION_RUN_COMMAND
            putExtra(TermuxConstants.EXTRA_COMMAND_PATH, "$PREFIX/bin/bash")
            putExtra(TermuxConstants.EXTRA_ARGUMENTS, arrayOf("-lc", command))
            putExtra(TermuxConstants.EXTRA_WORKDIR, HOME)
            putExtra(TermuxConstants.EXTRA_BACKGROUND, true)
            putExtra(TermuxConstants.EXTRA_COMMAND_LABEL, "ADBTest Termux command")
            putExtra(TermuxConstants.EXTRA_PENDING_INTENT, pendingIntent)
        }
        context.startService(intent)
        return executionId
    }

    const val HOME = "/data/data/com.termux/files/home"
    private const val PREFIX = "/data/data/com.termux/files/usr"
}

object TermuxConstants {
    const val PACKAGE_NAME = "com.termux"
    const val PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"
    const val RUN_COMMAND_SERVICE_NAME = "com.termux.app.RunCommandService"
    const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    const val EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
    const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"

    const val EXTRA_PLUGIN_RESULT_BUNDLE = "result"
    const val EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT = "stdout"
    const val EXTRA_PLUGIN_RESULT_BUNDLE_STDERR = "stderr"
    const val EXTRA_PLUGIN_RESULT_BUNDLE_EXIT_CODE = "exitCode"
    const val EXTRA_PLUGIN_RESULT_BUNDLE_ERR = "err"
    const val EXTRA_PLUGIN_RESULT_BUNDLE_ERRMSG = "errmsg"
}
