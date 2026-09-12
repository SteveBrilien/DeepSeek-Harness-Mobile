package com.stevebrilien.dshmobile.core.runtimeandroid

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class MobilePluginProfileCoordinatorTest {
    private lateinit var context: Context
    private lateinit var store: RuntimeStateStore
    private lateinit var coordinator: MobilePluginProfileCoordinator

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "runtime").deleteRecursively()
        File(context.filesDir, "persistent").deleteRecursively()
        store = RuntimeStateStore(context)
        store.ensureLayout()
        coordinator = MobilePluginProfileCoordinator(context, store.layout)
    }

    @After
    fun tearDown() {
        File(context.filesDir, "runtime").deleteRecursively()
        File(context.filesDir, "persistent").deleteRecursively()
    }

    @Test
    fun reconcileExistingProfilePreservesUserFieldsAndIsIdempotent() {
        val profile = File(store.layout.persistentDshHome, "profiles/web")
        assertTrue(profile.mkdirs())
        val original = JSONObject()
            .put("name", "user-web-profile")
            .put("private", true)
            .put("userCustom", JSONObject().put("keep", "yes"))
            .put(
                "dependencies",
                JSONObject()
                    .put("user-plugin", "1.2.3")
                    .put("dsh-client-ui-mobile", "file:/dsh-home/mobile-plugins/dsh-client-ui-mobile"),
            )
            .put(
                "dsh",
                JSONObject().put(
                    "profile",
                    JSONObject().put(
                        "bundles",
                        org.json.JSONArray()
                            .put("@deepseek-ai/dsh-base")
                            .put("@deepseek-ai/dsh-web-app")
                            .put("user-plugin")
                            .put("dsh-client-ui-mobile"),
                    ).put("patchReload", "live"),
                ),
            )
        val packageFile = File(profile, "package.json")
        packageFile.writeText(original.toString(2), StandardCharsets.UTF_8)
        val staleUi = File(profile, "node_modules/dsh-client-ui-mobile")
        assertTrue(staleUi.mkdirs())
        File(staleUi, "package.json").writeText("{\"version\":\"0.1.9\"}", StandardCharsets.UTF_8)

        coordinator.reconcile { error("seed must not be used for an existing profile") }

        val migrated = JSONObject(packageFile.readText(StandardCharsets.UTF_8))
        assertEquals("yes", migrated.getJSONObject("userCustom").getString("keep"))
        assertEquals("1.2.3", migrated.getJSONObject("dependencies").getString("user-plugin"))
        assertEquals(
            "file:/dsh-home/mobile-plugins/dsh-mobile-context",
            migrated.getJSONObject("dependencies").getString("@dsh-mobile/dsh-mobile-context"),
        )
        assertFalse(migrated.getJSONObject("dependencies").has("dsh-client-ui-mobile"))
        val bundles = migrated.getJSONObject("dsh").getJSONObject("profile").getJSONArray("bundles")
        val bundleNames = (0 until bundles.length()).map(bundles::getString)
        assertTrue(bundleNames.contains("user-plugin"))
        assertTrue(bundleNames.contains("@dsh-mobile/dsh-mobile-context"))
        assertFalse(bundleNames.contains("dsh-client-ui-mobile"))
        assertEquals(
            MobilePluginProfileCoordinator.MOBILE_CONTEXT_PLUGIN_VERSION,
            JSONObject(
                File(profile, "node_modules/@dsh-mobile/dsh-mobile-context/package.json")
                    .readText(StandardCharsets.UTF_8),
            ).getString("version"),
        )
        assertFalse(File(profile, "node_modules/dsh-client-ui-mobile").exists())
        assertEquals(
            MobilePluginProfileCoordinator.MOBILE_UI_PLUGIN_VERSION,
            JSONObject(
                File(store.layout.persistentDshHome, "mobile-plugins/dsh-client-ui-mobile/package.json")
                    .readText(StandardCharsets.UTF_8),
            ).getString("version"),
        )
        assertEquals(
            MobilePluginProfileCoordinator.MOBILE_WEB_PROFILE_MODE,
            File(store.layout.persistentDshHome, "mobile/ui-plugin.version")
                .readText(StandardCharsets.UTF_8),
        )
        assertTrue(coordinator.isReconciled())

        val firstResult = packageFile.readBytes()
        File(store.layout.persistentDshHome, "mobile-plugins/dsh-client-ui-mobile").deleteRecursively()
        coordinator.reconcile { error("reconciled profile must not reinstall seed") }
        assertTrue(firstResult.contentEquals(packageFile.readBytes()))
        assertTrue(File(store.layout.persistentDshHome, "mobile-plugins/dsh-client-ui-mobile/package.json").isFile)
        assertTrue(coordinator.isReconciled())
    }

    @Test
    fun reconcileBrokenExistingProfileDoesNotCommitMarkers() {
        val profile = File(store.layout.persistentDshHome, "profiles/web")
        assertTrue(profile.mkdirs())
        File(profile, "user-data.txt").writeText("keep", StandardCharsets.UTF_8)

        assertThrows(IllegalStateException::class.java) {
            coordinator.reconcile { false }
        }

        assertTrue(File(profile, "user-data.txt").isFile)
        assertFalse(File(store.layout.persistentDshHome, "mobile/context-plugin.version").exists())
        assertFalse(File(store.layout.persistentDshHome, "mobile/ui-plugin.version").exists())
    }
}
