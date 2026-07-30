package io.github.kioskrelay.web

import android.os.Build
import android.webkit.WebViewClient

internal fun secureWebViewClient(
    policy: NavigationPolicy,
    events: SecureWebViewEvents,
): WebViewClient =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Api26SecureWebViewClient(policy, events)
    } else {
        SecureWebViewClient(policy, events)
    }
