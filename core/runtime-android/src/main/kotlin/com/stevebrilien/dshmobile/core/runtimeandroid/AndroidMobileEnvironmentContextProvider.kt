package com.stevebrilien.dshmobile.core.runtimeandroid

import android.content.Context
import com.stevebrilien.dshmobile.core.dshapi.CapabilityAuthorization
import com.stevebrilien.dshmobile.core.dshapi.CapabilityAvailability
import com.stevebrilien.dshmobile.core.dshapi.CapabilitySnapshot
import com.stevebrilien.dshmobile.core.dshapi.ExecutionDomainDescriptor
import com.stevebrilien.dshmobile.core.dshapi.ExecutionDomainKind
import com.stevebrilien.dshmobile.core.dshapi.MobileEnvironmentContextProvider
import com.stevebrilien.dshmobile.core.dshapi.ProjectContextSnapshot
import com.stevebrilien.dshmobile.core.dshapi.SessionBootstrapEnvironment
import com.stevebrilien.dshmobile.core.model.SessionId
import com.stevebrilien.dshmobile.core.runtimeapi.HealthState
import com.stevebrilien.dshmobile.core.runtimeapi.RuntimeComponent

/**
 * Android source of truth for Mobile execution-domain semantics and current availability.
 *
 * The stable meanings below are safe to mirror in the process-stable DSH system section.
 * Availability/authorization remain dynamic and are queried instead of baked into that prefix.
 */
class AndroidMobileEnvironmentContextProvider(
    context: Context,
    private val projectContextResolver: suspend (SessionId) -> ProjectContextSnapshot? = { null },
) : MobileEnvironmentContextProvider {
    companion object {
        const val CAP_LINUX_SHELL = "execution.linux-runtime"
        const val CAP_ANDROID_LOCAL = "execution.android-local"
        const val CAP_ADB_SHELL = "execution.adb-shell"
        const val CAP_ANDROID_ROOT = "execution.android-root"
    }

    private val runtime = AndroidRuntimeManager(context.applicationContext)

    override val staticContextVersion: String = "mobile-context-v2"

    override val executionDomains: List<ExecutionDomainDescriptor> = listOf(
        ExecutionDomainDescriptor(
            kind = ExecutionDomainKind.LINUX_RUNTIME,
            privilegeLabel = "PRoot simulated root (not Android UID 0)",
            purpose = "Primary development environment for Git, Node, Python, DSH, builds, and project tooling.",
        ),
        ExecutionDomainDescriptor(
            kind = ExecutionDomainKind.ANDROID_LOCAL,
            privilegeLabel = "Android App UID",
            purpose = "Native recovery/diagnostic shell that remains available when the managed Linux runtime is broken.",
        ),
        ExecutionDomainDescriptor(
            kind = ExecutionDomainKind.ADB_SHELL,
            privilegeLabel = "Android shell UID (normally 2000)",
            purpose = "Enhanced Android diagnostics and system operations when Wireless ADB is paired and connected.",
        ),
        ExecutionDomainDescriptor(
            kind = ExecutionDomainKind.ANDROID_ROOT,
            privilegeLabel = "Android UID 0",
            purpose = "Optional future provider; never inferred from PRoot or ADB availability.",
        ),
    )

    override suspend fun sessionBootstrap(sessionId: SessionId): SessionBootstrapEnvironment =
        SessionBootstrapEnvironment(
            sessionId = sessionId,
            projectContext = projectContextResolver(sessionId),
            capabilities = currentCapabilities(),
        )

    override suspend fun currentCapabilities(): List<CapabilitySnapshot> {
        val runtimeHealth = runtime.health()
        val linuxComponent = runtimeHealth.components.firstOrNull { it.component == RuntimeComponent.LINUX_USERSPACE }
        val linuxAvailability = when {
            runtimeHealth.activeSlot == null -> CapabilityAvailability.UNAVAILABLE
            linuxComponent?.state == HealthState.HEALTHY -> CapabilityAvailability.AVAILABLE
            linuxComponent?.state == HealthState.FAILED -> CapabilityAvailability.UNAVAILABLE
            else -> CapabilityAvailability.DEGRADED
        }
        return listOf(
            CapabilitySnapshot(
                capabilityId = CAP_LINUX_SHELL,
                domain = ExecutionDomainKind.LINUX_RUNTIME,
                availability = linuxAvailability,
                authorization = CapabilityAuthorization.AUTO_ALLOWED,
                detail = linuxComponent?.detail ?: "No active runtime slot",
            ),
            CapabilitySnapshot(
                capabilityId = CAP_ANDROID_LOCAL,
                domain = ExecutionDomainKind.ANDROID_LOCAL,
                availability = CapabilityAvailability.AVAILABLE,
                authorization = CapabilityAuthorization.AUTO_ALLOWED,
                detail = "Native /system/bin/sh under the app UID",
            ),
            CapabilitySnapshot(
                capabilityId = CAP_ADB_SHELL,
                domain = ExecutionDomainKind.ADB_SHELL,
                availability = CapabilityAvailability.UNAVAILABLE,
                authorization = CapabilityAuthorization.CONFIRMATION_REQUIRED,
                detail = "Embedded Wireless ADB provider is not connected in this build",
            ),
            CapabilitySnapshot(
                capabilityId = CAP_ANDROID_ROOT,
                domain = ExecutionDomainKind.ANDROID_ROOT,
                availability = CapabilityAvailability.UNAVAILABLE,
                authorization = CapabilityAuthorization.DENIED,
                detail = "No Android root provider is configured",
            ),
        )
    }
}
