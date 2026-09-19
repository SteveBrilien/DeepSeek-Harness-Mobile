package com.stevebrilien.dshmobile.ui

import android.os.SystemClock

/** Activity-relative observational milestones only. No URI, token or user data. */
internal class StartupPhaseTimeline(private val clock: () -> Long = SystemClock::elapsedRealtime) {
    private var activityAt: Long? = null
    private var runtimeAt: Long? = null
    private var presentationAt: Long? = null
    private var reportedPresentation = false

    @Synchronized fun beginActivity() {
        activityAt = clock()
        runtimeAt = null
        presentationAt = null
        reportedPresentation = false
    }

    @Synchronized fun markRuntimeReady(): String? {
        val start = activityAt ?: return null
        if (runtimeAt != null) return null
        val now = clock()
        runtimeAt = now
        val base = "startup-timeline activity-to-runtime-ms=${(now - start).coerceAtLeast(0L)}"
        return listOfNotNull(base, presentationSummary()).joinToString("; ")
    }

    /** Called only for validated main-frame presentation-ready with a positive root. */
    @Synchronized fun markPresentationReady(): String? {
        if (activityAt == null || presentationAt != null) return null
        presentationAt = clock()
        return presentationSummary()
    }

    private fun presentationSummary(): String? {
        val start = activityAt ?: return null
        val runtime = runtimeAt ?: return null
        val presentation = presentationAt ?: return null
        if (reportedPresentation) return null
        reportedPresentation = true
        return "startup-timeline activity-to-presentation-ms=${(presentation - start).coerceAtLeast(0L)} " +
            "runtime-to-presentation-ms=${(presentation - runtime).coerceAtLeast(0L)} " +
            "composer-interactive=NOT_MEASURED"
    }
}
