package io.github.kioskrelay

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.kioskrelay.config.AppLocale
import io.github.kioskrelay.config.KioskRelayConfig
import io.github.kioskrelay.config.ScreenOrientation
import io.github.kioskrelay.startup.StartupLauncher
import io.github.kioskrelay.security.AdminEntryDetector
import io.github.kioskrelay.ui.theme.KioskRelayTheme
import io.github.kioskrelay.web.ImmersiveModeController

class MainActivity : AppCompatActivity() {
    private var activePresentationConfig: KioskRelayConfig? = null
    private var tvAdminEntryDetector = AdminEntryDetector()
    private var tvAdminEntryHandler: (() -> Unit)? = null

    private val container: AppContainer
        get() = (application as KioskRelayApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val initialDelayMillis = consumeBootDelayMillis()
        setContent {
            KioskRelayTheme {
                KioskRelayApp(
                    container = container,
                    initialDelayMillis = initialDelayMillis,
                    onPresentationPolicyChanged = ::applyPresentationPolicy,
                    onLocaleChanged = ::applyLocale,
                    registerTvAdminEntryHandler = ::registerTvAdminEntryHandler,
                )
            }
        }
    }

    private fun applyPresentationPolicy(config: KioskRelayConfig) {
        activePresentationConfig = config
        requestedOrientation = when (config.webView.orientation) {
            ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            ScreenOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ScreenOrientation.SENSOR -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        ImmersiveModeController.apply(
            activity = this,
            fullscreen = config.webView.fullscreen,
            keepScreenOn = config.webView.keepScreenOn,
        )
    }

    private fun applyLocale(locale: AppLocale) {
        val languageTags = when (locale) {
            AppLocale.SYSTEM -> ""
            AppLocale.ZH_CN -> "zh-CN"
            AppLocale.EN -> "en"
            AppLocale.ZH_TW -> "zh-TW"
            AppLocale.ES -> "es"
            AppLocale.JA -> "ja"
            AppLocale.KO -> "ko"
        }
        val desired = LocaleListCompat.forLanguageTags(languageTags)
        if (AppCompatDelegate.getApplicationLocales() != desired) {
            AppCompatDelegate.setApplicationLocales(desired)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        activePresentationConfig?.let { config ->
            ImmersiveModeController.onWindowFocusChanged(
                activity = this,
                hasFocus = hasFocus,
                fullscreen = config.webView.fullscreen,
                keepScreenOn = config.webView.keepScreenOn,
            )
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handler = tvAdminEntryHandler
        if (handler != null && tvAdminEntryDetector.registerKeyEvent(event)) {
            handler()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun registerTvAdminEntryHandler(handler: (() -> Unit)?) {
        tvAdminEntryHandler = handler
        tvAdminEntryDetector = AdminEntryDetector()
    }

    private fun consumeBootDelayMillis(): Long {
        if (!intent.getBooleanExtra(StartupLauncher.EXTRA_LAUNCHED_FROM_BOOT, false)) {
            return 0L
        }
        val seconds = intent.getIntExtra(
            StartupLauncher.EXTRA_BOOT_DELAY_SECONDS,
            0,
        ).coerceIn(0, 60)
        intent.removeExtra(StartupLauncher.EXTRA_LAUNCHED_FROM_BOOT)
        intent.removeExtra(StartupLauncher.EXTRA_BOOT_DELAY_SECONDS)
        return seconds * 1_000L
    }
}
