package io.njdldkl.android.adbtest.termux

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import java.util.concurrent.CopyOnWriteArraySet

class TermuxCommandResultService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            TermuxCommandResultBus.publish(intent.toTermuxCommandResult())
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun Intent.toTermuxCommandResult(): TermuxCommandResult {
        val resultBundle = getBundleExtra(TermuxConstants.EXTRA_PLUGIN_RESULT_BUNDLE)
        val errorCode = if (resultBundle?.containsKey(TermuxConstants.EXTRA_PLUGIN_RESULT_BUNDLE_ERR) == true) {
            resultBundle.getInt(TermuxConstants.EXTRA_PLUGIN_RESULT_BUNDLE_ERR)
        } else {
            null
        }
        val errorMessage = resultBundle?.getString(TermuxConstants.EXTRA_PLUGIN_RESULT_BUNDLE_ERRMSG)
        return TermuxCommandResult(
            executionId = getIntExtra(EXTRA_EXECUTION_ID, -1),
            command = getStringExtra(EXTRA_COMMAND).orEmpty(),
            exitCode = resultBundle?.getInt(TermuxConstants.EXTRA_PLUGIN_RESULT_BUNDLE_EXIT_CODE),
            stdout = resultBundle?.getString(TermuxConstants.EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT).orEmpty(),
            stderr = resultBundle?.getString(TermuxConstants.EXTRA_PLUGIN_RESULT_BUNDLE_STDERR).orEmpty(),
            error = if (errorCode != null && errorCode != android.app.Activity.RESULT_OK) {
                listOf(errorCode.toString(), errorMessage.orEmpty()).joinToString(": ").trimEnd(':', ' ')
            } else {
                null
            },
            finishedAtMillis = System.currentTimeMillis()
        )
    }

    companion object {
        const val EXTRA_EXECUTION_ID = "io.njdldkl.android.adbtest.termux.EXTRA_EXECUTION_ID"
        const val EXTRA_COMMAND = "io.njdldkl.android.adbtest.termux.EXTRA_COMMAND"
    }
}

object TermuxCommandResultBus {
    private val listeners = CopyOnWriteArraySet<(TermuxCommandResult) -> Unit>()

    fun addListener(listener: (TermuxCommandResult) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }

    fun publish(result: TermuxCommandResult) {
        listeners.forEach { it(result) }
    }
}

data class TermuxCommandResult(
    val executionId: Int,
    val command: String,
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
    val error: String?,
    val finishedAtMillis: Long
)

data class TermuxCommandEntry(
    val executionId: Int,
    val command: String,
    val startedAtMillis: Long = SystemClock.elapsedRealtime(),
    val result: TermuxCommandResult? = null
)
