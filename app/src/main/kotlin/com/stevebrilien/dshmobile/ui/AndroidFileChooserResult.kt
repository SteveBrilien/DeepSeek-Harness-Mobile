package com.stevebrilien.dshmobile.ui

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient

/**
 * Reconcile the system picker result with WebChromeClient's parsed values.
 * Some vendor pickers populate ClipData while also setting Intent.data to only
 * the first item. The WebView must receive every selected URI exactly once.
 * Only the explicitly returned result is inspected: no media-library scanning,
 * persistable grants, filenames or file contents are required.
 */
internal object AndroidFileChooserResult {
    // Untrusted picker payloads must not iterate or forward unbounded URI batches
    // on the WebView UI thread. DSH still enforces actual model/byte limits.
    private const val MAX_RESULT_URIS = 64

    fun resolve(
        resultCode: Int,
        data: Intent?,
        mode: Int,
        parsed: Array<Uri>?,
    ): Array<Uri>? {
        if (resultCode != Activity.RESULT_OK) return null
        val resolved = LinkedHashSet<Uri>()
        val multiple = mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
        if (multiple) {
            if ((data?.clipData?.itemCount ?: 0) > MAX_RESULT_URIS ||
                (parsed?.size ?: 0) > MAX_RESULT_URIS
            ) return null
            data?.clipData?.let { clip ->
                for (index in 0 until clip.itemCount) {
                    clip.getItemAt(index).uri?.let(resolved::add)
                    if (resolved.size > MAX_RESULT_URIS) return null
                }
            }
            data?.data?.let(resolved::add)
            if (resolved.size > MAX_RESULT_URIS) return null
            parsed?.forEach {
                resolved.add(it)
                if (resolved.size > MAX_RESULT_URIS) return null
            }
        } else {
            // Do not leak unintended additional files into a single-file input.
            val first = parsed?.firstOrNull() ?: data?.data
                ?: data?.clipData?.let { clip -> if (clip.itemCount > 0) clip.getItemAt(0).uri else null }
            first?.let(resolved::add)
        }
        // Android's chooser result is untrusted. The WebView is only permitted
        // content:// documents granted by the picker, never file://, data: or
        // intent:// paths supplied by a malicious third-party provider.
        val safe = resolved.filter {
            it.scheme == ContentResolver.SCHEME_CONTENT && !it.authority.isNullOrBlank()
        }
        return safe.takeIf { it.isNotEmpty() }?.toTypedArray()
    }
}
