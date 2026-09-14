package com.stevebrilien.dshmobile.core.runtimeandroid

import android.content.Context
import com.stevebrilien.dshmobile.core.recovery.RecoveryBackupManager
import com.stevebrilien.dshmobile.core.runtimeapi.RuntimeHealth
import com.stevebrilien.dshmobile.core.runtimeapi.RuntimeSlot
import kotlinx.coroutines.runBlocking
import java.nio.charset.StandardCharsets

/**
 * Synchronous entry point for Android components (foreground service / recovery UI).
 * Coroutine implementation details stay inside runtime-android rather than leaking into app.
 */
class RuntimeControlPlane(context: Context) {
    companion object {
        private val TOKEN_REDACTION = Regex("""token=[A-Za-z0-9_-]+""")
    }

    private val appContext = context.applicationContext
    private val manager = AndroidRuntimeManager(appContext)
    private val backups = RecoveryBackupManager(appContext)

    fun health(): RuntimeHealth = runBlocking { manager.health() }

    fun inspectResources(): RuntimeResourceInventory = manager.inspectResources()

    fun installSources(): List<RuntimeInstallSource> = manager.installSources()

    fun installDefault(
        preferredSourceId: String = "auto",
        progress: (RuntimeInstallProgress) -> Unit = {},
    ): Result<RuntimeSlot> = runBlocking {
        runCatching {
            val inventory = manager.inspectResources()
            val active = inventory.activeSlot
            if (
                active != null &&
                inventory.reusableInstalledRuntime &&
                !inventory.updateAvailable
            ) {
                val verified = manager.verify(active)
                if (verified.isSuccess) {
                    progress(RuntimeInstallProgress("reuse-runtime", "发现完整本地 Runtime，跳过重新安装", 98))
                    return@runCatching active
                }
                progress(
                    RuntimeInstallProgress(
                        "reuse-runtime",
                        "现有 Runtime 校验未通过，将在备用 slot 重新构建",
                        3,
                        logLine = verified.exceptionOrNull()?.message,
                    ),
                )
            }

            if (inventory.updateAvailable) {
                progress(RuntimeInstallProgress("backup-before-upgrade", "升级前创建 DSH 数据恢复快照", 5))
                val backup = backups.createCheckpoint()
                progress(
                    RuntimeInstallProgress(
                        "backup-before-upgrade",
                        "升级前恢复快照已验证",
                        7,
                        logLine = "${backup.file.name} · sha256=${backup.sha256}",
                    ),
                )
            }

            val target = manager.inactiveSlot()
            manager.stageDefault(target, preferredSourceId, progress).getOrThrow()
            manager.verify(target).getOrThrow()
            manager.activate(target).getOrThrow()
            target
        }
    }

    fun start(progress: (RuntimeStartProgress) -> Unit = {}): Result<Unit> = runBlocking {
        manager.start(progress)
    }

    fun isWebReady(): Boolean = manager.isWebReady()

    fun webLaunchUrl(): String? = manager.webLaunchUrl()

    fun webPresentation(): DshWebPresentationDescriptor? = manager.currentWebLaunchUrlForDesiredGeneration()?.let { launchUrl ->
        DshWebPresentationDescriptor(
            launchUrl = launchUrl,
            generation = manager.desiredPresentationGeneration(),
        )
    }

    fun stop(): Result<Unit> = runBlocking { manager.stop() }

    fun executeShell(
        command: String,
        workingDirectory: String = "/workspace",
        timeoutMillis: Long = 60_000L,
    ): Result<RuntimeShellResult> = runBlocking {
        manager.executeShell(command, workingDirectory, timeoutMillis)
    }

    fun rollback(): Result<Unit> = runBlocking {
        runCatching {
            manager.rollback().getOrThrow()
            manager.start().getOrThrow()
        }
    }

    fun clearLog() {
        manager.clearLog()
    }

    fun logTail(maxChars: Int = 32_000): String {
        val file = manager.logFile()
        if (!file.isFile) return "No DSH runtime log yet."
        val bytes = file.readBytes()
        val start = (bytes.size - maxChars.coerceAtLeast(1)).coerceAtLeast(0)
        return TOKEN_REDACTION.replace(
            String(bytes, start, bytes.size - start, StandardCharsets.UTF_8),
            "token=[redacted]",
        )
    }
}
