package com.stevebrilien.dshmobile.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Size
import androidx.core.content.ContextCompat
import androidx.webkit.WebMessageCompat
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import android.webkit.WebView
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Read-only, permission-gated, document-scoped thumbnail transport for the fixed DSH origin.
 * No arbitrary content URI, filesystem path or original image bytes ever arrive from JavaScript.
 * A user must explicitly open the attachment panel; the bridge never queries in install().
 */
internal class RecentMediaWebBridge(
    private val context: Context,
    private val requestPermission: () -> Unit,
) {
    private companion object {
        const val ORIGIN = "http://127.0.0.1:3080"
        const val OBJECT = "dshMobileRecentMedia"
        const val MAX_REQUEST_CHARS = 320
        const val MAX_THUMB_BYTES = 32 * 1024
        const val MAX_THUMBS_PER_PAGE = 12
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository = RecentMediaRepository(context)
    private val handles = LinkedHashMap<String, Uri>()
    private var epoch = 0
    private var pendingPermission: Pair<Int, JavaScriptReplyProxy>? = null

    fun install(view: WebView): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return false
        WebViewCompat.addWebMessageListener(view, OBJECT, setOf(ORIGIN),
            WebViewCompat.WebMessageListener { _, message, origin, mainFrame, reply ->
                if (!mainFrame || origin.toString() != ORIGIN || message.type != WebMessageCompat.TYPE_STRING) {
                    return@WebMessageListener
                }
                val raw = message.data ?: return@WebMessageListener
                if (raw.length !in 1..MAX_REQUEST_CHARS) return@WebMessageListener
                val json = runCatching { JSONObject(raw) }.getOrNull() ?: return@WebMessageListener
                if (json.optInt("schema") != 1) return@WebMessageListener
                val requestId = json.optInt("id", -1)
                if (requestId !in 1..1_000_000) return@WebMessageListener
                when (json.optString("action")) {
                    "list" -> list(requestId, reply)
                    "thumb" -> thumb(requestId, json.optString("key"), reply)
                    "permission" -> permission(requestId, reply)
                }
            })
        return true
    }

    private fun permitted(): Boolean = Build.VERSION.SDK_INT in 26..32 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    private fun send(reply: JavaScriptReplyProxy, id: Int, state: String, photos: JSONArray? = null,
                     thumbnail: String? = null) {
        val response = JSONObject().put("schema", 1).put("id", id).put("state", state)
        if (photos != null) response.put("photos", photos)
        if (thumbnail != null) response.put("thumbnail", thumbnail)
        runCatching { reply.postMessage(response.toString()) }
    }

    private fun list(id: Int, reply: JavaScriptReplyProxy) {
        handles.clear()
        if (!permitted()) {
            send(reply, id, if (Build.VERSION.SDK_INT in 26..32) "permission-required" else "unsupported")
            return
        }
        when (val result = repository.page(limit = MAX_THUMBS_PER_PAGE)) {
            RecentMediaRepository.Result.PermissionRequired -> send(reply, id, "permission-required")
            RecentMediaRepository.Result.Unavailable -> send(reply, id, "unavailable")
            is RecentMediaRepository.Result.Items -> {
                if (!permitted()) { send(reply, id, "permission-required"); return }
                val items = JSONArray()
                result.images.forEach { image ->
                    val key = UUID.randomUUID().toString()
                    handles[key] = image.uri
                    items.put(JSONObject().put("key", key))
                }
                send(reply, id, "items", photos = items)
            }
        }
    }

    private fun thumb(id: Int, key: String, reply: JavaScriptReplyProxy) {
        if (!permitted()) { handles.clear(); send(reply, id, "permission-required"); return }
        if (key.length != 36) { send(reply, id, "unavailable"); return }
        val uri = handles[key] ?: run { send(reply, id, "unavailable"); return }
        val currentEpoch = epoch
        scope.launch {
            val encoded = withContext(Dispatchers.IO) {
                runCatching {
                    // Fixed 96px decoder budget, bounded JPEG result. No full photo bytes to JS.
                    if (Build.VERSION.SDK_INT < 29) return@runCatching null
                    val bitmap = context.contentResolver.loadThumbnail(uri, Size(96, 96), null)
                    val out = ByteArrayOutputStream()
                    if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 66, out)) return@runCatching null
                    val bytes = out.toByteArray()
                    if (bytes.size !in 1..MAX_THUMB_BYTES) return@runCatching null
                    "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                }.getOrNull()
            }
            if (epoch != currentEpoch || !permitted() || handles[key] != uri) return@launch
            send(reply, id, if (encoded == null) "unavailable" else "thumbnail", thumbnail = encoded)
        }
    }

    private fun permission(id: Int, reply: JavaScriptReplyProxy) {
        if (permitted()) { send(reply, id, "granted"); return }
        if (Build.VERSION.SDK_INT !in 26..32) { send(reply, id, "unsupported"); return }
        if (pendingPermission != null) { send(reply, id, "unavailable"); return }
        pendingPermission = id to reply
        runCatching { requestPermission() }.onFailure {
            pendingPermission = null
            send(reply, id, "unavailable")
        }
    }

    fun onPermissionResult(granted: Boolean) {
        val pending = pendingPermission ?: return
        pendingPermission = null
        handles.clear()
        send(pending.second, pending.first, if (granted && permitted()) "granted" else "permission-required")
    }

    fun resetDocument() {
        ++epoch
        handles.clear()
        pendingPermission = null
    }

    fun close() {
        resetDocument()
        scope.cancel()
    }
}
