package com.stevebrilien.dshmobile.ui

import com.stevebrilien.dshmobile.core.runtimeandroid.PresentationHandshakeContract
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class DshPresentationBridgeTest {
    private fun validPayload(phase: String = "viewport-probed"): String = JSONObject()
        .put("schema", PresentationHandshakeContract.SCHEMA_VERSION)
        .put("phase", phase)
        .put("seq", 2)
        .put("androidWebView", true)
        .put("compatVersion", "0.1.0")
        .put("bootRev", "boot-abc")
        .put("comboRev", "combo-def")
        .put("repairMode", "existing-100dvh")
        .put("documentReadyState", "interactive")
        .put(
            "metrics",
            JSONObject()
                .put("rootGeneration", 1)
                .put("innerWidth", 360.0)
                .put("innerHeight", 670.0)
                .put("documentClientHeight", 670.0)
                .put("visualViewportHeight", 670.0)
                .put("vh100", 670.0)
                .put("dvh100", 0.0)
                .put("rootWidth", 360.0)
                .put("rootHeight", 0.0)
                .put("verticalViewportPatchedDeclarations", 17),
        )
        .toString()

    @Test
    fun acceptsBoundedMainFrameMessageFromExactLoopbackOrigin() {
        val parsed = DshPresentationHandshakeParser.parse(
            validPayload(),
            "http://127.0.0.1:3080",
            true,
        ).getOrThrow()

        assertEquals(PresentationHandshakeContract.SCHEMA_VERSION, parsed.schema)
        assertEquals("viewport-probed", parsed.phase)
        assertTrue(parsed.androidWebView)
        assertEquals("boot-abc", parsed.bootRev)
        assertEquals("combo-def", parsed.comboRev)
        assertEquals(670.0, parsed.innerHeight ?: -1.0, 0.0)
        assertEquals(0.0, parsed.dvh100 ?: -1.0, 0.0)
        assertEquals(0.0, parsed.rootHeight ?: -1.0, 0.0)
        assertEquals(17, parsed.verticalViewportPatchedDeclarations)
    }

    @Test
    fun rejectsWrongOriginFrameSchemaPhaseAndOversizePayload() {
        val trusted = "http://127.0.0.1:3080"
        assertTrue(
            DshPresentationHandshakeParser.parse(
                validPayload(),
                "http://localhost:3080",
                true,
            ).isFailure,
        )
        assertTrue(DshPresentationHandshakeParser.parse(validPayload(), trusted, false).isFailure)

        val wrongSchema = JSONObject(validPayload())
            .put("schema", PresentationHandshakeContract.SCHEMA_VERSION + 1)
            .toString()
        assertTrue(DshPresentationHandshakeParser.parse(wrongSchema, trusted, true).isFailure)

        val wrongPhase = JSONObject(validPayload()).put("phase", "arbitrary-command").toString()
        assertTrue(DshPresentationHandshakeParser.parse(wrongPhase, trusted, true).isFailure)

        val oversized = "x".repeat(DshPresentationHandshakeParser.MAX_MESSAGE_CHARS + 1)
        assertTrue(DshPresentationHandshakeParser.parse(oversized, trusted, true).isFailure)
    }

    @Test
    fun navigationStateAcceptsExpectedAndroidPhaseGrammar() {
        val state = DshPresentationNavigationState()
        assertNull(state.accept(telemetry(sequence = 1, phase = "compat-active", rootGeneration = 1)))
        assertNull(state.accept(telemetry(sequence = 2, phase = "viewport-probed", rootGeneration = 1)))
        assertNull(state.accept(telemetry(sequence = 3, phase = "root-contract-applied", rootGeneration = 1)))
        assertNull(state.accept(telemetry(sequence = 4, phase = "viewport-probed", rootGeneration = 1)))
        assertNull(state.accept(telemetry(sequence = 5, phase = "presentation-ready", rootGeneration = 1)))
        // Scheduled cheap probes remain legal after the first ready message.
        assertNull(state.accept(telemetry(sequence = 6, phase = "viewport-probed", rootGeneration = 1)))
        // Root replacement may re-apply the contract with a new root generation.
        assertNull(state.accept(telemetry(sequence = 7, phase = "root-contract-applied", rootGeneration = 2)))
    }

    @Test
    fun navigationStateRejectsSkippedInitialPhaseSequenceRegressionAndRootRegression() {
        val state = DshPresentationNavigationState()
        assertTrue(
            state.accept(telemetry(sequence = 1, phase = "presentation-ready", rootGeneration = 1))
                ?.startsWith("invalid-phase-transition") == true,
        )

        state.reset()
        assertNull(state.accept(telemetry(sequence = 1, phase = "compat-active", rootGeneration = 2)))
        assertNull(state.accept(telemetry(sequence = 2, phase = "viewport-probed", rootGeneration = 2)))
        assertTrue(
            state.accept(telemetry(sequence = 2, phase = "viewport-probed", rootGeneration = 2))
                ?.startsWith("non-monotonic-sequence") == true,
        )
        assertTrue(
            state.accept(telemetry(sequence = 3, phase = "root-contract-applied", rootGeneration = 1))
                ?.startsWith("root-generation-regressed") == true,
        )
    }

    private fun telemetry(
        sequence: Int,
        phase: String,
        rootGeneration: Int?,
    ): DshPresentationTelemetry = DshPresentationTelemetry(
        schema = PresentationHandshakeContract.SCHEMA_VERSION,
        phase = phase,
        sequence = sequence,
        androidWebView = true,
        compatVersion = "0.1.0",
        bootRev = "boot-abc",
        comboRev = "combo-def",
        repairMode = "existing-100dvh",
        documentReadyState = "interactive",
        rootGeneration = rootGeneration,
        innerWidth = 360.0,
        innerHeight = 670.0,
        documentClientHeight = 670.0,
        visualViewportHeight = 670.0,
        vh100 = 670.0,
        dvh100 = 670.0,
        rootWidth = 360.0,
        rootHeight = 670.0,
        verticalViewportPatchedDeclarations = 0,
    )

    @Test
    fun optionalMetricsStayNullInsteadOfInventingGeometry() {
        val payload = JSONObject()
            .put("schema", PresentationHandshakeContract.SCHEMA_VERSION)
            .put("phase", "compat-active")
            .put("seq", 1)
            .put("androidWebView", false)
            .put("compatVersion", "0.1.0")
            .toString()
        val parsed = DshPresentationHandshakeParser.parse(
            payload,
            "http://127.0.0.1:3080",
            true,
        ).getOrThrow()

        assertFalse(parsed.androidWebView)
        assertNull(parsed.bootRev)
        assertNull(parsed.comboRev)
        assertNull(parsed.rootHeight)
        assertNull(parsed.verticalViewportPatchedDeclarations)
    }
}
