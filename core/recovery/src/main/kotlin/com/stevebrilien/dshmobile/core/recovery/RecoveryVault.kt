package com.stevebrilien.dshmobile.core.recovery

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

data class RecoveryVaultStatus(
    val root: File,
    val manifest: File,
    val persistentAcrossUninstall: Boolean,
    val usingFallbackStorage: Boolean,
    val allFilesAccessGranted: Boolean,
    val warning: String? = null,
)

data class RecoveryDiscovery(
    val status: RecoveryVaultStatus,
    val manifestExists: Boolean,
    val schemaVersion: Int?,
    val vaultId: String?,
    val projectCount: Int,
    val sessionCount: Int,
    val localPluginCount: Int,
    val warnings: List<String>,
)

class RecoveryVault(private val context: Context) {
    companion object {
        const val MANIFEST_SCHEMA_VERSION = 1
        const val LAYOUT_VERSION = 1
        const val VAULT_DIRECTORY_NAME = "DeepSeekHarness"

        private val REQUIRED_DIRECTORIES = listOf(
            "Projects",
            "Sessions",
            "Plugins/Installed",
            "Plugins/Local",
            "Plugins/State",
            "Recovery/Identity/ssh",
            "Recovery/Identity/trusted-hosts",
            "Recovery/Identity/device-pairings",
            "Recovery/Config/app",
            "Recovery/Config/dsh",
            "Recovery/Config/plugins",
            "Recovery/Config/projects",
            "Recovery/Secrets/encrypted-vault",
            "Recovery/Plugins/local-source",
            "Recovery/Plugins/working-tree-snapshots",
            "Recovery/Plugins/package-manifests",
            "Recovery/Plugins/version-locks",
            "Recovery/Sessions/snapshots",
            "Recovery/Runtime",
            "Recovery/Snapshots",
            "Exports",
            ".Trash",
        )
    }

    fun status(): RecoveryVaultStatus {
        val storagePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        val legacyTarget = context.applicationInfo.targetSdkVersion <= Build.VERSION_CODES.P
        val legacySharedAccess = legacyTarget && storagePermissionGranted
        val preRSharedAccess = Build.VERSION.SDK_INT < Build.VERSION_CODES.R && storagePermissionGranted
        val managerAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()
        val allFiles = legacySharedAccess || preRSharedAccess || managerAccess
        val externalRoot = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
        val canUsePersistentSharedRoot = allFiles && externalRoot != null
        val root = if (canUsePersistentSharedRoot) {
            File(externalRoot, VAULT_DIRECTORY_NAME)
        } else {
            val fallback = context.getExternalFilesDir(null) ?: context.filesDir
            File(fallback, VAULT_DIRECTORY_NAME)
        }
        val fallback = !canUsePersistentSharedRoot
        return RecoveryVaultStatus(
            root = root,
            manifest = File(root, "Recovery/manifest.json"),
            persistentAcrossUninstall = !fallback,
            usingFallbackStorage = fallback,
            allFilesAccessGranted = allFiles,
            warning = if (fallback) {
                "Recovery Vault is using app-specific fallback storage. It may be removed when the app is uninstalled. Grant shared-storage/all-files access before relying on it as the only backup."
            } else {
                null
            },
        )
    }

    fun ensureLayout(): Result<RecoveryVaultStatus> = runCatching {
        val status = status()
        REQUIRED_DIRECTORIES.forEach { relative ->
            val dir = File(status.root, relative)
            check(dir.exists() || dir.mkdirs()) { "Unable to create ${dir.absolutePath}" }
        }
        if (!status.manifest.exists()) {
            writeNewManifest(status)
        } else {
            touchManifest(status)
        }
        status
    }

    fun discover(): RecoveryDiscovery {
        val status = status()
        val warnings = mutableListOf<String>()
        status.warning?.let(warnings::add)
        if (!status.root.exists()) {
            return RecoveryDiscovery(
                status = status,
                manifestExists = false,
                schemaVersion = null,
                vaultId = null,
                projectCount = 0,
                sessionCount = 0,
                localPluginCount = 0,
                warnings = warnings + "Recovery Vault root does not exist yet.",
            )
        }

        val parsed = readManifest(status.manifest).getOrNull()
        if (status.manifest.exists() && parsed == null) {
            warnings += "Recovery manifest exists but could not be parsed. Preserve it and use Recovery tools before replacing it."
        }
        val schema = parsed?.optInt("schemaVersion")?.takeIf { it > 0 }
        if (schema != null && schema > MANIFEST_SCHEMA_VERSION) {
            warnings += "Recovery manifest schema $schema is newer than supported schema $MANIFEST_SCHEMA_VERSION."
        }

        return RecoveryDiscovery(
            status = status,
            manifestExists = status.manifest.exists(),
            schemaVersion = schema,
            vaultId = parsed?.optString("vaultId")?.takeIf { it.isNotBlank() },
            projectCount = childDirectoryCount(File(status.root, "Projects")),
            sessionCount = childCount(File(status.root, "Sessions")),
            localPluginCount = childDirectoryCount(File(status.root, "Plugins/Local")),
            warnings = warnings,
        )
    }

    fun verify(): Result<RecoveryDiscovery> = runCatching {
        val ensured = ensureLayout().getOrThrow()
        val manifest = readManifest(ensured.manifest).getOrThrow()
        val schema = manifest.optInt("schemaVersion", -1)
        check(schema in 1..MANIFEST_SCHEMA_VERSION) {
            "Unsupported Recovery manifest schema: $schema"
        }
        check(manifest.optString("vaultId").isNotBlank()) { "Recovery manifest is missing vaultId" }
        discover()
    }

    private fun writeNewManifest(status: RecoveryVaultStatus) {
        val now = System.currentTimeMillis()
        val json = JSONObject()
            .put("schemaVersion", MANIFEST_SCHEMA_VERSION)
            .put("layoutVersion", LAYOUT_VERSION)
            .put("vaultId", UUID.randomUUID().toString())
            .put("createdAtEpochMillis", now)
            .put("updatedAtEpochMillis", now)
            .put("containsPlaintextSecrets", false)
        atomicWrite(status.manifest, json.toString(2))
    }

    private fun touchManifest(status: RecoveryVaultStatus) {
        val json = readManifest(status.manifest).getOrNull() ?: return
        json.put("updatedAtEpochMillis", System.currentTimeMillis())
        atomicWrite(status.manifest, json.toString(2))
    }

    private fun readManifest(file: File): Result<JSONObject> = runCatching {
        JSONObject(file.readText(StandardCharsets.UTF_8))
    }

    private fun atomicWrite(file: File, content: String) {
        file.parentFile?.let { check(it.exists() || it.mkdirs()) }
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(content.toByteArray(StandardCharsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (t: Throwable) {
            atomic.failWrite(stream)
            throw t
        }
    }

    private fun childDirectoryCount(dir: File): Int = dir.listFiles()?.count { it.isDirectory } ?: 0
    private fun childCount(dir: File): Int = dir.listFiles()?.size ?: 0
}
