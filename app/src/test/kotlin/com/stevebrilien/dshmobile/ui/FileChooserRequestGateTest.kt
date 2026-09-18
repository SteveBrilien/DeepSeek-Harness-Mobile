package com.stevebrilien.dshmobile.ui

import android.net.Uri
import android.webkit.ValueCallback
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class FileChooserRequestGateTest {
    private class Receiver : ValueCallback<Array<Uri>> {
        val results = mutableListOf<List<Uri>?>()
        override fun onReceiveValue(value: Array<Uri>?) { results.add(value?.toList()) }
    }

    @Test fun overlappingRequestCancelsOnlyNewCallback() {
        val gate = FileChooserRequestGate()
        val old = Receiver()
        val newer = Receiver()
        assertTrue(gate.begin(old, 1))
        gate.stage(FileChooserRequestGate.Kind.FILE)
        assertFalse(gate.begin(newer, 1))
        assertEquals(listOf(null), newer.results)
        assertTrue(old.results.isEmpty())
        val result = gate.takeResult(FileChooserRequestGate.Kind.FILE)!!
        assertSame(old, result.callback)
        assertEquals(1, result.mode)
        result.callback?.onReceiveValue(arrayOf(Uri.parse("content://test/one")))
        assertEquals(listOf(listOf(Uri.parse("content://test/one"))), old.results)
        assertFalse(gate.inFlight)
    }

    @Test fun navigationCancelsOldCallbackButReservesLauncherUntilLateResultIsDrained() {
        val gate = FileChooserRequestGate()
        val old = Receiver()
        val candidate = Receiver()
        assertTrue(gate.begin(old, 1))
        gate.stage(FileChooserRequestGate.Kind.FILE)
        gate.abandonDocument()
        gate.abandonDocument()
        assertEquals(listOf(null), old.results)
        assertTrue(gate.inFlight)
        assertFalse(gate.hasActiveCallback)
        assertFalse(gate.begin(candidate, 1))
        assertEquals(listOf(null), candidate.results)
        val late = gate.takeResult(FileChooserRequestGate.Kind.FILE)!!
        assertNull(late.callback)
        assertFalse(gate.inFlight)
        val next = Receiver()
        assertTrue(gate.begin(next, 3))
        gate.stage(FileChooserRequestGate.Kind.FILE)
        assertEquals(3, gate.takeResult(FileChooserRequestGate.Kind.FILE)?.mode)
    }

    @Test fun wrongLauncherResultCannotStealPendingCameraRequest() {
        val gate = FileChooserRequestGate()
        val callback = Receiver()
        val output = File("capture-test.jpg") to Uri.parse("content://app.captures/one")
        assertTrue(gate.begin(callback, 0))
        gate.stage(FileChooserRequestGate.Kind.CAMERA, output)
        assertNull(gate.takeResult(FileChooserRequestGate.Kind.FILE))
        assertTrue(gate.inFlight)
        assertEquals(output, gate.takeResult(FileChooserRequestGate.Kind.CAMERA)?.cameraOutput)
        assertFalse(gate.inFlight)
    }

    @Test fun failureBeforeLaunchReleasesGateAndDeliversSingleCancellation() {
        val gate = FileChooserRequestGate()
        val old = Receiver()
        assertTrue(gate.begin(old, 0))
        gate.abortBeforeLaunch()
        gate.abortBeforeLaunch()
        gate.abandonDocument()
        assertEquals(listOf(null), old.results)
        assertFalse(gate.inFlight)
        assertTrue(gate.begin(Receiver(), 0))
    }
}
