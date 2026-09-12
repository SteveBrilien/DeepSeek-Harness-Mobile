package com.stevebrilien.dshmobile.runtime

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.stevebrilien.dshmobile.core.runtimeandroid.RuntimeInstallProgress
import com.stevebrilien.dshmobile.core.runtimeandroid.RuntimeStartProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class RuntimeStartupTelemetryTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "runtime/install-status.json").delete()
        File(context.filesDir, "runtime/install-status.json.bak").delete()
        File(context.filesDir, "runtime/install.log").delete()
    }

    @Test
    fun runtimeStartResetsInstallTelemetryAndPublishesStages() {
        val telemetry = RuntimeInstallTelemetry(context)
        telemetry.begin("mirror-a")
        telemetry.update(
            RuntimeInstallProgress(
                phase = "download",
                message = "downloading",
                percent = 40,
                downloadedBytes = 1234,
                totalBytes = 4321,
                sourceId = "mirror-a",
                sourceName = "Mirror A",
            ),
        )

        telemetry.beginRuntimeStart("starting DSH")
        var snapshot = telemetry.snapshot()
        assertTrue(snapshot.running)
        assertFalse(snapshot.failed)
        assertTrue(snapshot.runtimeInstalled)
        assertEquals("start", snapshot.phase)
        assertNull(snapshot.downloadedBytes)
        assertNull(snapshot.totalBytes)
        assertNull(snapshot.sourceId)
        assertNull(snapshot.sourceName)

        telemetry.updateRuntimeStart(
            RuntimeStartProgress(
                phase = "ensure-mobile-plugins",
                message = "reconciling mobile plugins",
                percent = 50,
                logLine = "ensure-mobile-plugins: ok",
            ),
        )
        snapshot = telemetry.snapshot()
        assertEquals("ensure-mobile-plugins", snapshot.phase)
        assertEquals("reconciling mobile plugins", snapshot.message)
        assertEquals(50, snapshot.percent)
        assertTrue(snapshot.logs.any { it.contains("ensure-mobile-plugins: ok") })
    }

    @Test
    fun runtimeStartFailureBecomesTerminalImmediately() {
        val telemetry = RuntimeInstallTelemetry(context)
        telemetry.beginRuntimeStart("starting DSH")
        telemetry.updateRuntimeStart(RuntimeStartProgress("spawn-dsh-web", "spawning", 65))
        telemetry.fail(IllegalStateException("process exited code=7"), runtimeInstalled = true)

        val snapshot = telemetry.snapshot()
        assertFalse(snapshot.running)
        assertTrue(snapshot.failed)
        assertTrue(snapshot.runtimeInstalled)
        assertEquals("failed", snapshot.phase)
        assertTrue(snapshot.message.contains("process exited code=7"))
    }

    @Test
    fun foregroundServiceDispatchCarriesRequestedAction() {
        val capture = CapturingContext(context)
        RuntimeForegroundService.dispatch(capture, RuntimeForegroundService.ACTION_START)

        val intent = capture.foregroundIntent
        requireNotNull(intent)
        assertEquals(RuntimeForegroundService.ACTION_START, intent.action)
        assertEquals(RuntimeForegroundService::class.java.name, intent.component?.className)
    }

    private class CapturingContext(base: Context) : ContextWrapper(base) {
        var foregroundIntent: Intent? = null

        override fun startForegroundService(service: Intent): ComponentName? {
            foregroundIntent = service
            return service.component
        }
    }
}
