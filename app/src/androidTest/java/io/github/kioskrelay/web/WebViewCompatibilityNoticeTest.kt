package io.github.kioskrelay.web

import androidx.compose.ui.input.key.Key
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
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

    @Test fun versionBar_remainsAboveFullSizeErrorContent() {
        compose.setContent {
            KioskRelayTheme {
                WebViewVersionFrame(Modifier.fillMaxSize(), WebViewCompatibility("90.0.4430.91", 90, true)) {
                    Surface(Modifier.fillMaxSize()) { Text("Page failed") }
                }
            }
        }
        val bar = compose.onNodeWithTag("webview-version-bar").assertIsDisplayed()
        val error = compose.onNodeWithText("Page failed").assertIsDisplayed()
        assertTrue(bar.fetchSemanticsNode().boundsInRoot.bottom <= error.fetchSemanticsNode().boundsInRoot.top)
    }

    @Test fun unidentifiedVersion_alertDismissesByRemoteAndBarRemains() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            KioskRelayTheme {
                WebViewVersionFrame(Modifier.fillMaxSize(), WebViewCompatibility("WebView ?", 90, true, false)) {
                    Text("Webpage")
                }
            }
        }
        val warning = compose.onNodeWithText(context.getString(R.string.web_version_unknown))
        warning.assertIsDisplayed()
        val confirm = compose.onNodeWithText(context.getString(R.string.confirm))
        confirm.assertIsFocused()
        confirm.performKeyInput { pressKey(Key.DirectionCenter) }
        warning.assertDoesNotExist()
        compose.onNodeWithTag("webview-version-bar").assertIsDisplayed()
        compose.onNodeWithText("Webpage").assertIsDisplayed()
    }

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
