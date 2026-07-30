package io.github.kioskrelay.web

import android.app.Activity
import android.os.Build
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.R)
internal object Api30WindowUi {
    fun apply(activity: Activity, fullscreen: Boolean) {
        activity.window.setDecorFitsSystemWindows(!fullscreen)
        val controller = activity.window.insetsController ?: return
        if (fullscreen) {
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsets.Type.systemBars())
        } else {
            controller.show(WindowInsets.Type.systemBars())
        }
    }
}
