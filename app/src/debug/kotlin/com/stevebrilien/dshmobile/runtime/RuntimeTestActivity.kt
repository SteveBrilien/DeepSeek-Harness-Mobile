package com.stevebrilien.dshmobile.runtime

import android.app.Activity
import android.os.Bundle

/** Debug-only ADB bridge for device smoke tests. Not packaged in release builds. */
class RuntimeTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = when (intent.getStringExtra(EXTRA_ACTION)?.lowercase()) {
            "install" -> RuntimeForegroundService.ACTION_INSTALL
            "start" -> RuntimeForegroundService.ACTION_START
            "stop" -> RuntimeForegroundService.ACTION_STOP
            "rollback" -> RuntimeForegroundService.ACTION_ROLLBACK
            else -> null
        }
        if (action != null) {
            RuntimeForegroundService.dispatch(applicationContext, action)
            setResult(RESULT_OK)
        } else {
            setResult(RESULT_CANCELED)
        }
        finish()
    }

    companion object {
        const val EXTRA_ACTION = "runtime_action"
    }
}
