package com.stevebrilien.dshmobile.core.runtimeandroid

import android.content.Context
import android.system.Os
import com.stevebrilien.dshmobile.core.recovery.RecoveryVault
import com.stevebrilien.dshmobile.core.runtimeapi.RuntimeSlot
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal class NativeRuntimeInstaller(
    context: Context,
    private val vault: RecoveryVault,
    private val stateStore: RuntimeStateStore,
) {
    companion object {
        private const val MOBILE_CONTEXT_PLUGIN_VERSION = "0.2.1"
        private val MOBILE_CONTEXT_PLUGIN_ASSETS = listOf(
            "package.json",
            "cordis.patch.yml",
            "lib/index.js",
        )
    }

    private val appContext = context.applicationContext
    private val layout get() = stateStore.layout

    fun ensureNativeLauncher() {
        stateStore.ensureLayout()
        RuntimePins.nativeAssets.forEach { (name, expectedSha) ->
            val destination = File(layout.nativeDir, name)
            if (!destination.exists() || sha256(destination) != expectedSha) {
                val tmp = File(layout.nativeDir, ".$name.tmp")
                appContext.assets.open("runtime/native/aarch64/$name").use { input ->
                    FileOutputStream(tmp).use { output -> input.copyTo(output) }
                }
                check(sha256(tmp) == expectedSha) { "Native runtime asset checksum mismatch: $name" }
                if (destination.exists()) check(destination.delete())
                check(tmp.renameTo(destination)) { "Unable to install native runtime asset: $name" }
            }
            val executable = name == "proot" || name == "loader"
            check(destination.setReadable(true, true)) { "Unable to mark $name readable" }
            if (executable) check(destination.setExecutable(true, true)) { "Unable to mark $name executable" }
        }
    }

    fun stage(slot: RuntimeSlot, progress: (String) -> Unit = {}) {
        ensureNativeLauncher()
        progress("Downloading Alpine ${RuntimePins.ALPINE_VERSION}")
        val archive = obtainRootfsArchive()
        val slotDir = layout.slotRoot(slot)
        val staged = File(slotDir.parentFile, ".${slotDir.name}.staging-${System.currentTimeMillis()}")
        if (staged.exists()) staged.deleteRecursively()
        check(staged.mkdirs()) { "Unable to create staging directory ${staged.absolutePath}" }
        val rootfs = File(staged, "rootfs")
        check(rootfs.mkdirs())

        try {
            progress("Extracting Linux userspace")
            extractRootfs(archive, rootfs)
            prepareDns(rootfs)
            progress("Installing runtime packages")
            runInsideRootfs(
                rootfs,
                "apk update && apk add --no-cache bash ca-certificates curl git openssh-client python3 nodejs npm",
                timeoutMillis = 15 * 60_000L,
            )
            progress("Installing pnpm ${RuntimePins.PNPM_VERSION}")
            runInsideRootfs(
                rootfs,
                "npm install -g pnpm@${RuntimePins.PNPM_VERSION}",
                timeoutMillis = 15 * 60_000L,
            )
            progress("Installing DSH ${RuntimePins.DSH_VERSION}")
            runInsideRootfs(
                rootfs,
                "mkdir -p /opt/dsh && cd /opt/dsh && " +
                    "printf '%s\\n' '{\"private\":true}' > package.json && " +
                    "PNPM_CONFIG_AUTO_INSTALL_PEERS=true pnpm add @deepseek-ai/dsh@${RuntimePins.DSH_VERSION}",
                timeoutMillis = 30 * 60_000L,
            )
            progress("Installing DSH Mobile context integration")
            ensureMobileContextIntegrationForRootfs(rootfs)
            progress("Verifying runtime")
            val versions = runInsideRootfs(
                rootfs,
                "node --version && npm --version && " +
                    "node /opt/dsh/node_modules/@deepseek-ai/dsh/lib/bin.js --version",
                timeoutMillis = 60_000L,
            )
            check(versions.contains(RuntimePins.DSH_VERSION)) { "DSH verification did not report pinned version" }
            val manifest = runtimeManifest(slot)
            File(staged, "runtime.json").writeText(manifest.toString(2), StandardCharsets.UTF_8)

            if (slotDir.exists()) slotDir.deleteRecursively()
            check(staged.renameTo(slotDir)) { "Unable to atomically activate staged slot directory" }
            writePersistentRuntimeLock(slot)
            progress("Runtime slot ${slot.name} staged")
        } catch (t: Throwable) {
            staged.deleteRecursively()
            throw t
        }
    }

    fun verify(slot: RuntimeSlot): String {
        ensureNativeLauncher()
        val rootfs = layout.rootfs(slot)
        check(rootfs.isDirectory) { "Runtime slot ${slot.name} is not installed" }
        val manifestFile = layout.slotManifest(slot)
        check(manifestFile.isFile) { "Runtime slot ${slot.name} has no manifest" }
        val manifest = JSONObject(manifestFile.readText(StandardCharsets.UTF_8))
        check(manifest.optInt("schemaVersion", -1) == RuntimePins.RUNTIME_MANIFEST_VERSION)
        check(manifest.optString("alpineVersion") == RuntimePins.ALPINE_VERSION)
        check(manifest.optString("dshVersion") == RuntimePins.DSH_VERSION)
        return runInsideRootfs(
            rootfs,
            "test -x /usr/bin/node && test -f /opt/dsh/node_modules/@deepseek-ai/dsh/lib/bin.js && " +
                "node /opt/dsh/node_modules/@deepseek-ai/dsh/lib/bin.js --version",
            timeoutMillis = 60_000L,
        )
    }

    fun buildProcess(slot: RuntimeSlot, shellCommand: String): ProcessBuilder {
        ensureNativeLauncher()
        return buildProcessForRootfs(layout.rootfs(slot), shellCommand)
    }

    fun ensureMobileContextIntegration(slot: RuntimeSlot) {
        ensureNativeLauncher()
        ensureMobileContextIntegrationForRootfs(layout.rootfs(slot))
    }

    private fun ensureMobileContextIntegrationForRootfs(rootfs: File) {
        check(rootfs.isDirectory) { "Runtime rootfs is missing: ${rootfs.absolutePath}" }
        val pluginDir = File(layout.persistentDshHome, "mobile-plugins/dsh-mobile-context")
        MOBILE_CONTEXT_PLUGIN_ASSETS.forEach { relative ->
            val destination = File(pluginDir, relative)
            destination.parentFile?.let { check(it.exists() || it.mkdirs()) }
            appContext.assets.open("runtime/dsh-mobile-context/$relative").use { input ->
                val tmp = File(destination.parentFile, ".${destination.name}.tmp")
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
                if (destination.exists()) check(destination.delete())
                check(tmp.renameTo(destination)) { "Unable to install DSH Mobile context asset: $relative" }
            }
        }

        val marker = File(layout.persistentDshHome, "mobile/context-plugin.version")
        if (marker.readTextIfExists() == MOBILE_CONTEXT_PLUGIN_VERSION) return
        runInsideRootfs(
            rootfs,
            "mkdir -p /dsh-home/mobile && " +
                "node /opt/dsh/node_modules/@deepseek-ai/dsh/lib/bin.js plugin --profile web add " +
                "file:/dsh-home/mobile-plugins/dsh-mobile-context",
            timeoutMillis = 10 * 60_000L,
        )
        marker.parentFile?.let { check(it.exists() || it.mkdirs()) }
        marker.writeText(MOBILE_CONTEXT_PLUGIN_VERSION, StandardCharsets.UTF_8)
    }

    private fun obtainRootfsArchive(): File {
        stateStore.ensureLayout()
        val target = File(layout.downloadsDir, "alpine-minirootfs-${RuntimePins.ALPINE_VERSION}-aarch64.tar.gz")
        if (target.exists() && sha256(target) == RuntimePins.ALPINE_ROOTFS_SHA256) return target
        if (target.exists()) target.delete()
        val tmp = File(layout.downloadsDir, ".${target.name}.download")
        if (tmp.exists()) tmp.delete()

        val connection = (URL(RuntimePins.ALPINE_ROOTFS_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            check(connection.responseCode in 200..299) {
                "Rootfs download failed with HTTP ${connection.responseCode}"
            }
            connection.inputStream.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        check(sha256(tmp) == RuntimePins.ALPINE_ROOTFS_SHA256) { "Alpine rootfs SHA-256 mismatch" }
        check(tmp.renameTo(target)) { "Unable to finalize rootfs download" }
        return target
    }

    private fun extractRootfs(archive: File, rootfs: File) {
        data class PendingHardLink(val destination: File, val targetName: String)
        val pendingHardLinks = mutableListOf<PendingHardLink>()
        val rootPath = rootfs.toPath().toAbsolutePath().normalize()

        BufferedInputStream(FileInputStream(archive)).use { buffered ->
            GzipCompressorInputStream(buffered).use { gzip ->
                TarArchiveInputStream(gzip).use { tar ->
                    while (true) {
                        val entry = tar.nextTarEntry ?: break
                        val cleanName = entry.name.removePrefix("./")
                        if (cleanName.isBlank()) continue
                        val destinationPath = rootPath.resolve(cleanName).normalize()
                        check(destinationPath.startsWith(rootPath)) { "Unsafe path in rootfs archive: ${entry.name}" }
                        val destination = destinationPath.toFile()

                        when {
                            entry.isDirectory -> check(destination.exists() || destination.mkdirs())
                            entry.isSymbolicLink -> {
                                destination.parentFile?.let { check(it.exists() || it.mkdirs()) }
                                if (destination.exists() || destination.isFile) destination.delete()
                                Os.symlink(entry.linkName, destination.absolutePath)
                            }
                            entry.isLink -> pendingHardLinks += PendingHardLink(destination, entry.linkName)
                            entry.isFile -> {
                                destination.parentFile?.let { check(it.exists() || it.mkdirs()) }
                                FileOutputStream(destination).use { output -> tar.copyTo(output) }
                                applyMode(destination, entry.mode)
                                if (entry.modTime != null) destination.setLastModified(entry.modTime.time)
                            }
                            else -> Unit // Device nodes/FIFOs are supplied by PRoot binds and are not materialized.
                        }
                    }
                }
            }
        }

        pendingHardLinks.forEach { link ->
            val targetPath = rootPath.resolve(link.targetName.removePrefix("./")).normalize()
            check(targetPath.startsWith(rootPath)) { "Unsafe hardlink target: ${link.targetName}" }
            link.destination.parentFile?.let { check(it.exists() || it.mkdirs()) }
            if (link.destination.exists()) link.destination.delete()
            Os.link(targetPath.toString(), link.destination.absolutePath)
        }
    }

    private fun applyMode(file: File, mode: Int) {
        file.setReadable(mode and 0b100_100_100 != 0, false)
        file.setWritable(mode and 0b010_010_010 != 0, true)
        file.setExecutable(mode and 0b001_001_001 != 0, false)
    }

    private fun prepareDns(rootfs: File) {
        val resolv = File(rootfs, "etc/resolv.conf")
        resolv.parentFile?.let { check(it.exists() || it.mkdirs()) }
        resolv.writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n", StandardCharsets.UTF_8)
    }

    private fun runInsideRootfs(rootfs: File, command: String, timeoutMillis: Long): String {
        val process = buildProcessForRootfs(rootfs, command)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val reader = Thread {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (output.length < 512_000) output.appendLine(line)
                }
            }
        }.apply { start() }
        val completed = process.waitFor(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!completed) {
            process.destroy()
            if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly()
            throw IllegalStateException("Runtime command timed out")
        }
        reader.join(2_000)
        check(process.exitValue() == 0) { "Runtime command failed (${process.exitValue()}):\n$output" }
        return output.toString()
    }

    private fun buildProcessForRootfs(rootfs: File, command: String): ProcessBuilder {
        check(rootfs.isDirectory) { "Rootfs is missing: ${rootfs.absolutePath}" }
        val proot = File(layout.nativeDir, "proot")
        val loader = File(layout.nativeDir, "loader")
        val vaultRoot = vault.status().root
        val dshHome = layout.persistentDshHome.apply {
            check(exists() || mkdirs()) { "Unable to create persistent DSH home" }
            setReadable(true, true)
            setWritable(true, true)
            setExecutable(true, true)
        }
        val args = mutableListOf(
            proot.absolutePath,
            "--link2symlink",
            "-0",
            "-r", rootfs.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/storage",
            "-b", "${dshHome.absolutePath}:/dsh-home",
        )
        if (vaultRoot.exists()) args += listOf("-b", "${vaultRoot.absolutePath}:/workspace")
        args += listOf(
            "-w", "/dsh-home",
            "/usr/bin/env", "-i",
            "HOME=/dsh-home",
            "USER=root",
            "LOGNAME=root",
            "LANG=C.UTF-8",
            "TERM=xterm-256color",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "DSH_HOME=/dsh-home",
            "/bin/sh", "-lc", command,
        )
        return ProcessBuilder(args).apply {
            directory(layout.root)
            environment().apply {
                clear()
                put("LD_LIBRARY_PATH", layout.nativeDir.absolutePath)
                put("PROOT_LOADER", loader.absolutePath)
                put("PROOT_TMP_DIR", layout.tmpDir.absolutePath)
                put("PROOT_NO_SECCOMP", "1")
                put("TMPDIR", layout.tmpDir.absolutePath)
            }
        }
    }

    private fun runtimeManifest(slot: RuntimeSlot): JSONObject = JSONObject()
        .put("schemaVersion", RuntimePins.RUNTIME_MANIFEST_VERSION)
        .put("slot", slot.name)
        .put("alpineVersion", RuntimePins.ALPINE_VERSION)
        .put("rootfsSha256", RuntimePins.ALPINE_ROOTFS_SHA256)
        .put("prootVersion", RuntimePins.PROOT_VERSION)
        .put("pnpmVersion", RuntimePins.PNPM_VERSION)
        .put("dshVersion", RuntimePins.DSH_VERSION)
        .put("createdAtEpochMillis", System.currentTimeMillis())

    private fun writePersistentRuntimeLock(slot: RuntimeSlot) {
        val recovery = vault.ensureLayout().getOrThrow()
        val lockFile = File(recovery.root, "Recovery/Runtime/runtime.lock")
        val json = runtimeManifest(slot)
            .put("rebuildable", true)
            .put("nativeAssetSha256", JSONObject(RuntimePins.nativeAssets))
        lockFile.parentFile?.let { check(it.exists() || it.mkdirs()) }
        lockFile.writeText(json.toString(2), StandardCharsets.UTF_8)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun File.readTextIfExists(): String? =
        if (isFile) readText(StandardCharsets.UTF_8).trim() else null
}
