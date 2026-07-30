package io.github.kioskrelay.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

internal interface SecureWebViewEvents {
    fun onPageStarted(view: WebView, url: String)

    fun onPageFinished(view: WebView, url: String)

    fun onMainFrameError(
        view: WebView,
        url: String?,
        errorCode: Int?,
        description: String,
    )

    fun onNavigationBlocked(view: WebView, url: String)

    fun onSslError(view: WebView, url: String?, primaryError: Int)

    fun onRendererGone(view: WebView, didCrash: Boolean)
}

/**
 * A restrictive WebView client. Only top-level navigation is origin checked; subresources may be
 * cross-origin because modern web applications commonly load fonts, scripts and APIs from CDNs.
 */
internal open class SecureWebViewClient(
    private val policy: NavigationPolicy,
    private val events: SecureWebViewEvents,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        return blockWhenDisallowed(view, request.url.toString())
    }

    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
        blockWhenDisallowed(view, url)

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        // Re-check after the navigation is committed as a defense in depth for OEM WebView
        // implementations that do not route every redirect through shouldOverrideUrlLoading.
        if (!policy.isAllowed(url)) {
            view.stopLoading()
            events.onNavigationBlocked(view, url)
            return
        }
        events.onPageStarted(view, url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        events.onPageFinished(view, url)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (!request.isForMainFrame) return
        events.onMainFrameError(
            view = view,
            url = request.url?.toString(),
            errorCode = error.errorCode,
            description = error.description?.toString().orEmpty(),
        )
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        if (!request.isForMainFrame) return
        events.onMainFrameError(
            view = view,
            url = request.url?.toString(),
            errorCode = errorResponse.statusCode,
            description = "HTTP ${errorResponse.statusCode}",
        )
    }

    @SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError,
    ) {
        // Kiosk deployments may use self-signed, expired or privately issued certificates.
        // The administrator controls the configured destination, so keep loading consistently.
        handler.proceed()
    }

    private fun blockWhenDisallowed(view: WebView, url: String): Boolean {
        if (policy.isAllowed(url)) return false
        events.onNavigationBlocked(view, url)
        return true
    }
}
