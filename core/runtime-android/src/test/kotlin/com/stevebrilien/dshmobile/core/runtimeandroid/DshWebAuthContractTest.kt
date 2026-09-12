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
    fun statusContractRejectsTransitional404AndServerFailures() {
        assertTrue(DshWebAuthContract.isExpectedReadyStatusLine("HTTP/1.1 200 OK"))
        assertTrue(DshWebAuthContract.isExpectedReadyStatusLine("HTTP/1.1 303 See Other"))
        assertTrue(DshWebAuthContract.isExpectedReadyStatusLine("HTTP/1.1 401 Unauthorized"))
        assertFalse(DshWebAuthContract.isExpectedReadyStatusLine("HTTP/1.1 404 Not Found"))
        assertFalse(DshWebAuthContract.isExpectedReadyStatusLine("HTTP/1.1 503 Service Unavailable"))
        assertFalse(DshWebAuthContract.isExpectedReadyStatusLine("garbage"))
    }
}
