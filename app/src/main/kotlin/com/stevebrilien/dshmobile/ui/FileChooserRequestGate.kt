package com.stevebrilien.dshmobile.ui

import android.net.Uri
import android.webkit.ValueCallback
import java.io.File

/**
 * An ActivityResult stays in flight after a page has abandoned its WebView callback.
 * Keep the old launch reserved until its result is drained, otherwise a late result
 * could be forwarded into the next document's file input. Owned by the WebView host,
 * not the transient Chat composable; never persists callbacks or file paths to disk.
 */
internal class FileChooserRequestGate {
    enum class Kind { FILE, CAMERA }

    data class Completion(
        val mode: Int,
        val callback: ValueCallback<Array<Uri>>?,
        val cameraOutput: Pair<File, Uri>?,
    )

    private data class Pending(
        val mode: Int,
        var callback: ValueCallback<Array<Uri>>?,
        var kind: Kind? = null,
        var cameraOutput: Pair<File, Uri>? = null,
    )

    private var pending: Pending? = null

    val inFlight: Boolean get() = pending != null
    val hasActiveCallback: Boolean get() = pending?.callback != null

    /** Reject the new callback only; the earlier picker and its result retain ownership. */
    fun begin(callback: ValueCallback<Array<Uri>>, mode: Int): Boolean {
        if (pending != null) {
            callback.onReceiveValue(null)
            return false
        }
        pending = Pending(mode, callback)
        return true
    }

    /** Call immediately before invoking the appropriate ActivityResult launcher. */
    fun stage(kind: Kind, cameraOutput: Pair<File, Uri>? = null) {
        val current = checkNotNull(pending) { "No file chooser request to stage" }
        check(current.kind == null) { "File chooser launch was already staged" }
        current.kind = kind
        current.cameraOutput = cameraOutput
    }

    /** A preparation/launch error guarantees that no OS result is outstanding. */
    fun abortBeforeLaunch() {
        val current = pending ?: return
        pending = null
        current.callback?.onReceiveValue(null)
    }

    /** Cancel the old WebView callback, but do NOT free the Android launcher slot. */
    fun abandonDocument() {
        val current = pending ?: return
        val callback = current.callback
        current.callback = null
        callback?.onReceiveValue(null)
    }

    /** Consume only the result from the matching launcher; state resets before delivery. */
    fun takeResult(kind: Kind): Completion? {
        val current = pending ?: return null
        if (current.kind != kind) return null
        pending = null
        return Completion(current.mode, current.callback, current.cameraOutput)
    }
}
