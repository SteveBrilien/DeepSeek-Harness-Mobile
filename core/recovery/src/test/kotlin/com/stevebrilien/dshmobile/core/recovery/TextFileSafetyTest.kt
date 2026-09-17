package com.stevebrilien.dshmobile.core.recovery

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.charset.CharacterCodingException
import java.nio.file.Files

class TextFileSafetyTest {
    @Test fun validUtf8RoundTripsWithoutReplacingMultibyteCharacters() {
        val original = "你好 🌊\nabc\r\n\t"
        val bytes = TextFileSafety.encodeUtf8(original)
        assertEquals(original, TextFileSafety.decodeUtf8(bytes))
        assertArrayEquals(original.toByteArray(Charsets.UTF_8), bytes)
    }

    @Test fun malformedUtf8IsRejectedInsteadOfBecomingReplacementCharacters() {
        try {
            TextFileSafety.decodeUtf8(byteArrayOf(0xc3.toByte(), 0x28))
            fail("expected malformed input rejection")
        } catch (_: CharacterCodingException) { }
    }

    @Test fun embeddedNulIsNotOpenedAsText() {
        try {
            TextFileSafety.decodeUtf8(byteArrayOf(65, 0, 66))
            fail("expected binary rejection")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("Binary"))
        }
    }

    @Test fun unpairedSurrogateCannotSilentlyBecomeReplacementQuestionMark() {
        try {
            TextFileSafety.encodeUtf8("broken\uD800")
            fail("expected bad string rejection")
        } catch (_: CharacterCodingException) { }
    }

    @Test fun unchangedFilePassesAndExternallyChangedFileIsRejected() {
        val file = Files.createTempFile("dshm-edit-test", ".txt").toFile()
        try {
            file.writeText("first")
            val revision = TextFileSafety.sha256(file.readBytes())
            TextFileSafety.requireUnchanged(file, revision)
            file.writeText("second")
            try {
                TextFileSafety.requireUnchanged(file, revision)
                fail("expected revision conflict")
            } catch (expected: IllegalStateException) {
                assertTrue(expected.message.orEmpty().contains("changed"))
            }
            assertEquals("second", file.readText())
        } finally { file.delete() }
    }

    @Test fun deletedFileAndInvalidRevisionAreRejectedWithoutCreatingFiles() {
        val file = Files.createTempFile("dshm-edit-deleted", ".txt").toFile()
        val revision = TextFileSafety.sha256(byteArrayOf())
        file.delete()
        try {
            TextFileSafety.requireUnchanged(file, revision)
            fail("expected deletion detection")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("no longer exists"))
        }
        assertTrue(!file.exists())
        try {
            TextFileSafety.requireUnchanged(file, "not-a-sha")
            fail("expected invalid revision")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("Invalid"))
        }
    }
}
