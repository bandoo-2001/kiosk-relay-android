package io.github.kioskrelay.web

import android.os.Build
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import androidx.annotation.RequiresApi

/**
 * Kept in an API-gated class so Android 7.0/7.1 never verifies a method signature containing the
 * API 26-only [RenderProcessGoneDetail] type.
 */
@RequiresApi(Build.VERSION_CODES.O)
internal class Api26SecureWebViewClient(
    policy: NavigationPolicy,
    private val events: SecureWebViewEvents,
) : SecureWebViewClient(policy, events) {
    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean {
        events.onRendererGone(view, detail.didCrash())
        // The host owns removing and destroying the unusable WebView instance.
        return true
    }
}
