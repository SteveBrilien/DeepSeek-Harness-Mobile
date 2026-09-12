package com.stevebrilien.dshmobile.core.runtimeandroid

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Reconciles APK-owned mobile plugins into the persistent DSH Web profile without
 * entering PRoot or invoking DSH/pnpm. The operation is deterministic, offline and
 * idempotent; user-owned profile fields and bundles are preserved.
 */
internal class MobilePluginProfileCoordinator(
    private val context: Context,
    private val layout: RuntimeLayout,
) {
    companion object {
        const val MOBILE_CONTEXT_PLUGIN_VERSION = "0.2.1"
        const val MOBILE_UI_PLUGIN_VERSION = "0.1.9"

        private val CONTEXT_ASSETS = listOf(
            "package.json",
            "cordis.patch.yml",
            "lib/index.js",
        )
        private val UI_ASSETS = listOf(
            "package.json",
            "cordis.patch.yml",
            "lib/index.js",
            "lib/client.js",
            "LICENSE",
        )
        private const val CONTEXT_PACKAGE = "@dsh-mobile/dsh-mobile-context"
        private const val UI_PACKAGE = "dsh-client-ui-mobile"
        private const val CONTEXT_FILE_DEP = "file:/dsh-home/mobile-plugins/dsh-mobile-context"
        private const val UI_FILE_DEP = "file:/dsh-home/mobile-plugins/dsh-client-ui-mobile"
    }

    private data class PluginSpec(
        val packageName: String,
        val version: String,
        val assetRoot: String,
        val assets: List<String>,
        val persistentRelative: String,
        val profileRelative: String,
        val dependencyValue: String,
    )

    private val specs = listOf(
        PluginSpec(
            CONTEXT_PACKAGE,
            MOBILE_CONTEXT_PLUGIN_VERSION,
            "runtime/dsh-mobile-context",
            CONTEXT_ASSETS,
            "mobile-plugins/dsh-mobile-context",
            "node_modules/@dsh-mobile/dsh-mobile-context",
            CONTEXT_FILE_DEP,
        ),
        PluginSpec(
            UI_PACKAGE,
            MOBILE_UI_PLUGIN_VERSION,
            "runtime/dsh-client-ui-mobile",
            UI_ASSETS,
            "mobile-plugins/dsh-client-ui-mobile",
            "node_modules/dsh-client-ui-mobile",
            UI_FILE_DEP,
        ),
    )

    private val profileDir get() = File(layout.persistentDshHome, "profiles/web")
    private val packageFile get() = File(profileDir, "package.json")
    private val contextMarker get() = File(layout.persistentDshHome, "mobile/context-plugin.version")
    private val uiMarker get() = File(layout.persistentDshHome, "mobile/ui-plugin.version")

    fun reconcile(installSeedIfMissing: () -> Boolean) {
        layout.persistentDshHome.let { check(it.exists() || it.mkdirs()) }
        if (isReconciled()) return

        specs.forEach { spec ->
            replaceManagedTree(
                assetRoot = spec.assetRoot,
                assetFiles = spec.assets,
                destination = File(layout.persistentDshHome, spec.persistentRelative),
            )
        }

        if (!profileDir.isDirectory || profileDir.listFiles().isNullOrEmpty()) {
            check(installSeedIfMissing()) { "Bundled Web profile seed could not be installed" }
        }
        check(packageFile.isFile) {
            "Existing DSH Web profile has no package.json; refusing to replace user profile"
        }

        specs.forEach { spec ->
            replaceManagedTree(
                assetRoot = spec.assetRoot,
                assetFiles = spec.assets,
                destination = File(profileDir, spec.profileRelative),
            )
        }

        val profileJson = JSONObject(packageFile.readText(StandardCharsets.UTF_8))
        val dependencies = profileJson.optJSONObject("dependencies")
            ?: JSONObject().also { profileJson.put("dependencies", it) }
        specs.forEach { dependencies.put(it.packageName, it.dependencyValue) }

        val dsh = profileJson.optJSONObject("dsh") ?: JSONObject().also { profileJson.put("dsh", it) }
        val profile = dsh.optJSONObject("profile") ?: JSONObject().also { dsh.put("profile", it) }
        val bundles = profile.optJSONArray("bundles") ?: JSONArray().also { profile.put("bundles", it) }
        specs.forEach { spec ->
            if (!bundles.containsString(spec.packageName)) bundles.put(spec.packageName)
        }
        writeAtomic(packageFile, profileJson.toString(2).toByteArray(StandardCharsets.UTF_8))

        check(isProfileContractValid()) { "DSH mobile plugin profile reconciliation failed" }
        writeAtomic(contextMarker, MOBILE_CONTEXT_PLUGIN_VERSION.toByteArray(StandardCharsets.UTF_8))
        writeAtomic(uiMarker, MOBILE_UI_PLUGIN_VERSION.toByteArray(StandardCharsets.UTF_8))
        check(isReconciled()) { "DSH mobile plugin reconciliation marker verification failed" }
    }

    fun isReconciled(): Boolean =
        contextMarker.readTextIfExists() == MOBILE_CONTEXT_PLUGIN_VERSION &&
            uiMarker.readTextIfExists() == MOBILE_UI_PLUGIN_VERSION &&
            isProfileContractValid()

    private fun isProfileContractValid(): Boolean = runCatching {
        if (!packageFile.isFile) return@runCatching false
        val profileJson = JSONObject(packageFile.readText(StandardCharsets.UTF_8))
        val dependencies = profileJson.optJSONObject("dependencies") ?: return@runCatching false
        val bundles = profileJson.optJSONObject("dsh")
            ?.optJSONObject("profile")
            ?.optJSONArray("bundles")
            ?: return@runCatching false

        specs.all { spec ->
            dependencies.optString(spec.packageName) == spec.dependencyValue &&
                bundles.containsString(spec.packageName) &&
                packageVersion(File(profileDir, spec.profileRelative)) == spec.version &&
                packageVersion(File(layout.persistentDshHome, spec.persistentRelative)) == spec.version
        }
    }.getOrDefault(false)

    private fun packageVersion(directory: File): String? {
        val manifest = File(directory, "package.json")
        if (!manifest.isFile) return null
        return runCatching {
            JSONObject(manifest.readText(StandardCharsets.UTF_8)).optString("version").takeIf(String::isNotBlank)
        }.getOrNull()
    }

    private fun replaceManagedTree(
        assetRoot: String,
        assetFiles: List<String>,
        destination: File,
    ) {
        val parent = destination.parentFile ?: error("Managed plugin directory has no parent")
        check(parent.exists() || parent.mkdirs()) { "Unable to create ${parent.absolutePath}" }
        val staging = File(parent, ".${destination.name}.staging-${System.nanoTime()}")
        deleteNode(staging)
        check(staging.mkdirs()) { "Unable to create plugin staging directory ${staging.absolutePath}" }
        try {
            assetFiles.forEach { relative ->
                val target = File(staging, relative)
                target.parentFile?.let { check(it.exists() || it.mkdirs()) }
                context.assets.open("$assetRoot/$relative").use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
            }
            check(File(staging, "package.json").isFile) { "Managed plugin staging manifest is missing" }
            activateManagedTree(staging, destination)
        } finally {
            deleteNode(staging)
        }
    }

    private fun activateManagedTree(staging: File, destination: File) {
        val parent = destination.parentFile ?: error("Managed plugin directory has no parent")
        val backup = File(parent, ".${destination.name}.backup-${System.nanoTime()}")
        deleteNode(backup)
        val hadPrevious = nodeExists(destination)
        var previousMoved = false
        try {
            if (hadPrevious) {
                check(destination.renameTo(backup)) {
                    "Unable to stage previous managed plugin ${destination.name} for replacement"
                }
                previousMoved = true
            }
            check(staging.renameTo(destination)) { "Unable to activate managed plugin ${destination.name}" }
            if (previousMoved) deleteNode(backup)
        } catch (failure: Throwable) {
            if (!nodeExists(destination) && previousMoved && nodeExists(backup)) {
                runCatching {
                    check(backup.renameTo(destination)) {
                        "Unable to restore previous managed plugin ${destination.name}"
                    }
                }.onFailure(failure::addSuppressed)
            }
            throw failure
        } finally {
            if (nodeExists(backup) && nodeExists(destination)) deleteNode(backup)
        }
    }

    private fun nodeExists(file: File): Boolean =
        file.exists() || Files.isSymbolicLink(file.toPath())

    private fun deleteNode(file: File) {
        if (!nodeExists(file)) return
        if (Files.isSymbolicLink(file.toPath()) || !file.isDirectory) {
            check(file.delete()) { "Unable to delete ${file.absolutePath}" }
        } else {
            check(file.deleteRecursively()) { "Unable to delete ${file.absolutePath}" }
        }
    }

    private fun writeAtomic(file: File, bytes: ByteArray) {
        file.parentFile?.let { check(it.exists() || it.mkdirs()) }
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (t: Throwable) {
            atomic.failWrite(stream)
            throw t
        }
    }

    private fun JSONArray.containsString(value: String): Boolean {
        for (index in 0 until length()) {
            if (optString(index) == value) return true
        }
        return false
    }

    private fun File.readTextIfExists(): String? =
        if (isFile) readText(StandardCharsets.UTF_8).trim() else null
}
