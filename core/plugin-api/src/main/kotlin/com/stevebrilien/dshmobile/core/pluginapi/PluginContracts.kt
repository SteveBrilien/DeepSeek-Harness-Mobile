package com.stevebrilien.dshmobile.core.pluginapi

enum class PluginSurface {
    DSH_HOST,
    DSH_BROWSER,
    MOBILE_NATIVE,
}

enum class PluginHealthState {
    UNKNOWN,
    HEALTHY,
    DEGRADED,
    CRASH_LOOP,
    DISABLED,
}

data class MobileExtensionDescriptor(
    val pluginId: String,
    val displayName: String,
    val version: String,
    val surfaces: Set<PluginSurface>,
    val requiredCapabilities: Set<String> = emptySet(),
)

data class PluginHealth(
    val pluginId: String,
    val state: PluginHealthState,
    val detail: String? = null,
)

interface MobilePluginHost {
    suspend fun installed(): List<MobileExtensionDescriptor>
    suspend fun health(pluginId: String): PluginHealth
    suspend fun enable(pluginId: String): Result<Unit>
    suspend fun disable(pluginId: String): Result<Unit>
    suspend fun enterSafeMode(): Result<Unit>
}
