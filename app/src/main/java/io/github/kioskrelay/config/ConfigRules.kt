package io.github.kioskrelay.config

import java.io.File

class ConfigValidationException(message: String) : IllegalArgumentException(message)

object ConfigRules {
    const val MAX_PRODUCT_NAME_LENGTH = 80
    const val MAX_STATUS_MESSAGE_LENGTH = 160
    const val MAX_URL_LENGTH = 2_048
    const val MAX_USER_AGENT_LENGTH = 512
    const val MAX_ALLOWED_ORIGINS = 32
    const val MAX_RETRY_STEPS = 10
    const val MAX_BOOT_DELAY_SECONDS = 60

    fun validate(config: KioskRelayConfig): KioskRelayConfig {
        requireValid(config.schemaVersion == CURRENT_CONFIG_SCHEMA_VERSION) {
            "Unsupported config schema version: ${config.schemaVersion}"
        }
        requireValid(config.branding.productName.isNotBlank()) {
            "Product name must not be blank"
        }
        requireLength("Product name", config.branding.productName, MAX_PRODUCT_NAME_LENGTH)
        requireLength("Loading message", config.branding.loadingMessage, MAX_STATUS_MESSAGE_LENGTH)
        requireLength("Offline message", config.branding.offlineMessage, MAX_STATUS_MESSAGE_LENGTH)
        requireLength("Error message", config.branding.errorMessage, MAX_STATUS_MESSAGE_LENGTH)
        validateAssetPath(config.branding.logoRelativePath)
        validateAssetPath(config.branding.splashRelativePath)
        validateColor(config.branding.primaryColorArgb)
        validateColor(config.branding.backgroundColorArgb)

        requireLength("Initial URL", config.webView.initialUrl, MAX_URL_LENGTH)
        requireLength("User agent", config.webView.customUserAgent, MAX_USER_AGENT_LENGTH)
        requireValid(config.webView.allowedOrigins.size <= MAX_ALLOWED_ORIGINS) {
            "Too many allowed origins"
        }

        if (config.webView.initialUrl.isNotBlank()) {
            val result = UrlPolicy.validateInitialUrl(
                rawUrl = config.webView.initialUrl,
                allowHttp = config.webView.allowHttp,
            )
            requireValid(result.isValid) {
                "Invalid initial URL: ${result.error ?: UrlValidationError.INVALID_URL}"
            }
        } else {
            requireValid(!config.onboardingCompleted) {
                "An initial URL is required after onboarding"
            }
        }

        config.webView.allowedOrigins.forEach { origin ->
            requireLength("Allowed origin", origin, MAX_URL_LENGTH)
            val normalized = UrlPolicy.normalizeOrigin(origin)
            requireValid(normalized != null && normalized == origin) {
                "Allowed origin is not canonical: $origin"
            }
            requireValid(
                origin.startsWith("https://") ||
                    (config.webView.allowHttp && origin.startsWith("http://")),
            ) {
                "Origin scheme is not allowed: $origin"
            }
        }

        requireValid(config.runtime.retryDelaysSeconds.isNotEmpty()) {
            "At least one retry delay is required"
        }
        requireValid(config.runtime.retryDelaysSeconds.size <= MAX_RETRY_STEPS) {
            "Too many retry steps"
        }
        requireValid(config.runtime.retryDelaysSeconds.all { it in 1..300 }) {
            "Retry delays must be between 1 and 300 seconds"
        }
        requireValid(config.runtime.bootDelaySeconds in 0..MAX_BOOT_DELAY_SECONDS) {
            "Boot delay must be between 0 and $MAX_BOOT_DELAY_SECONDS seconds"
        }
        return config
    }

    /**
     * Applies safe canonical forms at trust boundaries. It intentionally does not silently
     * repair malformed URLs or unsupported schemas; callers must reject those via [validate].
     */
    fun canonicalize(config: KioskRelayConfig): KioskRelayConfig {
        val normalizedInitialUrl = if (config.webView.initialUrl.isBlank()) {
            ""
        } else {
            UrlPolicy.validateInitialUrl(
                rawUrl = config.webView.initialUrl,
                allowHttp = config.webView.allowHttp,
            ).normalizedUrl ?: config.webView.initialUrl.trim()
        }
        val normalizedOrigins = config.webView.allowedOrigins.mapTo(linkedSetOf()) {
            UrlPolicy.normalizeOrigin(it) ?: it.trim()
        }
        val initialOrigin = if (normalizedInitialUrl.isBlank()) {
            null
        } else {
            UrlPolicy.normalizeOrigin(normalizedInitialUrl)
        }
        if (initialOrigin != null) normalizedOrigins += initialOrigin

        return config.copy(
            branding = config.branding.copy(
                productName = config.branding.productName.trim(),
                logoRelativePath = config.branding.logoRelativePath?.trim()?.ifEmpty { null },
                splashRelativePath = config.branding.splashRelativePath?.trim()?.ifEmpty { null },
            ),
            webView = config.webView.copy(
                initialUrl = normalizedInitialUrl,
                allowedOrigins = normalizedOrigins,
                customUserAgent = config.webView.customUserAgent.trim(),
            ),
        )
    }

    fun isSafeRelativeAssetPath(path: String): Boolean {
        if (path.isBlank() || path.length > 128 || path.contains('\\') || path.startsWith('/')) {
            return false
        }
        val segments = path.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) return false
        return path == "logo.webp" || path == "splash.webp"
    }

    fun resolveAsset(root: File, relativePath: String): File {
        if (!isSafeRelativeAssetPath(relativePath)) {
            throw ConfigValidationException("Unsafe asset path")
        }
        val canonicalRoot = root.canonicalFile
        val resolved = File(canonicalRoot, relativePath).canonicalFile
        if (resolved.parentFile != canonicalRoot) {
            throw ConfigValidationException("Asset path escapes its root")
        }
        return resolved
    }

    private fun validateAssetPath(path: String?) {
        if (path != null && !isSafeRelativeAssetPath(path)) {
            throw ConfigValidationException("Invalid asset path")
        }
    }

    private fun validateColor(color: Long) {
        requireValid(color in 0..0xFFFF_FFFFL) { "ARGB color is out of range" }
    }

    private fun requireLength(label: String, value: String, maximum: Int) {
        requireValid(value.length <= maximum) { "$label exceeds $maximum characters" }
    }

    private inline fun requireValid(condition: Boolean, lazyMessage: () -> String) {
        if (!condition) throw ConfigValidationException(lazyMessage())
    }
}
