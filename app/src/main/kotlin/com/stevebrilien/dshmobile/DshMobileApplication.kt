package com.stevebrilien.dshmobile

import android.app.Application
import com.stevebrilien.dshmobile.runtime.RuntimeSupervisor

/** Process-wide application kernel for long-lived control-plane coordinators. */
class DshMobileApplication : Application() {
    val runtimeSupervisor: RuntimeSupervisor by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RuntimeSupervisor(this)
    }

    override fun onTerminate() {
        // Android does not normally call onTerminate on devices; this remains useful
        // for emulators/tests and documents ownership of the process scope.
        runCatching { runtimeSupervisor.close() }
        super.onTerminate()
    }
}
