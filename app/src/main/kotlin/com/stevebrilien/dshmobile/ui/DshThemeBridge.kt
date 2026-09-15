package com.stevebrilien.dshmobile.ui

import android.webkit.WebView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

internal data class DshWebThemePalette(
    val base: String,
    val layer1: String,
    val layer2: String,
    val layer3: String,
    val textPrimary: String,
    val textSecondary: String,
    val textTertiary: String,
    val border1: String,
    val border2: String,
    val accent: String,
    val accentSoft: String,
    val accentText: String,
    val hover: String,
    val selected: String,
    val warning: String,
    val warningBg: String,
    val success: String,
    val danger: String,
    val codeBg: String,
)

internal data class DshWebThemeSnapshot(
    val schema: Int,
    val preference: String,
    val activeId: String,
    val colorScheme: String,
    val revision: Int,
    val palette: DshWebThemePalette,
) {
    val isDark: Boolean get() = colorScheme == "dark"

    fun summary(): String =
        "schema=$schema preference=$preference active=$activeId scheme=$colorScheme revision=$revision"
}

internal object DshThemeBridgeParser {
    const val SCHEMA_VERSION = 1
    const val JS_OBJECT_NAME = "dshMobileTheme"
    const val MAX_MESSAGE_CHARS = 6 * 1024

    private val colorPattern = Regex("^#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?$")

    fun parse(raw: String, sourceOrigin: String, isMainFrame: Boolean): Result<DshWebThemeSnapshot> = runCatching {
        check(isMainFrame) { "non-main-frame" }
        check(isTrustedOrigin(sourceOrigin)) { "untrusted-origin" }
        check(raw.length in 1..MAX_MESSAGE_CHARS) { "payload-size" }

        val json = JSONObject(raw)
        val schema = json.optInt("schema", -1)
        check(schema == SCHEMA_VERSION) { "schema" }
        val preference = bounded(json.optString("preference"), 64, "preference")
        val activeId = bounded(json.optString("activeId"), 64, "active-id")
        val colorScheme = json.optString("colorScheme")
        check(colorScheme == "light" || colorScheme == "dark") { "color-scheme" }
        val revision = json.optInt("revision", -1)
        check(revision in 0..1_000_000) { "revision" }
        val colors = json.optJSONObject("colors") ?: error("colors")

        DshWebThemeSnapshot(
            schema = schema,
            preference = preference,
            activeId = activeId,
            colorScheme = colorScheme,
            revision = revision,
            palette = DshWebThemePalette(
                base = color(colors, "base"),
                layer1 = color(colors, "layer1"),
                layer2 = color(colors, "layer2"),
                layer3 = color(colors, "layer3"),
                textPrimary = color(colors, "textPrimary"),
                textSecondary = color(colors, "textSecondary"),
                textTertiary = color(colors, "textTertiary"),
                border1 = color(colors, "border1"),
                border2 = color(colors, "border2"),
                accent = color(colors, "accent"),
                accentSoft = color(colors, "accentSoft"),
                accentText = color(colors, "accentText"),
                hover = color(colors, "hover"),
                selected = color(colors, "selected"),
                warning = color(colors, "warning"),
                warningBg = color(colors, "warningBg"),
                success = color(colors, "success"),
                danger = color(colors, "danger"),
                codeBg = color(colors, "codeBg"),
            ),
        )
    }

    private fun isTrustedOrigin(origin: String): Boolean = runCatching {
        val parsed = java.net.URI(origin)
        parsed.scheme == "http" &&
            parsed.host == "127.0.0.1" &&
            parsed.port == 3080 &&
            (parsed.path.isNullOrEmpty() || parsed.path == "/") &&
            parsed.query == null &&
            parsed.fragment == null
    }.getOrDefault(false)

    private fun bounded(value: String, max: Int, reason: String): String {
        check(value.isNotBlank() && value.length <= max) { reason }
        return value
    }

    private fun color(json: JSONObject, name: String): String {
        val value = json.optString(name)
        check(colorPattern.matches(value)) { "color-$name" }
        return value.uppercase()
    }
}

internal object DshThemeBridge {
    fun install(
        webView: WebView,
        diagnostics: DshWebViewDiagnostics,
        onTheme: (DshWebThemeSnapshot) -> Unit,
    ): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            diagnostics.append(
                "theme-bridge unsupported feature=${WebViewFeature.WEB_MESSAGE_LISTENER} schema=${DshThemeBridgeParser.SCHEMA_VERSION}",
            )
            return false
        }

        WebViewCompat.addWebMessageListener(
            webView,
            DshThemeBridgeParser.JS_OBJECT_NAME,
            setOf(DshPresentationHandshakeParser.TRUSTED_ORIGIN),
            WebViewCompat.WebMessageListener { _, message, sourceOrigin, isMainFrame, _ ->
                if (message.type != WebMessageCompat.TYPE_STRING) {
                    diagnostics.append("theme-handshake rejected reason=message-type")
                    return@WebMessageListener
                }
                val raw = message.data ?: run {
                    diagnostics.append("theme-handshake rejected reason=empty-data")
                    return@WebMessageListener
                }
                DshThemeBridgeParser.parse(raw, sourceOrigin.toString(), isMainFrame)
                    .onSuccess { snapshot ->
                        diagnostics.append("theme-handshake ${snapshot.summary()}")
                        onTheme(snapshot)
                    }
                    .onFailure { failure ->
                        diagnostics.append(
                            "theme-handshake rejected reason=${failure.message ?: failure::class.java.simpleName}",
                        )
                    }
            },
        )
        diagnostics.append(
            "theme-bridge installed object=${DshThemeBridgeParser.JS_OBJECT_NAME} " +
                "origin=${DshPresentationHandshakeParser.TRUSTED_ORIGIN} schema=${DshThemeBridgeParser.SCHEMA_VERSION}",
        )
        return true
    }
}
