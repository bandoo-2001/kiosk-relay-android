package io.github.kioskrelay.web

import io.github.kioskrelay.config.KioskRelayConfig
import io.github.kioskrelay.config.UrlPolicy

data class WebViewRuntimeConfig(
    val initialUrl: String,
    val navigationPolicy: NavigationPolicy,
    val retryPolicy: RetryPolicy = RetryPolicy(),
    val javaScriptEnabled: Boolean = true,
    val domStorageEnabled: Boolean = true,
    val acceptCookies: Boolean = true,
    val acceptThirdPartyCookies: Boolean = false,
    val supportZoom: Boolean = false,
    val customUserAgent: String? = null,
    val textZoom: Int = 100,
    val refreshOnNetworkRecovery: Boolean = true,
) {
    init {
        require(initialUrl.isNotBlank()) { "Initial URL must not be blank" }
        require(textZoom in 50..200) { "Text zoom must be between 50 and 200" }
    }

    companion object {
        fun from(config: KioskRelayConfig): WebViewRuntimeConfig =
            WebViewRuntimeConfig(
                initialUrl = config.webView.initialUrl,
                navigationPolicy = ConfigNavigationPolicy(config.webView),
                retryPolicy = RetryPolicy.fromSeconds(config.runtime.retryDelaysSeconds),
                acceptCookies = config.webView.acceptCookies,
                supportZoom = config.webView.supportZoom,
                customUserAgent = config.webView.customUserAgent.ifBlank { null },
                refreshOnNetworkRecovery = config.webView.refreshOnNetworkRecovery,
            )

        /**
         * Builds an isolated runtime for the onboarding "test page" action. It uses the exact
         * origin derived from the candidate URL and performs one retry before reporting Fatal.
         */
        fun forPageProbe(
            rawUrl: String,
            allowHttp: Boolean,
        ): WebViewRuntimeConfig? {
            val validation = UrlPolicy.validateInitialUrl(rawUrl, allowHttp)
            val normalizedUrl = validation.normalizedUrl ?: return null
            val origin = validation.origin ?: return null
            return WebViewRuntimeConfig(
                initialUrl = normalizedUrl,
                navigationPolicy = ConfigNavigationPolicy(
                    allowedOrigins = setOf(origin),
                    allowHttp = allowHttp,
                ),
                retryPolicy = RetryPolicy(listOf(5_000L)),
            )
        }
    }
}
