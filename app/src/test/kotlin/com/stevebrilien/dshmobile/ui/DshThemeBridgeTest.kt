package com.stevebrilien.dshmobile.ui

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class DshThemeBridgeTest {
    private fun validPayload(): String {
        val colors = JSONObject()
        listOf(
            "base",
            "layer1",
            "layer2",
            "layer3",
            "textPrimary",
            "textSecondary",
            "textTertiary",
            "border1",
            "border2",
            "accent",
            "accentSoft",
            "accentText",
            "hover",
            "selected",
            "warning",
            "warningBg",
            "success",
            "danger",
            "codeBg",
        ).forEachIndexed { index, key ->
            colors.put(key, if (index == 7) "#1FFFFFFF" else "#${(0x101010 + index).toString(16).padStart(6, '0')}")
        }
        return JSONObject()
            .put("schema", DshThemeBridgeParser.SCHEMA_VERSION)
            .put("preference", "system")
            .put("activeId", "dark")
            .put("colorScheme", "dark")
            .put("revision", 7)
            .put("colors", colors)
            .toString()
    }

    @Test
    fun acceptsBoundedThemeSnapshotFromExactLoopbackMainFrame() {
        val parsed = DshThemeBridgeParser.parse(
            validPayload(),
            DshPresentationHandshakeParser.TRUSTED_ORIGIN,
            true,
        ).getOrThrow()

        assertEquals("system", parsed.preference)
        assertEquals("dark", parsed.activeId)
        assertTrue(parsed.isDark)
        assertEquals(7, parsed.revision)
        assertEquals("#1FFFFFFF", parsed.palette.border1)
    }

    @Test
    fun rejectsWrongOriginFrameSchemeMalformedColorAndOversizePayload() {
        val trusted = DshPresentationHandshakeParser.TRUSTED_ORIGIN
        assertTrue(DshThemeBridgeParser.parse(validPayload(), "http://localhost:3080", true).isFailure)
        assertTrue(DshThemeBridgeParser.parse(validPayload(), trusted, false).isFailure)

        val wrongScheme = JSONObject(validPayload()).put("colorScheme", "sepia").toString()
        assertTrue(DshThemeBridgeParser.parse(wrongScheme, trusted, true).isFailure)

        val malformed = JSONObject(validPayload())
        val colors = malformed.getJSONObject("colors")
        colors.put("base", "rgb(1,2,3)")
        assertTrue(DshThemeBridgeParser.parse(malformed.toString(), trusted, true).isFailure)

        val oversized = "x".repeat(DshThemeBridgeParser.MAX_MESSAGE_CHARS + 1)
        assertTrue(DshThemeBridgeParser.parse(oversized, trusted, true).isFailure)
    }

    @Test
    fun acceptsTokyoNightAsRegisteredDarkThemeIdentity() {
        val payload = JSONObject(validPayload())
            .put("preference", "tokyo-night")
            .put("activeId", "tokyo-night")
            .put("colorScheme", "dark")
            .toString()
        val parsed = DshThemeBridgeParser.parse(
            payload,
            DshPresentationHandshakeParser.TRUSTED_ORIGIN,
            true,
        ).getOrThrow()

        assertEquals("tokyo-night", parsed.preference)
        assertEquals("tokyo-night", parsed.activeId)
        assertTrue(parsed.isDark)
    }

    @Test
    fun lightSnapshotIsNotDarkAndAllowsRegisteredThemePreference() {
        val payload = JSONObject(validPayload())
            .put("preference", "future-theme")
            .put("activeId", "future-theme")
            .put("colorScheme", "light")
            .toString()
        val parsed = DshThemeBridgeParser.parse(
            payload,
            DshPresentationHandshakeParser.TRUSTED_ORIGIN,
            true,
        ).getOrThrow()

        assertEquals("future-theme", parsed.preference)
        assertFalse(parsed.isDark)
    }
}
