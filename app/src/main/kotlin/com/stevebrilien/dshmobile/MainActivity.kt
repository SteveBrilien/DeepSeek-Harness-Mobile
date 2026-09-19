package com.stevebrilien.dshmobile

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.stevebrilien.dshmobile.ui.DshMobileApp

class MainActivity : ComponentActivity() {
    private val rehideNavigation = Runnable {
        if (hasWindowFocus()) hideNavigationBar()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as? DshMobileApplication)?.startupTimeline?.beginActivity()
        enableEdgeToEdge()
        installNavigationBarRecovery()
        hideNavigationBar()
        setContent { DshMobileApp() }
    }

    override fun onPostResume() {
        super.onPostResume()
        window.decorView.post(::hideNavigationBar)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // OriginOS may restore the three-button bar one frame after transient system UI
            // (for example after the screenshot overlay). Re-assert immersive-sticky after
            // focus settles without changing layout insets underneath Compose.
            window.decorView.removeCallbacks(rehideNavigation)
            window.decorView.postDelayed(rehideNavigation, 80L)
        }
    }

    override fun onDestroy() {
        window.decorView.removeCallbacks(rehideNavigation)
        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener(null)
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun installNavigationBarRecovery() {
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            val navigationHidden = visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION != 0
            if (!navigationHidden && hasWindowFocus()) {
                // Keep a deliberate edge reveal usable, but do not let incidental OriginOS
                // overlays leave the navigation bar permanently attached to the app.
                window.decorView.removeCallbacks(rehideNavigation)
                window.decorView.postDelayed(rehideNavigation, 1_200L)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun hideNavigationBar() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
}
