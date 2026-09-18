package com.stevebrilien.dshmobile.core.runtimeandroid

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest

/**
 * Reconciles APK-owned DSH integration into the persistent Web profile without entering
 * PRoot or invoking DSH/pnpm. The mobile context, WebView compatibility, and scoped mobile UI
 * packages are all active managed bundles. WebView compatibility owns only the viewport/root
 * contract; the mobile UI package owns only phone navigation/settings presentation. User-owned
 * profile fields and unrelated bundles are preserved.
 */
internal class MobilePluginProfileCoordinator(
    private val context: Context,
    private val layout: RuntimeLayout,
) {
    companion object {
        const val MOBILE_CONTEXT_PLUGIN_VERSION = "0.2.2"
        const val WEBVIEW_COMPAT_PLUGIN_VERSION = "0.1.2"
        const val MOBILE_UI_PLUGIN_VERSION = "0.4.3-dshm.1"
        const val TOKYO_THEME_PLUGIN_VERSION = "0.2.2-dshm.1"
        const val ATTACHMENT_SOURCES_PLUGIN_VERSION = "0.1.1-dshm.1"
        const val MOBILE_WEB_PROFILE_MODE = "dsh-mobile-ui-v1"

        private val CONTEXT_ASSETS = listOf(
            "package.json",
            "cordis.patch.yml",
            "lib/index.js",
        )
        private val COMPAT_ASSETS = listOf(
            "package.json",
            "cordis.patch.yml",
            "lib/index.js",
            "lib/client.js",
        )
        private val UI_ASSETS = listOf(
            "package.json",
            "cordis.patch.yml",
            "lib/index.js",
            "lib/client.js",
            "LICENSE",
        )
        private val ATTACHMENT_SOURCES_ASSETS = listOf(
            "package.json",
            "cordis.patch.yml",
            "lib/index.js",
            "lib/client.js",
        )
        private val TOKYO_THEME_ASSETS = listOf(
            "package.json",
            "cordis.patch.yml",
            "lib/index.js",
            "lib/client.js",
        )
        private const val CONTEXT_PACKAGE = "@dsh-mobile/dsh-mobile-context"
        private const val COMPAT_PACKAGE = "@dsh-mobile/dsh-webview-compat"
        private const val UI_PACKAGE = "dsh-client-ui-mobile"
        private const val TOKYO_THEME_PACKAGE = "dsh-plugin-tokyo-night"
        private const val ATTACHMENT_SOURCES_PACKAGE = "@dsh-mobile/dsh-mobile-attachment-sources"
        private const val CONTEXT_FILE_DEP = "file:/dsh-home/mobile-plugins/dsh-mobile-context"
        private const val COMPAT_FILE_DEP = "file:/dsh-home/mobile-plugins/dsh-webview-compat"
        private const val UI_FILE_DEP = "file:/dsh-home/mobile-plugins/dsh-client-ui-mobile"
        private const val TOKYO_THEME_FILE_DEP = "file:/dsh-home/mobile-plugins/dsh-plugin-tokyo-night"
        private const val ATTACHMENT_SOURCES_FILE_DEP = "file:/dsh-home/mobile-plugins/dsh-mobile-attachment-sources"
    }

    private data class PluginSpec(
        val packageName: String,
        val version: String,
        val assetRoot: String,
        val assets: List<String>,
        val persistentRelative: String,
        val profileRelative: String,
        val dependencyValue: String? = null,
    )

    private val contextSpec = PluginSpec(
        CONTEXT_PACKAGE,
        MOBILE_CONTEXT_PLUGIN_VERSION,
        "runtime/dsh-mobile-context",
        CONTEXT_ASSETS,
        "mobile-plugins/dsh-mobile-context",
        "node_modules/@dsh-mobile/dsh-mobile-context",
        CONTEXT_FILE_DEP,
    )
    private val compatSpec = PluginSpec(
        COMPAT_PACKAGE,
        WEBVIEW_COMPAT_PLUGIN_VERSION,
        "runtime/dsh-webview-compat",
        COMPAT_ASSETS,
        "mobile-plugins/dsh-webview-compat",
        "node_modules/@dsh-mobile/dsh-webview-compat",
        COMPAT_FILE_DEP,
    )
    private val uiSpec = PluginSpec(
        UI_PACKAGE,
        MOBILE_UI_PLUGIN_VERSION,
        "runtime/dsh-client-ui-mobile",
        UI_ASSETS,
        "mobile-plugins/dsh-client-ui-mobile",
        "node_modules/dsh-client-ui-mobile",
        UI_FILE_DEP,
    )
    private val tokyoThemeSpec = PluginSpec(
        TOKYO_THEME_PACKAGE,
        TOKYO_THEME_PLUGIN_VERSION,
        "runtime/dsh-plugin-tokyo-night",
        TOKYO_THEME_ASSETS,
        "mobile-plugins/dsh-plugin-tokyo-night",
        "node_modules/dsh-plugin-tokyo-night",
        TOKYO_THEME_FILE_DEP,
    )

    private val attachmentSourcesSpec = PluginSpec(
        ATTACHMENT_SOURCES_PACKAGE,
        ATTACHMENT_SOURCES_PLUGIN_VERSION,
        "runtime/dsh-mobile-attachment-sources",
        ATTACHMENT_SOURCES_ASSETS,
        "mobile-plugins/dsh-mobile-attachment-sources",
        "node_modules/@dsh-mobile/dsh-mobile-attachment-sources",
        ATTACHMENT_SOURCES_FILE_DEP,
    )

    private val profileDir get() = File(layout.persistentDshHome, "profiles/web")
    private val packageFile get() = File(profileDir, "package.json")
    private val contextMarker get() = File(layout.persistentDshHome, "mobile/context-plugin.version")
    private val uiMarker get() = File(layout.persistentDshHome, "mobile/ui-plugin.version")
    private val presentationMarker get() = File(layout.persistentDshHome, "mobile/presentation-generation")

    fun desiredPresentationGeneration(): String = PresentationCandidateIdentity.compute(
        dshSeedSha256 = RuntimePins.DSH_SEED_SHA256,
        webProfileSeedSha256 = RuntimePins.DSH_WEB_PROFILE_SEED_SHA256,
        profileMode = MOBILE_WEB_PROFILE_MODE,
        managedArtifactHashes = mapOf(
            contextSpec.packageName to assetTreeHash(contextSpec),
            compatSpec.packageName to assetTreeHash(compatSpec),
            uiSpec.packageName to assetTreeHash(uiSpec),
            attachmentSourcesSpec.packageName to assetTreeHash(attachmentSourcesSpec),
            tokyoThemeSpec.packageName to assetTreeHash(tokyoThemeSpec),
        ),
    )

    fun reconcile(installSeedIfMissing: () -> Boolean) {
        layout.persistentDshHome.let { check(it.exists() || it.mkdirs()) }
        val desiredGeneration = desiredPresentationGeneration()
        if (isReconciled(desiredGeneration)) return

        // All APK-managed packages are staged and activated individually. The compatibility and UI
        // concerns stay separate packages even though both participate in one presentation identity.
        listOf(contextSpec, compatSpec, uiSpec, attachmentSourcesSpec, tokyoThemeSpec).forEach { spec ->
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

        replaceManagedTree(
            assetRoot = contextSpec.assetRoot,
            assetFiles = contextSpec.assets,
            destination = File(profileDir, contextSpec.profileRelative),
        )
        replaceManagedTree(
            assetRoot = compatSpec.assetRoot,
            assetFiles = compatSpec.assets,
            destination = File(profileDir, compatSpec.profileRelative),
        )
        replaceManagedTree(
            assetRoot = uiSpec.assetRoot,
            assetFiles = uiSpec.assets,
            destination = File(profileDir, uiSpec.profileRelative),
        )
        replaceManagedTree(
            assetRoot = attachmentSourcesSpec.assetRoot,
            assetFiles = attachmentSourcesSpec.assets,
            destination = File(profileDir, attachmentSourcesSpec.profileRelative),
        )
        replaceManagedTree(
            assetRoot = tokyoThemeSpec.assetRoot,
            assetFiles = tokyoThemeSpec.assets,
            destination = File(profileDir, tokyoThemeSpec.profileRelative),
        )

        val profileJson = JSONObject(packageFile.readText(StandardCharsets.UTF_8))
        val dependencies = profileJson.optJSONObject("dependencies")
            ?: JSONObject().also { profileJson.put("dependencies", it) }
        dependencies.put(contextSpec.packageName, contextSpec.dependencyValue)
        dependencies.put(compatSpec.packageName, compatSpec.dependencyValue)
        dependencies.put(uiSpec.packageName, uiSpec.dependencyValue)
        dependencies.put(attachmentSourcesSpec.packageName, attachmentSourcesSpec.dependencyValue)
        dependencies.put(tokyoThemeSpec.packageName, tokyoThemeSpec.dependencyValue)

        val dsh = profileJson.optJSONObject("dsh") ?: JSONObject().also { profileJson.put("dsh", it) }
        val profile = dsh.optJSONObject("profile") ?: JSONObject().also { dsh.put("profile", it) }
        val bundles = profile.optJSONArray("bundles") ?: JSONArray().also { profile.put("bundles", it) }
        if (!bundles.containsString(contextSpec.packageName)) bundles.put(contextSpec.packageName)
        if (!bundles.containsString(compatSpec.packageName)) bundles.put(compatSpec.packageName)
        if (!bundles.containsString(uiSpec.packageName)) bundles.put(uiSpec.packageName)
        if (!bundles.containsString(attachmentSourcesSpec.packageName)) bundles.put(attachmentSourcesSpec.packageName)
        if (!bundles.containsString(tokyoThemeSpec.packageName)) bundles.put(tokyoThemeSpec.packageName)
        profile.put("bundles", bundles)
        writeAtomic(packageFile, profileJson.toString(2).toByteArray(StandardCharsets.UTF_8))

        check(isProfileContractValid()) { "DSH native Web profile reconciliation failed" }
        writeAtomic(contextMarker, MOBILE_CONTEXT_PLUGIN_VERSION.toByteArray(StandardCharsets.UTF_8))
        writeAtomic(uiMarker, MOBILE_WEB_PROFILE_MODE.toByteArray(StandardCharsets.UTF_8))
        writeAtomic(presentationMarker, desiredGeneration.toByteArray(StandardCharsets.UTF_8))
        check(isReconciled(desiredGeneration)) { "DSH native Web profile marker verification failed" }
    }

    fun isActivePresentationReconciled(): Boolean =
        isActivePresentationReconciled(desiredPresentationGeneration())

    fun isReconciled(): Boolean = isReconciled(desiredPresentationGeneration())

    private fun isActivePresentationReconciled(desiredGeneration: String): Boolean =
        contextMarker.readTextIfExists() == MOBILE_CONTEXT_PLUGIN_VERSION &&
            uiMarker.readTextIfExists() == MOBILE_WEB_PROFILE_MODE &&
            presentationMarker.readTextIfExists() == desiredGeneration &&
            isActiveProfileContractValid()

    private fun isReconciled(desiredGeneration: String): Boolean =
        isActivePresentationReconciled(desiredGeneration)

    private fun isProfileContractValid(): Boolean = isActiveProfileContractValid()

    private fun isActiveProfileContractValid(): Boolean = runCatching {
        if (!packageFile.isFile) return@runCatching false
        val profileJson = JSONObject(packageFile.readText(StandardCharsets.UTF_8))
        val dependencies = profileJson.optJSONObject("dependencies") ?: return@runCatching false
        val bundles = profileJson.optJSONObject("dsh")
            ?.optJSONObject("profile")
            ?.optJSONArray("bundles")
            ?: return@runCatching false

        dependencies.optString(contextSpec.packageName) == contextSpec.dependencyValue &&
            dependencies.optString(compatSpec.packageName) == compatSpec.dependencyValue &&
            dependencies.optString(uiSpec.packageName) == uiSpec.dependencyValue &&
            dependencies.optString(attachmentSourcesSpec.packageName) == attachmentSourcesSpec.dependencyValue &&
            dependencies.optString(tokyoThemeSpec.packageName) == tokyoThemeSpec.dependencyValue &&
            bundles.containsString(contextSpec.packageName) &&
            bundles.containsString(compatSpec.packageName) &&
            bundles.containsString(uiSpec.packageName) &&
            bundles.containsString(attachmentSourcesSpec.packageName) &&
            bundles.containsString(tokyoThemeSpec.packageName) &&
            packageVersion(File(profileDir, contextSpec.profileRelative)) == contextSpec.version &&
            packageVersion(File(profileDir, compatSpec.profileRelative)) == compatSpec.version &&
            packageVersion(File(profileDir, uiSpec.profileRelative)) == uiSpec.version &&
            packageVersion(File(profileDir, attachmentSourcesSpec.profileRelative)) == attachmentSourcesSpec.version &&
            packageVersion(File(profileDir, tokyoThemeSpec.profileRelative)) == tokyoThemeSpec.version &&
            packageVersion(File(layout.persistentDshHome, contextSpec.persistentRelative)) == contextSpec.version &&
            packageVersion(File(layout.persistentDshHome, compatSpec.persistentRelative)) == compatSpec.version &&
            packageVersion(File(layout.persistentDshHome, uiSpec.persistentRelative)) == uiSpec.version &&
            packageVersion(File(layout.persistentDshHome, attachmentSourcesSpec.persistentRelative)) == attachmentSourcesSpec.version &&
            packageVersion(File(layout.persistentDshHome, tokyoThemeSpec.persistentRelative)) == tokyoThemeSpec.version &&
            managedTreeMatches(contextSpec, File(profileDir, contextSpec.profileRelative)) &&
            managedTreeMatches(compatSpec, File(profileDir, compatSpec.profileRelative)) &&
            managedTreeMatches(uiSpec, File(profileDir, uiSpec.profileRelative)) &&
            managedTreeMatches(attachmentSourcesSpec, File(profileDir, attachmentSourcesSpec.profileRelative)) &&
            managedTreeMatches(tokyoThemeSpec, File(profileDir, tokyoThemeSpec.profileRelative)) &&
            managedTreeMatches(contextSpec, File(layout.persistentDshHome, contextSpec.persistentRelative)) &&
            managedTreeMatches(compatSpec, File(layout.persistentDshHome, compatSpec.persistentRelative)) &&
            managedTreeMatches(uiSpec, File(layout.persistentDshHome, uiSpec.persistentRelative)) &&
            managedTreeMatches(attachmentSourcesSpec, File(layout.persistentDshHome, attachmentSourcesSpec.persistentRelative)) &&
            managedTreeMatches(tokyoThemeSpec, File(layout.persistentDshHome, tokyoThemeSpec.persistentRelative))
    }.getOrDefault(false)

    private fun managedTreeMatches(spec: PluginSpec, directory: File): Boolean =
        runCatching { directoryTreeHash(directory, spec.assets) == assetTreeHash(spec) }.getOrDefault(false)

    private fun assetTreeHash(spec: PluginSpec): String = treeHash(spec.assets) { relative ->
        context.assets.open("${spec.assetRoot}/$relative").use { it.readBytes() }
    }

    private fun directoryTreeHash(directory: File, assets: List<String>): String = treeHash(assets) { relative ->
        val file = File(directory, relative)
        check(file.isFile) { "Managed presentation asset is missing: ${file.absolutePath}" }
        file.readBytes()
    }

    private fun treeHash(
        assets: List<String>,
        readBytes: (String) -> ByteArray,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        assets.sorted().forEach { relative ->
            digest.update(relative.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(readBytes(relative))
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

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

    private fun JSONArray.withoutString(value: String): JSONArray {
        val result = JSONArray()
        for (index in 0 until length()) {
            val item = opt(index)
            if (item is String && item == value) continue
            result.put(item)
        }
        return result
    }


    private fun File.readTextIfExists(): String? =
        if (isFile) readText(StandardCharsets.UTF_8).trim() else null
}
