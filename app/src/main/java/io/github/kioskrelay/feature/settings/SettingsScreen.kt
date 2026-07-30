package io.github.kioskrelay.feature.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.kioskrelay.R
import io.github.kioskrelay.config.AppLocale
import io.github.kioskrelay.config.KioskRelayConfig
import io.github.kioskrelay.config.ScreenOrientation
import io.github.kioskrelay.ui.theme.KioskBackground
import io.github.kioskrelay.ui.theme.KioskSurface
import kotlin.math.roundToInt

enum class SettingsSection {
    BRAND,
    WEB,
    RUNTIME,
    SECURITY,
    MAINTENANCE,
}

enum class MaintenanceAction {
    RELOAD,
    CLEAR_CACHE,
    CLEAR_COOKIES,
    CLEAR_WEB_DATA,
    EXPORT_CONFIG,
    IMPORT_CONFIG,
    VIEW_DIAGNOSTICS,
    EXPORT_DIAGNOSTICS,
    RESET_DEFAULTS,
}

@Composable
fun SettingsScreen(
    initialConfig: KioskRelayConfig,
    onSave: (KioskRelayConfig) -> Unit,
    onDismiss: () -> Unit,
    onLogoSelected: (Uri, (String?) -> Unit) -> Unit,
    onSplashSelected: (Uri, (String?) -> Unit) -> Unit,
    onChangePassword: (String) -> Unit,
    onMaintenanceAction: (MaintenanceAction, KioskRelayConfig) -> Unit,
    operationInProgress: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var draft by remember(initialConfig) { mutableStateOf(initialConfig) }
    var section by rememberSaveable { mutableStateOf(SettingsSection.BRAND) }
    var passwordDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDestructiveAction by rememberSaveable {
        mutableStateOf<MaintenanceAction?>(null)
    }
    var confirmHttpEnable by rememberSaveable { mutableStateOf(false) }
    // An in-flight coroutine belongs to the current composition and must not leave a restored
    // screen permanently disabled after Activity/process recreation.
    var pendingImageImports by remember { mutableIntStateOf(0) }
    val interactionBlocked = operationInProgress || pendingImageImports > 0

    BackHandler {
        if (!interactionBlocked) onDismiss()
    }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri ->
            pendingImageImports++
            onLogoSelected(uri) { relativePath ->
                pendingImageImports = (pendingImageImports - 1).coerceAtLeast(0)
                if (relativePath != null) {
                    draft = draft.copy(
                        branding = draft.branding.copy(logoRelativePath = relativePath),
                    )
                }
            }
        }
    }
    val splashPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri ->
            pendingImageImports++
            onSplashSelected(uri) { relativePath ->
                pendingImageImports = (pendingImageImports - 1).coerceAtLeast(0)
                if (relativePath != null) {
                    draft = draft.copy(
                        branding = draft.branding.copy(splashRelativePath = relativePath),
                    )
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = KioskBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KioskSurface)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !interactionBlocked,
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = { onSave(draft) },
                    enabled = !interactionBlocked,
                    modifier = Modifier.testTag("settings-save"),
                ) {
                    Text(stringResource(R.string.save_and_close))
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val wide = maxWidth >= 900.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    SettingsNavigation(
                        selected = section,
                        onSelected = { section = it },
                        modifier = Modifier
                            .width(260.dp)
                            .fillMaxHeight(),
                    )
                    SettingsContent(
                        section = section,
                        draft = draft,
                        onDraftChange = {
                            if (it.webView.allowHttp && !draft.webView.allowHttp) {
                                confirmHttpEnable = true
                            } else {
                                draft = it
                            }
                        },
                        onChooseLogo = { logoPicker.launch(arrayOf("image/*")) },
                        onChooseSplash = { splashPicker.launch(arrayOf("image/*")) },
                        imageImportInProgress = pendingImageImports > 0,
                        operationInProgress = operationInProgress,
                        onPasswordClick = { passwordDialog = true },
                        onAction = {
                            if (
                                it == MaintenanceAction.CLEAR_COOKIES ||
                                it == MaintenanceAction.CLEAR_WEB_DATA ||
                                it == MaintenanceAction.RESET_DEFAULTS
                            ) {
                                pendingDestructiveAction = it
                            } else {
                                onMaintenanceAction(it, draft)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    SettingsNavigation(
                        selected = section,
                        onSelected = { section = it },
                        horizontal = true,
                    )
                    SettingsContent(
                        section = section,
                        draft = draft,
                        onDraftChange = {
                            if (it.webView.allowHttp && !draft.webView.allowHttp) {
                                confirmHttpEnable = true
                            } else {
                                draft = it
                            }
                        },
                        onChooseLogo = { logoPicker.launch(arrayOf("image/*")) },
                        onChooseSplash = { splashPicker.launch(arrayOf("image/*")) },
                        imageImportInProgress = pendingImageImports > 0,
                        operationInProgress = operationInProgress,
                        onPasswordClick = { passwordDialog = true },
                        onAction = {
                            if (
                                it == MaintenanceAction.CLEAR_COOKIES ||
                                it == MaintenanceAction.CLEAR_WEB_DATA ||
                                it == MaintenanceAction.RESET_DEFAULTS
                            ) {
                                pendingDestructiveAction = it
                            } else {
                                onMaintenanceAction(it, draft)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (passwordDialog) {
        ChangePasswordDialog(
            onDismiss = { passwordDialog = false },
            onConfirm = {
                passwordDialog = false
                onChangePassword(it)
            },
        )
    }

    pendingDestructiveAction?.let { action ->
        ConfirmationDialog(
            onDismiss = { pendingDestructiveAction = null },
            onConfirm = {
                pendingDestructiveAction = null
                onMaintenanceAction(action, draft)
            },
        )
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
                        draft = draft.copy(
                            webView = draft.webView.copy(allowHttp = true),
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
private fun SettingsNavigation(
    selected: SettingsSection,
    onSelected: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
) {
    val entries = listOf(
        SettingsSection.BRAND to R.string.settings_brand,
        SettingsSection.WEB to R.string.settings_web,
        SettingsSection.RUNTIME to R.string.settings_runtime,
        SettingsSection.SECURITY to R.string.settings_security,
        SettingsSection.MAINTENANCE to R.string.settings_maintenance,
    )
    val focusRequesters = remember {
        List(entries.size) { FocusRequester() }
    }
    LaunchedEffect(focusRequesters) {
        focusRequesters.first().requestFocus()
    }
    if (horizontal) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(KioskSurface)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            entries.forEachIndexed { index, (section, title) ->
                OutlinedButton(
                    onClick = { onSelected(section) },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequesters[index])
                        .focusProperties {
                            if (index > 0) {
                                left = focusRequesters[index - 1]
                                up = focusRequesters[index - 1]
                                previous = focusRequesters[index - 1]
                            }
                            if (index < entries.lastIndex) {
                                right = focusRequesters[index + 1]
                                down = focusRequesters[index + 1]
                                next = focusRequesters[index + 1]
                            }
                        },
                ) {
                    Text(stringResource(title), maxLines = 1)
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .background(KioskSurface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            entries.forEachIndexed { index, (section, title) ->
                val selectedBackground = if (section == selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                } else {
                    Color.Transparent
                }
                Text(
                    text = stringResource(title),
                    fontWeight = if (section == selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequesters[index])
                        .focusProperties {
                            if (index > 0) {
                                left = focusRequesters[index - 1]
                                up = focusRequesters[index - 1]
                                previous = focusRequesters[index - 1]
                            }
                            if (index < entries.lastIndex) {
                                right = focusRequesters[index + 1]
                                down = focusRequesters[index + 1]
                                next = focusRequesters[index + 1]
                            }
                        }
                        .background(selectedBackground, RoundedCornerShape(12.dp))
                        .clickable { onSelected(section) }
                        .padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsContent(
    section: SettingsSection,
    draft: KioskRelayConfig,
    onDraftChange: (KioskRelayConfig) -> Unit,
    onChooseLogo: () -> Unit,
    onChooseSplash: () -> Unit,
    imageImportInProgress: Boolean,
    operationInProgress: Boolean,
    onPasswordClick: () -> Unit,
    onAction: (MaintenanceAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (section) {
            SettingsSection.BRAND -> BrandSettings(
                draft,
                onDraftChange,
                onChooseLogo,
                onChooseSplash,
                imageImportInProgress,
                operationInProgress,
            )
            SettingsSection.WEB -> WebSettings(draft, onDraftChange)
            SettingsSection.RUNTIME -> RuntimeSettings(draft, onDraftChange)
            SettingsSection.SECURITY -> SecuritySettings(draft, onDraftChange, onPasswordClick)
            SettingsSection.MAINTENANCE -> MaintenanceSettings(
                onAction = onAction,
                enabled = !imageImportInProgress && !operationInProgress,
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun BrandSettings(
    draft: KioskRelayConfig,
    onDraftChange: (KioskRelayConfig) -> Unit,
    onChooseLogo: () -> Unit,
    onChooseSplash: () -> Unit,
    imageImportInProgress: Boolean,
    operationInProgress: Boolean,
) {
    SettingsHeader(R.string.settings_brand)
    OutlinedTextField(
        value = draft.branding.productName,
        onValueChange = {
            onDraftChange(draft.copy(branding = draft.branding.copy(productName = it.take(60))))
        },
        label = { Text(stringResource(R.string.product_name)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    ColorField(
        label = stringResource(R.string.primary_color_hex),
        value = draft.branding.primaryColorArgb,
    ) {
        onDraftChange(draft.copy(branding = draft.branding.copy(primaryColorArgb = it)))
    }
    ColorField(
        label = stringResource(R.string.background_color_hex),
        value = draft.branding.backgroundColorArgb,
    ) {
        onDraftChange(draft.copy(branding = draft.branding.copy(backgroundColorArgb = it)))
    }
    OutlinedTextField(
        value = draft.branding.loadingMessage,
        onValueChange = {
            onDraftChange(draft.copy(branding = draft.branding.copy(loadingMessage = it.take(160))))
        },
        label = { Text(stringResource(R.string.loading_message)) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.branding.offlineMessage,
        onValueChange = {
            onDraftChange(draft.copy(branding = draft.branding.copy(offlineMessage = it.take(160))))
        },
        label = { Text(stringResource(R.string.offline_message)) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.branding.errorMessage,
        onValueChange = {
            onDraftChange(draft.copy(branding = draft.branding.copy(errorMessage = it.take(160))))
        },
        label = { Text(stringResource(R.string.error_message)) },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = onChooseLogo,
            enabled = !imageImportInProgress && !operationInProgress,
        ) {
            Text(stringResource(R.string.choose_logo))
        }
        OutlinedButton(
            onClick = onChooseSplash,
            enabled = !imageImportInProgress && !operationInProgress,
        ) {
            Text(stringResource(R.string.choose_splash_background))
        }
    }
    if (imageImportInProgress) {
        Text(
            stringResource(R.string.image_import_in_progress),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WebSettings(
    draft: KioskRelayConfig,
    onDraftChange: (KioskRelayConfig) -> Unit,
) {
    SettingsHeader(R.string.settings_web)
    OutlinedTextField(
        value = draft.webView.initialUrl,
        onValueChange = {
            onDraftChange(draft.copy(webView = draft.webView.copy(initialUrl = it.trim())))
        },
        label = { Text(stringResource(R.string.dashboard_url)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings-url"),
        singleLine = true,
    )
    OutlinedTextField(
        value = draft.webView.allowedOrigins.joinToString("\n"),
        onValueChange = { origins ->
            onDraftChange(
                draft.copy(
                    webView = draft.webView.copy(
                        allowedOrigins = origins.lineSequence()
                            .map(String::trim)
                            .filter(String::isNotBlank)
                            .toSet(),
                    ),
                ),
            )
        },
        label = { Text(stringResource(R.string.allowed_origins)) },
        supportingText = { Text(stringResource(R.string.allowed_origins_hint)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
    )
    SettingsSwitch(
        stringResource(R.string.allow_http),
        draft.webView.allowHttp,
    ) {
        onDraftChange(draft.copy(webView = draft.webView.copy(allowHttp = it)))
    }
    Text(stringResource(R.string.orientation), fontWeight = FontWeight.SemiBold)
    ChoiceRow(
        entries = listOf(
            ScreenOrientation.LANDSCAPE to stringResource(R.string.orientation_landscape),
            ScreenOrientation.PORTRAIT to stringResource(R.string.orientation_portrait),
            ScreenOrientation.SENSOR to stringResource(R.string.orientation_system),
        ),
        selected = draft.webView.orientation,
    ) {
        onDraftChange(draft.copy(webView = draft.webView.copy(orientation = it)))
    }
    SettingsSwitch(
        stringResource(R.string.immersive_fullscreen),
        draft.webView.fullscreen,
    ) {
        onDraftChange(draft.copy(webView = draft.webView.copy(fullscreen = it)))
    }
    SettingsSwitch(
        stringResource(R.string.keep_screen_on),
        draft.webView.keepScreenOn,
    ) {
        onDraftChange(draft.copy(webView = draft.webView.copy(keepScreenOn = it)))
    }
    SettingsSwitch(
        stringResource(R.string.accept_cookies),
        draft.webView.acceptCookies,
    ) {
        onDraftChange(draft.copy(webView = draft.webView.copy(acceptCookies = it)))
    }
    SettingsSwitch(
        stringResource(R.string.support_zoom),
        draft.webView.supportZoom,
    ) {
        onDraftChange(draft.copy(webView = draft.webView.copy(supportZoom = it)))
    }
    OutlinedTextField(
        value = draft.webView.customUserAgent,
        onValueChange = {
            onDraftChange(draft.copy(webView = draft.webView.copy(customUserAgent = it.take(512))))
        },
        label = { Text(stringResource(R.string.custom_user_agent)) },
        supportingText = { Text(stringResource(R.string.custom_user_agent_hint)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RuntimeSettings(
    draft: KioskRelayConfig,
    onDraftChange: (KioskRelayConfig) -> Unit,
) {
    SettingsHeader(R.string.settings_runtime)
    SettingsSwitch(
        stringResource(R.string.reload_on_network),
        draft.webView.refreshOnNetworkRecovery,
    ) {
        onDraftChange(
            draft.copy(webView = draft.webView.copy(refreshOnNetworkRecovery = it)),
        )
    }
    SettingsSwitch(
        stringResource(R.string.boot_launch),
        draft.runtime.bootStartEnabled,
    ) {
        onDraftChange(draft.copy(runtime = draft.runtime.copy(bootStartEnabled = it)))
    }
    if (draft.runtime.bootStartEnabled) {
        Text(stringResource(R.string.boot_delay, draft.runtime.bootDelaySeconds))
        androidx.compose.material3.Slider(
            value = draft.runtime.bootDelaySeconds.toFloat(),
            onValueChange = {
                onDraftChange(
                    draft.copy(
                        runtime = draft.runtime.copy(
                            bootDelaySeconds = it.roundToInt().coerceIn(0, 60),
                        ),
                    ),
                )
            },
            valueRange = 0f..60f,
            steps = 11,
        )
    }
    Text(
        stringResource(R.string.retry_schedule),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SecuritySettings(
    draft: KioskRelayConfig,
    onDraftChange: (KioskRelayConfig) -> Unit,
    onPasswordClick: () -> Unit,
) {
    SettingsHeader(R.string.settings_security)
    Text(
        stringResource(R.string.change_password_hint),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = onPasswordClick) {
        Text(stringResource(R.string.change_password))
    }
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.onboarding_language), fontWeight = FontWeight.SemiBold)
    ChoiceRow(
        entries = listOf(
            AppLocale.SYSTEM to stringResource(R.string.language_system),
            AppLocale.ZH_CN to stringResource(R.string.language_chinese),
            AppLocale.EN to stringResource(R.string.language_english),
        ),
        selected = draft.locale,
    ) {
        onDraftChange(draft.copy(locale = it))
    }
}

@Composable
private fun MaintenanceSettings(
    onAction: (MaintenanceAction) -> Unit,
    enabled: Boolean,
) {
    SettingsHeader(R.string.settings_maintenance)
    val actions = listOf(
        MaintenanceAction.RELOAD to R.string.reload_page,
        MaintenanceAction.CLEAR_CACHE to R.string.clear_cache,
        MaintenanceAction.CLEAR_COOKIES to R.string.clear_cookies,
        MaintenanceAction.CLEAR_WEB_DATA to R.string.clear_web_data,
        MaintenanceAction.EXPORT_CONFIG to R.string.export_configuration,
        MaintenanceAction.IMPORT_CONFIG to R.string.import_configuration,
        MaintenanceAction.VIEW_DIAGNOSTICS to R.string.view_diagnostics,
        MaintenanceAction.EXPORT_DIAGNOSTICS to R.string.export_diagnostics,
        MaintenanceAction.RESET_DEFAULTS to R.string.restore_defaults,
    )
    actions.forEach { (action, label) ->
        OutlinedButton(
            onClick = { onAction(action) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(label))
        }
    }
}

@Composable
private fun ColorField(label: String, value: Long, onValue: (Long) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toArgbHex()) }
    OutlinedTextField(
        value = text,
        onValueChange = { candidate ->
            text = candidate.take(9).uppercase()
            parseArgb(text)?.let(onValue)
        },
        label = { Text(label) },
        leadingIcon = {
            Box(
                Modifier
                    .padding(8.dp)
                    .height(24.dp)
                    .width(24.dp)
                    .background(Color(value), RoundedCornerShape(6.dp)),
            )
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun SettingsHeader(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.headlineMedium,
    )
    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
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
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
                modifier = Modifier.weight(1f),
            ) {
                Text(if (selected == value) "✓ $label" else label)
            }
        }
    }
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    // Keep credentials in memory only; they must not survive Activity state restoration.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val valid = password.length in 6..64 && password == confirmation
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.change_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(64) },
                    label = { Text(stringResource(R.string.new_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it.take(64) },
                    label = { Text(stringResource(R.string.confirm_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = confirmation.isNotEmpty() && confirmation != password,
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(password) }, enabled = valid) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(R.string.destructive_confirmation)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private fun Long.toArgbHex(): String = "#%08X".format(this and 0xFFFFFFFFL)

private fun parseArgb(value: String): Long? {
    val normalized = value.removePrefix("#")
    if (normalized.length !in setOf(6, 8)) return null
    return normalized.toLongOrNull(16)?.let {
        if (normalized.length == 6) it or 0xFF000000L else it
    }
}
