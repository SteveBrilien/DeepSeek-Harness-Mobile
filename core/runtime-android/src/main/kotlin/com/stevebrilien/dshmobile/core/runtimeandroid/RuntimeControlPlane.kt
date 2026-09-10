package com.stevebrilien.dshmobile.core.runtimeandroid

import android.content.Context
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

    private val manager = AndroidRuntimeManager(context.applicationContext)

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
                inventory.installedDshVersion == inventory.targetDshVersion
            ) {
                progress(RuntimeInstallProgress("reuse-runtime", "发现完整本地 Runtime，跳过重新安装", 98))
                manager.verify(active).getOrThrow()
                return@runCatching active
            }

            val target = manager.inactiveSlot()
            manager.stageDefault(target, preferredSourceId, progress).getOrThrow()
            manager.verify(target).getOrThrow()
            manager.activate(target).getOrThrow()
            target
        }
    }

    fun start(): Result<Unit> = runBlocking { manager.start() }

    fun isWebReady(): Boolean = manager.isWebReady()

    fun webLaunchUrl(): String? = manager.webLaunchUrl()

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
