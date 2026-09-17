package com.stevebrilien.dshmobile.ui

import android.content.Intent
import android.provider.MediaStore
import android.webkit.WebChromeClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AttachmentPickerPolicyTest {
    @Test fun nativeSheetAppliesOnlyToWebOpenModes() {
        assertTrue(AttachmentPickerPolicy.isOpenMode(WebChromeClient.FileChooserParams.MODE_OPEN))
        assertTrue(AttachmentPickerPolicy.isOpenMode(WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE))
        assertFalse(AttachmentPickerPolicy.isOpenMode(WebChromeClient.FileChooserParams.MODE_SAVE))
        assertFalse(AttachmentPickerPolicy.allowsMultiple(WebChromeClient.FileChooserParams.MODE_OPEN))
        assertTrue(AttachmentPickerPolicy.allowsMultiple(WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE))
    }

    @Test fun sourceChoicesHonorWebAcceptConstraints() {
        assertTrue(AttachmentPickerPolicy.acceptsImages(null))
        assertTrue(AttachmentPickerPolicy.acceptsImages(emptyArray()))
        assertTrue(AttachmentPickerPolicy.acceptsImages(arrayOf("*/*")))
        assertTrue(AttachmentPickerPolicy.acceptsImages(arrayOf("IMAGE/JPEG")))
        assertTrue(AttachmentPickerPolicy.acceptsImages(arrayOf("text/plain,image/png")))
        assertTrue(AttachmentPickerPolicy.acceptsImages(arrayOf(".jpg,.pdf")))
        assertFalse(AttachmentPickerPolicy.acceptsImages(arrayOf("application/pdf")))
        assertFalse(AttachmentPickerPolicy.acceptsImages(arrayOf("text/plain", ".csv")))
    }

    @Test fun albumAndCameraRespectExactWebImageTypes() {
        val mode = WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
        val pngOnly = arrayOf("image/png")
        assertTrue(AttachmentPickerPolicy.acceptsImages(pngOnly))
        assertFalse(AttachmentPickerPolicy.acceptsCamera(pngOnly))
        assertFalse(AttachmentPickerPolicy.acceptsCamera(arrayOf("text/plain,image/webp")))
        assertTrue(AttachmentPickerPolicy.acceptsCamera(arrayOf(".jpeg,application/pdf")))
        assertTrue(AttachmentPickerPolicy.acceptsCamera(emptyArray()))
        assertEquals("image/png", AttachmentPickerPolicy.albumIntent(mode, pngOnly).type)
        val mixed = AttachmentPickerPolicy.albumIntent(mode, arrayOf(".png,image/jpeg,application/pdf"))
        assertEquals("image/*", mixed.type)
        assertEquals(listOf("image/jpeg", "image/png"),
            mixed.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList())
        assertFalse(AttachmentPickerPolicy.acceptsImages(arrayOf("application/pdf")))
        assertTrue(runCatching { AttachmentPickerPolicy.albumIntent(mode, arrayOf("application/pdf")) }.isFailure)
    }

    @Test fun photoAlbumIntentPreservesSingleOrMultipleAndReadGrant() {
        val single = AttachmentPickerPolicy.albumIntent(WebChromeClient.FileChooserParams.MODE_OPEN)
        val multi = AttachmentPickerPolicy.albumIntent(WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE)
        assertEquals(Intent.ACTION_GET_CONTENT, multi.action)
        assertEquals("image/*", multi.type)
        assertFalse(single.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
        assertTrue(multi.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
        assertTrue(multi.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertFalse(multi.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
    }

    @Test fun cameraUriIsPrivateAndSeparateFromApkUpdateProvider() {
        val context = RuntimeEnvironment.getApplication()
        val capture = AttachmentCaptureStore.create(context)
        try {
            assertTrue(capture.first.isFile)
            assertTrue(capture.first.canonicalPath.startsWith(context.cacheDir.canonicalPath + "/attachment-capture/"))
            assertEquals("content", capture.second.scheme)
            assertEquals("${context.packageName}.captures", capture.second.authority)
            assertFalse(capture.second.authority == "${context.packageName}.updates")
            val camera = AttachmentPickerPolicy.cameraIntent(context, capture.second)
            assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, camera.action)
            assertEquals(capture.second, camera.getParcelableExtra<android.net.Uri>(MediaStore.EXTRA_OUTPUT))
            assertEquals(capture.second, camera.clipData?.getItemAt(0)?.uri)
            assertTrue(camera.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertTrue(camera.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
            assertTrue(runCatching {
                androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.updates", capture.first
                )
            }.isFailure)
        } finally { capture.first.delete() }
    }
}
