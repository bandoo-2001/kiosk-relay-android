package io.github.kioskrelay.feature.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.pressKey
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
    fun fourStepFlow_acceptsUntestedValidPageAndFinishesWithoutPassword() {
        val finished = AtomicBoolean(false)
        setScreen { finished.set(true) }

        composeRule.onNodeWithTag(NEXT).performClick()
        composeRule.onNodeWithTag("branding-preview").assertExists()
        composeRule.onNodeWithTag(NEXT).performClick()
        composeRule.onNodeWithTag("onboarding-url")
            .performTextInput("https://display.example.com/dashboard")
        composeRule.onNodeWithTag(NEXT).performClick()
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
        val toggle = composeRule.onNodeWithText(context.getString(R.string.allow_http))
        toggle.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        toggle.performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.onNodeWithText(
            context.getString(R.string.http_risk_confirm_title),
        ).assertExists()
        val cancel = composeRule.onNodeWithText(context.getString(R.string.cancel))
        cancel.assertIsFocused()
        cancel.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithText(context.getString(R.string.http_risk_confirm_title)).assertDoesNotExist()
    }

    @Test
    fun welcomeStep_exposesEverySupportedLanguage() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        setScreen()

        listOf(
            R.string.language_system,
            R.string.language_chinese,
            R.string.language_english,
            R.string.language_chinese_traditional,
            R.string.language_spanish,
            R.string.language_japanese,
            R.string.language_korean,
        ).forEach { label ->
            composeRule.onNodeWithText(context.getString(label)).assertExists()
        }
    }

    @Test
    fun delaySlider_downLeavesValueUnchangedAndReachesFinish() {
        setScreen(initialDraft = OnboardingDraft(url = "https://display.example.com/", bootLaunchEnabled = true))
        repeat(3) { composeRule.onNodeWithTag(NEXT).performClick() }
        val slider = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        slider.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        slider.performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithText(ApplicationProvider.getApplicationContext<android.content.Context>().getString(R.string.boot_delay, 15)).assertExists()
        slider.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithText(ApplicationProvider.getApplicationContext<android.content.Context>().getString(R.string.boot_delay, 15)).assertExists()
        composeRule.onNodeWithTag("onboarding-previous").assertIsFocused()
        composeRule.onNodeWithTag("onboarding-previous").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag(NEXT).assertIsFocused()
    }

    private fun setScreen(initialDraft: OnboardingDraft = OnboardingDraft(), onFinish: () -> Unit = {}) {
        composeRule.setContent {
            var draft by remember { mutableStateOf(initialDraft) }
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
