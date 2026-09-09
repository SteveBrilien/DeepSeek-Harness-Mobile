package com.stevebrilien.dshmobile.core.runtimeapi

import com.stevebrilien.dshmobile.core.model.RuntimeId

enum class RuntimeSlot { A, B }

enum class RuntimeComponent {
    LINUX_USERSPACE,
    NODE,
    DSH,
    PLUGINS,
}

enum class HealthState {
    UNKNOWN,
    HEALTHY,
    DEGRADED,
    FAILED,
}

data class ComponentHealth(
    val component: RuntimeComponent,
    val state: HealthState,
    val detail: String? = null,
)

data class RuntimeHealth(
    val runtimeId: RuntimeId,
    val activeSlot: RuntimeSlot?,
    val components: List<ComponentHealth>,
)

data class RuntimeInstallRequest(
    val targetSlot: RuntimeSlot,
    val componentVersions: Map<RuntimeComponent, String>,
)

interface RuntimeManager {
    suspend fun health(): RuntimeHealth
    suspend fun start(): Result<Unit>
    suspend fun stop(): Result<Unit>
    suspend fun stage(request: RuntimeInstallRequest): Result<Unit>
    suspend fun verify(slot: RuntimeSlot): Result<Unit>
    suspend fun activate(slot: RuntimeSlot): Result<Unit>
    suspend fun rollback(): Result<Unit>
}
