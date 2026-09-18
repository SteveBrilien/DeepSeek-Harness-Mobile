package com.stevebrilien.dshmobile.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

/** Only the Android 8–12 legacy READ_EXTERNAL_STORAGE path is enabled for now.
 * No constructor or UI composition accesses media: [page] is an explicit action,
 * requiring an already granted permission. Other OS versions fail closed until
 * their distinct partial/full photo permission contracts are implemented.
 */
internal class RecentMediaRepository(
    private val context: Context,
    private val permissionGranted: () -> Boolean = {
        Build.VERSION.SDK_INT in 26..32 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
    },
    private val query: (Array<String>, String?, Array<String>?, String) -> Cursor? =
        { columns, where, args, order ->
            context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, where, args, order)
        },
) {
    data class Image(val uri: Uri, val id: Long, val dateAddedSeconds: Long, val mimeType: String, val sizeBytes: Long)
    data class Anchor(val dateAddedSeconds: Long, val id: Long)

    sealed interface Result {
        data object PermissionRequired : Result
        data object Unavailable : Result
        data class Items(val images: List<Image>, val next: Anchor?) : Result
    }

    fun page(after: Anchor? = null, limit: Int = 20): Result {
        require(limit in 1..20) { "Media pages must be bounded to 1..20 items" }
        if (!permissionGranted()) return Result.PermissionRequired
        if (after != null && (after.id <= 0 || after.dateAddedSeconds < 0)) return Result.Unavailable
        val id = MediaStore.Images.Media._ID
        val added = MediaStore.Images.Media.DATE_ADDED
        val mime = MediaStore.Images.Media.MIME_TYPE
        val size = MediaStore.Images.Media.SIZE
        val where = if (after == null) null else "($added < ? OR ($added = ? AND $id < ?))"
        val args = after?.let { arrayOf(it.dateAddedSeconds.toString(), it.dateAddedSeconds.toString(), it.id.toString()) }
        // Stable seek-pagination avoids offset drift when another app adds pictures.
        // The caller only gets content:// identities; never filesystem DATA paths.
        val order = "$added DESC, $id DESC"
        return try {
            val cursor = query(arrayOf(id, added, mime, size), where, args, order) ?: return Result.Unavailable
            cursor.use { rows ->
                val idIndex = rows.getColumnIndexOrThrow(id)
                val addedIndex = rows.getColumnIndexOrThrow(added)
                val mimeIndex = rows.getColumnIndexOrThrow(mime)
                val sizeIndex = rows.getColumnIndexOrThrow(size)
                val images = ArrayList<Image>(limit)
                while (images.size < limit && rows.moveToNext()) {
                    val entryId = rows.getLong(idIndex)
                    val date = rows.getLong(addedIndex)
                    val mediaType = rows.getString(mimeIndex).orEmpty().lowercase(java.util.Locale.ROOT)
                    val bytes = rows.getLong(sizeIndex)
                    if (entryId <= 0L || date < 0L || bytes <= 0L || !mediaType.startsWith("image/")) continue
                    images.add(Image(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, entryId), entryId, date, mediaType, bytes))
                }
                // Do not cache media identities across permission revocation or Session changes.
                Result.Items(images, images.lastOrNull()?.let { Anchor(it.dateAddedSeconds, it.id) })
            }
        } catch (_: SecurityException) {
            Result.PermissionRequired
        } catch (_: Exception) {
            // OEM MediaStore providers can fail or remove columns. No photos or
            // provider/URI details are logged, and no broad-storage fallback runs.
            Result.Unavailable
        }
    }
}
