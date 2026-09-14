package com.stevebrilien.dshmobile.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DshWebViewDiagnosticsTest {
    @Test
    fun redactionRemovesLaunchTokensFromUrlsAndConsoleText() {
        val input = "url=http://127.0.0.1:3080/?token=secret_123&x=1 console token=other-secret"
        val output = DshWebViewDiagnostics.redact(input)
        assertFalse(output.contains("secret_123"))
        assertFalse(output.contains("other-secret"))
        assertTrue(output.contains("token=[redacted]"))
        assertTrue(output.contains("&x=1"))
    }
    @Test
    fun redactionDoesNotLeakSuffixWhenTokenContainsLowercaseS() {
        val input = "launch=http://127.0.0.1:3080/?token=abc_sSuffix-42"
        val output = DshWebViewDiagnostics.redact(input)
        assertFalse(output.contains("abc_sSuffix-42"))
        assertFalse(output.contains("Suffix-42"))
        assertTrue(output.endsWith("token=[redacted]"))
    }

}
