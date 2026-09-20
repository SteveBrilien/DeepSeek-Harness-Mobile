package com.stevebrilien.dshmobile.ui

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AndroidFileChooserResultTest {
    private val first = Uri.parse("content://picker.test/first")
    private val second = Uri.parse("content://picker.test/second")
    private val third = Uri.parse("content://picker.test/third")
    private val multiple = WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
    private val single = WebChromeClient.FileChooserParams.MODE_OPEN

    private fun pickerResult(): Intent = Intent(Intent.ACTION_GET_CONTENT).apply {
        data = first
        clipData = ClipData.newUri(RuntimeEnvironment.getApplication().contentResolver, "test", first).also {
            it.addItem(ClipData.Item(second))
            it.addItem(ClipData.Item(third))
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    @Test fun multipleModeKeepsEveryClipUriWhenParsedResultOnlyContainsFirst() {
        assertArrayEquals(
            arrayOf(first, second, third),
            AndroidFileChooserResult.resolve(Activity.RESULT_OK, pickerResult(), multiple, arrayOf(first)),
        )
    }

    @Test fun multipleModeDeduplicatesDataClipAndParsedValues() {
        assertArrayEquals(
            arrayOf(first, second, third),
            AndroidFileChooserResult.resolve(Activity.RESULT_OK, pickerResult(), multiple, arrayOf(first, second, third)),
        )
    }

    @Test fun multipleModeSupportsDataOnlyAndParsedOnlyProviders() {
        assertArrayEquals(arrayOf(first), AndroidFileChooserResult.resolve(Activity.RESULT_OK, Intent().apply { data = first }, multiple, null))
        assertArrayEquals(arrayOf(first, second), AndroidFileChooserResult.resolve(Activity.RESULT_OK, null, multiple, arrayOf(first, second)))
    }

    @Test fun singleModeNeverForwardsExtraSelectedItems() {
        assertArrayEquals(arrayOf(first), AndroidFileChooserResult.resolve(Activity.RESULT_OK, pickerResult(), single, arrayOf(first, second, third)))
        assertArrayEquals(arrayOf(first), AndroidFileChooserResult.resolve(Activity.RESULT_OK, pickerResult(), single, null))
    }

    @Test fun untrustedSchemesAreRejectedEvenWhenPickerClaimsSuccess() {
        val hostile = arrayOf(
            Uri.parse("file:///data/data/other.app/secret"),
            Uri.parse("javascript:alert(1)"),
            Uri.parse("intent://untrusted"),
        )
        assertNull(AndroidFileChooserResult.resolve(Activity.RESULT_OK, null, multiple, hostile))
        assertArrayEquals(
            arrayOf(first, second),
            AndroidFileChooserResult.resolve(
                Activity.RESULT_OK,
                Intent().apply { data = first },
                multiple,
                arrayOf(hostile[0], second, hostile[1]),
            ),
        )
    }

    @Test fun oversizedPickerResultsFailClosedInsteadOfForwardingPartialBatches() {
        val many = (1..65).map { Uri.parse("content://picker.test/$it") }
        assertNull(AndroidFileChooserResult.resolve(Activity.RESULT_OK, null, multiple, many.toTypedArray()))
        assertArrayEquals(
            many.take(64).toTypedArray(),
            AndroidFileChooserResult.resolve(Activity.RESULT_OK, null, multiple, many.take(64).toTypedArray()),
        )
        val overflowingClip = ClipData.newUri(RuntimeEnvironment.getApplication().contentResolver, "test", first)
        repeat(64) { overflowingClip.addItem(ClipData.Item(Uri.parse("content://picker.test/clip-$it"))) }
        assertNull(AndroidFileChooserResult.resolve(
            Activity.RESULT_OK, Intent().apply { clipData = overflowingClip }, multiple, null,
        ))
        val clipAtLimit = ClipData.newUri(RuntimeEnvironment.getApplication().contentResolver, "test", first)
        repeat(63) { clipAtLimit.addItem(ClipData.Item(Uri.parse("content://picker.test/clip-$it"))) }
        assertNull(AndroidFileChooserResult.resolve(
            Activity.RESULT_OK,
            Intent().apply { clipData = clipAtLimit; data = Uri.parse("content://picker.test/extra") },
            multiple,
            null,
        ))
    }

    @Test fun malformedContentUriWithoutAuthorityNeverReachesWebView() {
        val invalid = Uri.parse("content:///private/image")
        assertNull(AndroidFileChooserResult.resolve(Activity.RESULT_OK, null, single, arrayOf(invalid)))
        assertArrayEquals(
            arrayOf(first),
            AndroidFileChooserResult.resolve(Activity.RESULT_OK, null, multiple, arrayOf(invalid, first)),
        )
    }

    @Test fun cancelAndEmptyResultsDoNotReturnUris() {
        assertNull(AndroidFileChooserResult.resolve(Activity.RESULT_CANCELED, pickerResult(), multiple, arrayOf(first)))
        assertNull(AndroidFileChooserResult.resolve(Activity.RESULT_OK, Intent(), multiple, null))
        assertNull(AndroidFileChooserResult.resolve(Activity.RESULT_OK, null, single, null))
    }

    @Test fun malformedNullClipItemsAreIgnoredWithoutDroppingValidItems() {
        val intent = Intent().apply {
            clipData = ClipData.newPlainText("test", "not a URI").also {
                it.addItem(ClipData.Item(second))
            }
        }
        assertArrayEquals(arrayOf(second), AndroidFileChooserResult.resolve(Activity.RESULT_OK, intent, multiple, null))
    }
}
