package io.github.kioskrelay.data

import io.github.kioskrelay.config.AppLocale
import io.github.kioskrelay.config.BrandingConfig
import io.github.kioskrelay.config.ConfigDefaults
import io.github.kioskrelay.config.ConfigRules
import io.github.kioskrelay.config.KioskRelayConfig
import io.github.kioskrelay.config.RuntimeConfig
import io.github.kioskrelay.config.ScreenOrientation
import io.github.kioskrelay.config.WebViewConfig
import org.json.JSONArray
import org.json.JSONObject

object ConfigJsonCodec {
    fun encode(config: KioskRelayConfig): String {
        val safe = ConfigRules.validate(ConfigRules.canonicalize(config))
        return JSONObject()
            .put("schemaVersion", safe.schemaVersion)
            .put("locale", safe.locale.name)
            .put("onboardingCompleted", safe.onboardingCompleted)
            .put(
                "branding",
                JSONObject()
                    .put("productName", safe.branding.productName)
                    .putNullable("logoRelativePath", safe.branding.logoRelativePath)
                    .putNullable("splashRelativePath", safe.branding.splashRelativePath)
                    .put("primaryColorArgb", safe.branding.primaryColorArgb)
                    .put("backgroundColorArgb", safe.branding.backgroundColorArgb)
                    .put("loadingMessage", safe.branding.loadingMessage)
                    .put("offlineMessage", safe.branding.offlineMessage)
                    .put("errorMessage", safe.branding.errorMessage),
            )
            .put(
                "webView",
                JSONObject()
                    .put("initialUrl", safe.webView.initialUrl)
                    .put("allowedOrigins", JSONArray(safe.webView.allowedOrigins.toList()))
                    .put("allowHttp", safe.webView.allowHttp)
                    .put("orientation", safe.webView.orientation.name)
                    .put("fullscreen", safe.webView.fullscreen)
                    .put("keepScreenOn", safe.webView.keepScreenOn)
                    .put("supportZoom", safe.webView.supportZoom)
                    .put("customUserAgent", safe.webView.customUserAgent)
                    .put("acceptCookies", safe.webView.acceptCookies)
                    .put(
                        "refreshOnNetworkRecovery",
                        safe.webView.refreshOnNetworkRecovery,
                    ),
            )
            .put(
                "runtime",
                JSONObject()
                    .put("retryDelaysSeconds", JSONArray(safe.runtime.retryDelaysSeconds))
                    .put("bootStartEnabled", safe.runtime.bootStartEnabled)
                    .put("bootDelaySeconds", safe.runtime.bootDelaySeconds),
            )
            .toString(2)
    }

    fun decode(json: String): KioskRelayConfig {
        val defaults = ConfigDefaults.config
        val root = JSONObject(json)
        val schemaVersion = root.optInt("schemaVersion", 0)
        if (schemaVersion != defaults.schemaVersion) {
            throw ConfigArchiveException(
                ArchiveError.UNSUPPORTED_SCHEMA,
                "Unsupported config schema version: $schemaVersion",
            )
        }

        val branding = root.optJSONObject("branding") ?: JSONObject()
        val webView = root.optJSONObject("webView") ?: JSONObject()
        val runtime = root.optJSONObject("runtime") ?: JSONObject()

        val decoded = KioskRelayConfig(
            schemaVersion = schemaVersion,
            locale = root.optEnum("locale", defaults.locale),
            onboardingCompleted = root.optBoolean(
                "onboardingCompleted",
                defaults.onboardingCompleted,
            ),
            branding = BrandingConfig(
                productName = branding.optString(
                    "productName",
                    defaults.branding.productName,
                ),
                logoRelativePath = branding.optNullableString("logoRelativePath"),
                splashRelativePath = branding.optNullableString("splashRelativePath"),
                primaryColorArgb = branding.optLong(
                    "primaryColorArgb",
                    defaults.branding.primaryColorArgb,
                ),
                backgroundColorArgb = branding.optLong(
                    "backgroundColorArgb",
                    defaults.branding.backgroundColorArgb,
                ),
                loadingMessage = branding.optString(
                    "loadingMessage",
                    defaults.branding.loadingMessage,
                ),
                offlineMessage = branding.optString(
                    "offlineMessage",
                    defaults.branding.offlineMessage,
                ),
                errorMessage = branding.optString(
                    "errorMessage",
                    defaults.branding.errorMessage,
                ),
            ),
            webView = WebViewConfig(
                initialUrl = webView.optString(
                    "initialUrl",
                    defaults.webView.initialUrl,
                ),
                allowedOrigins = webView.optStringSet("allowedOrigins"),
                allowHttp = webView.optBoolean("allowHttp", defaults.webView.allowHttp),
                orientation = webView.optEnum(
                    "orientation",
                    defaults.webView.orientation,
                ),
                fullscreen = webView.optBoolean(
                    "fullscreen",
                    defaults.webView.fullscreen,
                ),
                keepScreenOn = webView.optBoolean(
                    "keepScreenOn",
                    defaults.webView.keepScreenOn,
                ),
                supportZoom = webView.optBoolean(
                    "supportZoom",
                    defaults.webView.supportZoom,
                ),
                customUserAgent = webView.optString(
                    "customUserAgent",
                    defaults.webView.customUserAgent,
                ),
                acceptCookies = webView.optBoolean(
                    "acceptCookies",
                    defaults.webView.acceptCookies,
                ),
                refreshOnNetworkRecovery = webView.optBoolean(
                    "refreshOnNetworkRecovery",
                    defaults.webView.refreshOnNetworkRecovery,
                ),
            ),
            runtime = RuntimeConfig(
                retryDelaysSeconds = runtime.optIntList(
                    "retryDelaysSeconds",
                    defaults.runtime.retryDelaysSeconds,
                ),
                bootStartEnabled = runtime.optBoolean(
                    "bootStartEnabled",
                    defaults.runtime.bootStartEnabled,
                ),
                bootDelaySeconds = runtime.optInt(
                    "bootDelaySeconds",
                    defaults.runtime.bootDelaySeconds,
                ),
            ),
        )
        return ConfigRules.validate(ConfigRules.canonicalize(decoded))
    }

    private fun JSONObject.putNullable(key: String, value: String?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key).ifEmpty { null }

    private inline fun <reified T : Enum<T>> JSONObject.optEnum(
        key: String,
        fallback: T,
    ): T {
        val raw = optString(key, fallback.name)
        return enumValues<T>().firstOrNull { it.name == raw } ?: fallback
    }

    private fun JSONObject.optStringSet(key: String): Set<String> {
        val array = optJSONArray(key) ?: return emptySet()
        return buildSet(array.length()) {
            for (index in 0 until array.length()) add(array.getString(index))
        }
    }

    private fun JSONObject.optIntList(key: String, fallback: List<Int>): List<Int> {
        val array = optJSONArray(key) ?: return fallback
        return buildList(array.length()) {
            for (index in 0 until array.length()) add(array.getInt(index))
        }
    }
}
