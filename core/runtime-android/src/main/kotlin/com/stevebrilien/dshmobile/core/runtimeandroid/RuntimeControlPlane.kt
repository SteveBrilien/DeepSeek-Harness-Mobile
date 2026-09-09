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
    private val manager = AndroidRuntimeManager(context.applicationContext)

    fun health(): RuntimeHealth = runBlocking { manager.health() }

    fun installDefault(progress: (String) -> Unit = {}): Result<RuntimeSlot> = runBlocking {
        val target = manager.inactiveSlot()
        manager.stageDefault(target, progress).getOrThrow()
        manager.verify(target).getOrThrow()
        manager.activate(target).getOrThrow()
        Result.success(target)
    }

    fun start(): Result<Unit> = runBlocking { manager.start() }

    fun stop(): Result<Unit> = runBlocking { manager.stop() }

    fun executeShell(
        command: String,
        workingDirectory: String = "/workspace",
        timeoutMillis: Long = 60_000L,
    ): Result<RuntimeShellResult> = runBlocking {
        manager.executeShell(command, workingDirectory, timeoutMillis)
    }

    fun rollback(): Result<Unit> = runBlocking {
        manager.rollback().getOrThrow()
        manager.start().getOrThrow()
        Result.success(Unit)
    }

    fun logTail(maxChars: Int = 32_000): String {
        val file = manager.logFile()
        if (!file.isFile) return "No DSH runtime log yet."
        val bytes = file.readBytes()
        val start = (bytes.size - maxChars.coerceAtLeast(1)).coerceAtLeast(0)
        return String(bytes, start, bytes.size - start, StandardCharsets.UTF_8)
    }
}
