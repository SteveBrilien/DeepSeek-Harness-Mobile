package com.stevebrilien.dshmobile.core.runtimeandroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationCandidateIdentityTest {
    private val baseArtifacts = mapOf(
        "@dsh-mobile/dsh-mobile-context" to "11".repeat(32),
        "@dsh-mobile/dsh-webview-compat" to "22".repeat(32),
    )

    private fun generation(
        dshSeed: String = "aa".repeat(32),
        profileSeed: String = "bb".repeat(32),
        profileMode: String = "dsh-webview-compat-v1",
        artifacts: Map<String, String> = baseArtifacts,
        handshakeSchema: Int = PresentationCandidateIdentity.HANDSHAKE_SCHEMA_VERSION,
    ): String = PresentationCandidateIdentity.compute(
        dshSeedSha256 = dshSeed,
        webProfileSeedSha256 = profileSeed,
        profileMode = profileMode,
        managedArtifactHashes = artifacts,
        handshakeSchemaVersion = handshakeSchema,
    )

    @Test
    fun generationIsStableAcrossArtifactMapOrder() {
        val reversed = baseArtifacts.entries.reversed().associate { it.toPair() }
        assertEquals(generation(), generation(artifacts = reversed))
    }

    @Test
    fun generationChangesWhenAnyManagedIdentityInputChanges() {
        val baseline = generation()
        assertNotEquals(baseline, generation(dshSeed = "cc".repeat(32)))
        assertNotEquals(baseline, generation(profileSeed = "dd".repeat(32)))
        assertNotEquals(baseline, generation(profileMode = "another-profile-mode"))
        assertNotEquals(
            baseline,
            generation(
                artifacts = baseArtifacts +
                    ("@dsh-mobile/dsh-webview-compat" to "44".repeat(32)),
            ),
        )
        assertNotEquals(baseline, generation(handshakeSchema = PresentationHandshakeContract.SCHEMA_VERSION + 1))
    }

    @Test
    fun generationHasExplicitSchemaPrefix() {
        assertTrue(generation().startsWith("dshm-presentation-v1-"))
    }

    @Test
    fun reuseRequiresEndpointLaunchProfileAndBothMatchingGenerations() {
        val desired = generation()
        assertTrue(
            PresentationReusePolicy.canReuse(
                endpointReady = true,
                launchUrlAvailable = true,
                profileCurrentBeforeReconcile = true,
                profileReconciled = true,
                liveProcessGeneration = desired,
                persistedProcessGeneration = desired,
                desiredGeneration = desired,
            ),
        )

        fun reusable(
            endpointReady: Boolean = true,
            launchUrlAvailable: Boolean = true,
            profileCurrentBeforeReconcile: Boolean = true,
            profileReconciled: Boolean = true,
            live: String? = desired,
            persisted: String? = desired,
        ): Boolean = PresentationReusePolicy.canReuse(
            endpointReady = endpointReady,
            launchUrlAvailable = launchUrlAvailable,
            profileCurrentBeforeReconcile = profileCurrentBeforeReconcile,
            profileReconciled = profileReconciled,
            liveProcessGeneration = live,
            persistedProcessGeneration = persisted,
            desiredGeneration = desired,
        )

        assertFalse(reusable(endpointReady = false))
        assertFalse(reusable(launchUrlAvailable = false))
        assertFalse(reusable(profileCurrentBeforeReconcile = false))
        assertFalse(reusable(profileReconciled = false))
        assertFalse(reusable(live = null))
        assertFalse(reusable(live = "stale"))
        assertFalse(reusable(persisted = null))
        assertFalse(reusable(persisted = "stale"))
    }
}
