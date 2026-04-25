package io.njdldkl.android.adbtest

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.njdldkl.android.adbtest.termux.TermuxCommandEntry
import io.njdldkl.android.adbtest.termux.TermuxCommandRunner

@Composable
internal fun TermuxCommandPage(
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
