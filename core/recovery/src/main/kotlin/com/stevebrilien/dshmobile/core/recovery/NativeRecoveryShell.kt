package com.stevebrilien.dshmobile.core.recovery

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class NativeShellResult(
    val command: String,
    val workingDirectory: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val elapsedMillis: Long,
    val timedOut: Boolean,
)

class NativeRecoveryShell(
    private val context: Context,
    private val fileManager: NativeFileManager = NativeFileManager(context),
) {
    fun defaultWorkingDirectory(): File = fileManager.browserRoot().recoveryRoot

    fun execute(
        command: String,
        workingDirectory: File = defaultWorkingDirectory(),
        timeoutMillis: Long = 60_000,
        outputLimitBytes: Int = 256 * 1024,
    ): Result<NativeShellResult> = runCatching {
        check(command.isNotBlank()) { "Command cannot be empty." }
        val root = fileManager.browserRoot().root.canonicalFile
        val cwd = workingDirectory.canonicalFile
        check(cwd == root || cwd.path.startsWith(root.path + File.separator)) {
            "Recovery shell working directory escapes the browser root."
        }
        check(cwd.exists() && cwd.isDirectory) { "Working directory does not exist: ${cwd.absolutePath}" }

        val started = System.currentTimeMillis()
        val process = ProcessBuilder("/system/bin/sh", "-c", command)
            .directory(cwd)
            .redirectErrorStream(false)
            .apply {
                environment()["HOME"] = context.filesDir.absolutePath
                environment()["DSHM_RECOVERY_ROOT"] = fileManager.browserRoot().recoveryRoot.absolutePath
                environment()["DSHM_BROWSER_ROOT"] = root.absolutePath
                environment()["PATH"] = listOf(
                    "/system/bin",
                    "/system/xbin",
                    "/vendor/bin",
                    environment()["PATH"].orEmpty(),
                ).filter { it.isNotBlank() }.joinToString(":")
            }
            .start()

        val stdoutCollector = BoundedStreamCollector(process.inputStream, outputLimitBytes)
        val stderrCollector = BoundedStreamCollector(process.errorStream, outputLimitBytes)
        val stdoutThread = Thread(stdoutCollector, "dshm-recovery-stdout").apply { start() }
        val stderrThread = Thread(stderrCollector, "dshm-recovery-stderr").apply { start() }

        val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroy()
            if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly()
        }
        stdoutThread.join(2_000)
        stderrThread.join(2_000)

        NativeShellResult(
            command = command,
            workingDirectory = cwd.absolutePath,
            exitCode = if (finished) process.exitValue() else -1,
            stdout = stdoutCollector.text(),
            stderr = stderrCollector.text(),
            elapsedMillis = System.currentTimeMillis() - started,
            timedOut = !finished,
        )
    }

    private class BoundedStreamCollector(
        private val input: java.io.InputStream,
        private val maxBytes: Int,
    ) : Runnable {
        private val bytes = java.io.ByteArrayOutputStream()
        @Volatile private var truncated = false

        override fun run() {
            input.use { stream ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    val remaining = maxBytes - bytes.size()
                    if (remaining <= 0) {
                        truncated = true
                        continue
                    }
                    val toWrite = minOf(read, remaining)
                    bytes.write(buffer, 0, toWrite)
                    if (toWrite < read) truncated = true
                }
            }
        }

        fun text(): String {
            val base = bytes.toByteArray().toString(StandardCharsets.UTF_8)
            return if (truncated) "$base\n[output truncated]" else base
        }
    }
}
