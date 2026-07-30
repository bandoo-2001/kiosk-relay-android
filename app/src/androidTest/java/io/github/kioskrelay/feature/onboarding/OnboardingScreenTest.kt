package io.github.kioskrelay.feature.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import io.github.kioskrelay.R
import io.github.kioskrelay.data.BrandImageImporter
import io.github.kioskrelay.data.BrandImageKind
import io.github.kioskrelay.data.ImageImportError
import io.github.kioskrelay.data.ImageImportResult
import io.github.kioskrelay.ui.theme.KioskRelayTheme
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fourStepFlow_acceptsUntestedValidPageAndFinishes() {
        val finished = AtomicBoolean(false)
        setScreen { finished.set(true) }

        composeRule.onNodeWithTag(NEXT).performClick()
        composeRule.onNodeWithTag("branding-preview").assertExists()
        composeRule.onNodeWithTag(NEXT).performClick()
        composeRule.onNodeWithTag("onboarding-url")
            .performTextInput("https://display.example.com/dashboard")
        composeRule.onNodeWithTag(NEXT).performClick()
        composeRule.onNodeWithTag("onboarding-password").performTextInput("secure1")
        composeRule.onNodeWithTag("onboarding-password-confirmation")
            .performTextInput("secure1")
        composeRule.onNodeWithTag(NEXT).performClick()

        composeRule.waitUntil(timeoutMillis = 2_000) { finished.get() }
        assertTrue(finished.get())
    }

    @Test
    fun emptyUrl_keepsWebStepAndShowsValidation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        setScreen()

        composeRule.onNodeWithTag(NEXT).performClick()
        composeRule.onNodeWithTag(NEXT).performClick()
        composeRule.onNodeWithTag(NEXT).performClick()

        composeRule.onNodeWithText(context.getString(R.string.invalid_url)).assertExists()
    }

    @Test
    fun httpRequiresExplicitRiskConfirmation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        setScreen()

        composeRule.onNodeWithTag(NEXT).performClick()
        composeRule.onNodeWithTag(NEXT).performClick()
        composeRule.onNodeWithText(context.getString(R.string.allow_http)).performClick()

        composeRule.onNodeWithText(
            context.getString(R.string.http_risk_confirm_title),
        ).assertExists()
    }

    private fun setScreen(onFinish: () -> Unit = {}) {
        composeRule.setContent {
            var draft by remember { mutableStateOf(OnboardingDraft()) }
            KioskRelayTheme {
                OnboardingScreen(
                    draft = draft,
                    onDraftChange = { draft = it },
                    onTestUrl = {},
                    onFinish = { onFinish() },
                    onLocaleApplied = {},
                    onLogoSelected = {},
                    onSplashSelected = {},
                    imageImporter = MissingImageImporter,
                )
            }
        }
    }

    private object MissingImageImporter : BrandImageImporter {
        override suspend fun importImage(
            uri: android.net.Uri,
            kind: BrandImageKind,
        ): ImageImportResult =
            ImageImportResult.Failure(ImageImportError.INVALID_IMAGE)

        override fun resolve(relativePath: String): File? = null
    }

    private companion object {
        const val NEXT = "onboarding-next"
    }
}
