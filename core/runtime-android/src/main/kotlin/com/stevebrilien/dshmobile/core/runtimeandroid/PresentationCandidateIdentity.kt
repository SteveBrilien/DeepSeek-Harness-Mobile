package com.stevebrilien.dshmobile.core.runtimeandroid

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Public protocol identity shared by the Android WebView host and presentation generation. */
object PresentationHandshakeContract {
    const val SCHEMA_VERSION = 2
}

/**
 * Content-addressed identity for the APK-managed DSH presentation contract.
 *
 * This is deliberately separate from DSH's own boot/combo `rev` values. The Android
 * generation answers "which APK-managed presentation contract should this local DSH
 * process be serving?" while DSH revisions answer "which browser graph/resources did
 * this DSH process actually serve?".
 */
internal object PresentationCandidateIdentity {
    const val SCHEMA_VERSION = 1
    const val HANDSHAKE_SCHEMA_VERSION = PresentationHandshakeContract.SCHEMA_VERSION

    fun compute(
        dshSeedSha256: String,
        webProfileSeedSha256: String,
        profileMode: String,
        managedArtifactHashes: Map<String, String>,
        handshakeSchemaVersion: Int = HANDSHAKE_SCHEMA_VERSION,
    ): String {
        require(dshSeedSha256.isNotBlank())
        require(webProfileSeedSha256.isNotBlank())
        require(profileMode.isNotBlank())
        require(managedArtifactHashes.isNotEmpty())

        val canonical = buildString {
            append("schema=").append(SCHEMA_VERSION).append('\n')
            append("handshakeSchema=").append(handshakeSchemaVersion).append('\n')
            append("dshSeedSha256=").append(dshSeedSha256.lowercase()).append('\n')
            append("webProfileSeedSha256=").append(webProfileSeedSha256.lowercase()).append('\n')
            append("profileMode=").append(profileMode).append('\n')
            managedArtifactHashes.toSortedMap().forEach { (name, hash) ->
                append("artifact:").append(name).append('=').append(hash.lowercase()).append('\n')
            }
        }
        return "dshm-presentation-v$SCHEMA_VERSION-${sha256(canonical.toByteArray(StandardCharsets.UTF_8))}"
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}

/**
 * Conservative process-reuse policy used by both startup and presentation lookup.
 *
 * A reachable endpoint is never sufficient by itself. Reuse is allowed only when the
 * profile is reconciled and both the in-process registry and the persisted launch record
 * identify the exact desired presentation generation.
 */
internal object PresentationReusePolicy {
    fun canReuse(
        endpointReady: Boolean,
        launchUrlAvailable: Boolean,
        profileCurrentBeforeReconcile: Boolean,
        profileReconciled: Boolean,
        liveProcessGeneration: String?,
        persistedProcessGeneration: String?,
        desiredGeneration: String,
    ): Boolean =
        endpointReady &&
            launchUrlAvailable &&
            profileCurrentBeforeReconcile &&
            profileReconciled &&
            liveProcessGeneration == desiredGeneration &&
            persistedProcessGeneration == desiredGeneration
}
