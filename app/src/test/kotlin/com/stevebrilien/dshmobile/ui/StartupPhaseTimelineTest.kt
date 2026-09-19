package com.stevebrilien.dshmobile.ui

import org.junit.Assert.*
import org.junit.Test

class StartupPhaseTimelineTest {
    @Test fun recordsOnlyOnePairOfMilestonesAndDoesNotClaimComposerReadiness() {
        var time = 1000L
        val t = StartupPhaseTimeline { time }
        assertNull(t.markRuntimeReady())
        assertNull(t.markPresentationReady())
        t.beginActivity()
        time = 1200L
        assertEquals("startup-timeline activity-to-runtime-ms=200", t.markRuntimeReady())
        assertNull(t.markRuntimeReady())
        time = 1550L
        assertEquals(
            "startup-timeline activity-to-presentation-ms=550 runtime-to-presentation-ms=350 composer-interactive=NOT_MEASURED",
            t.markPresentationReady(),
        )
        assertNull(t.markPresentationReady())
    }

    @Test fun earlyPresentationIsReportedAfterRuntimeAndActivityRecreationResetsSamples() {
        var time = 100L
        val t = StartupPhaseTimeline { time }
        t.beginActivity()
        time = 180L
        assertNull(t.markPresentationReady())
        time = 200L
        val reported = t.markRuntimeReady() ?: error("expected timeline")
        assertTrue(reported.contains("activity-to-runtime-ms=100"))
        assertTrue(reported.contains("activity-to-presentation-ms=80"))
        assertEquals(1, reported.split("composer-interactive=NOT_MEASURED").size - 1)
        t.beginActivity()
        time = 240L
        assertEquals("startup-timeline activity-to-runtime-ms=40", t.markRuntimeReady())
        time = 250L
        assertTrue(t.markPresentationReady()?.contains("activity-to-presentation-ms=50") == true)
    }
}
