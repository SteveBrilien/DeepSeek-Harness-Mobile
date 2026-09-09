package com.stevebrilien.dshmobile.core.recovery

import android.content.Context
import java.io.File

class NativeRecoveryController(
    private val context: Context,
    private val vault: RecoveryVault = RecoveryVault(context),
    private val backups: RecoveryBackupManager = RecoveryBackupManager(context, vault),
) : RecoveryController {

    override suspend fun inspect(): RecoverySnapshot {
        val vaultDiscovery = vault.discover()
        val runtimeRoot = File(context.filesDir, "runtime")
        val slotA = File(runtimeRoot, "slot-a")
        val slotB = File(runtimeRoot, "slot-b")
        val activeSlot = File(runtimeRoot, "active-slot").takeIf { it.isFile }
            ?.readText()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val checks = listOf(
            RecoveryCheck(
                component = RecoveryComponent.PERSISTENT_STORAGE,
                state = when {
                    vaultDiscovery.status.persistentAcrossUninstall && vaultDiscovery.manifestExists -> RecoveryHealthState.HEALTHY
                    vaultDiscovery.status.root.exists() -> RecoveryHealthState.DEGRADED
                    else -> RecoveryHealthState.FAILED
                },
                detail = vaultDiscovery.warnings.joinToString(" ").ifBlank {
                    "Recovery Vault: ${vaultDiscovery.status.root.absolutePath}"
                },
            ),
            RecoveryCheck(
                component = RecoveryComponent.RUNTIME_SLOT_A,
                state = if (slotA.exists()) RecoveryHealthState.HEALTHY else RecoveryHealthState.UNKNOWN,
                detail = if (slotA.exists()) slotA.absolutePath else "Runtime slot A has not been installed yet.",
            ),
            RecoveryCheck(
                component = RecoveryComponent.RUNTIME_SLOT_B,
                state = if (slotB.exists()) RecoveryHealthState.HEALTHY else RecoveryHealthState.UNKNOWN,
                detail = if (slotB.exists()) slotB.absolutePath else "Runtime slot B has not been installed yet.",
            ),
            RecoveryCheck(
                component = RecoveryComponent.NODE,
                state = runtimeComponentState(runtimeRoot, "node"),
                detail = "Native Recovery Core does not depend on Node. Runtime manager will provide deeper checks when installed.",
            ),
            RecoveryCheck(
                component = RecoveryComponent.DSH,
                state = runtimeComponentState(runtimeRoot, "dsh"),
                detail = "Native Recovery Core remains available even when DSH is absent or broken.",
            ),
            RecoveryCheck(
                component = RecoveryComponent.PLUGINS,
                state = if (File(vaultDiscovery.status.root, "Plugins").exists()) RecoveryHealthState.HEALTHY else RecoveryHealthState.DEGRADED,
                detail = "Plugin state is isolated from Native Recovery Core startup.",
            ),
            RecoveryCheck(
                component = RecoveryComponent.PRIVILEGE_BRIDGE,
                state = RecoveryHealthState.UNKNOWN,
                detail = "Privilege bridge provider is not required for Native Recovery Core.",
            ),
            RecoveryCheck(
                component = RecoveryComponent.BACKUP_MIRROR,
                state = if (backups.lastBackupEpochMillis() != null) RecoveryHealthState.DEGRADED else RecoveryHealthState.UNKNOWN,
                detail = if (backups.lastBackupEpochMillis() != null) "本机校验快照已可用；受信远端镜像将在发布验收时同步到 OrangePi。" else "尚未创建校验备份。",
            ),
        )

        return RecoverySnapshot(
            checks = checks,
            lastKnownGoodRuntimeSlot = activeSlot,
            lastBackupEpochMillis = backups.lastBackupEpochMillis(),
        )
    }

    override suspend fun execute(action: RecoveryAction): Result<Unit> = when (action) {
        RecoveryAction.VERIFY_PERSISTENT_DATA -> vault.verify().map { Unit }
        RecoveryAction.CREATE_CHECKPOINT -> runCatching { backups.createCheckpoint(); Unit }
        RecoveryAction.EXPORT_PORTABLE_BACKUP -> runCatching { backups.createPortableExport(); Unit }
        RecoveryAction.VERIFY_LATEST_BACKUP -> backups.verifyLatest().map { Unit }
        RecoveryAction.EXPORT_DIAGNOSTICS -> exportDiagnostics()
        RecoveryAction.START_SAFE_MODE -> createSafeModeMarker(true)
        RecoveryAction.STOP_RUNTIME -> stopRuntimeMarker()
        RecoveryAction.RESTART_DSH,
        RecoveryAction.REBUILD_RUNTIME,
        RecoveryAction.ROLLBACK_RUNTIME -> Result.failure(
            UnsupportedOperationException("$action requires the runtime manager, which is intentionally outside Native Recovery Core MVP."),
        )
    }

    private suspend fun exportDiagnostics(): Result<Unit> = runCatching {
        val status = vault.ensureLayout().getOrThrow()
        val diagnosticsDir = File(status.root, "Exports/Diagnostics")
        check(diagnosticsDir.exists() || diagnosticsDir.mkdirs())
        val snapshot = inspect()
        val output = buildString {
            appendLine("DeepSeek Harness Mobile Native Recovery Diagnostics")
            appendLine("generatedAtEpochMillis=${System.currentTimeMillis()}")
            appendLine("recoveryRoot=${status.root.absolutePath}")
            appendLine("persistentAcrossUninstall=${status.persistentAcrossUninstall}")
            appendLine("allFilesAccessGranted=${status.allFilesAccessGranted}")
            snapshot.checks.forEach { check ->
                appendLine("${check.component}=${check.state}; ${check.detail.orEmpty()}")
            }
        }
        File(diagnosticsDir, "diagnostics-${System.currentTimeMillis()}.txt").writeText(output)
    }

    private fun createSafeModeMarker(enabled: Boolean): Result<Unit> = runCatching {
        val marker = File(context.filesDir, "safe-mode")
        if (enabled) marker.writeText("1") else marker.delete()
    }

    private fun stopRuntimeMarker(): Result<Unit> = runCatching {
        val runtimeRoot = File(context.filesDir, "runtime")
        check(runtimeRoot.exists() || runtimeRoot.mkdirs())
        File(runtimeRoot, "requested-stop").writeText(System.currentTimeMillis().toString())
    }

    private fun runtimeComponentState(runtimeRoot: File, name: String): RecoveryHealthState {
        val component = File(runtimeRoot, name)
        return if (component.exists()) RecoveryHealthState.HEALTHY else RecoveryHealthState.UNKNOWN
    }
}
