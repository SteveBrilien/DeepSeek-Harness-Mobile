package com.stevebrilien.dshmobile.runtime

import android.content.Context
import android.util.AtomicFile
import com.stevebrilien.dshmobile.core.runtimeandroid.RuntimeInstallProgress
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

data class RuntimeInstallSnapshot(
    val running: Boolean = false,
    val failed: Boolean = false,
    val runtimeInstalled: Boolean = false,
    val startedAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val phase: String = "idle",
    val message: String = "等待安装",
    val percent: Int? = null,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
    val sourceId: String? = null,
    val sourceName: String? = null,
    val logs: List<String> = emptyList(),
) {
    val elapsedMillis: Long
        get() = if (startedAtMillis <= 0L) 0L else ((if (running) System.currentTimeMillis() else updatedAtMillis) - startedAtMillis).coerceAtLeast(0L)

    val etaMillis: Long?
        get() {
            val p = percent ?: return null
            if (!running || p !in 3..99 || elapsedMillis <= 0L) return null
            return (elapsedMillis.toDouble() * (100 - p) / p).toLong().coerceAtLeast(0L)
        }
}

class RuntimeInstallTelemetry(context: Context) {
    private val root = File(context.filesDir, "runtime")
    private val stateFile = File(root, "install-status.json")
    private val logFile = File(root, "install.log")
    private var writerState: JSONObject? = null
    private var lastStateWriteAt = 0L

    @Synchronized
    fun begin(preferredSourceId: String) {
        root.mkdirs()
        logFile.writeText("", StandardCharsets.UTF_8)
        val now = System.currentTimeMillis()
        val state = JSONObject()
            .put("running", true)
            .put("failed", false)
            .put("runtimeInstalled", false)
            .put("startedAtMillis", now)
            .put("updatedAtMillis", now)
            .put("phase", "starting")
            .put("message", "准备安装本地 Runtime")
            .put("percent", 0)
            .put("sourceId", preferredSourceId)
            .put("sourceName", if (preferredSourceId == "auto") "自动选择" else "手动选择")
        writerState = state
        lastStateWriteAt = now
        writeState(state)
        appendLog("开始安装 · 下载源：${if (preferredSourceId == "auto") "自动选择" else preferredSourceId}")
    }

    @Synchronized
    fun update(progress: RuntimeInstallProgress) {
        val current = writerState ?: readStateJson().also { writerState = it }
        val previousPhase = current.optString("phase")
        val previousMessage = current.optString("message")
        val previousPercent = if (current.has("percent")) current.optInt("percent") else null
        val now = System.currentTimeMillis()
        progress.logLine?.takeIf { it.isNotBlank() }?.let(::appendLog)
        if (progress.phase != previousPhase || progress.message != previousMessage) appendLog(progress.message)
        current
            .put("running", true)
            .put("failed", false)
            .put("updatedAtMillis", now)
            .put("phase", progress.phase)
            .put("message", progress.message)
        progress.percent?.let { current.put("percent", it.coerceIn(0, 100)) }
        progress.downloadedBytes?.let { current.put("downloadedBytes", it) }
        progress.totalBytes?.let { current.put("totalBytes", it) }
        progress.sourceId?.let { current.put("sourceId", it) }
        progress.sourceName?.let { current.put("sourceName", it) }
        val important = progress.phase != previousPhase ||
            progress.message != previousMessage ||
            (progress.percent != null && progress.percent != previousPercent) ||
            now - lastStateWriteAt >= 450L
        if (important) {
            writeState(current)
            lastStateWriteAt = now
        }
    }

    @Synchronized
    fun succeed(message: String = "Runtime 安装完成") {
        val current = writerState ?: readStateJson().also { writerState = it }
        appendLog(message)
        current
            .put("running", false)
            .put("failed", false)
            .put("runtimeInstalled", true)
            .put("updatedAtMillis", System.currentTimeMillis())
            .put("phase", "complete")
            .put("message", message)
            .put("percent", 100)
        writeState(current)
        lastStateWriteAt = System.currentTimeMillis()
    }

    @Synchronized
    fun markRuntimeInstalled(message: String = "Runtime 已安装，正在启动 DSH") {
        val current = writerState ?: readStateJson().also { writerState = it }
        appendLog(message)
        current
            .put("running", true)
            .put("failed", false)
            .put("runtimeInstalled", true)
            .put("updatedAtMillis", System.currentTimeMillis())
            .put("phase", "start")
            .put("message", message)
            .put("percent", 99)
        writeState(current)
        lastStateWriteAt = System.currentTimeMillis()
    }

    @Synchronized
    fun beginRuntimeStart(message: String = "正在启动 DSH") {
        val current = writerState ?: readStateJson().also { writerState = it }
        val now = System.currentTimeMillis()
        if (current.optLong("startedAtMillis", 0L) <= 0L) current.put("startedAtMillis", now)
        appendLog(message)
        current
            .put("running", true)
            .put("failed", false)
            .put("runtimeInstalled", true)
            .put("updatedAtMillis", now)
            .put("phase", "start")
            .put("message", message)
            .put("percent", 99)
        writeState(current)
        lastStateWriteAt = now
    }

    @Synchronized
    fun fail(error: Throwable, runtimeInstalled: Boolean? = null) {
        val detail = (error.message ?: error::class.java.simpleName).lineSequence().firstOrNull()?.take(240) ?: "未知错误"
        val current = writerState ?: readStateJson().also { writerState = it }
        val installed = runtimeInstalled ?: current.optBoolean("runtimeInstalled", false)
        appendLog(if (installed) "DSH 启动未就绪 · $detail" else "安装失败 · $detail")
        current
            .put("running", false)
            .put("failed", true)
            .put("runtimeInstalled", installed)
            .put("updatedAtMillis", System.currentTimeMillis())
            .put("phase", "failed")
            .put("message", detail)
        writeState(current)
        lastStateWriteAt = System.currentTimeMillis()
    }

    @Synchronized
    fun appendDiagnostic(title: String, text: String, maxLines: Int = 100) {
        appendLog(title)
        text.lineSequence()
            .filter { it.isNotBlank() }
            .toList()
            .takeLast(maxLines.coerceIn(1, 200))
            .forEach(::appendLog)
    }

    @Synchronized
    fun snapshot(maxLogLines: Int = 250): RuntimeInstallSnapshot {
        val json = readStateJson()
        val logs = if (logFile.isFile) {
            runCatching { logFile.readLines(StandardCharsets.UTF_8).takeLast(maxLogLines) }.getOrDefault(emptyList())
        } else emptyList()
        return RuntimeInstallSnapshot(
            running = json.optBoolean("running", false),
            failed = json.optBoolean("failed", false),
            runtimeInstalled = json.optBoolean("runtimeInstalled", false),
            startedAtMillis = json.optLong("startedAtMillis", 0L),
            updatedAtMillis = json.optLong("updatedAtMillis", 0L),
            phase = json.optString("phase", "idle"),
            message = json.optString("message", "等待安装"),
            percent = json.takeIf { it.has("percent") }?.optInt("percent"),
            downloadedBytes = json.takeIf { it.has("downloadedBytes") }?.optLong("downloadedBytes"),
            totalBytes = json.takeIf { it.has("totalBytes") }?.optLong("totalBytes"),
            sourceId = json.optString("sourceId").takeIf { it.isNotBlank() },
            sourceName = json.optString("sourceName").takeIf { it.isNotBlank() },
            logs = logs,
        )
    }

    private fun readStateJson(): JSONObject = runCatching {
        if (stateFile.isFile) JSONObject(stateFile.readText(StandardCharsets.UTF_8)) else JSONObject()
    }.getOrElse { JSONObject() }

    private fun writeState(json: JSONObject) {
        root.mkdirs()
        val atomic = AtomicFile(stateFile)
        val stream = atomic.startWrite()
        try {
            stream.write(json.toString(2).toByteArray(StandardCharsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (t: Throwable) {
            atomic.failWrite(stream)
            throw t
        }
    }

    private fun appendLog(line: String) {
        root.mkdirs()
        val clean = line.replace('\u0000', ' ').trim().take(800)
        if (clean.isBlank()) return
        logFile.appendText("${System.currentTimeMillis()} · $clean\n", StandardCharsets.UTF_8)
        if (logFile.length() > 512L * 1024L) {
            val tail = logFile.readLines(StandardCharsets.UTF_8).takeLast(250)
            logFile.writeText(tail.joinToString("\n", postfix = "\n"), StandardCharsets.UTF_8)
        }
    }
}
