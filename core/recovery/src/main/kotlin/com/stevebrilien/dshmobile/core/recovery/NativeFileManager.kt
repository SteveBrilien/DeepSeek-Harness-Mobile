package com.stevebrilien.dshmobile.core.recovery

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileBrowserRoot(
    val root: File,
    val recoveryRoot: File,
    val fullSharedStorageAccess: Boolean,
    val persistentRecoveryAvailable: Boolean,
    val warning: String? = null,
)

data class NativeFileEntry(
    val name: String,
    val absolutePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedEpochMillis: Long,
    val hidden: Boolean,
)

data class TextFileContent(
    val file: NativeFileEntry,
    val content: String,
    val charsetName: String,
)

enum class FileClipboardMode { COPY, MOVE }

data class FileClipboard(
    val sourcePath: String,
    val mode: FileClipboardMode,
)

class NativeFileManager(
    private val context: Context,
    private val vault: RecoveryVault = RecoveryVault(context),
) {
    companion object {
        const val DEFAULT_MAX_TEXT_BYTES = 2L * 1024L * 1024L
    }

    fun browserRoot(): FileBrowserRoot {
        val vaultStatus = vault.status()
        val allFiles = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
        val shared = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
        val browseRoot = if (allFiles && shared != null) {
            shared
        } else {
            vaultStatus.root
        }
        return FileBrowserRoot(
            root = browseRoot,
            recoveryRoot = vaultStatus.root,
            fullSharedStorageAccess = allFiles && shared != null,
            persistentRecoveryAvailable = vaultStatus.persistentAcrossUninstall,
            warning = vaultStatus.warning,
        )
    }

    fun list(directory: File): Result<List<NativeFileEntry>> = runCatching {
        val safe = requireInsideBrowserRoot(directory)
        check(safe.isDirectory) { "Not a directory: ${safe.absolutePath}" }
        safe.listFiles()
            ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase(Locale.getDefault()) }))
            ?.map(::entry)
            ?: emptyList()
    }

    fun parent(directory: File): File? {
        val root = browserRoot().root.canonicalFile
        val current = runCatching { directory.canonicalFile }.getOrNull() ?: return null
        if (current == root) return null
        val parent = current.parentFile ?: return null
        return if (isInside(parent, root)) parent else null
    }

    fun createDirectory(parent: File, name: String): Result<File> = runCatching {
        validateName(name)
        val safeParent = requireInsideBrowserRoot(parent)
        val target = File(safeParent, name)
        check(!target.exists()) { "Already exists: ${target.name}" }
        check(target.mkdirs()) { "Unable to create directory: ${target.absolutePath}" }
        target
    }

    fun createFile(parent: File, name: String): Result<File> = runCatching {
        validateName(name)
        val safeParent = requireInsideBrowserRoot(parent)
        val target = File(safeParent, name)
        check(!target.exists()) { "Already exists: ${target.name}" }
        target.parentFile?.mkdirs()
        check(target.createNewFile()) { "Unable to create file: ${target.absolutePath}" }
        target
    }

    fun readText(file: File, maxBytes: Long = DEFAULT_MAX_TEXT_BYTES): Result<TextFileContent> = runCatching {
        val safe = requireInsideBrowserRoot(file)
        check(safe.isFile) { "Not a file: ${safe.absolutePath}" }
        check(safe.length() <= maxBytes) { "File is larger than the editor limit (${maxBytes / 1024 / 1024} MiB)." }
        val bytes = safe.readBytes()
        check(!looksBinary(bytes)) { "Binary file preview is not supported by the text editor." }
        val charset: Charset = StandardCharsets.UTF_8
        TextFileContent(entry(safe), bytes.toString(charset), charset.name())
    }

    fun saveText(file: File, content: String): Result<File> = runCatching {
        val safe = requireInsideBrowserRoot(file)
        check(!safe.isDirectory) { "Cannot save text into a directory." }
        if (safe.exists()) snapshotBeforeOverwrite(safe)
        val temp = File(safe.parentFile, ".${safe.name}.dshm-tmp-${System.nanoTime()}")
        temp.writeText(content, StandardCharsets.UTF_8)
        if (!temp.renameTo(safe)) {
            safe.outputStream().use { output -> temp.inputStream().use { it.copyTo(output) } }
            temp.delete()
        }
        safe
    }

    fun rename(file: File, newName: String): Result<File> = runCatching {
        validateName(newName)
        val safe = requireInsideBrowserRoot(file)
        val target = File(safe.parentFile, newName)
        requireInsideBrowserRoot(target)
        check(!target.exists()) { "Already exists: ${target.name}" }
        check(safe.renameTo(target)) { "Unable to rename ${safe.name}" }
        target
    }

    fun deleteToTrash(file: File): Result<File> = runCatching {
        val safe = requireInsideBrowserRoot(file)
        val trash = File(vault.ensureLayout().getOrThrow().root, ".Trash")
        check(trash.exists() || trash.mkdirs()) { "Unable to create Trash." }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        val target = uniqueTarget(trash, "$stamp-${safe.name}")
        moveAcrossFilesystems(safe, target)
        target
    }

    fun copy(source: File, destinationDirectory: File): Result<File> = runCatching {
        val safeSource = requireInsideBrowserRoot(source)
        val safeDestination = requireInsideBrowserRoot(destinationDirectory)
        check(safeDestination.isDirectory) { "Destination is not a directory." }
        val target = uniqueTarget(safeDestination, safeSource.name)
        copyRecursivelyStrict(safeSource, target)
        target
    }

    fun move(source: File, destinationDirectory: File): Result<File> = runCatching {
        val safeSource = requireInsideBrowserRoot(source)
        val safeDestination = requireInsideBrowserRoot(destinationDirectory)
        check(safeDestination.isDirectory) { "Destination is not a directory." }
        check(!isInside(safeDestination, safeSource)) { "Cannot move a directory into itself." }
        val target = uniqueTarget(safeDestination, safeSource.name)
        moveAcrossFilesystems(safeSource, target)
        target
    }

    fun ensureRecoveryLayout(): Result<RecoveryVaultStatus> = vault.ensureLayout()

    private fun snapshotBeforeOverwrite(file: File) {
        val status = vault.ensureLayout().getOrThrow()
        val historyDir = File(status.root, "Recovery/Snapshots/files")
        check(historyDir.exists() || historyDir.mkdirs())
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        val safeName = file.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = uniqueTarget(historyDir, "$stamp-$safeName")
        file.copyTo(target, overwrite = false)
    }

    private fun moveAcrossFilesystems(source: File, target: File) {
        if (source.renameTo(target)) return
        copyRecursivelyStrict(source, target)
        check(source.deleteRecursively()) { "Copied but failed to remove original: ${source.absolutePath}" }
    }

    private fun copyRecursivelyStrict(source: File, target: File) {
        if (source.isDirectory) {
            check(target.mkdirs()) { "Unable to create ${target.absolutePath}" }
            source.listFiles()?.forEach { child ->
                copyRecursivelyStrict(child, File(target, child.name))
            }
        } else {
            target.parentFile?.let { check(it.exists() || it.mkdirs()) }
            source.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
            target.setLastModified(source.lastModified())
        }
    }

    private fun requireInsideBrowserRoot(file: File): File {
        val root = browserRoot().root.canonicalFile
        val candidate = file.canonicalFile
        check(isInside(candidate, root)) { "Path escapes current browser root: ${candidate.absolutePath}" }
        return candidate
    }

    private fun isInside(candidate: File, root: File): Boolean {
        val candidatePath = candidate.canonicalPath
        val rootPath = root.canonicalPath
        return candidatePath == rootPath || candidatePath.startsWith(rootPath + File.separator)
    }

    private fun validateName(name: String) {
        check(name.isNotBlank()) { "Name cannot be empty." }
        check(name != "." && name != "..") { "Reserved name." }
        check(!name.contains('/') && !name.contains('\\') && !name.contains('\u0000')) { "Invalid file name." }
    }

    private fun uniqueTarget(parent: File, preferredName: String): File {
        var candidate = File(parent, preferredName)
        var index = 1
        while (candidate.exists()) {
            candidate = File(parent, "$preferredName.$index")
            index += 1
        }
        return candidate
    }

    private fun entry(file: File) = NativeFileEntry(
        name = file.name.ifBlank { file.absolutePath },
        absolutePath = file.absolutePath,
        isDirectory = file.isDirectory,
        sizeBytes = if (file.isFile) file.length() else 0L,
        modifiedEpochMillis = file.lastModified(),
        hidden = file.isHidden,
    )

    private fun looksBinary(bytes: ByteArray): Boolean {
        val sample = bytes.take(4096)
        return sample.any { it == 0.toByte() }
    }
}
