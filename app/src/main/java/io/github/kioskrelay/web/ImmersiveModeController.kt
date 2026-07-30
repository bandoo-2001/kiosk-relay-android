package io.github.kioskrelay.web

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowManager

object ImmersiveModeController {
    fun apply(
        activity: Activity,
        fullscreen: Boolean,
        keepScreenOn: Boolean,
    ) {
        if (keepScreenOn) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Api30WindowUi.apply(activity, fullscreen)
        } else {
            applyLegacy(activity, fullscreen)
        }
    }

    /**
     * Call from Activity.onWindowFocusChanged so navigation bars dismissed by user interaction are
     * hidden again when kiosk mode regains focus.
     */
    fun onWindowFocusChanged(
        activity: Activity,
        hasFocus: Boolean,
        fullscreen: Boolean,
        keepScreenOn: Boolean,
    ) {
        if (hasFocus) apply(activity, fullscreen, keepScreenOn)
    }

    @Suppress("DEPRECATION")
    private fun applyLegacy(activity: Activity, fullscreen: Boolean) {
        activity.window.decorView.systemUiVisibility = if (fullscreen) {
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        } else {
            View.SYSTEM_UI_FLAG_VISIBLE
        }
    }
}
