package io.github.kioskrelay.data

import io.github.kioskrelay.config.AppLocale
import io.github.kioskrelay.config.BrandingConfig
import io.github.kioskrelay.config.ConfigDefaults
import io.github.kioskrelay.config.ConfigRules
import io.github.kioskrelay.config.KioskRelayConfig
import io.github.kioskrelay.config.RuntimeConfig
import io.github.kioskrelay.config.ScreenOrientation
import io.github.kioskrelay.config.WebViewConfig
import io.github.kioskrelay.proto.AppLocaleProto
import io.github.kioskrelay.proto.BrandingConfigProto
import io.github.kioskrelay.proto.KioskRelayConfigProto
import io.github.kioskrelay.proto.RuntimeConfigProto
import io.github.kioskrelay.proto.ScreenOrientationProto
import io.github.kioskrelay.proto.WebViewConfigProto

object ConfigProtoMapper {
    val defaultProto: KioskRelayConfigProto
        get() = ConfigDefaults.config.toProto()

    fun KioskRelayConfig.toProto(): KioskRelayConfigProto {
        val safe = ConfigRules.validate(ConfigRules.canonicalize(this))
        return KioskRelayConfigProto.newBuilder()
            .setSchemaVersion(safe.schemaVersion)
            .setLocale(safe.locale.toProto())
            .setOnboardingCompleted(safe.onboardingCompleted)
            .setBranding(safe.branding.toProto())
            .setWebView(safe.webView.toProto())
            .setRuntime(safe.runtime.toProto())
            .build()
    }

    fun KioskRelayConfigProto.toDomain(): KioskRelayConfig {
        val defaults = ConfigDefaults.config
        val brandingProto = if (hasBranding()) branding else null
        val webViewProto = if (hasWebView()) webView else null
        val runtimeProto = if (hasRuntime()) runtime else null

        val config = KioskRelayConfig(
            schemaVersion = if (hasSchemaVersion()) schemaVersion else defaults.schemaVersion,
            locale = if (hasLocale()) locale.toDomain() else defaults.locale,
            onboardingCompleted = if (hasOnboardingCompleted()) {
                onboardingCompleted
            } else {
                defaults.onboardingCompleted
            },
            branding = BrandingConfig(
                productName = brandingProto.optionalString(
                    BrandingConfigProto::hasProductName,
                    BrandingConfigProto::getProductName,
                    defaults.branding.productName,
                ),
                logoRelativePath = brandingProto.optionalNullableString(
                    BrandingConfigProto::hasLogoRelativePath,
                    BrandingConfigProto::getLogoRelativePath,
                ),
                splashRelativePath = brandingProto.optionalNullableString(
                    BrandingConfigProto::hasSplashRelativePath,
                    BrandingConfigProto::getSplashRelativePath,
                ),
                primaryColorArgb = if (brandingProto?.hasPrimaryColorArgb() == true) {
                    brandingProto.primaryColorArgb
                } else {
                    defaults.branding.primaryColorArgb
                },
                backgroundColorArgb = if (brandingProto?.hasBackgroundColorArgb() == true) {
                    brandingProto.backgroundColorArgb
                } else {
                    defaults.branding.backgroundColorArgb
                },
                loadingMessage = brandingProto.optionalString(
                    BrandingConfigProto::hasLoadingMessage,
                    BrandingConfigProto::getLoadingMessage,
                    defaults.branding.loadingMessage,
                ),
                offlineMessage = brandingProto.optionalString(
                    BrandingConfigProto::hasOfflineMessage,
                    BrandingConfigProto::getOfflineMessage,
                    defaults.branding.offlineMessage,
                ),
                errorMessage = brandingProto.optionalString(
                    BrandingConfigProto::hasErrorMessage,
                    BrandingConfigProto::getErrorMessage,
                    defaults.branding.errorMessage,
                ),
            ),
            webView = WebViewConfig(
                initialUrl = webViewProto.optionalString(
                    WebViewConfigProto::hasInitialUrl,
                    WebViewConfigProto::getInitialUrl,
                    defaults.webView.initialUrl,
                ),
                allowedOrigins = webViewProto?.allowedOriginsList?.toCollection(linkedSetOf())
                    ?: defaults.webView.allowedOrigins,
                allowHttp = webViewProto.optionalBoolean(
                    WebViewConfigProto::hasAllowHttp,
                    WebViewConfigProto::getAllowHttp,
                    defaults.webView.allowHttp,
                ),
                orientation = if (webViewProto?.hasOrientation() == true) {
                    webViewProto.orientation.toDomain()
                } else {
                    defaults.webView.orientation
                },
                fullscreen = webViewProto.optionalBoolean(
                    WebViewConfigProto::hasFullscreen,
                    WebViewConfigProto::getFullscreen,
                    defaults.webView.fullscreen,
                ),
                keepScreenOn = webViewProto.optionalBoolean(
                    WebViewConfigProto::hasKeepScreenOn,
                    WebViewConfigProto::getKeepScreenOn,
                    defaults.webView.keepScreenOn,
                ),
                supportZoom = webViewProto.optionalBoolean(
                    WebViewConfigProto::hasSupportZoom,
                    WebViewConfigProto::getSupportZoom,
                    defaults.webView.supportZoom,
                ),
                customUserAgent = webViewProto.optionalString(
                    WebViewConfigProto::hasCustomUserAgent,
                    WebViewConfigProto::getCustomUserAgent,
                    defaults.webView.customUserAgent,
                ),
                acceptCookies = webViewProto.optionalBoolean(
                    WebViewConfigProto::hasAcceptCookies,
                    WebViewConfigProto::getAcceptCookies,
                    defaults.webView.acceptCookies,
                ),
                refreshOnNetworkRecovery = webViewProto.optionalBoolean(
                    WebViewConfigProto::hasRefreshOnNetworkRecovery,
                    WebViewConfigProto::getRefreshOnNetworkRecovery,
                    defaults.webView.refreshOnNetworkRecovery,
                ),
            ),
            runtime = RuntimeConfig(
                retryDelaysSeconds = runtimeProto?.retryDelaysSecondsList
                    ?.ifEmpty { defaults.runtime.retryDelaysSeconds }
                    ?: defaults.runtime.retryDelaysSeconds,
                bootStartEnabled = runtimeProto.optionalBoolean(
                    RuntimeConfigProto::hasBootStartEnabled,
                    RuntimeConfigProto::getBootStartEnabled,
                    defaults.runtime.bootStartEnabled,
                ),
                bootDelaySeconds = if (runtimeProto?.hasBootDelaySeconds() == true) {
                    runtimeProto.bootDelaySeconds
                } else {
                    defaults.runtime.bootDelaySeconds
                },
            ),
        )
        return ConfigRules.validate(ConfigRules.canonicalize(config))
    }

    private fun BrandingConfig.toProto(): BrandingConfigProto =
        BrandingConfigProto.newBuilder()
            .setProductName(productName)
            .also { builder -> logoRelativePath?.let(builder::setLogoRelativePath) }
            .also { builder -> splashRelativePath?.let(builder::setSplashRelativePath) }
            .setPrimaryColorArgb(primaryColorArgb)
            .setBackgroundColorArgb(backgroundColorArgb)
            .setLoadingMessage(loadingMessage)
            .setOfflineMessage(offlineMessage)
            .setErrorMessage(errorMessage)
            .build()

    private fun WebViewConfig.toProto(): WebViewConfigProto =
        WebViewConfigProto.newBuilder()
            .setInitialUrl(initialUrl)
            .addAllAllowedOrigins(allowedOrigins)
            .setAllowHttp(allowHttp)
            .setOrientation(orientation.toProto())
            .setFullscreen(fullscreen)
            .setKeepScreenOn(keepScreenOn)
            .setSupportZoom(supportZoom)
            .setCustomUserAgent(customUserAgent)
            .setAcceptCookies(acceptCookies)
            .setRefreshOnNetworkRecovery(refreshOnNetworkRecovery)
            .build()

    private fun RuntimeConfig.toProto(): RuntimeConfigProto =
        RuntimeConfigProto.newBuilder()
            .addAllRetryDelaysSeconds(retryDelaysSeconds)
            .setBootStartEnabled(bootStartEnabled)
            .setBootDelaySeconds(bootDelaySeconds)
            .build()

    private fun AppLocale.toProto(): AppLocaleProto = when (this) {
        AppLocale.SYSTEM -> AppLocaleProto.APP_LOCALE_SYSTEM
        AppLocale.ZH_CN -> AppLocaleProto.APP_LOCALE_ZH_CN
        AppLocale.EN -> AppLocaleProto.APP_LOCALE_EN
    }

    private fun AppLocaleProto.toDomain(): AppLocale = when (this) {
        AppLocaleProto.APP_LOCALE_ZH_CN -> AppLocale.ZH_CN
        AppLocaleProto.APP_LOCALE_EN -> AppLocale.EN
        AppLocaleProto.APP_LOCALE_SYSTEM,
        AppLocaleProto.UNRECOGNIZED,
        -> AppLocale.SYSTEM
    }

    private fun ScreenOrientation.toProto(): ScreenOrientationProto = when (this) {
        ScreenOrientation.LANDSCAPE -> ScreenOrientationProto.SCREEN_ORIENTATION_LANDSCAPE
        ScreenOrientation.PORTRAIT -> ScreenOrientationProto.SCREEN_ORIENTATION_PORTRAIT
        ScreenOrientation.SENSOR -> ScreenOrientationProto.SCREEN_ORIENTATION_SENSOR
    }

    private fun ScreenOrientationProto.toDomain(): ScreenOrientation = when (this) {
        ScreenOrientationProto.SCREEN_ORIENTATION_PORTRAIT -> ScreenOrientation.PORTRAIT
        ScreenOrientationProto.SCREEN_ORIENTATION_SENSOR -> ScreenOrientation.SENSOR
        ScreenOrientationProto.SCREEN_ORIENTATION_LANDSCAPE,
        ScreenOrientationProto.UNRECOGNIZED,
        -> ScreenOrientation.LANDSCAPE
    }

    private inline fun <T> T?.optionalString(
        hasValue: T.() -> Boolean,
        getValue: T.() -> String,
        fallback: String,
    ): String = if (this != null && hasValue()) getValue() else fallback

    private inline fun <T> T?.optionalNullableString(
        hasValue: T.() -> Boolean,
        getValue: T.() -> String,
    ): String? = if (this != null && hasValue()) getValue().ifEmpty { null } else null

    private inline fun <T> T?.optionalBoolean(
        hasValue: T.() -> Boolean,
        getValue: T.() -> Boolean,
        fallback: Boolean,
    ): Boolean = if (this != null && hasValue()) getValue() else fallback
}
