package io.github.kioskrelay.config

import io.github.kioskrelay.data.ConfigJsonCodec
import io.github.kioskrelay.data.ConfigProtoMapper.toDomain
import io.github.kioskrelay.data.ConfigProtoMapper.toProto
import io.github.kioskrelay.proto.KioskRelayConfigProto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigAndUrlPolicyTest {
    @Test
    fun defaults_matchIndustrialTabletProfileAndAndroidSeven() {
        val defaults = ConfigDefaults.config

        assertEquals(CURRENT_CONFIG_SCHEMA_VERSION, defaults.schemaVersion)
        assertEquals("KioskRelay", defaults.branding.productName)
        assertEquals(0xFF2F80EDL, defaults.branding.primaryColorArgb)
        assertEquals(0xFF071A2BL, defaults.branding.backgroundColorArgb)
        assertEquals(ScreenOrientation.LANDSCAPE, defaults.webView.orientation)
        assertTrue(defaults.webView.fullscreen)
        assertTrue(defaults.webView.keepScreenOn)
        assertFalse(defaults.webView.allowHttp)
        assertEquals(listOf(5, 10, 20, 40, 60, 60), defaults.runtime.retryDelaysSeconds)
        assertEquals(10, defaults.runtime.bootDelaySeconds)
        assertFalse(defaults.onboardingCompleted)
    }

    @Test
    fun config_roundTripsThroughProtoAndJson() {
        val config = configured()

        assertEquals(config, config.toProto().toDomain())
        assertEquals(config, ConfigJsonCodec.decode(ConfigJsonCodec.encode(config)))
    }

    @Test
    fun partialSchemaV1Proto_migratesMissingFieldsToSafeDefaults() {
        val partial = KioskRelayConfigProto.newBuilder()
            .setSchemaVersion(CURRENT_CONFIG_SCHEMA_VERSION)
            .build()

        val migrated = partial.toDomain()

        assertEquals(ConfigDefaults.config, migrated)
        assertTrue(migrated.webView.fullscreen)
        assertTrue(migrated.webView.keepScreenOn)
        assertEquals(10, migrated.runtime.bootDelaySeconds)
    }

    @Test
    fun canonicalize_addsInitialOriginAndNormalizesDefaultPort() {
        val config = ConfigRules.canonicalize(
            ConfigDefaults.config.copy(
                webView = ConfigDefaults.config.webView.copy(
                    initialUrl = "https://EXAMPLE.com:443/dashboard?station=1",
                ),
            ),
        )

        assertEquals(
            "https://example.com/dashboard?station=1",
            config.webView.initialUrl,
        )
        assertEquals(setOf("https://example.com"), config.webView.allowedOrigins)
    }

    @Test
    fun http_requiresExplicitOptIn() {
        val rejected = UrlPolicy.validateInitialUrl(
            rawUrl = "http://device.local/",
            allowHttp = false,
        )
        val accepted = UrlPolicy.validateInitialUrl(
            rawUrl = "http://device.local/",
            allowHttp = true,
        )

        assertFalse(rejected.isValid)
        assertEquals(UrlValidationError.HTTP_DISABLED, rejected.error)
        assertTrue(accepted.isValid)
        assertEquals("http://device.local", accepted.origin)
    }

    @Test
    fun originMatch_isExactForSchemeHostAndPort() {
        val allowlist = setOf("https://display.example.com:8443")

        assertTrue(
            UrlPolicy.isNavigationAllowed(
                "https://display.example.com:8443/page",
                allowlist,
                allowHttp = false,
            ),
        )
        assertFalse(
            UrlPolicy.isNavigationAllowed(
                "https://sub.display.example.com:8443/page",
                allowlist,
                allowHttp = false,
            ),
        )
        assertFalse(
            UrlPolicy.isNavigationAllowed(
                "https://display.example.com/page",
                allowlist,
                allowHttp = false,
            ),
        )
        assertFalse(
            UrlPolicy.isNavigationAllowed(
                "http://display.example.com:8443/page",
                allowlist,
                allowHttp = true,
            ),
        )
    }

    @Test
    fun unsafeSchemesAndCredentials_areRejected() {
        assertNull(UrlPolicy.normalizeOrigin("file:///sdcard/page.html"))
        assertNull(UrlPolicy.normalizeOrigin("javascript:alert(1)"))
        assertFalse(
            UrlPolicy.validateInitialUrl(
                "https://admin:secret@example.com/",
                allowHttp = false,
            ).isValid,
        )
    }

    private fun configured(): KioskRelayConfig = ConfigDefaults.config.copy(
        locale = AppLocale.ZH_CN,
        onboardingCompleted = true,
        branding = ConfigDefaults.config.branding.copy(
            productName = "展厅大屏",
            loadingMessage = "加载中",
        ),
        webView = ConfigDefaults.config.webView.copy(
            initialUrl = "https://display.example.com/dashboard",
            allowedOrigins = setOf("https://display.example.com"),
            customUserAgent = "KioskRelay/Test",
        ),
        runtime = ConfigDefaults.config.runtime.copy(
            bootStartEnabled = true,
            bootDelaySeconds = 15,
        ),
    )
}
