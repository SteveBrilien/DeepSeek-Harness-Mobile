package com.stevebrilien.dshmobile.core.runtimeandroid

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.stevebrilien.dshmobile.core.runtimeapi.RuntimeSlot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class RuntimeStateStoreTest {
    private lateinit var context: Context
    private lateinit var store: RuntimeStateStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "runtime").deleteRecursively()
        store = RuntimeStateStore(context)
        store.ensureLayout()
    }

    @After
    fun tearDown() {
        File(context.filesDir, "runtime").deleteRecursively()
    }

    @Test
    fun legacyStateWithoutPresentationFieldsRemainsReadable() {
        store.layout.stateFile.writeText(
            """{
              "schemaVersion": 1,
              "generation": 4,
              "activeSlot": "A",
              "previousSlot": "B"
            }""".trimIndent(),
            StandardCharsets.UTF_8,
        )

        val state = store.read()
        assertEquals(RuntimeSlot.A, state.activeSlot)
        assertEquals(RuntimeSlot.B, state.previousSlot)
        assertEquals(4L, state.generation)
        assertNull(state.presentationGeneration)
        assertEquals(0L, state.presentationStartedAtMillis)
    }

    @Test
    fun presentationLaunchCanBeRecordedAndCleared() {
        store.recordPresentationLaunch("generation-a", startedAtMillis = 1234L)
        val recorded = store.read()
        assertEquals("generation-a", recorded.presentationGeneration)
        assertEquals(1234L, recorded.presentationStartedAtMillis)

        store.clearPresentationLaunch()
        val cleared = store.read()
        assertNull(cleared.presentationGeneration)
        assertEquals(0L, cleared.presentationStartedAtMillis)
    }

    @Test
    fun activatingRuntimeSlotInvalidatesPresentationGeneration() {
        store.recordPresentationLaunch("generation-a", startedAtMillis = 1234L)
        store.activate(RuntimeSlot.A)

        val state = store.read()
        assertEquals(RuntimeSlot.A, state.activeSlot)
        assertNull(state.presentationGeneration)
        assertEquals(0L, state.presentationStartedAtMillis)
        assertTrue(state.generation > 0L)
    }
}
