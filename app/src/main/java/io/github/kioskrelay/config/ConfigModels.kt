package io.github.kioskrelay.config

const val CURRENT_CONFIG_SCHEMA_VERSION: Int = 1

enum class AppLocale {
    SYSTEM,
    ZH_CN,
    EN,
    ZH_TW,
    ES,
    JA,
    KO,
}

enum class ScreenOrientation {
    LANDSCAPE,
    PORTRAIT,
    SENSOR,
}

data class BrandingConfig(
    val productName: String = "KioskRelay",
    val logoRelativePath: String? = null,
    val splashRelativePath: String? = null,
    val primaryColorArgb: Long = 0xFF2F80EDL,
    val backgroundColorArgb: Long = 0xFF071A2BL,
    val loadingMessage: String = "",
    val offlineMessage: String = "",
    val errorMessage: String = "",
)

data class WebViewConfig(
    val initialUrl: String = "",
    val allowedOrigins: Set<String> = emptySet(),
    val allowHttp: Boolean = false,
    val orientation: ScreenOrientation = ScreenOrientation.LANDSCAPE,
    val fullscreen: Boolean = true,
    val keepScreenOn: Boolean = true,
    val supportZoom: Boolean = false,
    val customUserAgent: String = "",
    val acceptCookies: Boolean = true,
    val refreshOnNetworkRecovery: Boolean = true,
)

data class RuntimeConfig(
    val retryDelaysSeconds: List<Int> = listOf(5, 10, 20, 40, 60, 60),
    val bootStartEnabled: Boolean = false,
    val bootDelaySeconds: Int = 10,
)

data class KioskRelayConfig(
    val schemaVersion: Int = CURRENT_CONFIG_SCHEMA_VERSION,
    val locale: AppLocale = AppLocale.SYSTEM,
    val onboardingCompleted: Boolean = false,
    val branding: BrandingConfig = BrandingConfig(),
    val webView: WebViewConfig = WebViewConfig(),
    val runtime: RuntimeConfig = RuntimeConfig(),
)

object ConfigDefaults {
    val config: KioskRelayConfig
        get() = KioskRelayConfig()
}
