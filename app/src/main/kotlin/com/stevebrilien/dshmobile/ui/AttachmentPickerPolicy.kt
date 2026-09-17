package com.stevebrilien.dshmobile.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.webkit.WebChromeClient
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** Native chooser only provides sources; DSH still owns the file input, upload and send. */
internal object AttachmentPickerPolicy {
    fun isOpenMode(mode: Int): Boolean = mode == WebChromeClient.FileChooserParams.MODE_OPEN ||
        mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE

    fun allowsMultiple(mode: Int): Boolean = mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE

    private val imageExtensions = mapOf(
        ".jpg" to "image/jpeg", ".jpeg" to "image/jpeg", ".png" to "image/png",
        ".gif" to "image/gif", ".webp" to "image/webp", ".bmp" to "image/bmp",
        ".heic" to "image/heic", ".heif" to "image/heif",
    )

    private fun acceptTokens(acceptTypes: Array<String>?): List<String> = acceptTypes.orEmpty()
        .flatMap { it.split(',') }
        .map { it.trim().lowercase(java.util.Locale.ROOT).substringBefore(';') }
        .filter { it.isNotBlank() }

    /** null = unrestricted images; empty = no supported image type. */
    private fun acceptedImageTypes(acceptTypes: Array<String>?): Set<String>? {
        val tokens = acceptTokens(acceptTypes)
        if (tokens.isEmpty() || tokens.any { it == "*/*" || it == "image/*" }) return null
        return tokens.mapNotNull { token ->
            imageExtensions[token] ?: token.takeIf { it.startsWith("image/") && it.length > 6 && !it.contains('*') }
        }.toSet()
    }

    fun acceptsImages(acceptTypes: Array<String>?): Boolean = acceptedImageTypes(acceptTypes) != emptySet<String>()

    /** Android ACTION_IMAGE_CAPTURE produces JPEG; do not offer it for PNG-only inputs. */
    fun acceptsCamera(acceptTypes: Array<String>?): Boolean = acceptedImageTypes(acceptTypes)?.contains("image/jpeg") != false

    fun albumIntent(mode: Int, acceptTypes: Array<String>? = null): Intent = Intent(Intent.ACTION_GET_CONTENT).apply {
        val accepted = acceptedImageTypes(acceptTypes)
        require(accepted == null || accepted.isNotEmpty()) { "The Web input does not accept images." }
        addCategory(Intent.CATEGORY_OPENABLE)
        type = if (accepted?.size == 1) accepted.single() else "image/*"
        if (accepted != null && accepted.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, accepted.sorted().toTypedArray())
        if (allowsMultiple(mode)) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun cameraIntent(context: Context, output: Uri): Intent =
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, output)
            clipData = ClipData.newUri(context.contentResolver, "photo", output)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

}

/** Separate, private FileProvider from the update APK sharing authority. */
internal object AttachmentCaptureStore {
    private const val DIRECTORY = "attachment-capture"
    private const val MAX_AGE_MILLIS = 24L * 60L * 60L * 1000L

    fun create(context: Context, now: Long = System.currentTimeMillis()): Pair<File, Uri> {
        val dir = File(context.cacheDir, DIRECTORY)
        check(dir.isDirectory || dir.mkdirs()) { "Cannot prepare camera cache." }
        dir.listFiles()?.filter { it.isFile && it.lastModified() < now - MAX_AGE_MILLIS }
            ?.forEach { it.delete() } // no recursive deletion; only our private, old camera files
        val file = File.createTempFile("capture-${UUID.randomUUID()}-", ".jpg", dir)
        return try {
            file to FileProvider.getUriForFile(context, "${context.packageName}.captures", file)
        } catch (failure: Exception) {
            file.delete()
            throw failure
        }
    }
}
