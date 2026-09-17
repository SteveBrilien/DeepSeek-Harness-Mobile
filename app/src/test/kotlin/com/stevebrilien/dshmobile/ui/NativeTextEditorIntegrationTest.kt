package com.stevebrilien.dshmobile.ui

import android.content.Context
import com.stevebrilien.dshmobile.core.recovery.NativeFileManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/** Only files created by these tests inside Robolectric's isolated browser root. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class NativeTextEditorIntegrationTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val manager = NativeFileManager(context)

    private fun withFixture(block: (File) -> Unit) {
        val root = manager.browserRoot().root
        val folder = manager.createDirectory(root, "unit-editor-${UUID.randomUUID()}").getOrThrow()
        try {
            val file = manager.createFile(folder, "test.txt").getOrThrow()
            block(file)
        } finally {
            folder.deleteRecursively() // Never touches external/user files, only this fixture folder.
        }
    }

    @Test fun savingUnmodifiedRevisionPersistsUnicodeAndProducesReadableHash() = withFixture { file ->
        file.writeText("old")
        val loaded = manager.readText(file).getOrThrow()
        manager.saveText(file, "你好 🌊\n", loaded.sha256).getOrThrow()
        val newer = manager.readText(file).getOrThrow()
        assertEquals("你好 🌊\n", newer.content)
        assertTrue(newer.sha256 != loaded.sha256)
    }

    @Test fun externalWriterConflictPreservesBothOriginalAndDraft() = withFixture { file ->
        file.writeText("initial")
        val revision = manager.readText(file).getOrThrow().sha256
        file.writeText("from terminal")
        val attempt = manager.saveText(file, "unsaved draft", revision)
        assertTrue(attempt.isFailure)
        assertEquals("from terminal", file.readText())
    }

    @Test fun externalDeletionCannotBeResurrectedByEditor() = withFixture { file ->
        file.writeText("initial")
        val revision = manager.readText(file).getOrThrow().sha256
        assertTrue(file.delete())
        val attempt = manager.saveText(file, "draft", revision)
        assertTrue(attempt.isFailure)
        assertFalse(file.exists())
    }

    @Test fun malformedUtf8CannotBeReadOrSilentlyRepaired() = withFixture { file ->
        val bytes = byteArrayOf(0xc3.toByte(), 0x28)
        file.writeBytes(bytes)
        assertTrue(manager.readText(file).isFailure)
        assertTrue(file.readBytes().contentEquals(bytes))
    }
}
