package com.stevebrilien.dshmobile.core.dshapi

import com.stevebrilien.dshmobile.core.model.SessionId

enum class ExecutionDomainKind {
    LINUX_RUNTIME,
    ANDROID_LOCAL,
    ADB_SHELL,
    ANDROID_ROOT,
}

enum class CapabilityAvailability {
    AVAILABLE,
    DEGRADED,
    UNAVAILABLE,
}

enum class CapabilityAuthorization {
    AUTO_ALLOWED,
    CONFIRMATION_REQUIRED,
    DENIED,
}

data class ExecutionDomainDescriptor(
    val kind: ExecutionDomainKind,
    val privilegeLabel: String,
    val purpose: String,
)

data class CapabilitySnapshot(
    val capabilityId: String,
    val domain: ExecutionDomainKind,
    val availability: CapabilityAvailability,
    val authorization: CapabilityAuthorization,
    val detail: String? = null,
)

data class SessionBootstrapEnvironment(
    val sessionId: SessionId,
    val projectContext: ProjectContextSnapshot?,
    val capabilities: List<CapabilitySnapshot>,
)

data class EnvironmentDelta(
    val capabilityId: String,
    val previousAvailability: CapabilityAvailability,
    val currentAvailability: CapabilityAvailability,
    val previousAuthorization: CapabilityAuthorization,
    val currentAuthorization: CapabilityAuthorization,
    val reason: String? = null,
)

/**
 * Canonical Mobile capability state consumed by DSH context injection, terminal routing,
 * permission gates, diagnostics, and native UI.
 *
 * Cache contract:
 * - global static prompt rules are versioned and immutable for the life of a DSH process;
 * - a session bootstrap snapshot is emitted once for a new Session and is never rewritten;
 * - runtime changes are represented as append-only EnvironmentDelta values or queried on demand.
 */
interface MobileEnvironmentContextProvider {
    val staticContextVersion: String
    val executionDomains: List<ExecutionDomainDescriptor>

    suspend fun sessionBootstrap(sessionId: SessionId): SessionBootstrapEnvironment
    suspend fun currentCapabilities(): List<CapabilitySnapshot>
}
