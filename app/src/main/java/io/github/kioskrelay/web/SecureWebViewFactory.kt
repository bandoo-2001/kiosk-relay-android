package io.github.kioskrelay.web

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Message
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.kioskrelay.BuildConfig

@SuppressLint("SetJavaScriptEnabled")
internal fun createSecureWebView(
    context: Context,
    config: WebViewRuntimeConfig,
    client: WebViewClient,
): WebView =
    WebView(context).apply webView@{
        settings.apply {
            javaScriptEnabled = config.javaScriptEnabled
            domStorageEnabled = config.domStorageEnabled
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = true
            setGeolocationEnabled(false)
            saveFormData = false
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
            blockNetworkLoads = false
            textZoom = config.textZoom
            setSupportZoom(config.supportZoom)
            builtInZoomControls = config.supportZoom
            displayZoomControls = false
            config.customUserAgent?.takeIf(String::isNotBlank)?.let { userAgentString = it }
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(config.acceptCookies)
            setAcceptThirdPartyCookies(this@webView, config.acceptThirdPartyCookies)
        }
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        webViewClient = client
        webChromeClient = LockedDownWebChromeClient
        setDownloadListener { _, _, _, _, _ ->
            // Downloads are intentionally disabled in kiosk mode.
        }
    }

internal object LockedDownWebChromeClient : WebChromeClient() {
    override fun onPermissionRequest(request: PermissionRequest) {
        request.deny()
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback,
    ) {
        callback.invoke(origin, false, false)
    }

    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?,
    ): Boolean = false

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?,
    ): Boolean {
        filePathCallback?.onReceiveValue(null)
        return true
    }
}

internal fun clearWebViewSiteData(webView: WebView) {
    webView.clearCache(true)
    webView.clearFormData()
    webView.clearHistory()
    WebStorage.getInstance().deleteAllData()
}
