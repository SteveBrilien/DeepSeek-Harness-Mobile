package com.stevebrilien.dshmobile.core.recovery

enum class RecoveryComponent {
    PERSISTENT_STORAGE,
    RUNTIME_SLOT_A,
    RUNTIME_SLOT_B,
    NODE,
    DSH,
    PLUGINS,
    PRIVILEGE_BRIDGE,
    BACKUP_MIRROR,
}

enum class RecoveryHealthState {
    UNKNOWN,
    HEALTHY,
    DEGRADED,
    FAILED,
}

data class RecoveryCheck(
    val component: RecoveryComponent,
    val state: RecoveryHealthState,
    val detail: String? = null,
)

data class RecoverySnapshot(
    val checks: List<RecoveryCheck>,
    val lastKnownGoodRuntimeSlot: String? = null,
    val lastBackupEpochMillis: Long? = null,
)

enum class RecoveryAction {
    RESTART_DSH,
    STOP_RUNTIME,
    REBUILD_RUNTIME,
    ROLLBACK_RUNTIME,
    START_SAFE_MODE,
    VERIFY_PERSISTENT_DATA,
    CREATE_CHECKPOINT,
    EXPORT_PORTABLE_BACKUP,
    VERIFY_LATEST_BACKUP,
    EXPORT_DIAGNOSTICS,
}

interface RecoveryController {
    suspend fun inspect(): RecoverySnapshot
    suspend fun execute(action: RecoveryAction): Result<Unit>
}
