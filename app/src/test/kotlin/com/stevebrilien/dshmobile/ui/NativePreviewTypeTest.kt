package com.stevebrilien.dshmobile.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePreviewTypeTest {
    @Test fun supportsImageExtensionsCaseInsensitivelyButNeverPretendsDocumentsAreImages() {
        listOf("a.png", "b.JPG", "c.jpeg", "d.webp", "e.gif", "f.bmp").forEach {
            assertTrue(isNativePreviewImage(it))
        }
        listOf("a.pdf", "image.jpg.txt", "noextension", ".hidden", "a.svg", "a.zip").forEach {
            assertFalse(isNativePreviewImage(it))
        }
    }
}
