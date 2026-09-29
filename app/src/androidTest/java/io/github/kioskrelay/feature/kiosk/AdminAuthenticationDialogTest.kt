package io.github.kioskrelay.feature.kiosk

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import io.github.kioskrelay.R
import io.github.kioskrelay.security.AdminAuthenticator
import io.github.kioskrelay.security.AuthenticationResult
import io.github.kioskrelay.security.PasswordSetResult
import io.github.kioskrelay.ui.theme.KioskRelayTheme
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AdminAuthenticationDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun validPassword_opensProtectedSettings() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val authenticated = AtomicBoolean(false)
        composeRule.setContent {
            KioskRelayTheme {
                AdminAuthenticationDialog(
                    authenticator = SuccessfulAuthenticator,
                    onDismiss = {},
                    onAuthenticated = { authenticated.set(true) },
                )
            }
        }

        composeRule.onNodeWithTag("admin-password").assertIsFocused()
        composeRule.onNodeWithTag("admin-password").performTextInput("secure1")
        composeRule.onNodeWithTag("admin-password").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithText(context.getString(R.string.cancel)).assertIsFocused()
        composeRule.onNodeWithText(context.getString(R.string.cancel)).performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithText(context.getString(R.string.unlock)).assertIsFocused()
        composeRule.onNodeWithText(context.getString(R.string.unlock)).performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.waitUntil(timeoutMillis = 2_000) { authenticated.get() }
        assertTrue(authenticated.get())
    }

    private object SuccessfulAuthenticator : AdminAuthenticator {
        override suspend fun hasPassword(): Boolean = true

        override suspend fun setPassword(password: CharArray): PasswordSetResult =
            PasswordSetResult.Success

        override suspend fun verify(password: CharArray): AuthenticationResult =
            if (password.concatToString() == "secure1") {
                AuthenticationResult.Success
            } else {
                AuthenticationResult.InvalidCredential(remainingAttemptsBeforeLock = 4)
            }

        override suspend fun clear() = Unit
    }
}
