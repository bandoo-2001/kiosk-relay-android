package io.github.kioskrelay.feature.settings

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import io.github.kioskrelay.ui.RemoteButton as Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import io.github.kioskrelay.ui.RemoteOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.kioskrelay.startup.StartupPermissionSettings
import io.github.kioskrelay.ui.remoteVerticalNavigation
import io.github.kioskrelay.ui.BootDelaySlider
import io.github.kioskrelay.ui.RemoteSwitch as SettingsSwitch
import io.github.kioskrelay.ui.remoteFocus
import io.github.kioskrelay.ui.initialFocus
import io.github.kioskrelay.R
import io.github.kioskrelay.config.AppLocale
import io.github.kioskrelay.config.KioskRelayConfig
import io.github.kioskrelay.config.ScreenOrientation
import io.github.kioskrelay.data.BrandImageImporter
import io.github.kioskrelay.data.BrandImageKind
import io.github.kioskrelay.ui.BrandLogo
import io.github.kioskrelay.ui.rememberBrandImage
import io.github.kioskrelay.ui.theme.KioskBackground
import io.github.kioskrelay.ui.theme.KioskSurface
import java.util.Locale

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
    onLogoRemoved: ((Boolean) -> Unit) -> Unit,
    onSplashRemoved: ((Boolean) -> Unit) -> Unit,
    imageImporter: BrandImageImporter,
    onChangePassword: (String) -> Unit,
    onMaintenanceAction: (MaintenanceAction, KioskRelayConfig) -> Unit,
    operationInProgress: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var draft by remember(initialConfig) { mutableStateOf(initialConfig) }
    var section by rememberSaveable { mutableStateOf(SettingsSection.BRAND) }
    val contentFocus = remember { FocusRequester() }
    val navigationFocus = remember { SettingsSection.entries.map { FocusRequester() } }
    val saveFocus = remember { FocusRequester() }
    val contentNavigation = Modifier.focusRequester(contentFocus).focusProperties {
        onExit = {
            when (requestedFocusDirection) {
                FocusDirection.Previous -> navigationFocus[section.ordinal].requestFocus()
                FocusDirection.Next -> saveFocus.requestFocus()
            }
        }
    }.focusGroup()
    var enterContent by remember { mutableIntStateOf(0) }
    LaunchedEffect(enterContent) {
        if (enterContent > 0) contentFocus.requestFocus()
    }
    var passwordDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDestructiveAction by rememberSaveable {
        mutableStateOf<MaintenanceAction?>(null)
    }
    var confirmHttpEnable by rememberSaveable { mutableStateOf(false) }
    // An in-flight coroutine belongs to the current composition and must not leave a restored
    // screen permanently disabled after Activity/process recreation.
    var pendingImageImports by remember { mutableIntStateOf(0) }
    var brandImageRevision by remember { mutableIntStateOf(0) }
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
                    brandImageRevision++
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
                    brandImageRevision++
                    draft = draft.copy(
                        branding = draft.branding.copy(splashRelativePath = relativePath),
                    )
                }
            }
        }
    }
    fun removeBrandImage(kind: BrandImageKind) {
        pendingImageImports++
        val onRemoved: (Boolean) -> Unit = { removed ->
            pendingImageImports = (pendingImageImports - 1).coerceAtLeast(0)
            if (removed) {
                brandImageRevision++
                draft = draft.copy(
                    branding = when (kind) {
                        BrandImageKind.LOGO ->
                            draft.branding.copy(logoRelativePath = null)
                        BrandImageKind.SPLASH ->
                            draft.branding.copy(splashRelativePath = null)
                    },
                )
            }
        }
        when (kind) {
            BrandImageKind.LOGO -> onLogoRemoved(onRemoved)
            BrandImageKind.SPLASH -> onSplashRemoved(onRemoved)
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
                    Icon(Icons.Outlined.Close, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = { onSave(draft) },
                    enabled = !interactionBlocked,
                    modifier = Modifier.focusRequester(saveFocus).testTag("settings-save"),
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
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
                        contentFocus = contentFocus,
                        focusRequesters = navigationFocus,
                        onEnterContent = { enterContent++ },
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
                        onRemoveLogo = { removeBrandImage(BrandImageKind.LOGO) },
                        onRemoveSplash = { removeBrandImage(BrandImageKind.SPLASH) },
                        imageImporter = imageImporter,
                        brandImageRevision = brandImageRevision,
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
                        modifier = Modifier.weight(1f).then(contentNavigation),
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    SettingsNavigation(
                        selected = section,
                        onSelected = { section = it },
                        contentFocus = contentFocus,
                        focusRequesters = navigationFocus,
                        onEnterContent = { enterContent++ },
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
                        onRemoveLogo = { removeBrandImage(BrandImageKind.LOGO) },
                        onRemoveSplash = { removeBrandImage(BrandImageKind.SPLASH) },
                        imageImporter = imageImporter,
                        brandImageRevision = brandImageRevision,
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
                        modifier = Modifier.weight(1f).then(contentNavigation),
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
                OutlinedButton(onClick = { confirmHttpEnable = false }, modifier = Modifier.initialFocus()) {
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
    contentFocus: FocusRequester,
    focusRequesters: List<FocusRequester>,
    onEnterContent: () -> Unit,
) {
    val entries = listOf(
        Triple(SettingsSection.BRAND, R.string.settings_brand, Icons.Outlined.Palette),
        Triple(SettingsSection.WEB, R.string.settings_web, Icons.Outlined.Language),
        Triple(SettingsSection.RUNTIME, R.string.settings_runtime, Icons.Outlined.PlayCircle),
        Triple(SettingsSection.SECURITY, R.string.settings_security, Icons.Outlined.Security),
        Triple(SettingsSection.MAINTENANCE, R.string.settings_maintenance, Icons.Outlined.Build),
    )
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
            entries.forEachIndexed { index, (section, title, icon) ->
                OutlinedButton(
                    onClick = { onSelected(section); onEnterContent() },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("settings-section-${section.name.lowercase()}")
                        .focusRequester(focusRequesters[index])
                        .onFocusChanged { if (it.isFocused) onSelected(section) }
                        .focusProperties {
                            down = contentFocus
                            if (index > 0) {
                                left = focusRequesters[index - 1]
                                previous = focusRequesters[index - 1]
                            }
                            if (index < entries.lastIndex) {
                                right = focusRequesters[index + 1]
                                next = focusRequesters[index + 1]
                            }
                        },
                ) {
                    Icon(icon, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
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
            entries.forEachIndexed { index, (section, title, icon) ->
                val selectedBackground = if (section == selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                } else {
                    Color.Transparent
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings-section-${section.name.lowercase()}")
                        .focusRequester(focusRequesters[index])
                        .onFocusChanged { if (it.isFocused) onSelected(section) }
                        .focusProperties {
                            right = contentFocus
                            if (index > 0) {
                                up = focusRequesters[index - 1]
                                previous = focusRequesters[index - 1]
                            }
                            if (index < entries.lastIndex) {
                                down = focusRequesters[index + 1]
                                next = focusRequesters[index + 1]
                            }
                        }
                        .background(selectedBackground, RoundedCornerShape(12.dp))
                        .remoteFocus()
                        .clickable { onSelected(section); onEnterContent() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(icon, contentDescription = null)
                    Text(
                        text = stringResource(title),
                        fontWeight =
                            if (section == selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
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
    onRemoveLogo: () -> Unit,
    onRemoveSplash: () -> Unit,
    imageImporter: BrandImageImporter,
    brandImageRevision: Int,
    imageImportInProgress: Boolean,
    operationInProgress: Boolean,
    onPasswordClick: () -> Unit,
    onAction: (MaintenanceAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(section) { scroll.scrollTo(0) }
    Column(
        modifier = modifier
            .verticalScroll(scroll)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (section) {
            SettingsSection.BRAND -> BrandSettings(
                draft,
                onDraftChange,
                onChooseLogo,
                onChooseSplash,
                onRemoveLogo,
                onRemoveSplash,
                imageImporter,
                brandImageRevision,
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
    onRemoveLogo: () -> Unit,
    onRemoveSplash: () -> Unit,
    imageImporter: BrandImageImporter,
    brandImageRevision: Int,
    imageImportInProgress: Boolean,
    operationInProgress: Boolean,
) {
    val context = LocalContext.current
    val defaultLoadingMessage = localizedString(
        context = context,
        locale = draft.locale,
        resourceId = R.string.default_loading_message,
    )
    val defaultOfflineMessage = localizedString(
        context = context,
        locale = draft.locale,
        resourceId = R.string.default_offline_message,
    )
    val defaultErrorMessage = localizedString(
        context = context,
        locale = draft.locale,
        resourceId = R.string.brand_default_error_message,
    )
    SettingsHeader(R.string.settings_brand)
    OutlinedTextField(
        value = draft.branding.productName,
        onValueChange = {
            onDraftChange(draft.copy(branding = draft.branding.copy(productName = it.take(60))))
        },
        label = { Text(stringResource(R.string.product_name)) },
        modifier = Modifier.remoteVerticalNavigation().fillMaxWidth(),
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
        value = draft.branding.loadingMessage.ifBlank { defaultLoadingMessage },
        onValueChange = {
            onDraftChange(draft.copy(branding = draft.branding.copy(loadingMessage = it.take(160))))
        },
        label = { Text(stringResource(R.string.loading_message)) },
        modifier = Modifier.remoteVerticalNavigation()
            .fillMaxWidth()
            .testTag("settings-loading-message"),
    )
    OutlinedTextField(
        value = draft.branding.offlineMessage.ifBlank { defaultOfflineMessage },
        onValueChange = {
            onDraftChange(draft.copy(branding = draft.branding.copy(offlineMessage = it.take(160))))
        },
        label = { Text(stringResource(R.string.offline_message)) },
        modifier = Modifier.remoteVerticalNavigation()
            .fillMaxWidth()
            .testTag("settings-offline-message"),
    )
    OutlinedTextField(
        value = draft.branding.errorMessage.ifBlank { defaultErrorMessage },
        onValueChange = {
            onDraftChange(draft.copy(branding = draft.branding.copy(errorMessage = it.take(160))))
        },
        label = { Text(stringResource(R.string.error_message)) },
        modifier = Modifier.remoteVerticalNavigation()
            .fillMaxWidth()
            .testTag("settings-error-message"),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BrandImagePreview(
            title = stringResource(R.string.logo_preview),
            branding = draft.branding,
            imageImporter = imageImporter,
            imageRevision = brandImageRevision,
            kind = BrandImageKind.LOGO,
            hasCustomImage = draft.branding.logoRelativePath != null,
            onChoose = onChooseLogo,
            onRemove = onRemoveLogo,
            enabled = !imageImportInProgress && !operationInProgress,
            modifier = Modifier
                .weight(1f)
                .testTag("settings-logo-preview"),
        )
        BrandImagePreview(
            title = stringResource(R.string.splash_preview),
            branding = draft.branding,
            imageImporter = imageImporter,
            imageRevision = brandImageRevision,
            kind = BrandImageKind.SPLASH,
            hasCustomImage = draft.branding.splashRelativePath != null,
            onChoose = onChooseSplash,
            onRemove = onRemoveSplash,
            enabled = !imageImportInProgress && !operationInProgress,
            modifier = Modifier
                .weight(1f)
                .testTag("settings-splash-preview"),
        )
    }
    if (imageImportInProgress) {
        Text(
            stringResource(R.string.image_import_in_progress),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BrandImagePreview(
    title: String,
    branding: io.github.kioskrelay.config.BrandingConfig,
    imageImporter: BrandImageImporter,
    imageRevision: Int,
    kind: BrandImageKind,
    hasCustomImage: Boolean,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val image = when (kind) {
        BrandImageKind.LOGO -> null
        BrandImageKind.SPLASH -> rememberBrandImage(
            importer = imageImporter,
            relativePath = branding.splashRelativePath,
            imageRevision = imageRevision,
        )
    }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(
                        Color(branding.backgroundColorArgb),
                        RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    kind == BrandImageKind.LOGO -> BrandLogo(
                        branding = branding,
                        imageImporter = imageImporter,
                        imageRevision = imageRevision,
                    )
                    image != null -> Image(
                        bitmap = image,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> Text(
                        stringResource(R.string.default_appearance),
                        color = Color.White.copy(alpha = 0.78f),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onChoose,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Image, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(
                            if (kind == BrandImageKind.LOGO) {
                                R.string.choose_logo
                            } else {
                                R.string.choose_splash_background
                            },
                        ),
                        maxLines = 1,
                    )
                }
                OutlinedButton(
                    onClick = onRemove,
                    enabled = enabled && hasCustomImage,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(
                            if (kind == BrandImageKind.LOGO) {
                                "settings-remove-logo"
                            } else {
                                "settings-remove-splash"
                            },
                        ),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.remove_image), maxLines = 1)
                }
            }
        }
    }
}

internal fun localizedString(
    context: Context,
    locale: AppLocale,
    @StringRes resourceId: Int,
): String {
    val selectedLocale = when (locale) {
        AppLocale.SYSTEM -> Resources.getSystem().configuration.locales[0]
        AppLocale.ZH_CN -> Locale.SIMPLIFIED_CHINESE
        AppLocale.EN -> Locale.ENGLISH
        AppLocale.ZH_TW -> Locale.TRADITIONAL_CHINESE
        AppLocale.ES -> Locale.forLanguageTag("es")
        AppLocale.JA -> Locale.JAPANESE
        AppLocale.KO -> Locale.KOREAN
    }
    val configuration = Configuration(context.resources.configuration).apply {
        setLocale(selectedLocale)
    }
    return context.createConfigurationContext(configuration).getString(resourceId)
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
        modifier = Modifier.remoteVerticalNavigation()
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
        modifier = Modifier.remoteVerticalNavigation().fillMaxWidth(),
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
        modifier = Modifier.remoteVerticalNavigation().fillMaxWidth(),
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
        StartupPermissionSettings()
        Text(stringResource(R.string.boot_delay, draft.runtime.bootDelaySeconds))
        BootDelaySlider(draft.runtime.bootDelaySeconds) { seconds ->
            onDraftChange(draft.copy(runtime = draft.runtime.copy(bootDelaySeconds = seconds)))
        }
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
        Icon(Icons.Outlined.Lock, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.change_password))
    }
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.onboarding_language), fontWeight = FontWeight.SemiBold)
    ChoiceRow(
        entries = listOf(
            AppLocale.SYSTEM to stringResource(R.string.language_system),
            AppLocale.ZH_CN to stringResource(R.string.language_chinese),
            AppLocale.EN to stringResource(R.string.language_english),
            AppLocale.ZH_TW to stringResource(R.string.language_chinese_traditional),
            AppLocale.ES to stringResource(R.string.language_spanish),
            AppLocale.JA to stringResource(R.string.language_japanese),
            AppLocale.KO to stringResource(R.string.language_korean),
        ),
        selected = draft.locale,
    ) {
        onDraftChange(draft.copy(locale = it))
    }
    Text(
        stringResource(R.string.language_applies_after_save),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MaintenanceSettings(
    onAction: (MaintenanceAction) -> Unit,
    enabled: Boolean,
) {
    SettingsHeader(R.string.settings_maintenance)
    val actions = listOf(
        Triple(MaintenanceAction.RELOAD, R.string.reload_page, Icons.Outlined.Refresh),
        Triple(MaintenanceAction.CLEAR_CACHE, R.string.clear_cache, Icons.Outlined.DeleteSweep),
        Triple(MaintenanceAction.CLEAR_COOKIES, R.string.clear_cookies, Icons.Outlined.Cookie),
        Triple(MaintenanceAction.CLEAR_WEB_DATA, R.string.clear_web_data, Icons.Outlined.Storage),
        Triple(
            MaintenanceAction.EXPORT_CONFIG,
            R.string.export_configuration,
            Icons.Outlined.FileUpload,
        ),
        Triple(
            MaintenanceAction.IMPORT_CONFIG,
            R.string.import_configuration,
            Icons.Outlined.FileDownload,
        ),
        Triple(
            MaintenanceAction.VIEW_DIAGNOSTICS,
            R.string.view_diagnostics,
            Icons.Outlined.Info,
        ),
        Triple(
            MaintenanceAction.EXPORT_DIAGNOSTICS,
            R.string.export_diagnostics,
            Icons.Outlined.Share,
        ),
        Triple(MaintenanceAction.RESET_DEFAULTS, R.string.restore_defaults, Icons.Outlined.Restore),
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columnCount = if (maxWidth >= 600.dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            actions.chunked(columnCount).forEach { rowActions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowActions.forEach { (action, label, icon) ->
                        OutlinedButton(
                            onClick = { onAction(action) },
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(icon, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(label))
                        }
                    }
                    repeat(columnCount - rowActions.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
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
        modifier = Modifier.remoteVerticalNavigation().fillMaxWidth(),
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
private fun <T> ChoiceRow(
    entries: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        entries.forEach { (value, label) ->
            OutlinedButton(
                onClick = { onSelected(value) },
                modifier = Modifier.widthIn(min = 140.dp),
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
                    modifier = Modifier.remoteVerticalNavigation(),
                    value = password,
                    onValueChange = { password = it.take(64) },
                    label = { Text(stringResource(R.string.new_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                )
                OutlinedTextField(
                    modifier = Modifier.remoteVerticalNavigation(),
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
            OutlinedButton(onClick = onDismiss, modifier = Modifier.initialFocus()) {
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
            OutlinedButton(onClick = onDismiss, modifier = Modifier.initialFocus()) {
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
