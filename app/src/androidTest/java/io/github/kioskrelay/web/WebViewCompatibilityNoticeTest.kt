package io.github.kioskrelay.web

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import io.github.kioskrelay.R
import io.github.kioskrelay.ui.theme.KioskRelayTheme
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WebViewCompatibilityNoticeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun oldEngine_requiresExplicitRemoteAcknowledgement() {
        val accepted = AtomicBoolean(false)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            KioskRelayTheme {
                WebViewCompatibilityNotice(WebViewCompatibility("WebView 79", 79, true)) { accepted.set(true) }
            }
        }
        val settings = compose.onNodeWithText(context.getString(R.string.web_system_settings))
        settings.assertIsFocused()
        settings.performKeyInput { pressKey(Key.DirectionDown) }
        val proceed = compose.onNodeWithText(context.getString(R.string.web_try_anyway))
        proceed.assertIsFocused()
        proceed.performKeyInput { pressKey(Key.DirectionCenter) }
        assertTrue(accepted.get())
    }

    @Test fun missingEngine_doesNotOfferUnusableLoadAction() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            KioskRelayTheme {
                WebViewCompatibilityNotice(WebViewCompatibility("Unavailable", null, false)) {}
            }
        }
        compose.onNodeWithText(context.getString(R.string.web_try_anyway)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.web_engine_missing)).assertExists()
    }
}
