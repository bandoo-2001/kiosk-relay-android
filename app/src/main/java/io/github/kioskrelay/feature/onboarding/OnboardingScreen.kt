package io.github.kioskrelay.feature.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.kioskrelay.R
import io.github.kioskrelay.config.BrandingConfig
import io.github.kioskrelay.data.BrandImageImporter
import io.github.kioskrelay.ui.BrandLogo
import io.github.kioskrelay.ui.rememberBrandImage
import io.github.kioskrelay.ui.theme.KioskBackground
import io.github.kioskrelay.ui.theme.KioskBlue
import io.github.kioskrelay.ui.theme.KioskCyan
import io.github.kioskrelay.ui.theme.KioskSurface
import java.net.URI
import kotlin.math.roundToInt

enum class SetupLocale {
    SYSTEM,
    ZH_CN,
    ENGLISH,
    ZH_TW,
    SPANISH,
    JAPANESE,
    KOREAN,
}

enum class SetupOrientation { LANDSCAPE, PORTRAIT, FOLLOW_SYSTEM }

enum class WebTestResult { NOT_RUN, SUCCEEDED, FAILED }

data class OnboardingDraft(
    val locale: SetupLocale = SetupLocale.SYSTEM,
    val productName: String = "KioskRelay",
    val primaryColor: Long = 0xFF2F80ED,
    val backgroundColor: Long = 0xFF071A2B,
    val logoUri: String = "",
    val splashUri: String = "",
    val url: String = "",
    val allowHttp: Boolean = false,
    val orientation: SetupOrientation = SetupOrientation.LANDSCAPE,
    val fullscreen: Boolean = true,
    val keepScreenOn: Boolean = true,
    val reloadOnNetworkRecovery: Boolean = true,
    val bootLaunchEnabled: Boolean = false,
    val bootDelaySeconds: Int = 10,
    val webTestResult: WebTestResult = WebTestResult.NOT_RUN,
)

@Composable
fun OnboardingScreen(
    draft: OnboardingDraft,
    onDraftChange: (OnboardingDraft) -> Unit,
    onTestUrl: () -> Unit,
    onFinish: (password: String) -> Unit,
    onLocaleApplied: (SetupLocale) -> Unit,
    onLogoSelected: (Uri) -> Unit,
    onSplashSelected: (Uri) -> Unit,
    imageImporter: BrandImageImporter,
    imageImportInProgress: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    // Credentials must never be copied into Activity saved state.
    var password by remember { mutableStateOf("") }
    var passwordConfirmation by remember { mutableStateOf("") }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var confirmHttpEnable by rememberSaveable { mutableStateOf(false) }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            onDraftChange(draft.copy(logoUri = it.toString()))
            onLogoSelected(it)
        }
    }
    val splashPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            onDraftChange(draft.copy(splashUri = it.toString()))
            onSplashSelected(it)
        }
    }

    fun currentStepValid(): Boolean = when (step) {
        1 -> draft.productName.trim().isNotEmpty()
        2 -> isValidDashboardUrl(draft.url, draft.allowHttp)
        3 -> isOptionalAdministratorPasswordValid(password, passwordConfirmation)
        else -> true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0B3960), KioskBackground),
                    radius = 1_100f,
                ),
            ),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val isWide = maxWidth >= 900.dp
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (isWide) 64.dp else 20.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isWide) {
                    WelcomeRail(
                        currentStep = step,
                        modifier = Modifier
                            .weight(0.8f)
                            .fillMaxHeight()
                            .padding(end = 48.dp),
                    )
                }

                Card(
                    modifier = Modifier
                        .weight(1.2f)
                        .widthIn(max = 760.dp)
                        .fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = KioskSurface.copy(alpha = 0.96f)),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 28.dp, vertical = 24.dp),
                    ) {
                        StepIndicator(step)
                        Spacer(Modifier.height(20.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            when (step) {
                                0 -> WelcomeStep(
                                    selected = draft.locale,
                                    onSelected = {
                                        onDraftChange(draft.copy(locale = it))
                                        onLocaleApplied(it)
                                    },
                                )
                                1 -> BrandStep(
                                    draft = draft,
                                    imageImporter = imageImporter,
                                    imageImportInProgress = imageImportInProgress,
                                    showErrors = showErrors,
                                    onDraftChange = onDraftChange,
                                    onChooseLogo = { logoPicker.launch(arrayOf("image/*")) },
                                    onChooseSplash = { splashPicker.launch(arrayOf("image/*")) },
                                )
                                2 -> WebStep(
                                    draft = draft,
                                    showErrors = showErrors,
                                    onDraftChange = { candidate ->
                                        if (candidate.allowHttp && !draft.allowHttp) {
                                            confirmHttpEnable = true
                                        } else {
                                            onDraftChange(candidate)
                                        }
                                    },
                                    onTestUrl = onTestUrl,
                                )
                                else -> SecurityStep(
                                    draft = draft,
                                    password = password,
                                    confirmation = passwordConfirmation,
                                    showErrors = showErrors,
                                    onPasswordChange = { password = it.take(64) },
                                    onConfirmationChange = { passwordConfirmation = it.take(64) },
                                    onDraftChange = onDraftChange,
                                )
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                        ) {
                            if (step > 0) {
                                OutlinedButton(onClick = {
                                    showErrors = false
                                    step--
                                }) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.ArrowBack,
                                        contentDescription = null,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.previous))
                                }
                            }
                            Button(
                                modifier = Modifier.testTag("onboarding-next"),
                                enabled = !imageImportInProgress,
                                onClick = {
                                    if (!currentStepValid()) {
                                        showErrors = true
                                    } else if (step < 3) {
                                        showErrors = false
                                        step++
                                    } else {
                                        onFinish(password)
                                    }
                                },
                            ) {
                                Icon(
                                    if (step == 3) {
                                        Icons.Outlined.Check
                                    } else {
                                        Icons.AutoMirrored.Outlined.ArrowForward
                                    },
                                    contentDescription = null,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(
                                        if (step == 3) {
                                            R.string.finish_configuration
                                        } else {
                                            R.string.next
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmHttpEnable) {
        AlertDialog(
            onDismissRequest = { confirmHttpEnable = false },
            title = { Text(stringResource(R.string.http_risk_confirm_title)) },
            text = { Text(stringResource(R.string.http_risk_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmHttpEnable = false
                        onDraftChange(
                            draft.copy(
                                allowHttp = true,
                                webTestResult = WebTestResult.NOT_RUN,
                            ),
                        )
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmHttpEnable = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun WelcomeRail(currentStep: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "KioskRelay",
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        repeat(4) { index ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (index <= currentStep) KioskCyan else Color.White.copy(alpha = 0.18f),
                        ),
                )
                Text(
                    text = "${index + 1}",
                    modifier = Modifier.padding(start = 12.dp),
                    color = if (index == currentStep) Color.White else Color.White.copy(alpha = 0.58f),
                )
            }
        }
    }
}

@Composable
private fun StepIndicator(step: Int) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(4) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(
                        if (index <= step) {
                            Brush.horizontalGradient(listOf(KioskBlue, KioskCyan))
                        } else {
                            Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.12f),
                                    Color.White.copy(alpha = 0.12f),
                                ),
                            )
                        },
                    ),
            )
        }
    }
}

@Composable
private fun WelcomeStep(
    selected: SetupLocale,
    onSelected: (SetupLocale) -> Unit,
) {
    SectionTitle(
        title = stringResource(R.string.onboarding_welcome_title),
        body = stringResource(R.string.onboarding_welcome_body),
    )
    Spacer(Modifier.height(28.dp))
    Text(stringResource(R.string.onboarding_language), fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
    ChoiceRow(
        entries = listOf(
            SetupLocale.SYSTEM to stringResource(R.string.language_system),
            SetupLocale.ZH_CN to stringResource(R.string.language_chinese),
            SetupLocale.ENGLISH to stringResource(R.string.language_english),
            SetupLocale.ZH_TW to stringResource(R.string.language_chinese_traditional),
            SetupLocale.SPANISH to stringResource(R.string.language_spanish),
            SetupLocale.JAPANESE to stringResource(R.string.language_japanese),
            SetupLocale.KOREAN to stringResource(R.string.language_korean),
        ),
        selected = selected,
        onSelected = onSelected,
    )
}

@Composable
private fun BrandStep(
    draft: OnboardingDraft,
    imageImporter: BrandImageImporter,
    imageImportInProgress: Boolean,
    showErrors: Boolean,
    onDraftChange: (OnboardingDraft) -> Unit,
    onChooseLogo: () -> Unit,
    onChooseSplash: () -> Unit,
) {
    SectionTitle(stringResource(R.string.onboarding_brand_title))
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = draft.productName,
        onValueChange = { onDraftChange(draft.copy(productName = it.take(60))) },
        label = { Text(stringResource(R.string.product_name)) },
        isError = showErrors && draft.productName.isBlank(),
        supportingText = {
            if (showErrors && draft.productName.isBlank()) {
                Text(stringResource(R.string.field_required))
            }
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding-product-name"),
    )
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.brand_color), fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BRAND_COLORS.forEach { colorValue ->
            val color = Color(colorValue)
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable {
                        onDraftChange(draft.copy(primaryColor = colorValue))
                    }
                    .padding(if (draft.primaryColor == colorValue) 4.dp else 0.dp),
            )
        }
    }
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = onChooseLogo,
            enabled = !imageImportInProgress,
        ) {
            Icon(Icons.Outlined.Image, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.choose_logo))
        }
        OutlinedButton(
            onClick = onChooseSplash,
            enabled = !imageImportInProgress,
        ) {
            Icon(Icons.Outlined.Image, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.choose_splash_background))
        }
    }
    if (imageImportInProgress) {
        Text(
            stringResource(R.string.image_import_in_progress),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (draft.logoUri.isNotBlank() || draft.splashUri.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = listOfNotNull(
                draft.logoUri.takeIf(String::isNotBlank)?.let { stringResource(R.string.choose_logo) },
                draft.splashUri.takeIf(String::isNotBlank)?.let {
                    stringResource(R.string.choose_splash_background)
                },
            ).joinToString(" · "),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(24.dp))
    BrandingPreview(draft, imageImporter)
}

@Composable
private fun BrandingPreview(
    draft: OnboardingDraft,
    imageImporter: BrandImageImporter,
) {
    val backgroundImage = rememberBrandImage(
        importer = imageImporter,
        relativePath = draft.splashUri.takeIf { it == "splash.webp" },
    )
    val branding = BrandingConfig(
        productName = draft.productName.ifBlank { "KioskRelay" },
        logoRelativePath = draft.logoUri.takeIf { it == "logo.webp" },
        primaryColorArgb = draft.primaryColor,
        backgroundColorArgb = draft.backgroundColor,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .testTag("branding-preview")
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(draft.backgroundColor),
                        Color(draft.primaryColor).copy(alpha = 0.55f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        backgroundImage?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f)),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandLogo(
                branding = branding,
                imageImporter = imageImporter,
                modifier = Modifier.size(76.dp),
            )
            Text(
                text = branding.productName,
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

@Composable
private fun WebStep(
    draft: OnboardingDraft,
    showErrors: Boolean,
    onDraftChange: (OnboardingDraft) -> Unit,
    onTestUrl: () -> Unit,
) {
    val validUrl = isValidDashboardUrl(draft.url, draft.allowHttp)
    SectionTitle(stringResource(R.string.onboarding_web_title))
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = draft.url,
        onValueChange = {
            onDraftChange(draft.copy(url = it.trim(), webTestResult = WebTestResult.NOT_RUN))
        },
        label = { Text(stringResource(R.string.dashboard_url)) },
        placeholder = { Text(stringResource(R.string.dashboard_url_hint)) },
        isError = showErrors && !validUrl,
        supportingText = {
            if (showErrors && !validUrl) Text(stringResource(R.string.invalid_url))
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding-url"),
    )
    SettingsSwitch(
        title = stringResource(R.string.allow_http),
        checked = draft.allowHttp,
        onCheckedChange = {
            onDraftChange(draft.copy(allowHttp = it, webTestResult = WebTestResult.NOT_RUN))
        },
    )
    if (draft.allowHttp) {
        Text(
            stringResource(R.string.http_warning),
            color = MaterialTheme.colorScheme.tertiary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Spacer(Modifier.height(18.dp))
    Text(stringResource(R.string.orientation), fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
    ChoiceRow(
        entries = listOf(
            SetupOrientation.LANDSCAPE to stringResource(R.string.orientation_landscape),
            SetupOrientation.PORTRAIT to stringResource(R.string.orientation_portrait),
            SetupOrientation.FOLLOW_SYSTEM to stringResource(R.string.orientation_system),
        ),
        selected = draft.orientation,
        onSelected = { onDraftChange(draft.copy(orientation = it)) },
    )
    SettingsSwitch(
        stringResource(R.string.immersive_fullscreen),
        draft.fullscreen,
    ) { onDraftChange(draft.copy(fullscreen = it)) }
    SettingsSwitch(
        stringResource(R.string.keep_screen_on),
        draft.keepScreenOn,
    ) { onDraftChange(draft.copy(keepScreenOn = it)) }
    SettingsSwitch(
        stringResource(R.string.reload_on_network),
        draft.reloadOnNetworkRecovery,
    ) { onDraftChange(draft.copy(reloadOnNetworkRecovery = it)) }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = onTestUrl,
        enabled = validUrl,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.test_webpage))
    }
    Text(
        text = stringResource(
            when (draft.webTestResult) {
                WebTestResult.NOT_RUN -> R.string.test_not_run
                WebTestResult.SUCCEEDED -> R.string.test_succeeded
                WebTestResult.FAILED -> R.string.test_failed
            },
        ),
        color = when (draft.webTestResult) {
            WebTestResult.NOT_RUN -> MaterialTheme.colorScheme.onSurfaceVariant
            WebTestResult.SUCCEEDED -> MaterialTheme.colorScheme.tertiary
            WebTestResult.FAILED -> MaterialTheme.colorScheme.error
        },
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SecurityStep(
    draft: OnboardingDraft,
    password: String,
    confirmation: String,
    showErrors: Boolean,
    onPasswordChange: (String) -> Unit,
    onConfirmationChange: (String) -> Unit,
    onDraftChange: (OnboardingDraft) -> Unit,
) {
    val passwordInvalid =
        showErrors && !isOptionalAdministratorPasswordValid(password, confirmation)
    SectionTitle(stringResource(R.string.onboarding_security_title))
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(R.string.administrator_password_optional)) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError = passwordInvalid,
        supportingText = { Text(stringResource(R.string.password_optional_rule)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding-password"),
    )
    OutlinedTextField(
        value = confirmation,
        onValueChange = onConfirmationChange,
        label = { Text(stringResource(R.string.confirm_password)) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError = passwordInvalid,
        supportingText = {
            if (showErrors && confirmation != password) {
                Text(stringResource(R.string.password_mismatch))
            }
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding-password-confirmation"),
    )
    SettingsSwitch(
        title = stringResource(R.string.boot_launch),
        checked = draft.bootLaunchEnabled,
        onCheckedChange = { onDraftChange(draft.copy(bootLaunchEnabled = it)) },
    )
    Text(
        stringResource(R.string.boot_launch_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (draft.bootLaunchEnabled) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.boot_delay, draft.bootDelaySeconds))
        Slider(
            value = draft.bootDelaySeconds.toFloat(),
            onValueChange = {
                onDraftChange(draft.copy(bootDelaySeconds = it.roundToInt().coerceIn(0, 60)))
            },
            valueRange = 0f..60f,
            steps = 11,
        )
    }
    Spacer(Modifier.height(20.dp))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(draft.productName, fontWeight = FontWeight.SemiBold)
            Text(draft.url, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(
                    when (draft.orientation) {
                        SetupOrientation.LANDSCAPE -> R.string.orientation_landscape
                        SetupOrientation.PORTRAIT -> R.string.orientation_portrait
                        SetupOrientation.FOLLOW_SYSTEM -> R.string.orientation_system
                    },
                ),
            )
        }
    }
}

internal fun isOptionalAdministratorPasswordValid(
    password: String,
    confirmation: String,
): Boolean =
    (password.isEmpty() && confirmation.isEmpty()) ||
        (password.length in 6..64 && password == confirmation)

@Composable
private fun SectionTitle(title: String, body: String? = null) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    body?.let {
        Spacer(Modifier.height(10.dp))
        Text(
            it,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun <T> ChoiceRow(
    entries: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        entries.forEach { (value, label) ->
            OutlinedButton(
                onClick = { onSelected(value) },
                modifier = Modifier.widthIn(min = 140.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Checkbox(
                    checked = selected == value,
                    onCheckedChange = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(label, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

internal fun isValidDashboardUrl(value: String, allowHttp: Boolean): Boolean {
    return runCatching {
        val uri = URI(value.trim())
        val scheme = uri.scheme?.lowercase()
        val allowedScheme = scheme == "https" || (allowHttp && scheme == "http")
        allowedScheme && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
}

private val BRAND_COLORS = listOf(
    0xFF2F80ED,
    0xFF26D9E8,
    0xFF56C271,
    0xFF8C63E8,
    0xFFFF8B4D,
    0xFFEB5C7A,
)
