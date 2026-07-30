package io.github.kioskrelay.feature.settings

import android.view.KeyEvent
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kioskrelay.R
import io.github.kioskrelay.config.ConfigDefaults
import io.github.kioskrelay.config.KioskRelayConfig
import io.github.kioskrelay.ui.theme.KioskRelayTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
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
    fun dpadRight_movesFocusAcrossSettingsSections() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.setInTouchMode(true)
        try {
            setScreen()
            composeRule.waitForIdle()
            val brand = composeRule.onNodeWithTag("settings-section-brand")
            val web = composeRule.onNodeWithTag("settings-section-web")

            // The first hardware key leaves touch mode and establishes the initial focus.
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            composeRule.waitUntil(timeoutMillis = 2_000) {
                brand.fetchSemanticsNode().config.getOrElse(
                    androidx.compose.ui.semantics.SemanticsProperties.Focused,
                ) { false }
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
        onSave: (KioskRelayConfig) -> Unit = {},
        onAction: (MaintenanceAction) -> Unit = {},
        operationInProgress: Boolean = false,
    ) {
        val configured = ConfigDefaults.config.copy(
            onboardingCompleted = true,
            webView = ConfigDefaults.config.webView.copy(
                initialUrl = "https://display.example.com/",
                allowedOrigins = setOf("https://display.example.com"),
            ),
        )
        composeRule.setContent {
            KioskRelayTheme {
                SettingsScreen(
                    initialConfig = configured,
                    onSave = onSave,
                    onDismiss = {},
                    onLogoSelected = { _, complete -> complete(null) },
                    onSplashSelected = { _, complete -> complete(null) },
                    onChangePassword = {},
                    onMaintenanceAction = { action, _ -> onAction(action) },
                    operationInProgress = operationInProgress,
                )
            }
        }
    }
}
