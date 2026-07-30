package io.github.kioskrelay.web

import android.webkit.CookieManager
import android.webkit.WebView
import android.os.Build
import io.github.kioskrelay.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WebViewController internal constructor(
    private val scope: CoroutineScope,
) : SecureWebViewEvents {
    private val _state = MutableStateFlow<KioskUiState>(KioskUiState.Starting)
    val state: StateFlow<KioskUiState> = _state.asStateFlow()

    private val _recreationKey = MutableStateFlow(0)
    internal val recreationKey: StateFlow<Int> = _recreationKey.asStateFlow()

    private var webView: WebView? = null
    private var config: WebViewRuntimeConfig? = null
    private var retryJob: Job? = null
    private var lastRequestedUrl: String? = null
    private var pageFailed = false
    private var consecutiveFailures = 0
    private var consecutiveRendererFailures = 0
    private var rendererRecoveryBlocked = false
    private var networkAvailable = true

    internal fun attach(
        view: WebView,
        newConfig: WebViewRuntimeConfig,
    ) {
        val previousConfig = config
        webView = view
        config = newConfig
        pageFailed = false

        if (previousConfig != newConfig) {
            rendererRecoveryBlocked = false
            resetFailureCounters()
        } else if (rendererRecoveryBlocked) {
            // Keep a fresh, inert WebView ready for an explicit manual retry.
            return
        }

        val targetUrl = when {
            previousConfig == null -> newConfig.initialUrl
            previousConfig.initialUrl != newConfig.initialUrl -> newConfig.initialUrl
            lastRequestedUrl?.let(newConfig.navigationPolicy::isAllowed) == true ->
                checkNotNull(lastRequestedUrl)
            else -> newConfig.initialUrl
        }
        performLoad(targetUrl)
    }

    internal fun detach(view: WebView) {
        if (webView !== view) return
        retryJob?.cancel()
        retryJob = null
        webView = null
    }

    fun load(url: String) {
        rendererRecoveryBlocked = false
        resetFailureCounters()
        performLoad(url)
    }

    fun reload() {
        rendererRecoveryBlocked = false
        resetFailureCounters()
        val target = webView?.url
            ?.takeIf { config?.navigationPolicy?.isAllowed(it) == true }
            ?: lastRequestedUrl
            ?: config?.initialUrl
            ?: return
        performLoad(target)
    }

    fun goBackIfPossible(): Boolean {
        val activeView = webView ?: return false
        if (!activeView.canGoBack()) return false
        activeView.goBack()
        return true
    }

    fun clearCache(includeDiskFiles: Boolean = true) {
        webView?.clearCache(includeDiskFiles)
    }

    fun clearCookies(onComplete: (Boolean) -> Unit = {}) {
        CookieManager.getInstance().removeAllCookies { removed ->
            CookieManager.getInstance().flush()
            onComplete(removed)
        }
    }

    fun clearWebData() {
        webView?.let(::clearWebViewSiteData)
    }

    fun onNetworkAvailabilityChanged(isAvailable: Boolean) {
        val recovered = !networkAvailable && isAvailable
        networkAvailable = isAvailable

        if (!isAvailable) {
            retryJob?.cancel()
            retryJob = null
            _state.value = KioskUiState.Offline(
                lastUrl = webView?.url ?: lastRequestedUrl,
            )
            return
        }

        if (!recovered || rendererRecoveryBlocked) return
        val activeConfig = config ?: return
        if (activeConfig.refreshOnNetworkRecovery) {
            resetFailureCounters()
            performLoad(lastRequestedUrl ?: activeConfig.initialUrl)
        } else {
            val url = webView?.url ?: lastRequestedUrl
            if (url != null) _state.value = KioskUiState.Online(url)
        }
    }

    /**
     * Debug-only renderer recovery hook used by instrumentation tests.
     *
     * The BuildConfig guard is deliberately inside the method as well as at the call site so a
     * release build can never navigate to the WebView crash URL.
     */
    fun debugCrashRenderer(): Boolean {
        if (!BuildConfig.DEBUG || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val activeView = webView ?: return false
        activeView.loadUrl("chrome://crash")
        return true
    }

    internal fun destroy() {
        retryJob?.cancel()
        retryJob = null
        webView = null
        config = null
        rendererRecoveryBlocked = false
    }

    override fun onPageStarted(view: WebView, url: String) {
        if (webView !== view) return
        pageFailed = false
        lastRequestedUrl = url
        _state.value = KioskUiState.Loading(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        if (webView !== view || pageFailed) return
        val activeConfig = config ?: return
        if (!activeConfig.navigationPolicy.isAllowed(url)) {
            onNavigationBlocked(view, url)
            return
        }
        retryJob?.cancel()
        retryJob = null
        resetFailureCounters()
        lastRequestedUrl = url
        _state.value = KioskUiState.Online(url)
    }

    override fun onMainFrameError(
        view: WebView,
        url: String?,
        errorCode: Int?,
        description: String,
    ) {
        if (webView !== view || pageFailed) return
        pageFailed = true
        handlePageFailure(
            url = url ?: lastRequestedUrl,
            errorCode = errorCode,
            description = description.ifBlank { "Page load failed" }.take(MAX_ERROR_LENGTH),
        )
    }

    override fun onNavigationBlocked(view: WebView, url: String) {
        if (webView !== view) return
        retryJob?.cancel()
        retryJob = null
        _state.value = KioskUiState.PageError(
            url = url,
            errorCode = ERROR_NAVIGATION_BLOCKED,
            description = "Navigation blocked by kiosk origin policy",
        )
    }

    override fun onSslError(view: WebView, url: String?, primaryError: Int) {
        if (webView !== view) return
        pageFailed = true
        retryJob?.cancel()
        retryJob = null
        _state.value = KioskUiState.Fatal(
            lastUrl = url ?: lastRequestedUrl,
            reason = "TLS certificate error ($primaryError)",
        )
    }

    override fun onRendererGone(view: WebView, didCrash: Boolean) {
        if (webView !== view) return
        retryJob?.cancel()
        retryJob = null
        consecutiveRendererFailures += 1
        if (consecutiveRendererFailures > MAX_RENDERER_RECREATIONS) {
            rendererRecoveryBlocked = true
            _state.value = KioskUiState.Fatal(
                lastUrl = lastRequestedUrl,
                reason = "WebView renderer repeatedly terminated",
            )
            // The renderer-owned view cannot be reused. Recreate an inert instance so the
            // visible manual Retry action has a healthy target without continuing automatically.
            _recreationKey.value += 1
            return
        }

        _state.value = KioskUiState.RendererGone(
            didCrash = didCrash,
            lastUrl = lastRequestedUrl,
        )
        _recreationKey.value += 1
    }

    private fun handlePageFailure(
        url: String?,
        errorCode: Int?,
        description: String,
    ) {
        retryJob?.cancel()
        retryJob = null
        _state.value = KioskUiState.PageError(
            url = url,
            errorCode = errorCode,
            description = description,
        )

        if (!networkAvailable) {
            _state.value = KioskUiState.Offline(lastUrl = url)
            return
        }

        val targetUrl = url?.takeIf { config?.navigationPolicy?.isAllowed(it) == true }
            ?: config?.initialUrl
            ?: return
        consecutiveFailures += 1
        val retryDelay = config?.retryPolicy?.delayMillisForFailure(consecutiveFailures)
        if (retryDelay == null) {
            _state.value = KioskUiState.Fatal(
                lastUrl = targetUrl,
                reason = "Page failed after all retry attempts",
            )
            return
        }

        retryJob = scope.launch {
            _state.value = KioskUiState.BackoffRetry(
                url = targetUrl,
                attempt = consecutiveFailures,
                delayMillis = retryDelay,
            )
            delay(retryDelay)
            if (networkAvailable) performLoad(targetUrl)
        }
    }

    private fun performLoad(url: String) {
        val activeConfig = config ?: return
        if (!activeConfig.navigationPolicy.isAllowed(url)) {
            retryJob?.cancel()
            retryJob = null
            _state.value = KioskUiState.Fatal(
                lastUrl = url,
                reason = "Initial URL is outside the allowed origins",
            )
            return
        }
        if (!networkAvailable) {
            lastRequestedUrl = url
            _state.value = KioskUiState.Offline(lastUrl = url)
            return
        }
        pageFailed = false
        lastRequestedUrl = url
        webView?.loadUrl(url)
    }

    private fun resetFailureCounters() {
        retryJob?.cancel()
        retryJob = null
        consecutiveFailures = 0
        consecutiveRendererFailures = 0
    }

    companion object {
        const val ERROR_NAVIGATION_BLOCKED: Int = -10_001
        private const val MAX_RENDERER_RECREATIONS = 3
        private const val MAX_ERROR_LENGTH = 500
    }
}
