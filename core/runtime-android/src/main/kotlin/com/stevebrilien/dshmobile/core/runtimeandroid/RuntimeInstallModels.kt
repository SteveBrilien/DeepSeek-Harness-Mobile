package com.stevebrilien.dshmobile.core.runtimeandroid

import com.stevebrilien.dshmobile.core.runtimeapi.RuntimeSlot

data class RuntimeInstallSource(
    val id: String,
    val name: String,
    val description: String,
)

data class RuntimeInstallProgress(
    val phase: String,
    val message: String,
    val percent: Int? = null,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
    val sourceId: String? = null,
    val sourceName: String? = null,
    val logLine: String? = null,
)

data class RuntimeStartProgress(
    val phase: String,
    val message: String,
    val percent: Int? = null,
    val logLine: String? = null,
)

/**
 * Authenticated local DSH Web launch plus the managed mobile-presentation generation.
 *
 * The URL is intentionally ephemeral because it carries the current runtime launch
 * credential. [generation] is stable for one APK-managed browser-plugin contract and
 * lets the Android Web host invalidate stale resource cache exactly when that contract
 * changes.
 */
data class DshWebPresentationDescriptor(
    val launchUrl: String,
    val generation: String,
)

data class RuntimeResourceInventory(
    val activeSlot: RuntimeSlot?,
    val installedAlpineVersion: String?,
    val installedDshVersion: String?,
    val targetAlpineVersion: String,
    val targetDshVersion: String,
    val cachedRootfsAvailable: Boolean,
    val persistentCachedRootfsAvailable: Boolean,
    val reusableInstalledRuntime: Boolean,
    val updateAvailable: Boolean,
    val installedVersionIsNewer: Boolean,
)
