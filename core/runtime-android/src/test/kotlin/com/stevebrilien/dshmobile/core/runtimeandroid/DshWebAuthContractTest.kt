package com.stevebrilien.dshmobile.core.runtimeandroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DshWebAuthContractTest {
    @Test
    fun latestLaunchUrlNeverFallsBackToPreviousStartToken() {
        val oldUrl = "http://127.0.0.1:3080/?token=old-token"
        val currentUrl = "http://127.0.0.1:3080/?token=current-token"
        val beforeCurrentToken = """
            === DSH start 100 slot=A ===
            [startup] spawn-dsh-web
            dsh web: $oldUrl
            [startup] web-ready: HTTP/1.1 401 Unauthorized via raw 127.0.0.1

            === DSH start 200 slot=A ===
            [startup] spawn-dsh-web
            [startup] web-ready: HTTP/1.1 401 Unauthorized via raw 127.0.0.1
        """.trimIndent()

        assertNull(DshWebAuthContract.latestLaunchUrl(beforeCurrentToken))
        assertEquals(
            currentUrl,
            DshWebAuthContract.latestLaunchUrl("$beforeCurrentToken\ndsh web: $currentUrl\n"),
        )
    }

    @Test
    fun warmReuseDiagnosticsKeepOriginalProcessTokenVisible() {
        val originalUrl = "http://127.0.0.1:3080/?token=owned-process-token"
        val originalStart = "=== DSH start 100 slot=A ===\ndsh web: $originalUrl\n"
        val reused = originalStart + "[startup] fast-reuse-current-generation: ok durationMs=12\n"
        assertEquals(originalUrl, DshWebAuthContract.latestLaunchUrl(reused))

        // The startup coordinator must not add a new process marker when reusing.
        // A marker intentionally revokes lookup of previous tokens until a new DSH
        // process prints its own fresh URL; this is the old regression mechanism.
        val incorrectReuse = reused + "=== DSH start 200 slot=A ===\n"
        assertNull(DshWebAuthContract.latestLaunchUrl(incorrectReuse))
    }

    @Test
    fun statusContractRejectsTransitional404AndServerFailures() {
        assertTrue(DshWebAuthContract.isExpectedReadyStatusLine("HTTP/1.1 200 OK"))
        assertTrue(DshWebAuthContract.isExpectedReadyStatusLine("HTTP/1.1 303 See Other"))
        assertTrue(DshWebAuthContract.isExpectedReadyStatusLine("HTTP/1.1 401 Unauthorized"))
        assertFalse(DshWebAuthContract.isExpectedReadyStatusLine("HTTP/1.1 404 Not Found"))
        assertFalse(DshWebAuthContract.isExpectedReadyStatusLine("HTTP/1.1 503 Service Unavailable"))
        assertFalse(DshWebAuthContract.isExpectedReadyStatusLine("garbage"))
    }
}
