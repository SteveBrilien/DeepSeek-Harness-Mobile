package com.stevebrilien.dshmobile.core.privilegeapi

enum class PrivilegeProviderKind {
    STANDARD,
    LOCAL_ADB,
    SHIZUKU,
    ROOT,
}

enum class PrivilegeCapability {
    SHELL,
    PACKAGE_QUERY,
    PACKAGE_MANAGEMENT,
    ACTIVITY_CONTROL,
    SETTINGS_READ,
    SETTINGS_WRITE,
    PROCESS_INSPECTION,
    SYSTEM_FILE_READ,
    SYSTEM_FILE_WRITE,
}

data class PrivilegeState(
    val provider: PrivilegeProviderKind,
    val available: Boolean,
    val capabilities: Set<PrivilegeCapability>,
    val detail: String? = null,
)

data class ShellRequest(
    val command: String,
    val workingDirectory: String? = null,
    val timeoutMillis: Long = 120_000,
)

data class ShellResult(
    val provider: PrivilegeProviderKind,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

interface PrivilegeGateway {
    suspend fun state(): PrivilegeState
    suspend fun execute(request: ShellRequest): Result<ShellResult>
    suspend fun has(capability: PrivilegeCapability): Boolean
}
