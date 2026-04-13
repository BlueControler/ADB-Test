package io.njdldkl.android.adbtest.agent

class DeviceSnapshotCollector(
    private val executor: ShizukuAdbExecutor
) {

    suspend fun collect(): DeviceSnapshot {
        val screenshot = runCatching {
            executor.execute("screencap -p | base64").stdout.replace("\n", "").ifBlank { null }
        }.getOrNull()

        val ui = AgentAccessibilityService.dumpUiTree()
        val topActivity = resolveTopActivity()

        return DeviceSnapshot(
            screenshot = screenshot,
            ui = ui,
            currentPackage = AgentAccessibilityService.currentPackageName() ?: topActivity.first,
            activity = AgentAccessibilityService.currentActivityName() ?: topActivity.second
        )
    }

    private suspend fun resolveTopActivity(): Pair<String?, String?> {
        val output = runCatching {
            executor.execute("dumpsys window | grep mCurrentFocus").stdout
        }.getOrNull().orEmpty()
        if (output.isBlank()) return null to null
        val token = output.substringAfterLast(" ").substringBefore("}")
        val activity = token.substringAfter("/", "")
        val pkg = token.substringBefore("/", "")
        return pkg.ifBlank { null } to activity.ifBlank { null }
    }
}
