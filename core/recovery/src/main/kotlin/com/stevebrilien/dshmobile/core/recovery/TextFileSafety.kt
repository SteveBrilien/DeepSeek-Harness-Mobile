package com.stevebrilien.dshmobile.core.recovery

import java.io.File
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Lossless, bounded text editing primitives. Failure must never rewrite the original. */
internal object TextFileSafety {
    fun decodeUtf8(bytes: ByteArray): String {
        check(bytes.none { it == 0.toByte() }) { "Binary files cannot be edited as UTF-8 text." }
        val decoded = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
        return decoded.toString()
    }

    fun encodeUtf8(text: String): ByteArray {
        val encoded = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(text))
        return ByteArray(encoded.remaining()).also { encoded.get(it) }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun requireUnchanged(file: File, expectedSha256: String) {
        check(expectedSha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid editor revision." }
        check(file.isFile) { "File no longer exists. Original editor text has been retained." }
        val current = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                current.update(buffer, 0, count)
            }
        }
        val actual = current.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        check(actual == expectedSha256) {
            "File changed since it was opened. Your edits were not saved; reload or copy them elsewhere first."
        }
    }
}
