package com.stevebrilien.dshmobile.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.FileObserver
import android.os.IBinder
import com.stevebrilien.dshmobile.MainActivity
import com.stevebrilien.dshmobile.core.recovery.RecoveryBackupManager
import com.stevebrilien.dshmobile.core.recovery.SecretVaultManager
import com.stevebrilien.dshmobile.core.runtimeandroid.RuntimeControlPlane
import com.stevebrilien.dshmobile.core.runtimeandroid.RuntimeInstallProgress
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class RuntimeForegroundService : Service() {
    companion object {
        private const val CHANNEL_ID = "dsh_runtime"
        private const val NOTIFICATION_ID = 3108
        private const val EXTRA_SOURCE_ID = "runtime_source_id"

        const val ACTION_INSTALL = "com.stevebrilien.dshmobile.runtime.INSTALL"
        const val ACTION_START = "com.stevebrilien.dshmobile.runtime.START"
        const val ACTION_STOP = "com.stevebrilien.dshmobile.runtime.STOP"
        const val ACTION_ROLLBACK = "com.stevebrilien.dshmobile.runtime.ROLLBACK"

        fun dispatch(context: Context, action: String, preferredSourceId: String? = null) {
            val intent = Intent(context, RuntimeForegroundService::class.java).setAction(action)
            preferredSourceId?.let { intent.putExtra(EXTRA_SOURCE_ID, it) }
            context.startForegroundService(intent)
        }
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private lateinit var control: RuntimeControlPlane
    private lateinit var secretVault: SecretVaultManager
    private lateinit var recoveryBackups: RecoveryBackupManager
    private lateinit var telemetry: RuntimeInstallTelemetry
    private lateinit var credentialFile: File
    private lateinit var sshDir: File
    private var credentialObserver: FileObserver? = null
    private var sshObserver: FileObserver? = null

    override fun onCreate() {
        super.onCreate()
        control = RuntimeControlPlane(applicationContext)
        secretVault = SecretVaultManager(applicationContext)
        recoveryBackups = RecoveryBackupManager(applicationContext)
        telemetry = RuntimeInstallTelemetry(applicationContext)
        credentialFile = File(filesDir, "persistent/dsh-home/.credentials.yaml")
        credentialFile.parentFile?.let { it.mkdirs() }
        sshDir = File(filesDir, "persistent/dsh-home/.ssh")
        restorePersistentDshHomeBestEffort()
        restoreCredentialsBestEffort()
        restoreSshIdentityBestEffort()
        startCredentialObserver()
        startSshObserver()
        if (credentialFile.isFile) backupCredentialsBestEffort()
        if (sshDir.listFiles()?.isNotEmpty() == true) backupSshIdentityBestEffort()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val recoveredSnapshot = telemetry.snapshot()
        val action = intent?.action ?: if (recoveredSnapshot.running && !recoveredSnapshot.runtimeInstalled) {
            ACTION_INSTALL
        } else {
            ACTION_START
        }
        val preferredSourceId = intent?.getStringExtra(EXTRA_SOURCE_ID)
            ?: recoveredSnapshot.sourceId?.takeIf { it.isNotBlank() }
            ?: "auto"
        startForeground(NOTIFICATION_ID, notification("本地 Runtime 服务已启动"))
        if (!busy.compareAndSet(false, true)) {
            updateNotification("已有 Runtime 操作正在进行", telemetry.snapshot())
            return START_STICKY
        }

        executor.execute {
            try {
                when (action) {
                    ACTION_INSTALL -> installAndStart(preferredSourceId)
                    ACTION_START -> startRuntime()
                    ACTION_STOP -> {
                        runResult("正在停止 DSH Runtime", "DSH Runtime 已停止") { control.stop() }
                        backupUserStateBestEffort()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    ACTION_ROLLBACK -> runResult("正在回滚 Runtime", "已恢复上一 Runtime slot") { control.rollback() }
                    else -> updateNotification("未知 Runtime 操作")
                }
            } catch (t: Throwable) {
                when (action) {
                    ACTION_INSTALL -> runCatching { telemetry.fail(t) }
                    ACTION_START -> {
                        val runtimeInstalled = runCatching { control.inspectResources().reusableInstalledRuntime }
                            .getOrDefault(telemetry.snapshot().runtimeInstalled)
                        runCatching { telemetry.fail(t, runtimeInstalled = runtimeInstalled) }
                    }
                }
                val snapshot = if (action == ACTION_INSTALL || action == ACTION_START) telemetry.snapshot() else null
                updateNotification("Runtime 操作失败：${shortMessage(t)}", snapshot)
            } finally {
                busy.set(false)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        credentialObserver?.stopWatching()
        credentialObserver = null
        sshObserver?.stopWatching()
        sshObserver = null
        backupCredentialsBestEffort()
        backupSshIdentityBestEffort()
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun installAndStart(preferredSourceId: String) {
        runCatching { telemetry.begin(preferredSourceId) }
        updateNotification("正在准备 Runtime 安装", telemetry.snapshot())
        var lastNotificationAt = 0L
        var lastPhase = ""
        val install = control.installDefault(preferredSourceId) { progress ->
            runCatching { telemetry.update(progress) }
            val now = System.currentTimeMillis()
            if (progress.phase != lastPhase || now - lastNotificationAt >= 500L) {
                lastPhase = progress.phase
                lastNotificationAt = now
                updateNotification(progress.message, telemetry.snapshot())
            }
        }
        install.onFailure {
            runCatching { telemetry.fail(it) }
            updateNotification("Runtime 安装失败：${shortMessage(it)}", telemetry.snapshot())
            return
        }
        runCatching { telemetry.beginRuntimeStart("Runtime 已安装，正在启动 DSH") }
        updateNotification("Runtime 已安装到 slot ${install.getOrNull()?.name}，正在启动 DSH", telemetry.snapshot())
        val started = control.start { progress ->
            runCatching { telemetry.updateRuntimeStart(progress) }
            updateNotification(progress.message, telemetry.snapshot())
        }
        started.onSuccess {
            runCatching { telemetry.succeed("DSH Runtime 已安装并运行") }
            updateNotification("DSH Runtime 已安装并运行", telemetry.snapshot())
        }.onFailure {
            runCatching { telemetry.appendDiagnostic("DSH 启动日志", control.logTail(16_000)) }
            runCatching { telemetry.fail(it, runtimeInstalled = true) }
            updateNotification("Runtime 已安装，DSH 启动未就绪：${shortMessage(it)}", telemetry.snapshot())
        }
    }

    private fun startRuntime() {
        val inventory = runCatching { control.inspectResources() }.getOrElse { failure ->
            runCatching { telemetry.beginRuntimeStart("无法读取 Runtime 磁盘状态", runtimeInstalled = false) }
            runCatching { telemetry.fail(failure, runtimeInstalled = false) }
            updateNotification("Runtime 状态读取失败：${shortMessage(failure)}", telemetry.snapshot())
            return
        }
        val runtimePresent = inventory.activeSlot != null && inventory.reusableInstalledRuntime
        if (!runtimePresent) {
            val failure = IllegalStateException("尚未安装可复用的本地 Runtime")
            runCatching { telemetry.beginRuntimeStart("尚未安装本地 Runtime", runtimeInstalled = false) }
            runCatching { telemetry.fail(failure, runtimeInstalled = false) }
            updateNotification("尚未安装本地 Runtime", telemetry.snapshot())
            return
        }

        runCatching { telemetry.beginRuntimeStart("正在启动 DSH") }
        updateNotification("正在启动 DSH Runtime", telemetry.snapshot())
        control.start { progress ->
            runCatching { telemetry.updateRuntimeStart(progress) }
            updateNotification(progress.message, telemetry.snapshot())
        }.onSuccess {
            runCatching { telemetry.succeed("DSH Runtime 已运行") }
            updateNotification("DSH Runtime 正在运行", telemetry.snapshot())
        }.onFailure {
            runCatching { telemetry.appendDiagnostic("DSH 启动日志", control.logTail(16_000)) }
            runCatching { telemetry.fail(it, runtimeInstalled = true) }
            updateNotification(
                "Runtime 已安装，DSH 启动未就绪：${shortMessage(it)}",
                telemetry.snapshot(),
            )
        }
    }

    private fun runResult(
        working: String,
        success: String,
        operation: () -> Result<Unit>,
    ) {
        updateNotification(working)
        operation().onSuccess { updateNotification(success) }
            .onFailure { updateNotification("Runtime 错误：${shortMessage(it)}") }
    }

    private fun shortMessage(t: Throwable): String =
        (t.message ?: t::class.java.simpleName).lineSequence().firstOrNull()?.take(160) ?: "未知错误"

    private fun startCredentialObserver() {
        val parent = credentialFile.parentFile ?: return
        val mask = FileObserver.CLOSE_WRITE or FileObserver.CREATE or FileObserver.MOVED_TO
        credentialObserver?.stopWatching()
        credentialObserver = object : FileObserver(parent.absolutePath, mask) {
            override fun onEvent(event: Int, path: String?) {
                if (path != credentialFile.name) return
                runCatching { executor.execute { backupCredentialsBestEffort() } }
            }
        }.also { it.startWatching() }
    }

    private fun startSshObserver() {
        if (!sshDir.exists()) sshDir.mkdirs()
        val mask = FileObserver.CLOSE_WRITE or FileObserver.CREATE or FileObserver.MOVED_TO or
            FileObserver.DELETE or FileObserver.MOVED_FROM
        sshObserver?.stopWatching()
        sshObserver = object : FileObserver(sshDir.absolutePath, mask) {
            override fun onEvent(event: Int, path: String?) {
                if (path.isNullOrBlank()) return
                runCatching { executor.execute { backupSshIdentityBestEffort() } }
            }
        }.also { it.startWatching() }
    }

    private fun restorePersistentDshHomeBestEffort() {
        runCatching {
            val home = File(filesDir, "persistent/dsh-home")
            val meaningful = home.walkTopDown().any { it.isFile && it.name != ".credentials.yaml" }
            if (!meaningful) recoveryBackups.restorePersistentDshHomeFromLatest().getOrNull()
        }
    }

    private fun restoreSshIdentityBestEffort() {
        runCatching {
            val status = secretVault.status()
            val hasLocalIdentity = sshDir.isDirectory && sshDir.listFiles()?.isNotEmpty() == true
            if (!hasLocalIdentity && status.configured && status.deviceUnlocked && status.encryptedSshIdentityPresent) {
                secretVault.restoreSshIdentity(sshDir).getOrThrow()
            }
        }
    }

    private fun restoreCredentialsBestEffort() {
        runCatching {
            val status = secretVault.status()
            if (!credentialFile.exists() && status.configured && status.deviceUnlocked && status.encryptedCredentialsPresent) {
                secretVault.restoreDshCredentials(credentialFile).getOrThrow()
            }
        }
    }

    private fun backupSshIdentityBestEffort() {
        runCatching {
            val status = secretVault.status()
            if (sshDir.isDirectory && sshDir.walkTopDown().any { it.isFile } && status.configured && status.deviceUnlocked) {
                secretVault.backupSshIdentity(sshDir).getOrThrow()
            }
        }
    }

    private fun backupCredentialsBestEffort() {
        runCatching {
            val status = secretVault.status()
            if (credentialFile.isFile && status.configured && status.deviceUnlocked) {
                secretVault.backupDshCredentials(credentialFile).getOrThrow()
            }
        }
    }

    private fun backupUserStateBestEffort() {
        backupCredentialsBestEffort()
        backupSshIdentityBestEffort()
        runCatching { recoveryBackups.createCheckpoint() }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "DSH 本地 Runtime",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "保持手机本地 DeepSeek Harness Runtime 可用，并同步安装进度。"
                setShowBadge(false)
            },
        )
    }

    private fun updateNotification(text: String, snapshot: RuntimeInstallSnapshot? = null) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(text, snapshot))
    }

    private fun notification(text: String, snapshot: RuntimeInstallSnapshot? = null): Notification {
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("DeepSeek Harness Mobile")
            .setContentText(text)
            .setContentIntent(launch)
            .setOngoing(snapshot?.running != false)
            .setOnlyAlertOnce(true)
        snapshot?.percent?.let { builder.setProgress(100, it.coerceIn(0, 100), false) }
        snapshot?.let {
            val parts = buildList {
                if (!it.sourceName.isNullOrBlank()) add(it.sourceName)
                if (it.elapsedMillis > 0L) add("已用 ${formatDuration(it.elapsedMillis)}")
                it.etaMillis?.takeIf { eta -> eta > 0L }?.let { eta -> add("预计剩余 ${formatDuration(eta)}") }
            }
            if (parts.isNotEmpty()) builder.setSubText(parts.joinToString(" · "))
        }
        return builder.build()
    }

    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000L).coerceAtLeast(0L)
        val minutes = seconds / 60L
        val rest = seconds % 60L
        return if (minutes > 0L) String.format(Locale.US, "%d:%02d", minutes, rest) else "${rest}s"
    }
}
