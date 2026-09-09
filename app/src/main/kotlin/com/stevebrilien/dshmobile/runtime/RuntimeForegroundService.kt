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
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class RuntimeForegroundService : Service() {
    companion object {
        private const val CHANNEL_ID = "dsh_runtime"
        private const val NOTIFICATION_ID = 3108

        const val ACTION_INSTALL = "com.stevebrilien.dshmobile.runtime.INSTALL"
        const val ACTION_START = "com.stevebrilien.dshmobile.runtime.START"
        const val ACTION_STOP = "com.stevebrilien.dshmobile.runtime.STOP"
        const val ACTION_ROLLBACK = "com.stevebrilien.dshmobile.runtime.ROLLBACK"

        fun dispatch(context: Context, action: String) {
            val intent = Intent(context, RuntimeForegroundService::class.java).setAction(action)
            context.startForegroundService(intent)
        }
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private lateinit var control: RuntimeControlPlane
    private lateinit var secretVault: SecretVaultManager
    private lateinit var recoveryBackups: RecoveryBackupManager
    private lateinit var credentialFile: File
    private var credentialObserver: FileObserver? = null

    override fun onCreate() {
        super.onCreate()
        control = RuntimeControlPlane(applicationContext)
        secretVault = SecretVaultManager(applicationContext)
        recoveryBackups = RecoveryBackupManager(applicationContext)
        credentialFile = File(filesDir, "persistent/dsh-home/.credentials.yaml")
        credentialFile.parentFile?.let { it.mkdirs() }
        restorePersistentDshHomeBestEffort()
        restoreCredentialsBestEffort()
        startCredentialObserver()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        startForeground(NOTIFICATION_ID, notification("本地 Runtime 服务已启动"))
        if (!busy.compareAndSet(false, true)) {
            updateNotification("已有 Runtime 操作正在进行")
            return START_STICKY
        }

        executor.execute {
            try {
                when (action) {
                    ACTION_INSTALL -> installAndStart()
                    ACTION_START -> runResult("正在启动 DSH Runtime", "DSH Runtime 正在运行") { control.start() }
                    ACTION_STOP -> {
                        runResult("正在停止 DSH Runtime", "DSH Runtime 已停止") { control.stop() }
                        backupUserStateBestEffort()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    ACTION_ROLLBACK -> runResult("正在回滚 Runtime", "已恢复上一 Runtime slot") { control.rollback() }
                    else -> updateNotification("未知 Runtime 操作")
                }
            } finally {
                busy.set(false)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        credentialObserver?.stopWatching()
        credentialObserver = null
        backupCredentialsBestEffort()
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun installAndStart() {
        updateNotification("正在准备 Runtime 安装")
        val install = control.installDefault { stage -> updateNotification(stage) }
        install.onFailure {
            updateNotification("Runtime 安装失败：${shortMessage(it)}")
            return
        }
        updateNotification("Runtime 已安装到 slot ${install.getOrNull()?.name}，正在启动 DSH")
        runResult("正在启动 DSH Runtime", "DSH Runtime 已安装并运行") { control.start() }
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

    private fun restorePersistentDshHomeBestEffort() {
        runCatching {
            val home = File(filesDir, "persistent/dsh-home")
            val meaningful = home.walkTopDown().any { it.isFile && it.name != ".credentials.yaml" }
            if (!meaningful) recoveryBackups.restorePersistentDshHomeFromLatest().getOrNull()
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
                description = "保持手机本地 DeepSeek Harness Runtime 可用。"
                setShowBadge(false)
            },
        )
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(text))
    }

    private fun notification(text: String): Notification {
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("DeepSeek Harness Mobile")
            .setContentText(text)
            .setContentIntent(launch)
            .setOngoing(true)
            .build()
    }
}
