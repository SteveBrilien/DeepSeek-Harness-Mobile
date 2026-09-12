package com.stevebrilien.dshmobile.core.runtimeandroid

/**
 * Small, side-effect-free contract around the DSH Web process launch token.
 *
 * DSH prints one fresh token per Web process. The token is only accepted on GET `/`,
 * where it is exchanged for a persistent browser cookie. A token from an older start
 * attempt is therefore not interchangeable with the token owned by the current process.
 */
internal object DshWebAuthContract {
    private const val START_MARKER = "=== DSH start "
    private val launchUrl = Regex(
        """dsh web:\s+(http://(?:127\.0\.0\.1|localhost):${RuntimePins.DSH_HTTP_PORT}/\?token=[^\s()]+)""",
    )
    private val httpStatus = Regex("""^HTTP/\d(?:\.\d)?\s+(\d{3})(?:\s|$)""")

    /** Return only the launch URL printed after the newest Android start marker. */
    fun latestLaunchUrl(logTail: String): String? {
        if (logTail.isBlank()) return null
        val marker = logTail.lastIndexOf(START_MARKER)
        val currentAttempt = if (marker >= 0) logTail.substring(marker) else logTail
        return launchUrl.findAll(currentAttempt).lastOrNull()?.groupValues?.getOrNull(1)
    }

    /**
     * A bare DSH root is expected to answer 401 before browser-token exchange. Normal
     * 2xx/3xx responses are also ready. 404/5xx are not accepted as Web readiness.
     */
    fun isExpectedReadyStatusLine(statusLine: String): Boolean {
        val code = httpStatus.find(statusLine.trim())?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return false
        return code in 200..399 || code == 401
    }
}
