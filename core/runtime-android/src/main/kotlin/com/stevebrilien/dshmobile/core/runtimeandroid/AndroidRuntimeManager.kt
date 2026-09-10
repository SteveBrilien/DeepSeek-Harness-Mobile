package com.stevebrilien.dshmobile.core.runtimeandroid

import android.content.Context
import com.stevebrilien.dshmobile.core.model.RuntimeId
import com.stevebrilien.dshmobile.core.recovery.RecoveryVault
import com.stevebrilien.dshmobile.core.runtimeapi.ComponentHealth
import com.stevebrilien.dshmobile.core.runtimeapi.HealthState
import com.stevebrilien.dshmobile.core.runtimeapi.RuntimeComponent
import com.stevebrilien.dshmobile.core.runtimeapi.RuntimeHealth
import com.stevebrilien.dshmobile.core.runtimeapi.RuntimeInstallRequest
import com.stevebrilien.dshmobile.core.runtimeapi.RuntimeManager
import com.stevebrilien.dshmobile.core.runtimeapi.RuntimeSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class AndroidRuntimeManager(
    context: Context,
    private val vault: RecoveryVault = RecoveryVault(context.applicationContext),
) : RuntimeManager {
    companion object {
        private const val WEB_AUTH_REQUIRED_MARKER = "dsh web authentication required"
        private const val WEB_STARTUP_POLL_MILLIS = 250L
        private const val WEB_STARTUP_TIMEOUT_MILLIS = 180_000L
        private val WEB_LAUNCH_URL = Regex(
            """dsh web:\s+(http://(?:127\.0\.0\.1|localhost):${RuntimePins.DSH_HTTP_PORT}/\?token=[^\s()]+)""",
        )

        val DEFAULT_COMPONENT_VERSIONS: Map<RuntimeComponent, String> = mapOf(
            RuntimeComponent.LINUX_USERSPACE to "alpine-${RuntimePins.ALPINE_VERSION}",
            RuntimeComponent.NODE to "24",
            RuntimeComponent.DSH to RuntimePins.DSH_VERSION,
            RuntimeComponent.PLUGINS to "managed-by-dsh",
        )
    }

    private val appContext = context.applicationContext
    private val stateStore = RuntimeStateStore(appContext)
    private val installer = NativeRuntimeInstaller(appContext, vault, stateStore)
    private val contextSnapshotWriter = MobileContextSnapshotWriter(stateStore)
    private val runtimeId = RuntimeId("local-phone")
    @Volatile private var lastWebProbeDetail: String = "not probed"

    override suspend fun health(): RuntimeHealth = withContext(Dispatchers.IO) {
        stateStore.ensureLayout()
        val state = stateStore.read()
        val slot = state.activeSlot
        val rootfs = slot?.let(stateStore.layout::rootfs)
        val linuxHealthy = rootfs?.let { File(it, "bin/sh").isFile } == true
        val nodeHealthy = rootfs?.let { File(it, "usr/bin/node").isFile } == true
        val dshInstalled = rootfs?.let { File(it, "opt/dsh/node_modules/@deepseek-ai/dsh/lib/bin.js").isFile } == true
        val webReady = probeWebReady()

        RuntimeHealth(
            runtimeId = runtimeId,
            activeSlot = slot,
            components = listOf(
                ComponentHealth(
                    RuntimeComponent.LINUX_USERSPACE,
                    if (linuxHealthy) HealthState.HEALTHY else if (slot == null) HealthState.UNKNOWN else HealthState.FAILED,
                    if (slot == null) "No active slot" else "slot=${slot.name}",
                ),
                ComponentHealth(
                    RuntimeComponent.NODE,
                    if (nodeHealthy) HealthState.HEALTHY else if (linuxHealthy) HealthState.DEGRADED else HealthState.UNKNOWN,
                    if (nodeHealthy) "Node installed" else "Node not verified",
                ),
                ComponentHealth(
                    RuntimeComponent.DSH,
                    when {
                        webReady -> HealthState.HEALTHY
                        dshInstalled -> HealthState.DEGRADED
                        else -> HealthState.UNKNOWN
                    },
                    when {
                        webReady -> "DSH Web ready on 127.0.0.1:${RuntimePins.DSH_HTTP_PORT}"
                        dshInstalled -> "Installed but Web endpoint is offline"
                        else -> "DSH not installed"
                    },
                ),
                ComponentHealth(
                    RuntimeComponent.PLUGINS,
                    HealthState.UNKNOWN,
                    "Plugin health is owned by DSH and will be queried after startup",
                ),
            ),
        )
    }

    fun inspectResources(): RuntimeResourceInventory = installer.inspectResources()

    fun installSources(): List<RuntimeInstallSource> =
        listOf(RuntimeInstallSource("auto", "自动选择", "并行测速后选择当前最快可用源")) +
            RuntimePins.ALPINE_MIRRORS.map { RuntimeInstallSource(it.id, it.name, it.baseUrl) }

    override suspend fun start(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (probeWebReady()) return@runCatching
            val active = stateStore.read().activeSlot ?: error("No active runtime slot. Install a runtime first.")
            val logFile = File(stateStore.layout.logsDir, "dsh-web.log")
            logFile.parentFile?.let { check(it.exists() || it.mkdirs()) }
            logFile.appendText("\n=== DSH start ${System.currentTimeMillis()} slot=${active.name} ===\n", StandardCharsets.UTF_8)

            fun <T> startupStep(name: String, block: () -> T): T {
                logFile.appendText("[startup] $name\n", StandardCharsets.UTF_8)
                return try {
                    block().also { logFile.appendText("[startup] $name: ok\n", StandardCharsets.UTF_8) }
                } catch (t: Throwable) {
                    val detail = t.message?.lineSequence()?.firstOrNull().orEmpty().take(320)
                    logFile.appendText("[startup] $name: failed${if (detail.isNotBlank()) ": $detail" else ""}\n", StandardCharsets.UTF_8)
                    throw IllegalStateException("DSH startup preflight '$name' failed: ${t.message ?: t::class.java.simpleName}", t)
                }
            }

            startupStep("verify-start-prerequisites") { installer.verifyStartPrerequisites(active) }
            startupStep("write-mobile-context") { contextSnapshotWriter.writeStableBootSnapshot() }
            startupStep("ensure-mobile-plugins") { installer.ensureMobileContextIntegration(active) }

            // Launch the pinned DSH entrypoint directly with the verified Node binary. This
            // removes an extra shell-wrapper lookup from the long-lived Web process while
            // keeping /usr/local/bin/dsh for interactive/runtime commands.
            val command =
                "exec /usr/bin/node --expose-internals /opt/dsh/node_modules/@deepseek-ai/dsh/lib/bin.js web " +
                    "--host 127.0.0.1 --port ${RuntimePins.DSH_HTTP_PORT} --no-open"

            // A previous start attempt can remain alive without ever binding the Web port.
            // Restart that owned process instead of waiting another full startup window on it.
            if (RuntimeProcessRegistry.isAlive()) RuntimeProcessRegistry.stop()
            logFile.appendText("[startup] spawn-dsh-web\n", StandardCharsets.UTF_8)
            RuntimeProcessRegistry.start(installer.buildProcess(active, command), logFile)
            val deadline = System.currentTimeMillis() + WEB_STARTUP_TIMEOUT_MILLIS
            while (System.currentTimeMillis() < deadline) {
                if (probeWebReady()) {
                    logFile.appendText("[startup] web-ready: $lastWebProbeDetail\n", StandardCharsets.UTF_8)
                    return@runCatching
                }
                if (!RuntimeProcessRegistry.isAlive()) {
                    val exit = RuntimeProcessRegistry.exitCodeOrNull()?.toString() ?: "unknown"
                    error("DSH process exited (code=$exit) before the local Web endpoint became reachable. See ${logFile.absolutePath}")
                }
                Thread.sleep(WEB_STARTUP_POLL_MILLIS)
            }
            val runtimeProbe = probeWebInsideRuntime(active)
            error(
                "DSH process is still running, but the local Web endpoint did not become reachable within " +
                    "${WEB_STARTUP_TIMEOUT_MILLIS / 1_000}s. Last Android probe: $lastWebProbeDetail. " +
                    "Runtime-side probe: $runtimeProbe",
            )
        }
    }

    override suspend fun stop(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { RuntimeProcessRegistry.stop() }
    }

    override suspend fun stage(request: RuntimeInstallRequest): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val active = stateStore.read().activeSlot
            require(request.targetSlot != active) { "Refusing to stage over the active runtime slot" }
            installer.stage(request.targetSlot)
        }
    }

    suspend fun stageDefault(
        targetSlot: RuntimeSlot = inactiveSlot(),
        preferredSourceId: String = "auto",
        progress: (RuntimeInstallProgress) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val active = stateStore.read().activeSlot
            require(targetSlot != active) { "Refusing to stage over the active runtime slot" }
            installer.stage(targetSlot, preferredSourceId, progress)
        }
    }

    override suspend fun verify(slot: RuntimeSlot): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { installer.verify(slot) }.map { Unit }
    }

    override suspend fun activate(slot: RuntimeSlot): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            installer.verify(slot)
            RuntimeProcessRegistry.stop()
            stateStore.activate(slot)
        }
    }

    override suspend fun rollback(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val target = stateStore.rollbackTarget() ?: error("No previous runtime slot is available")
            installer.verify(target)
            RuntimeProcessRegistry.stop()
            stateStore.activate(target)
        }
    }

    fun inactiveSlot(): RuntimeSlot {
        val active = stateStore.read().activeSlot
        return if (active == RuntimeSlot.A) RuntimeSlot.B else RuntimeSlot.A
    }

    fun logFile(): File = File(stateStore.layout.logsDir, "dsh-web.log")

    fun isWebReady(): Boolean = probeWebReady()

    fun webLaunchUrl(): String? {
        val file = logFile()
        if (!file.isFile) return null
        val bytes = file.readBytes()
        val start = (bytes.size - 128 * 1024).coerceAtLeast(0)
        val tail = String(bytes, start, bytes.size - start, StandardCharsets.UTF_8)
        return WEB_LAUNCH_URL.findAll(tail).lastOrNull()?.groupValues?.getOrNull(1)
    }

    suspend fun executeShell(
        command: String,
        workingDirectory: String = "/workspace",
        timeoutMillis: Long = 60_000L,
        outputLimitBytes: Int = 256 * 1024,
    ): Result<RuntimeShellResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(command.isNotBlank()) { "Command cannot be empty." }
            require(workingDirectory.startsWith('/')) { "Runtime working directory must be absolute." }
            val active = stateStore.read().activeSlot ?: error("No active runtime slot. Install a runtime first.")
            installer.verify(active)

            val shellCommand = "cd ${shellQuote(workingDirectory)} && $command"
            val started = System.currentTimeMillis()
            val process = installer.buildProcess(active, shellCommand)
                .redirectErrorStream(false)
                .start()
            val stdout = BoundedCollector(process.inputStream, outputLimitBytes)
            val stderr = BoundedCollector(process.errorStream, outputLimitBytes)
            val stdoutThread = Thread(stdout, "dshm-runtime-shell-stdout").apply { start() }
            val stderrThread = Thread(stderr, "dshm-runtime-shell-stderr").apply { start() }
            val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroy()
                if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly()
            }
            stdoutThread.join(2_000)
            stderrThread.join(2_000)

            RuntimeShellResult(
                command = command,
                workingDirectory = workingDirectory,
                exitCode = if (finished) process.exitValue() else -1,
                stdout = stdout.text(),
                stderr = stderr.text(),
                elapsedMillis = System.currentTimeMillis() - started,
                timedOut = !finished,
            )
        }
    }

    private fun probeWebReady(): Boolean {
        // Prefer a literal IPv4 loopback probe. On Android devices with an active VPN,
        // URLConnection/DNS handling for `localhost` can diverge from the actual socket
        // DSH bound on 127.0.0.1. A raw HTTP probe avoids that false-negative path.
        probeRawLoopbackHttp()?.let { statusLine ->
            lastWebProbeDetail = "$statusLine via raw 127.0.0.1"
            return true
        }

        // Keep URLConnection fallbacks for OEM stacks where raw sockets are restricted.
        for (host in listOf("127.0.0.1", "localhost")) {
            val result = runCatching {
                val connection = (URL("http://$host:${RuntimePins.DSH_HTTP_PORT}/").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 1_500
                    readTimeout = 1_500
                    requestMethod = "GET"
                    useCaches = false
                    instanceFollowRedirects = false
                    setRequestProperty("Connection", "close")
                }
                try {
                    val code = connection.responseCode
                    val stream = if (code >= 400) connection.errorStream else connection.inputStream
                    val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { reader ->
                        val chars = CharArray(512)
                        val read = reader.read(chars)
                        if (read > 0) String(chars, 0, read) else ""
                    }.orEmpty()
                    val ready = code in 200..399 ||
                        code == HttpURLConnection.HTTP_UNAUTHORIZED ||
                        (body.contains(WEB_AUTH_REQUIRED_MARKER) && code >= 400)
                    ready to "HTTP $code via $host"
                } finally {
                    connection.disconnect()
                }
            }
            result.onSuccess { (ready, detail) ->
                lastWebProbeDetail = detail
                if (ready) return true
            }.onFailure { failure ->
                lastWebProbeDetail = "$host ${failure::class.java.simpleName}: ${failure.message ?: "no detail"}"
            }
        }
        return false
    }

    private fun probeWebInsideRuntime(slot: RuntimeSlot): String = runCatching {
        val command =
            "code=$(curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 2 --max-time 4 " +
                "http://127.0.0.1:${RuntimePins.DSH_HTTP_PORT}/ 2>/tmp/dshm-probe.err || true); " +
                "printf 'curl=%s' \"\$code\"; " +
                "if [ -s /tmp/dshm-probe.err ]; then printf ' stderr='; head -c 240 /tmp/dshm-probe.err; fi"
        val process = installer.buildProcess(slot, command).redirectErrorStream(true).start()
        val finished = process.waitFor(6, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return@runCatching "probe timed out"
        }
        process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }.trim().take(360)
            .ifBlank { "no output" }
    }.getOrElse { failure ->
        "${failure::class.java.simpleName}: ${failure.message ?: "no detail"}"
    }

    private fun probeRawLoopbackHttp(): String? = runCatching {
        Socket().use { socket ->
            socket.tcpNoDelay = true
            socket.soTimeout = 1_500
            socket.connect(InetSocketAddress("127.0.0.1", RuntimePins.DSH_HTTP_PORT), 1_500)
            val request = buildString {
                append("GET / HTTP/1.1\r\n")
                append("Host: 127.0.0.1:${RuntimePins.DSH_HTTP_PORT}\r\n")
                append("Connection: close\r\n\r\n")
            }
            socket.getOutputStream().apply {
                write(request.toByteArray(StandardCharsets.US_ASCII))
                flush()
            }
            val firstLine = socket.getInputStream().bufferedReader(StandardCharsets.US_ASCII).readLine().orEmpty()
            check(firstLine.startsWith("HTTP/")) { "No HTTP status line" }
            firstLine.take(96)
        }
    }.getOrNull()

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private class BoundedCollector(
        private val input: java.io.InputStream,
        private val maxBytes: Int,
    ) : Runnable {
        private val bytes = ByteArrayOutputStream()
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

private object RuntimeProcessRegistry {
    private val lock = Any()
    private var process: Process? = null

    fun start(builder: ProcessBuilder, logFile: File) = synchronized(lock) {
        if (process?.isAlive == true) return
        logFile.parentFile?.let { check(it.exists() || it.mkdirs()) }
        rotateIfNeeded(logFile)
        builder.redirectErrorStream(true)
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
        process = builder.start()
    }

    fun isAlive(): Boolean = synchronized(lock) { process?.isAlive == true }

    fun exitCodeOrNull(): Int? = synchronized(lock) {
        val current = process ?: return@synchronized null
        if (current.isAlive) null else runCatching { current.exitValue() }.getOrNull()
    }

    fun stop() = synchronized(lock) {
        val current = process ?: return
        if (current.isAlive) {
            current.destroy()
            if (!current.waitFor(5, TimeUnit.SECONDS)) {
                current.destroyForcibly()
                current.waitFor(2, TimeUnit.SECONDS)
            }
        }
        process = null
    }

    private fun rotateIfNeeded(logFile: File) {
        if (!logFile.exists() || logFile.length() < 4L * 1024L * 1024L) return
        val previous = File(logFile.parentFile, "${logFile.name}.1")
        if (previous.exists()) previous.delete()
        logFile.renameTo(previous)
    }
}
