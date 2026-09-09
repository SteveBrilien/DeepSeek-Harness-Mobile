package com.stevebrilien.dshmobile.core.recovery

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Integrity checked local checkpoint/export layer over the live Recovery Vault. */
class RecoveryBackupManager(
    private val context: Context,
    private val vault: RecoveryVault = RecoveryVault(context),
) {
    data class BackupResult(val file: File, val sha256: String, val entries: Int, val createdAt: Long)

    private val excludedPrefixes = listOf("Recovery/Snapshots/", "Exports/Backups/", ".Trash/")
    private val persistentDshHome = File(context.filesDir, "persistent/dsh-home")
    private val dshArchivePrefix = "AppPrivate/dsh-home/"

    fun createCheckpoint(): BackupResult = createArchive("Recovery/Snapshots", "checkpoint")

    fun createPortableExport(): BackupResult = createArchive("Exports/Backups", "dsh-mobile-backup")

    fun verifyLatest(): Result<BackupResult> = runCatching {
        val status = vault.ensureLayout().getOrThrow()
        val candidates = listOf(
            File(status.root, "Recovery/Snapshots"),
            File(status.root, "Exports/Backups"),
        ).flatMap { it.listFiles()?.filter { f -> f.isFile && f.extension == "zip" }.orEmpty() }
        val latest = candidates.maxByOrNull { it.lastModified() } ?: error("尚无可校验的恢复备份")
        verifyArchive(latest)
    }

    fun restorePersistentDshHomeFromLatest(): Result<Int> = runCatching {
        val status = vault.ensureLayout().getOrThrow()
        val candidates = listOf(
            File(status.root, "Exports/Backups"),
            File(status.root, "Recovery/Snapshots"),
        ).flatMap { it.listFiles()?.filter { f -> f.isFile && f.extension == "zip" }.orEmpty() }
        val archive = candidates.maxByOrNull { it.lastModified() } ?: error("尚无可恢复的 DSH 数据备份")
        verifyArchive(archive)
        var restored = 0
        ZipFile(archive).use { zip ->
            val manifest = zip.getInputStream(zip.getEntry("backup-manifest.json") ?: error("Backup manifest missing"))
                .bufferedReader().use { JSONObject(it.readText()) }
            val rows = manifest.getJSONArray("files")
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val archivePath = row.getString("path")
                if (!archivePath.startsWith(dshArchivePrefix)) continue
                val relative = archivePath.removePrefix(dshArchivePrefix)
                require(isSafeEntry(relative) && !isSensitiveDshPath(relative)) { "Unsafe DSH restore entry: $archivePath" }
                val root = persistentDshHome.canonicalFile
                val destination = File(root, relative).canonicalFile
                check(destination.path.startsWith(root.path + File.separator)) { "Restore path escapes DSH home: $relative" }
                val entry = zip.getEntry(archivePath) ?: error("Backup entry missing: $archivePath")
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                try {
                    val actual = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
                    check(actual == row.getString("sha256")) { "Backup checksum mismatch: $archivePath" }
                    destination.parentFile?.let { check(it.exists() || it.mkdirs()) }
                    val atomic = AtomicFile(destination)
                    val output = atomic.startWrite()
                    try {
                        output.write(bytes)
                        atomic.finishWrite(output)
                    } catch (t: Throwable) {
                        atomic.failWrite(output)
                        throw t
                    }
                    restored += 1
                } finally {
                    bytes.fill(0)
                }
            }
        }
        restored
    }

    fun verifyArchive(file: File): BackupResult {
        require(file.isFile) { "Backup archive does not exist: ${file.absolutePath}" }
        val outerSha = sha256(file)
        ZipFile(file).use { zip ->
            val manifestEntry = zip.getEntry("backup-manifest.json") ?: error("Backup manifest missing")
            val manifest = zip.getInputStream(manifestEntry).bufferedReader().use { JSONObject(it.readText()) }
            val rows = manifest.getJSONArray("files")
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val path = row.getString("path")
                require(isSafeEntry(path)) { "Unsafe backup entry: $path" }
                val entry = zip.getEntry(path) ?: error("Backup entry missing: $path")
                val digest = MessageDigest.getInstance("SHA-256")
                zip.getInputStream(entry).use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                    }
                }
                val actual = digest.digest().toHex()
                check(actual == row.getString("sha256")) { "Backup checksum mismatch: $path" }
                check(entry.size == row.getLong("size")) { "Backup size mismatch: $path" }
            }
            return BackupResult(
                file = file,
                sha256 = outerSha,
                entries = rows.length(),
                createdAt = manifest.optLong("createdAtEpochMillis", file.lastModified()),
            )
        }
    }

    fun lastBackupEpochMillis(): Long? {
        val state = File(vault.status().root, "Recovery/backup-state.json")
        return runCatching { JSONObject(state.readText()).optLong("lastSuccessfulBackupEpochMillis").takeIf { it > 0 } }.getOrNull()
    }

    private fun createArchive(relativeDir: String, prefix: String): BackupResult {
        val status = vault.ensureLayout().getOrThrow()
        check(status.persistentAcrossUninstall) {
            "恢复保险库尚未位于跨卸载持久存储；请先授权长期存储再创建发布级备份"
        }
        val outputDir = File(status.root, relativeDir).apply { check(exists() || mkdirs()) }
        val now = System.currentTimeMillis()
        val finalFile = File(outputDir, "$prefix-$now.zip")
        val partial = File(outputDir, ".${finalFile.name}.partial")
        partial.delete()

        val rows = JSONArray()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(partial))).use { out ->
            val rootCanonical = status.root.canonicalFile
            status.root.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val canonical = file.canonicalFile
                    if (!canonical.path.startsWith(rootCanonical.path + File.separator)) return@forEach
                    val relative = canonical.relativeTo(rootCanonical).invariantSeparatorsPath
                    if (!shouldInclude(relative)) return@forEach
                    val digest = MessageDigest.getInstance("SHA-256")
                    val entry = ZipEntry(relative).apply { time = file.lastModified() }
                    out.putNextEntry(entry)
                    var size = 0L
                    BufferedInputStream(FileInputStream(file)).use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            size += n
                        }
                    }
                    out.closeEntry()
                    rows.put(JSONObject().put("path", relative).put("size", size).put("sha256", digest.digest().toHex()))
                }
            addPersistentDshHome(out, rows)
            val manifest = JSONObject()
                .put("schemaVersion", 1)
                .put("createdAtEpochMillis", now)
                .put("vaultId", vault.discover().vaultId ?: JSONObject.NULL)
                .put("containsPlaintextSecrets", false)
                .put("includesPersistentDshHome", true)
                .put("files", rows)
            out.putNextEntry(ZipEntry("backup-manifest.json"))
            out.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
            out.closeEntry()
        }
        check(partial.renameTo(finalFile)) { "Unable to atomically publish backup archive" }
        val verified = verifyArchive(finalFile)
        writeState(now, finalFile, verified.sha256, rows.length())
        return verified
    }

    private fun addPersistentDshHome(out: ZipOutputStream, rows: JSONArray) {
        if (!persistentDshHome.isDirectory) return
        val root = persistentDshHome.canonicalFile
        persistentDshHome.walkTopDown().filter { it.isFile }.forEach { file ->
            val canonical = file.canonicalFile
            if (!canonical.path.startsWith(root.path + File.separator)) return@forEach
            val relative = canonical.relativeTo(root).invariantSeparatorsPath
            if (!isSafeEntry(relative) || isSensitiveDshPath(relative)) return@forEach
            val digest = MessageDigest.getInstance("SHA-256")
            val archivePath = dshArchivePrefix + relative
            out.putNextEntry(ZipEntry(archivePath).apply { time = file.lastModified() })
            var size = 0L
            BufferedInputStream(FileInputStream(file)).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    digest.update(buffer, 0, n)
                    size += n
                }
            }
            out.closeEntry()
            rows.put(JSONObject().put("path", archivePath).put("size", size).put("sha256", digest.digest().toHex()))
        }
    }

    private fun isSensitiveDshPath(relative: String): Boolean {
        val normalized = relative.lowercase()
        val basename = normalized.substringAfterLast('/')
        return basename == ".credentials.yaml" || basename == ".env" ||
            basename.endsWith(".pem") || basename.endsWith(".key") ||
            normalized.startsWith("secrets/") || normalized.contains("/secrets/")
    }

    private fun shouldInclude(relative: String): Boolean {
        if (!isSafeEntry(relative)) return false
        if (excludedPrefixes.any(relative::startsWith)) return false
        // Secrets are included only from the encrypted vault. Other accidental secret-like files are not copied.
        if (relative.startsWith("Recovery/Secrets/") && !relative.startsWith("Recovery/Secrets/encrypted-vault/")) return false
        return true
    }

    private fun writeState(now: Long, file: File, sha: String, count: Int) {
        val stateFile = File(vault.status().root, "Recovery/backup-state.json")
        val json = JSONObject()
            .put("schemaVersion", 1)
            .put("lastSuccessfulBackupEpochMillis", now)
            .put("file", file.name)
            .put("sha256", sha)
            .put("entries", count)
        stateFile.parentFile?.mkdirs()
        val atomic = AtomicFile(stateFile)
        val stream = atomic.startWrite()
        try {
            stream.write(json.toString(2).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (t: Throwable) {
            atomic.failWrite(stream)
            throw t
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().toHex()
    }

    private fun isSafeEntry(path: String): Boolean = path.isNotBlank() && !path.startsWith('/') && path.split('/').none { it == ".." }
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
