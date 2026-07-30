package io.github.kioskrelay.web

import io.github.kioskrelay.config.UrlPolicy
import io.github.kioskrelay.config.WebViewConfig

/**
 * Navigation boundary consumed by the WebView runtime.
 *
 * Keeping this interface small allows a settings screen to replace the active policy by recreating
 * [WebViewHost], without coupling the WebView controller to a repository implementation.
 */
fun interface NavigationPolicy {
    fun isAllowed(url: String): Boolean
}

data class ConfigNavigationPolicy(
    private val allowedOrigins: Set<String>,
    private val allowHttp: Boolean,
) : NavigationPolicy {
    constructor(config: WebViewConfig) : this(
        allowedOrigins = config.allowedOrigins,
        allowHttp = config.allowHttp,
    )

    override fun isAllowed(url: String): Boolean =
        UrlPolicy.isNavigationAllowed(
            rawUrl = url,
            allowedOrigins = allowedOrigins,
            allowHttp = allowHttp,
        )
}
