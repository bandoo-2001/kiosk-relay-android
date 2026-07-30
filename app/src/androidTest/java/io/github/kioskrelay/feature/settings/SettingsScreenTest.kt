package io.github.kioskrelay.feature.settings

import android.view.KeyEvent
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kioskrelay.R
import io.github.kioskrelay.config.AppLocale
import io.github.kioskrelay.config.ConfigDefaults
import io.github.kioskrelay.config.KioskRelayConfig
import io.github.kioskrelay.data.BrandImageImporter
import io.github.kioskrelay.data.BrandImageKind
import io.github.kioskrelay.data.ImageImportError
import io.github.kioskrelay.data.ImageImportResult
import io.github.kioskrelay.ui.theme.KioskRelayTheme
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun urlChange_isReturnedBySave() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val saved = AtomicReference<KioskRelayConfig?>()
        setScreen(onSave = saved::set)

        composeRule.onNodeWithText(context.getString(R.string.settings_web)).performClick()
        composeRule.onNodeWithTag("settings-url").performTextClearance()
        composeRule.onNodeWithTag("settings-url")
            .performTextInput("https://new.example.com/")
        composeRule.onNodeWithTag("settings-save").performClick()

        composeRule.waitUntil(timeoutMillis = 2_000) { saved.get() != null }
        assertEquals("https://new.example.com/", saved.get()?.webView?.initialUrl)
    }

    @Test
    fun restoreDefaults_requiresConfirmation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val action = AtomicReference<MaintenanceAction?>()
        setScreen(onAction = action::set)

        composeRule.onNodeWithText(
            context.getString(R.string.settings_maintenance),
        ).performClick()
        composeRule.onNodeWithText(context.getString(R.string.restore_defaults)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.confirm)).performClick()

        composeRule.waitUntil(timeoutMillis = 2_000) {
            action.get() == MaintenanceAction.RESET_DEFAULTS
        }
        assertEquals(MaintenanceAction.RESET_DEFAULTS, action.get())
    }

    @Test
    fun exportSnapshotPreparation_disablesSaveAndCancel() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        setScreen(operationInProgress = true)

        composeRule.onNodeWithTag("settings-save").assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.cancel)).assertIsNotEnabled()
    }

    @Test
    fun brandSection_showsLocalizedDefaultsAndImagePreviews() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val configured = configured().copy(locale = AppLocale.ZH_CN)

        setScreen(configured = configured)

        composeRule.onNodeWithText(
            localizedString(context, AppLocale.ZH_CN, R.string.default_loading_message),
        ).fetchSemanticsNode()
        composeRule.onNodeWithText(
            localizedString(context, AppLocale.ZH_CN, R.string.default_offline_message),
        ).fetchSemanticsNode()
        composeRule.onNodeWithText(
            localizedString(context, AppLocale.ZH_CN, R.string.brand_default_error_message),
        ).fetchSemanticsNode()
        composeRule.onNodeWithTag("settings-logo-preview").fetchSemanticsNode()
        composeRule.onNodeWithTag("settings-splash-preview").fetchSemanticsNode()
    }

    @Test
    fun everyExplicitLocaleHasLocalizedDefaultMessages() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val english = localizedString(context, AppLocale.EN, R.string.default_loading_message)

        listOf(
            AppLocale.ZH_CN,
            AppLocale.ZH_TW,
            AppLocale.ES,
            AppLocale.JA,
            AppLocale.KO,
        ).forEach { locale ->
            val loading = localizedString(context, locale, R.string.default_loading_message)
            val offline = localizedString(context, locale, R.string.default_offline_message)
            val error = localizedString(context, locale, R.string.brand_default_error_message)
            assertTrue(loading.isNotBlank() && offline.isNotBlank() && error.isNotBlank())
            assertNotEquals(english, loading)
        }
    }

    @Test
    fun removeLogo_restoresDefaultInSavedDraft() {
        val saved = AtomicReference<KioskRelayConfig?>()
        val configured = configured().copy(
            branding = ConfigDefaults.config.branding.copy(
                logoRelativePath = BrandImageKind.LOGO.relativePath,
            ),
        )

        setScreen(
            configured = configured,
            onSave = saved::set,
            onLogoRemoved = { complete -> complete(true) },
        )
        composeRule.onNodeWithTag("settings-remove-logo").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-remove-logo").assertIsNotEnabled()
        composeRule.onNodeWithTag("settings-save").performClick()

        composeRule.waitUntil(timeoutMillis = 2_000) { saved.get() != null }
        assertEquals(null, saved.get()?.branding?.logoRelativePath)
    }

    @Test
    fun dpadRight_movesFocusAcrossSettingsSections() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.setInTouchMode(true)
        try {
            setScreen()
            composeRule.waitForIdle()
            val brand = composeRule.onNodeWithTag("settings-section-brand")
            val web = composeRule.onNodeWithTag("settings-section-web")

            fun brandIsFocused(): Boolean =
                brand.fetchSemanticsNode().config.getOrElse(
                    androidx.compose.ui.semantics.SemanticsProperties.Focused,
                ) { false }

            // Touch devices need one key to leave touch mode. A real TV starts outside
            // touch mode, so the requested initial focus is already active.
            if (!brandIsFocused()) {
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
                composeRule.waitUntil(timeoutMillis = 2_000) {
                    brandIsFocused()
                }
            }
            brand.assertIsFocused()

            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            composeRule.waitUntil(timeoutMillis = 2_000) {
                web.fetchSemanticsNode().config.getOrElse(
                    androidx.compose.ui.semantics.SemanticsProperties.Focused,
                ) { false }
            }
            web.assertIsFocused()
        } finally {
            instrumentation.setInTouchMode(true)
        }
    }

    private fun setScreen(
        configured: KioskRelayConfig = configured(),
        onSave: (KioskRelayConfig) -> Unit = {},
        onAction: (MaintenanceAction) -> Unit = {},
        onLogoRemoved: ((Boolean) -> Unit) -> Unit = { it(true) },
        operationInProgress: Boolean = false,
    ) {
        composeRule.setContent {
            KioskRelayTheme {
                SettingsScreen(
                    initialConfig = configured,
                    onSave = onSave,
                    onDismiss = {},
                    onLogoSelected = { _, complete -> complete(null) },
                    onSplashSelected = { _, complete -> complete(null) },
                    onLogoRemoved = onLogoRemoved,
                    onSplashRemoved = { it(true) },
                    imageImporter = MissingImageImporter,
                    onChangePassword = {},
                    onMaintenanceAction = { action, _ -> onAction(action) },
                    operationInProgress = operationInProgress,
                )
            }
        }
    }

    private fun configured(): KioskRelayConfig =
        ConfigDefaults.config.copy(
            onboardingCompleted = true,
            webView = ConfigDefaults.config.webView.copy(
                initialUrl = "https://display.example.com/",
                allowedOrigins = setOf("https://display.example.com"),
            ),
        )

    private object MissingImageImporter : BrandImageImporter {
        override suspend fun importImage(
            uri: android.net.Uri,
            kind: BrandImageKind,
        ): ImageImportResult =
            ImageImportResult.Failure(ImageImportError.INVALID_IMAGE)

        override fun resolve(relativePath: String): File? = null
    }
}
