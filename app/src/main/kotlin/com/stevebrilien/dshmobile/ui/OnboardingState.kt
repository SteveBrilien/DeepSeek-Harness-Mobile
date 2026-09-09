package com.stevebrilien.dshmobile.ui

import android.content.Context
import androidx.core.content.edit
import com.stevebrilien.dshmobile.core.recovery.RecoveryDiscovery

internal enum class OnboardingMode {
    NEW_INSTALL,
    RECOVERY_FOUND,
}

internal class OnboardingStateStore(context: Context) {
    companion object {
        const val CURRENT_VERSION = 1
    }

    private val prefs = context.getSharedPreferences("dsh-mobile-onboarding", Context.MODE_PRIVATE)

    fun isComplete(): Boolean = prefs.getInt("completed_version", 0) >= CURRENT_VERSION

    fun complete() = prefs.edit {
        putInt("completed_version", CURRENT_VERSION)
        putLong("completed_at", System.currentTimeMillis())
    }

    fun reset() = prefs.edit { remove("completed_version"); remove("completed_at") }
}

internal fun onboardingMode(discovery: RecoveryDiscovery?): OnboardingMode =
    if (discovery?.manifestExists == true) OnboardingMode.RECOVERY_FOUND else OnboardingMode.NEW_INSTALL
