package com.stevebrilien.dshmobile.ui

import android.content.Context
import android.webkit.WebView
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

internal class DshWebViewDiagnostics(context: Context) {
    companion object {
        private const val MAX_LOG_BYTES = 1024L * 1024L
        private val TOKEN = Regex("""([?&]token=)[^&\\s\"']+""")
        private val TOKEN_ASSIGNMENT = Regex("""token=[A-Za-z0-9._~-]+""")
        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS XXX")

        internal fun redact(value: String): String =
            TOKEN_ASSIGNMENT.replace(TOKEN.replace(value, "\$1[redacted]"), "token=[redacted]")
    }

    private val logFile = File(context.filesDir, "runtime/logs/dsh-webview.log")
    private val lock = Any()

    fun append(message: String) = synchronized(lock) {
        rotateIfNeeded()
        logFile.parentFile?.let { check(it.exists() || it.mkdirs()) }
        val timestamp = OffsetDateTime.now().format(formatter)
        logFile.appendText("[$timestamp] ${redact(message)}\n", StandardCharsets.UTF_8)
    }

    fun appendProviderInfo(webView: WebView) {
        val pkg = runCatching { WebView.getCurrentWebViewPackage() }.getOrNull()
        append(
            "provider package=${pkg?.packageName ?: "unknown"} version=${pkg?.versionName ?: "unknown"} " +
                "ua=${webView.settings.userAgentString}",
        )
    }

    fun tail(maxChars: Int = 16_000): String {
        if (!logFile.isFile) return "No WebView diagnostic log yet."
        val bytes = logFile.readBytes()
        val start = (bytes.size - maxChars.coerceAtLeast(1)).coerceAtLeast(0)
        return redact(String(bytes, start, bytes.size - start, StandardCharsets.UTF_8))
    }

    private fun rotateIfNeeded() {
        if (!logFile.isFile || logFile.length() < MAX_LOG_BYTES) return
        val previous = File(logFile.parentFile, "${logFile.name}.1")
        if (previous.exists()) previous.delete()
        logFile.renameTo(previous)
    }
}
