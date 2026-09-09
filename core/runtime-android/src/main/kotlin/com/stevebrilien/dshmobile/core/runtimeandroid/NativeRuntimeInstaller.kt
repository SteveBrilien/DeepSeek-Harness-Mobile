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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class NativeRuntimeInstaller(
    context: Context,
    private val vault: RecoveryVault,
    private val stateStore: RuntimeStateStore,
) {
    companion object {
        private const val MOBILE_CONTEXT_PLUGIN_VERSION = "0.2.1"
        private const val PROBE_BYTES = 64 * 1024
        private val MOBILE_CONTEXT_PLUGIN_ASSETS = listOf(
            "package.json",
            "cordis.patch.yml",
            "lib/index.js",
        )
    }

    private data class MirrorProbe(
        val id: String,
        val name: String,
        val bytesPerSecond: Long,
        val elapsedMillis: Long,
        val ok: Boolean,
        val error: String? = null,
    )

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

    fun inspectResources(): RuntimeResourceInventory {
        stateStore.ensureLayout()
        val state = stateStore.read()
        val manifest = state.activeSlot
            ?.let(layout::slotManifest)
            ?.takeIf(File::isFile)
            ?.let { runCatching { JSONObject(it.readText(StandardCharsets.UTF_8)) }.getOrNull() }
        val installedAlpine = manifest?.optString("alpineVersion")?.takeIf { it.isNotBlank() }
        val installedDsh = manifest?.optString("dshVersion")?.takeIf { it.isNotBlank() }
        val localArchive = File(layout.downloadsDir, RuntimePins.ALPINE_ROOTFS_FILE)
        val persistentArchive = persistentRootfsCacheFile()
        val localCacheOk = localArchive.isFile && runCatching { sha256(localArchive) == RuntimePins.ALPINE_ROOTFS_SHA256 }.getOrDefault(false)
        val persistentCacheOk = persistentArchive.isFile && runCatching { sha256(persistentArchive) == RuntimePins.ALPINE_ROOTFS_SHA256 }.getOrDefault(false)
        val activeRootfsReady = state.activeSlot?.let { File(layout.rootfs(it), "bin/sh").isFile } == true
        val comparison = installedDsh?.let { compareLooseVersions(it, RuntimePins.DSH_VERSION) } ?: -1
        return RuntimeResourceInventory(
            activeSlot = state.activeSlot,
            installedAlpineVersion = installedAlpine,
            installedDshVersion = installedDsh,
            targetAlpineVersion = RuntimePins.ALPINE_VERSION,
            targetDshVersion = RuntimePins.DSH_VERSION,
            cachedRootfsAvailable = localCacheOk,
            persistentCachedRootfsAvailable = persistentCacheOk,
            reusableInstalledRuntime = state.activeSlot != null && activeRootfsReady && installedDsh != null,
            updateAvailable = state.activeSlot != null && activeRootfsReady && installedDsh != null && comparison < 0,
            installedVersionIsNewer = state.activeSlot != null && activeRootfsReady && installedDsh != null && comparison > 0,
        )
    }

    fun stage(
        slot: RuntimeSlot,
        preferredSourceId: String = "auto",
        progress: (RuntimeInstallProgress) -> Unit = {},
    ) {
        ensureNativeLauncher()
        progress(RuntimeInstallProgress("discover", "检测本机已有 Runtime 与缓存", 2))
        val reusableArchive = findReusableRootfsArchive(progress)
        val alpineMirror = selectAlpineMirror(preferredSourceId, progress)
        val archive = reusableArchive ?: downloadRootfsArchive(alpineMirror, progress)
        val slotDir = layout.slotRoot(slot)
        val staged = File(slotDir.parentFile, ".${slotDir.name}.staging-${System.currentTimeMillis()}")
        if (staged.exists()) staged.deleteRecursively()
        check(staged.mkdirs()) { "Unable to create staging directory ${staged.absolutePath}" }
        val rootfs = File(staged, "rootfs")
        check(rootfs.mkdirs())

        try {
            progress(RuntimeInstallProgress("extract", "解压 Linux userspace", 31, sourceId = alpineMirror.id, sourceName = alpineMirror.name))
            extractRootfs(archive, rootfs)
            prepareDns(rootfs)
            configureAlpineRepositories(rootfs, alpineMirror)

            progress(RuntimeInstallProgress("packages", "安装 Runtime 基础软件包", 43, sourceId = alpineMirror.id, sourceName = alpineMirror.name))
            val packageCommand = "apk update && apk add --no-cache bash ca-certificates curl git openssh-client python3 nodejs npm"
            runCatching {
                runInsideRootfs(rootfs, packageCommand, timeoutMillis = 15 * 60_000L) { line ->
                    progress(RuntimeInstallProgress("packages", "安装 Runtime 基础软件包", 48, sourceId = alpineMirror.id, sourceName = alpineMirror.name, logLine = line))
                }
            }.recoverCatching { firstFailure ->
                val official = RuntimePins.ALPINE_MIRRORS.first { it.id == "official" }
                if (alpineMirror.id == official.id) throw firstFailure
                progress(RuntimeInstallProgress("packages", "当前镜像不可用，自动回退 Alpine 官方源", 46, sourceId = official.id, sourceName = official.name, logLine = firstFailure.message))
                configureAlpineRepositories(rootfs, official)
                runInsideRootfs(rootfs, packageCommand, timeoutMillis = 15 * 60_000L) { line ->
                    progress(RuntimeInstallProgress("packages", "从 Alpine 官方源继续安装", 50, sourceId = official.id, sourceName = official.name, logLine = line))
                }
            }.getOrThrow()

            var npmMirror = selectNpmMirror(progress)
            progress(RuntimeInstallProgress("pnpm", "安装 pnpm ${RuntimePins.PNPM_VERSION}", 61, sourceId = npmMirror.id, sourceName = npmMirror.name))
            runCatching {
                runInsideRootfs(
                    rootfs,
                    "NPM_CONFIG_REGISTRY=${shellQuote(npmMirror.registryUrl)} npm install -g pnpm@${RuntimePins.PNPM_VERSION}",
                    timeoutMillis = 15 * 60_000L,
                ) { line ->
                    progress(RuntimeInstallProgress("pnpm", "安装 pnpm ${RuntimePins.PNPM_VERSION}", 66, sourceId = npmMirror.id, sourceName = npmMirror.name, logLine = line))
                }
            }.recoverCatching { firstFailure ->
                val officialNpm = RuntimePins.NPM_MIRRORS.first { it.id == "npm-official" }
                if (npmMirror.id == officialNpm.id) throw firstFailure
                npmMirror = officialNpm
                progress(RuntimeInstallProgress("pnpm", "npm 镜像不可用，自动回退官方 registry", 63, sourceId = officialNpm.id, sourceName = officialNpm.name, logLine = firstFailure.message))
                runInsideRootfs(
                    rootfs,
                    "NPM_CONFIG_REGISTRY=${shellQuote(officialNpm.registryUrl)} npm install -g pnpm@${RuntimePins.PNPM_VERSION}",
                    timeoutMillis = 15 * 60_000L,
                ) { line ->
                    progress(RuntimeInstallProgress("pnpm", "从 npm 官方源继续安装", 67, sourceId = officialNpm.id, sourceName = officialNpm.name, logLine = line))
                }
            }.getOrThrow()

            progress(RuntimeInstallProgress("dsh", "安装 DSH ${RuntimePins.DSH_VERSION}", 72, sourceId = npmMirror.id, sourceName = npmMirror.name))
            fun installDshWith(registry: NpmMirror) {
                runInsideRootfs(
                    rootfs,
                    "mkdir -p /opt/dsh && cd /opt/dsh && " +
                        "printf '%s\\n' '{\"private\":true}' > package.json && " +
                        "PNPM_CONFIG_REGISTRY=${shellQuote(registry.registryUrl)} PNPM_CONFIG_AUTO_INSTALL_PEERS=true " +
                        "pnpm add @deepseek-ai/dsh@${RuntimePins.DSH_VERSION}",
                    timeoutMillis = 30 * 60_000L,
                ) { line ->
                    progress(RuntimeInstallProgress("dsh", "安装 DSH ${RuntimePins.DSH_VERSION}", 80, sourceId = registry.id, sourceName = registry.name, logLine = line))
                }
            }
            runCatching { installDshWith(npmMirror) }.recoverCatching { firstFailure ->
                val officialNpm = RuntimePins.NPM_MIRRORS.first { it.id == "npm-official" }
                if (npmMirror.id == officialNpm.id) throw firstFailure
                npmMirror = officialNpm
                progress(RuntimeInstallProgress("dsh", "DSH 下载源不可用，自动回退 npm 官方源", 74, sourceId = officialNpm.id, sourceName = officialNpm.name, logLine = firstFailure.message))
                installDshWith(officialNpm)
            }.getOrThrow()

            progress(RuntimeInstallProgress("integration", "安装 DSH Mobile 环境集成", 88))
            ensureMobileContextIntegrationForRootfs(rootfs)
            progress(RuntimeInstallProgress("verify", "校验本地 Runtime", 94))
            val versions = runInsideRootfs(
                rootfs,
                "node --version && npm --version && " +
                    "node /opt/dsh/node_modules/@deepseek-ai/dsh/lib/bin.js --version",
                timeoutMillis = 60_000L,
            ) { line ->
                progress(RuntimeInstallProgress("verify", "校验本地 Runtime", 96, logLine = line))
            }
            check(versions.contains(RuntimePins.DSH_VERSION)) { "DSH verification did not report pinned version" }
            val manifest = runtimeManifest(slot)
            File(staged, "runtime.json").writeText(manifest.toString(2), StandardCharsets.UTF_8)

            if (slotDir.exists()) slotDir.deleteRecursively()
            check(staged.renameTo(slotDir)) { "Unable to atomically activate staged slot directory" }
            writePersistentRuntimeLock(slot)
            progress(RuntimeInstallProgress("staged", "Runtime slot ${slot.name} 已准备完成", 98))
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

    private fun findReusableRootfsArchive(progress: (RuntimeInstallProgress) -> Unit): File? {
        stateStore.ensureLayout()
        val target = File(layout.downloadsDir, RuntimePins.ALPINE_ROOTFS_FILE)
        if (target.isFile && runCatching { sha256(target) == RuntimePins.ALPINE_ROOTFS_SHA256 }.getOrDefault(false)) {
            progress(RuntimeInstallProgress("reuse", "发现本机已缓存 Alpine ${RuntimePins.ALPINE_VERSION}，直接复用", 8, logLine = target.absolutePath))
            return target
        }
        if (target.exists()) target.delete()

        val persistent = persistentRootfsCacheFile()
        if (persistent.isFile && runCatching { sha256(persistent) == RuntimePins.ALPINE_ROOTFS_SHA256 }.getOrDefault(false)) {
            progress(RuntimeInstallProgress("reuse", "发现恢复保险库中的 Alpine 缓存，正在复用", 8, logLine = persistent.absolutePath))
            val tmp = File(layout.downloadsDir, ".${target.name}.restore")
            if (tmp.exists()) tmp.delete()
            persistent.inputStream().use { input -> FileOutputStream(tmp).use { output -> input.copyTo(output) } }
            check(sha256(tmp) == RuntimePins.ALPINE_ROOTFS_SHA256)
            if (target.exists()) target.delete()
            check(tmp.renameTo(target)) { "Unable to restore cached rootfs" }
            return target
        }
        return null
    }

    private fun downloadRootfsArchive(
        mirror: AlpineMirror,
        progress: (RuntimeInstallProgress) -> Unit,
    ): File {
        stateStore.ensureLayout()
        val target = File(layout.downloadsDir, RuntimePins.ALPINE_ROOTFS_FILE)
        val tmp = File(layout.downloadsDir, ".${target.name}.download")
        if (tmp.exists()) tmp.delete()
        val url = RuntimePins.alpineRootfsUrl(mirror)
        progress(RuntimeInstallProgress("download", "从 ${mirror.name} 下载 Alpine ${RuntimePins.ALPINE_VERSION}", 9, sourceId = mirror.id, sourceName = mirror.name, logLine = url))
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept-Encoding", "identity")
        }
        try {
            check(connection.responseCode in 200..299) {
                "Rootfs download failed with HTTP ${connection.responseCode}"
            }
            val total = connection.contentLengthLong.takeIf { it > 0 }
            var downloaded = 0L
            var lastReportAt = 0L
            connection.inputStream.use { input ->
                FileOutputStream(tmp).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastReportAt >= 250L || (total != null && downloaded >= total)) {
                            lastReportAt = now
                            val percent = total?.let { (9 + (downloaded * 20L / it).toInt()).coerceIn(9, 29) } ?: 18
                            progress(
                                RuntimeInstallProgress(
                                    phase = "download",
                                    message = "正在下载 Alpine ${RuntimePins.ALPINE_VERSION}",
                                    percent = percent,
                                    downloadedBytes = downloaded,
                                    totalBytes = total,
                                    sourceId = mirror.id,
                                    sourceName = mirror.name,
                                ),
                            )
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        check(sha256(tmp) == RuntimePins.ALPINE_ROOTFS_SHA256) { "Alpine rootfs SHA-256 mismatch" }
        if (target.exists()) target.delete()
        check(tmp.renameTo(target)) { "Unable to finalize rootfs download" }
        cacheRootfsPersistentlyBestEffort(target, progress)
        return target
    }

    private fun cacheRootfsPersistentlyBestEffort(
        source: File,
        progress: (RuntimeInstallProgress) -> Unit,
    ) {
        runCatching {
            vault.ensureLayout().getOrThrow()
            val target = persistentRootfsCacheFile()
            target.parentFile?.let { check(it.exists() || it.mkdirs()) }
            if (target.isFile && sha256(target) == RuntimePins.ALPINE_ROOTFS_SHA256) return
            val tmp = File(target.parentFile, ".${target.name}.tmp")
            if (tmp.exists()) tmp.delete()
            source.inputStream().use { input -> FileOutputStream(tmp).use { output -> input.copyTo(output) } }
            check(sha256(tmp) == RuntimePins.ALPINE_ROOTFS_SHA256)
            if (target.exists()) target.delete()
            check(tmp.renameTo(target))
            progress(RuntimeInstallProgress("cache", "已将 Alpine 安装资源写入恢复保险库，可供重装复用", 30, logLine = target.absolutePath))
        }
    }

    private fun persistentRootfsCacheFile(): File =
        File(vault.status().root, "Recovery/Runtime/cache/${RuntimePins.ALPINE_ROOTFS_FILE}")

    private fun selectAlpineMirror(
        preferredSourceId: String,
        progress: (RuntimeInstallProgress) -> Unit,
    ): AlpineMirror {
        val requested = RuntimePins.ALPINE_MIRRORS.firstOrNull { it.id == preferredSourceId }
        if (requested != null) {
            progress(RuntimeInstallProgress("source", "使用手动选择的下载源：${requested.name}", 4, sourceId = requested.id, sourceName = requested.name))
            return requested
        }

        progress(RuntimeInstallProgress("source", "正在测速并选择 Alpine 下载源", 3))
        val executor = Executors.newFixedThreadPool(RuntimePins.ALPINE_MIRRORS.size.coerceAtMost(4))
        return try {
            val futures = RuntimePins.ALPINE_MIRRORS.associateWith { mirror ->
                executor.submit<MirrorProbe> { probeUrl(mirror.id, mirror.name, RuntimePins.alpineRootfsUrl(mirror), PROBE_BYTES) }
            }
            val results = futures.map { (mirror, future) ->
                mirror to runCatching { future.get(8, TimeUnit.SECONDS) }
                    .getOrElse { MirrorProbe(mirror.id, mirror.name, 0, 8_000, false, it.message) }
            }
            results.forEach { (_, probe) ->
                val detail = if (probe.ok) {
                    "${probe.name}: ${formatRate(probe.bytesPerSecond)} · ${probe.elapsedMillis} ms"
                } else {
                    "${probe.name}: 不可用${probe.error?.let { " · $it" }.orEmpty()}"
                }
                progress(RuntimeInstallProgress("source", "下载源测速", 4, sourceId = probe.id, sourceName = probe.name, logLine = detail))
            }
            val best = results.filter { it.second.ok }
                .maxByOrNull { it.second.bytesPerSecond }
                ?.first
                ?: RuntimePins.ALPINE_MIRRORS.first { it.id == "official" }
            progress(RuntimeInstallProgress("source", "已自动选择：${best.name}", 5, sourceId = best.id, sourceName = best.name))
            best
        } finally {
            executor.shutdownNow()
        }
    }

    private fun selectNpmMirror(progress: (RuntimeInstallProgress) -> Unit): NpmMirror {
        progress(RuntimeInstallProgress("source", "正在测速 npm registry", 57))
        val executor = Executors.newFixedThreadPool(RuntimePins.NPM_MIRRORS.size)
        return try {
            val futures = RuntimePins.NPM_MIRRORS.associateWith { mirror ->
                executor.submit<MirrorProbe> { probeUrl(mirror.id, mirror.name, "${mirror.registryUrl}/pnpm/latest", 16 * 1024) }
            }
            val results = futures.map { (mirror, future) ->
                mirror to runCatching { future.get(6, TimeUnit.SECONDS) }
                    .getOrElse { MirrorProbe(mirror.id, mirror.name, 0, 6_000, false, it.message) }
            }
            results.forEach { (_, probe) ->
                val detail = if (probe.ok) "${probe.name}: ${formatRate(probe.bytesPerSecond)} · ${probe.elapsedMillis} ms" else "${probe.name}: 不可用"
                progress(RuntimeInstallProgress("source", "npm 源测速", 59, sourceId = probe.id, sourceName = probe.name, logLine = detail))
            }
            results.filter { it.second.ok }
                .maxByOrNull { it.second.bytesPerSecond }
                ?.first
                ?: RuntimePins.NPM_MIRRORS.first { it.id == "npm-official" }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun probeUrl(id: String, name: String, url: String, maxBytes: Int): MirrorProbe {
        val started = System.nanoTime()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4_000
            readTimeout = 5_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Range", "bytes=0-${maxBytes - 1}")
            setRequestProperty("Accept-Encoding", "identity")
            useCaches = false
        }
        return try {
            val code = connection.responseCode
            check(code in 200..299 || code == HttpURLConnection.HTTP_PARTIAL) { "HTTP $code" }
            var bytes = 0
            connection.inputStream.use { input ->
                val buffer = ByteArray(8 * 1024)
                while (bytes < maxBytes) {
                    val read = input.read(buffer, 0, minOf(buffer.size, maxBytes - bytes))
                    if (read < 0) break
                    bytes += read
                }
            }
            val elapsed = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(1L)
            val rate = bytes.toLong() * 1_000L / elapsed
            MirrorProbe(id, name, rate, elapsed, bytes > 0)
        } catch (t: Throwable) {
            val elapsed = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(1L)
            MirrorProbe(id, name, 0, elapsed, false, t.message)
        } finally {
            connection.disconnect()
        }
    }

    private fun configureAlpineRepositories(rootfs: File, mirror: AlpineMirror) {
        val repositories = File(rootfs, "etc/apk/repositories")
        repositories.parentFile?.let { check(it.exists() || it.mkdirs()) }
        repositories.writeText(
            "${mirror.baseUrl}/v3.24/main\n${mirror.baseUrl}/v3.24/community\n",
            StandardCharsets.UTF_8,
        )
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
                            else -> Unit
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

    private fun runInsideRootfs(
        rootfs: File,
        command: String,
        timeoutMillis: Long,
        onOutputLine: (String) -> Unit = {},
    ): String {
        val process = buildProcessForRootfs(rootfs, command)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val reader = Thread {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (output.length < 512_000) output.appendLine(line)
                    runCatching { onOutputLine(line.take(500)) }
                }
            }
        }.apply { start() }
        val completed = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!completed) {
            process.destroy()
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
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

    private fun compareLooseVersions(left: String, right: String): Int {
        val l = Regex("\\d+").findAll(left).map { it.value.toIntOrNull() ?: 0 }.toList()
        val r = Regex("\\d+").findAll(right).map { it.value.toIntOrNull() ?: 0 }.toList()
        val count = maxOf(l.size, r.size)
        repeat(count) { index ->
            val a = l.getOrElse(index) { 0 }
            val b = r.getOrElse(index) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return left.compareTo(right)
    }

    private fun formatRate(bytesPerSecond: Long): String = when {
        bytesPerSecond >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSecond / 1024.0 / 1024.0)
        bytesPerSecond >= 1024L -> String.format(java.util.Locale.US, "%.0f KB/s", bytesPerSecond / 1024.0)
        else -> "$bytesPerSecond B/s"
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun File.readTextIfExists(): String? =
        if (isFile) readText(StandardCharsets.UTF_8).trim() else null
}
