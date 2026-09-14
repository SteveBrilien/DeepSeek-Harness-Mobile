package com.stevebrilien.dshmobile.ui

import android.webkit.WebView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.stevebrilien.dshmobile.core.runtimeandroid.PresentationHandshakeContract
import org.json.JSONObject

internal data class DshPresentationTelemetry(
    val schema: Int,
    val phase: String,
    val sequence: Int,
    val androidWebView: Boolean,
    val compatVersion: String,
    val bootRev: String?,
    val comboRev: String?,
    val repairMode: String?,
    val documentReadyState: String?,
    val rootGeneration: Int?,
    val innerWidth: Double?,
    val innerHeight: Double?,
    val documentClientHeight: Double?,
    val visualViewportHeight: Double?,
    val vh100: Double?,
    val dvh100: Double?,
    val rootWidth: Double?,
    val rootHeight: Double?,
) {
    fun summary(): String = buildString {
        append("schema=").append(schema)
        append(" phase=").append(phase)
        append(" seq=").append(sequence)
        append(" androidWebView=").append(androidWebView)
        append(" compat=").append(compatVersion)
        append(" bootRev=").append(bootRev ?: "none")
        append(" comboRev=").append(comboRev ?: "none")
        append(" repair=").append(repairMode ?: "none")
        append(" readyState=").append(documentReadyState ?: "unknown")
        append(" rootGeneration=").append(rootGeneration ?: -1)
        append(" viewport=").append(number(innerWidth)).append('x').append(number(innerHeight))
        append(" docClientHeight=").append(number(documentClientHeight))
        append(" visualViewportHeight=").append(number(visualViewportHeight))
        append(" vh100=").append(number(vh100))
        append(" dvh100=").append(number(dvh100))
        append(" root=").append(number(rootWidth)).append('x').append(number(rootHeight))
    }

    private fun number(value: Double?): String =
        value?.takeIf { it.isFinite() }?.let { "%.2f".format(java.util.Locale.US, it) } ?: "n/a"
}

internal object DshPresentationHandshakeParser {
    const val JS_OBJECT_NAME = "dshMobilePresentation"
    const val TRUSTED_ORIGIN = "http://127.0.0.1:3080"
    const val MAX_MESSAGE_CHARS = 8 * 1024

    private val allowedPhases = setOf(
        "compat-active",
        "viewport-probed",
        "root-contract-applied",
        "presentation-ready",
        "presentation-degraded",
    )

    fun parse(raw: String, sourceOrigin: String, isMainFrame: Boolean): Result<DshPresentationTelemetry> = runCatching {
        check(isMainFrame) { "non-main-frame" }
        check(isTrustedOrigin(sourceOrigin)) { "untrusted-origin" }
        check(raw.length in 1..MAX_MESSAGE_CHARS) { "payload-size" }

        val json = JSONObject(raw)
        val schema = json.optInt("schema", -1)
        check(schema == PresentationHandshakeContract.SCHEMA_VERSION) { "schema" }
        val phase = json.optString("phase")
        check(phase in allowedPhases) { "phase" }
        val sequence = json.optInt("seq", -1)
        check(sequence in 1..1_000_000) { "sequence" }
        val compatVersion = bounded(json.optString("compatVersion"), 64, "compat-version")
        val bootRev = boundedNullable(json.optString("bootRev"), 128, "boot-rev")
        val comboRev = boundedNullable(json.optString("comboRev"), 128, "combo-rev")
        val repairMode = boundedNullable(json.optString("repairMode"), 64, "repair-mode")
        val readyState = boundedNullable(json.optString("documentReadyState"), 32, "ready-state")
        val metrics = json.optJSONObject("metrics")

        DshPresentationTelemetry(
            schema = schema,
            phase = phase,
            sequence = sequence,
            androidWebView = json.optBoolean("androidWebView", false),
            compatVersion = compatVersion,
            bootRev = bootRev,
            comboRev = comboRev,
            repairMode = repairMode,
            documentReadyState = readyState,
            rootGeneration = metrics?.optIntOrNull("rootGeneration"),
            innerWidth = metrics?.optFiniteDouble("innerWidth"),
            innerHeight = metrics?.optFiniteDouble("innerHeight"),
            documentClientHeight = metrics?.optFiniteDouble("documentClientHeight"),
            visualViewportHeight = metrics?.optFiniteDouble("visualViewportHeight"),
            vh100 = metrics?.optFiniteDouble("vh100"),
            dvh100 = metrics?.optFiniteDouble("dvh100"),
            rootWidth = metrics?.optFiniteDouble("rootWidth"),
            rootHeight = metrics?.optFiniteDouble("rootHeight"),
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

    private fun boundedNullable(value: String, max: Int, reason: String): String? {
        if (value.isBlank()) return null
        check(value.length <= max) { reason }
        return value
    }

    private fun JSONObject.optFiniteDouble(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        val value = optDouble(name, Double.NaN)
        return value.takeIf { it.isFinite() }
    }

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (!has(name) || isNull(name)) null else optInt(name)
}

/**
 * Per-document protocol state. It deliberately validates only the one-way readiness grammar;
 * it never drives Runtime/UI behavior or exposes a native command surface.
 */
internal class DshPresentationNavigationState {
    private var lastSequence: Int = 0
    private var lastPhase: String? = null
    private var lastRootGeneration: Int? = null

    fun reset() {
        lastSequence = 0
        lastPhase = null
        lastRootGeneration = null
    }

    /** Returns null when accepted, otherwise a bounded diagnostic rejection reason. */
    fun accept(telemetry: DshPresentationTelemetry): String? {
        if (telemetry.sequence <= lastSequence) {
            return "non-monotonic-sequence current=$lastSequence incoming=${telemetry.sequence}"
        }

        val allowed = when (lastPhase) {
            null -> telemetry.phase == "compat-active"
            "compat-active" -> telemetry.phase == "viewport-probed"
            "viewport-probed" -> telemetry.phase in setOf(
                "viewport-probed",
                "root-contract-applied",
                "presentation-ready",
                "presentation-degraded",
            )
            "root-contract-applied" -> telemetry.phase in setOf(
                "root-contract-applied",
                "viewport-probed",
            )
            "presentation-ready", "presentation-degraded" -> telemetry.phase in setOf(
                "root-contract-applied",
                "viewport-probed",
            )
            else -> false
        }
        if (!allowed) {
            return "invalid-phase-transition from=${lastPhase ?: "none"} to=${telemetry.phase}"
        }

        val generation = telemetry.rootGeneration
        if (generation != null && lastRootGeneration != null && generation < lastRootGeneration!!) {
            return "root-generation-regressed current=$lastRootGeneration incoming=$generation"
        }

        lastSequence = telemetry.sequence
        lastPhase = telemetry.phase
        if (generation != null) lastRootGeneration = generation
        return null
    }
}

internal object DshPresentationBridge {
    fun install(
        webView: WebView,
        diagnostics: DshWebViewDiagnostics,
        onTelemetry: (DshPresentationTelemetry) -> Unit,
    ): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            diagnostics.append(
                "presentation-bridge unsupported feature=${WebViewFeature.WEB_MESSAGE_LISTENER} " +
                    "schema=${PresentationHandshakeContract.SCHEMA_VERSION}",
            )
            return false
        }

        WebViewCompat.addWebMessageListener(
            webView,
            DshPresentationHandshakeParser.JS_OBJECT_NAME,
            setOf(DshPresentationHandshakeParser.TRUSTED_ORIGIN),
            WebViewCompat.WebMessageListener { _, message, sourceOrigin, isMainFrame, _ ->
                if (message.type != WebMessageCompat.TYPE_STRING) {
                    diagnostics.append("presentation-handshake rejected reason=message-type")
                    return@WebMessageListener
                }
                val raw = message.data ?: run {
                    diagnostics.append("presentation-handshake rejected reason=empty-data")
                    return@WebMessageListener
                }
                DshPresentationHandshakeParser.parse(raw, sourceOrigin.toString(), isMainFrame)
                    .onSuccess { telemetry ->
                        diagnostics.append("presentation-handshake ${telemetry.summary()}")
                        onTelemetry(telemetry)
                    }
                    .onFailure { failure ->
                        diagnostics.append(
                            "presentation-handshake rejected reason=${failure.message ?: failure::class.java.simpleName}",
                        )
                    }
            },
        )
        diagnostics.append(
            "presentation-bridge installed object=${DshPresentationHandshakeParser.JS_OBJECT_NAME} " +
                "origin=${DshPresentationHandshakeParser.TRUSTED_ORIGIN} " +
                "schema=${PresentationHandshakeContract.SCHEMA_VERSION}",
        )
        return true
    }
}
