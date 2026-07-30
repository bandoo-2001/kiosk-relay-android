package io.github.kioskrelay.config

import java.net.URI
import java.util.Locale

enum class UrlValidationError {
    EMPTY,
    TOO_LONG,
    INVALID_URL,
    UNSUPPORTED_SCHEME,
    HTTP_DISABLED,
    MISSING_HOST,
    USER_INFO_NOT_ALLOWED,
    INVALID_PORT,
}

data class UrlValidationResult(
    val isValid: Boolean,
    val normalizedUrl: String? = null,
    val origin: String? = null,
    val error: UrlValidationError? = null,
)

object UrlPolicy {
    private val supportedSchemes = setOf("https", "http")

    fun validateInitialUrl(rawUrl: String, allowHttp: Boolean): UrlValidationResult {
        val candidate = rawUrl.trim()
        if (candidate.isEmpty()) return UrlValidationResult(false, error = UrlValidationError.EMPTY)
        if (candidate.length > ConfigRules.MAX_URL_LENGTH) {
            return UrlValidationResult(false, error = UrlValidationError.TOO_LONG)
        }

        val uri = parse(candidate)
            ?: return UrlValidationResult(false, error = UrlValidationError.INVALID_URL)
        val scheme = uri.scheme?.lowercase(Locale.US)
            ?: return UrlValidationResult(false, error = UrlValidationError.UNSUPPORTED_SCHEME)
        if (scheme !in supportedSchemes) {
            return UrlValidationResult(false, error = UrlValidationError.UNSUPPORTED_SCHEME)
        }
        if (scheme == "http" && !allowHttp) {
            return UrlValidationResult(false, error = UrlValidationError.HTTP_DISABLED)
        }
        if (uri.rawUserInfo != null) {
            return UrlValidationResult(false, error = UrlValidationError.USER_INFO_NOT_ALLOWED)
        }
        if (uri.host.isNullOrBlank()) {
            return UrlValidationResult(false, error = UrlValidationError.MISSING_HOST)
        }
        if (uri.port != -1 && uri.port !in 1..65_535) {
            return UrlValidationResult(false, error = UrlValidationError.INVALID_PORT)
        }
        val origin = canonicalOrigin(uri)
            ?: return UrlValidationResult(false, error = UrlValidationError.INVALID_URL)
        val normalizedUrl = canonicalUrl(uri, scheme)
            ?: return UrlValidationResult(false, error = UrlValidationError.INVALID_URL)
        return UrlValidationResult(
            isValid = true,
            normalizedUrl = normalizedUrl,
            origin = origin,
        )
    }

    fun normalizeOrigin(rawUrl: String): String? {
        if (rawUrl.isBlank() || rawUrl.length > ConfigRules.MAX_URL_LENGTH) return null
        val uri = parse(rawUrl.trim()) ?: return null
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
        if (
            scheme !in supportedSchemes ||
            uri.rawUserInfo != null ||
            uri.host.isNullOrBlank() ||
            (uri.port != -1 && uri.port !in 1..65_535)
        ) {
            return null
        }
        return canonicalOrigin(uri)
    }

    fun isNavigationAllowed(
        rawUrl: String,
        allowedOrigins: Set<String>,
        allowHttp: Boolean,
    ): Boolean {
        val validation = validateInitialUrl(rawUrl, allowHttp)
        if (!validation.isValid) return false
        val canonicalAllowed = allowedOrigins.asSequence()
            .mapNotNull(::normalizeOrigin)
            .toSet()
        return validation.origin in canonicalAllowed
    }

    private fun parse(value: String): URI? = try {
        URI(value)
    } catch (_: Exception) {
        null
    }

    private fun canonicalOrigin(uri: URI): String? {
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
        val rawHost = uri.host?.lowercase(Locale.US)?.removeSuffix(".") ?: return null
        val host = if (rawHost.contains(':') && !rawHost.startsWith("[")) "[$rawHost]" else rawHost
        val defaultPort = (scheme == "https" && uri.port == 443) ||
            (scheme == "http" && uri.port == 80)
        val port = if (uri.port == -1 || defaultPort) "" else ":${uri.port}"
        return "$scheme://$host$port"
    }

    private fun canonicalUrl(uri: URI, scheme: String): String? {
        val origin = canonicalOrigin(uri) ?: return null
        val rawPath = uri.rawPath.orEmpty().ifEmpty { "/" }
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
        return "$origin$rawPath$query$fragment"
    }
}
