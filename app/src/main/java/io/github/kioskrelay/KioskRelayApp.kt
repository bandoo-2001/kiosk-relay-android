package io.github.kioskrelay

import android.Manifest
import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.webkit.WebViewCompat
import io.github.kioskrelay.config.AppLocale
import io.github.kioskrelay.config.ConfigDefaults
import io.github.kioskrelay.config.ConfigRules
import io.github.kioskrelay.config.KioskRelayConfig
import io.github.kioskrelay.config.ScreenOrientation
import io.github.kioskrelay.config.UrlPolicy
import io.github.kioskrelay.data.BrandImageKind
import io.github.kioskrelay.data.BrandAssetEditSession
import io.github.kioskrelay.data.ConfigArchiveManager
import io.github.kioskrelay.data.ConfigurationExportStatus
import io.github.kioskrelay.data.ExternalDocumentTooLargeException
import io.github.kioskrelay.data.ImageImportError
import io.github.kioskrelay.data.ImageImportResult
import io.github.kioskrelay.diagnostics.DiagnosticLevel
import io.github.kioskrelay.feature.diagnostics.DeviceDiagnostics
import io.github.kioskrelay.feature.diagnostics.DiagnosticsScreen
import io.github.kioskrelay.feature.kiosk.KioskScreen
import io.github.kioskrelay.feature.onboarding.OnboardingDraft
import io.github.kioskrelay.feature.onboarding.OnboardingScreen
import io.github.kioskrelay.feature.onboarding.SetupLocale
import io.github.kioskrelay.feature.onboarding.SetupOrientation
import io.github.kioskrelay.feature.onboarding.WebTestDialog
import io.github.kioskrelay.feature.onboarding.WebTestResult
import io.github.kioskrelay.feature.settings.MaintenanceAction
import io.github.kioskrelay.feature.settings.SettingsScreen
import io.github.kioskrelay.security.PasswordSetResult
import io.github.kioskrelay.ui.StartupBrandScreen
import io.github.kioskrelay.web.KioskUiState
import io.github.kioskrelay.web.rememberWebViewController
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private object Routes {
    const val STARTUP = "startup"
    const val ONBOARDING = "onboarding"
    const val KIOSK = "kiosk"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"
}

@Composable
fun KioskRelayApp(
    container: AppContainer,
    initialDelayMillis: Long,
    onPresentationPolicyChanged: (KioskRelayConfig) -> Unit,
    onLocaleChanged: (AppLocale) -> Unit,
    registerTvAdminEntryHandler: ((() -> Unit)?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val webController = rememberWebViewController()

    var config by remember { mutableStateOf<KioskRelayConfig?>(null) }
    var onboardingDraft by rememberSaveable(stateSaver = OnboardingDraftSaver) {
        mutableStateOf(OnboardingDraft())
    }
    var onboardingLocaleOverride by rememberSaveable { mutableStateOf<String?>(null) }
    var onboardingImageImports by remember { mutableIntStateOf(0) }
    var showWebTest by rememberSaveable { mutableStateOf(false) }
    var initialRouteResolved by rememberSaveable { mutableStateOf(false) }
    var diagnosticsVersion by remember { mutableIntStateOf(0) }
    var settingsOperationInProgress by remember { mutableStateOf(false) }
    var settingsAssetSession by remember {
        mutableStateOf<BrandAssetEditSession?>(null)
    }
    var pendingConfigExportId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingConfigExport = container.pendingConfigExport
    val activeConfigExportId by container.configurationExportCoordinator
        .activeSnapshotId
        .collectAsStateWithLifecycle()
    val coordinatorSnapshotId = activeConfigExportId
    val recoveredConfigExportId = remember(
        pendingConfigExport,
        pendingConfigExportId,
        coordinatorSnapshotId,
    ) {
        if (coordinatorSnapshotId == null) {
            pendingConfigExport.recoverLaunchedSnapshotId(pendingConfigExportId)
        } else {
            null
        }
    }
    if (
        recoveredConfigExportId != null &&
        activeConfigExportId == null
    ) {
        container.configurationExportCoordinator.restore(recoveredConfigExportId)
    }
    val configExportOutcome by container.configurationExportCoordinator
        .latestOutcome
        .collectAsStateWithLifecycle()
    val settingsInteractionBlocked =
        settingsOperationInProgress || activeConfigExportId != null

    fun tryBeginSettingsOperation(): Boolean {
        if (
            settingsOperationInProgress ||
            container.configurationExportCoordinator.activeSnapshotId.value != null
        ) {
            return false
        }
        settingsOperationInProgress = true
        return true
    }

    val successMessage = stringResource(R.string.operation_succeeded)
    val failureMessage = stringResource(R.string.operation_failed, "%s")
    val imageFailureMessage = stringResource(R.string.image_import_failed, "%s")

    LaunchedEffect(configExportOutcome?.generation) {
        val announced = configExportOutcome ?: return@LaunchedEffect
        val outcome = container.configurationExportCoordinator.consumeOutcome(
            announced.generation,
        ) ?: return@LaunchedEffect
        when (outcome.status) {
            ConfigurationExportStatus.SUCCEEDED -> {
                container.diagnosticLog.record("config", "Configuration exported")
                snackbarHostState.showSnackbar(successMessage)
            }
            ConfigurationExportStatus.CANCELLED -> {
                container.diagnosticLog.record("config", "Configuration export cancelled")
            }
            ConfigurationExportStatus.FAILED -> {
                container.diagnosticLog.record(
                    category = "config",
                    message = "Configuration export failed",
                    level = DiagnosticLevel.ERROR,
                )
                snackbarHostState.showSnackbar(
                    failureMessage.format(outcome.errorMessage.orEmpty()),
                )
            }
        }
    }

    LaunchedEffect(container.externalDocumentStager) {
        val cleanup = runCatching {
            withContext(Dispatchers.IO) {
                container.externalDocumentStager.discardOrphans()
            }
        }
        cleanup.rethrowCancellation()
        cleanup.onSuccess { discarded ->
            if (discarded > 0) {
                container.diagnosticLog.record(
                    category = "storage",
                    message = "Removed $discarded stale private staging file(s)",
                    level = DiagnosticLevel.INFO,
                )
            }
        }.onFailure {
            container.diagnosticLog.record(
                category = "storage",
                message = "Unable to clean private staging files",
                level = DiagnosticLevel.WARNING,
            )
        }
    }

    LaunchedEffect(container.configRepository) {
        var assetsRecovered = false
        container.configRepository.config.collectLatest { stored ->
            if (!assetsRecovered) {
                val recoveryResult = runCatching {
                    container.brandingOperationCoordinator.runExclusive {
                        withContext(Dispatchers.IO) {
                            BrandAssetEditSession.recoverIfNeeded(
                                root = container.brandingDirectory,
                                currentConfig = stored,
                            )
                        }
                    }
                }
                recoveryResult.rethrowCancellation()
                val recoveryFailure = recoveryResult.exceptionOrNull()
                recoveryFailure?.let {
                    container.diagnosticLog.record(
                        category = "config",
                        message = "Branding recovery failed: ${it.message.orEmpty()}",
                        level = DiagnosticLevel.ERROR,
                    )
                }
                assetsRecovered = true
            }
            config = stored
        }
    }

    LaunchedEffect(config?.webView, config?.locale, onboardingLocaleOverride) {
        config?.let {
            onPresentationPolicyChanged(it)
            val activeLocale = if (it.onboardingCompleted) {
                it.locale
            } else {
                onboardingLocaleOverride
                    ?.let { saved -> SetupLocale.valueOf(saved) }
                    ?.toAppLocale()
                    ?: it.locale
            }
            onLocaleChanged(activeLocale)
        }
    }

    LaunchedEffect(config, initialRouteResolved) {
        val resolved = config ?: return@LaunchedEffect
        if (initialRouteResolved) return@LaunchedEffect
        if (initialDelayMillis > 0) delay(initialDelayMillis.coerceAtMost(60_000L))
        navController.navigate(
            if (resolved.onboardingCompleted) Routes.KIOSK else Routes.ONBOARDING,
        ) {
            popUpTo(Routes.STARTUP) { inclusive = true }
            launchSingleTop = true
        }
        initialRouteResolved = true
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        container.diagnosticLog.record(
            category = "startup",
            message = if (granted) {
                "Notification permission granted"
            } else {
                "Notification permission denied; Android 13+ boot fallback cannot notify"
            },
            level = if (granted) DiagnosticLevel.INFO else DiagnosticLevel.WARNING,
        )
    }

    val exportConfigLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(CONFIG_MIME_TYPE),
    ) { uri ->
        val snapshotId =
            pendingConfigExportId?.takeIf {
                it == container.configurationExportCoordinator.activeSnapshotId.value
            } ?: container.configurationExportCoordinator.activeSnapshotId.value
        if (snapshotId == null) {
            settingsOperationInProgress = false
            if (uri != null) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        failureMessage.format(
                            "Pending configuration export is unavailable",
                        ),
                    )
                }
            }
            return@rememberLauncherForActivityResult
        }

        val contentResolver = context.applicationContext.contentResolver
        val ownershipUpdate = runCatching {
            pendingConfigExport.markResultReceived(snapshotId)
        }
        val accepted = container.configurationExportCoordinator.complete(
            snapshotId = snapshotId,
            cancelled = uri == null,
        ) {
            container.configurationExportCoordinator.runExclusive {
                try {
                    ownershipUpdate.getOrThrow()
                    if (uri != null) {
                        val output = contentResolver.openOutputStream(uri)
                            ?: error("Unable to open export destination")
                        output.use {
                            pendingConfigExport.copyTo(snapshotId, it)
                        }
                    }
                } finally {
                    withContext(NonCancellable) {
                        pendingConfigExport.discard(snapshotId)
                    }
                }
            }
        }
        if (pendingConfigExportId == snapshotId) {
            pendingConfigExportId = null
        }
        settingsOperationInProgress = false
        if (!accepted) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    failureMessage.format(
                        "Configuration export result was already handled",
                    ),
                )
            }
        }
    }

    val importConfigLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            settingsOperationInProgress = true
            scope.launch {
                val stagedArchive = container.externalDocumentStager.newTarget("kioskrelay")
                val result = try {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            container.externalDocumentStager.copy(
                                uri = uri,
                                target = stagedArchive,
                                maximumBytes = ConfigArchiveManager.MAX_ARCHIVE_BYTES,
                            )
                        }
                        container.brandingOperationCoordinator.runExclusive {
                            var importedConfig: KioskRelayConfig? = null
                            try {
                                val assetSession = withContext(Dispatchers.IO) {
                                    BrandAssetEditSession.begin(container.brandingDirectory)
                                }
                                val imported = withContext(Dispatchers.IO) {
                                    stagedArchive.inputStream().use {
                                        container.configArchiveManager.import(
                                            input = it,
                                            assetRoot = container.brandingDirectory,
                                        )
                                    }
                                }
                                importedConfig = imported.config
                                withContext(Dispatchers.IO) {
                                    assetSession.prepareCommit(imported.config)
                                }
                                val stored =
                                    container.configRepository.replace(imported.config)
                                withContext(NonCancellable + Dispatchers.IO) {
                                    assetSession.commit()
                                }
                                stored
                            } catch (error: Throwable) {
                                val current = reconcileBrandingTransaction(container)
                                if (
                                    error !is CancellationException &&
                                    current == importedConfig
                                ) {
                                    checkNotNull(current)
                                } else {
                                    throw error
                                }
                            }
                        }
                    }
                } finally {
                    discardStagedDocument(container, stagedArchive)
                }
                settingsOperationInProgress = false
                result.rethrowCancellation()
                if (result.isSuccess) {
                    val imported = result.getOrThrow()
                    container.diagnosticLog.record("config", "Configuration imported")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        imported.runtime.bootStartEnabled
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    navigateAsRoot(
                        navController = navController,
                        route = if (imported.onboardingCompleted) {
                            Routes.KIOSK
                        } else {
                            Routes.ONBOARDING
                        },
                    )
                }
                showOperationResult(
                    result = result,
                    snackbar = snackbarHostState,
                    success = successMessage,
                    failurePattern = failureMessage,
                )
            }
        } else {
            settingsOperationInProgress = false
        }
    }

    val exportDiagnosticsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(uri)
                            ?: error("Unable to open diagnostics destination")
                        output.bufferedWriter().use { writer ->
                            writer.appendLine(deviceDiagnostics(context).asExportHeader())
                            writer.append(container.diagnosticLog.exportText())
                        }
                    }
                }
                settingsOperationInProgress = false
                result.rethrowCancellation()
                showOperationResult(
                    result = result,
                    snackbar = snackbarHostState,
                    success = successMessage,
                    failurePattern = failureMessage,
                )
            }
        } else {
            settingsOperationInProgress = false
        }
    }

    DisposableEffect(
        lifecycleOwner,
        pendingConfigExport,
        container.configurationExportCoordinator,
    ) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) {
                return@LifecycleEventObserver
            }
            val waitingSnapshotId =
                container.configurationExportCoordinator.activeSnapshotId.value
                    ?: return@LifecycleEventObserver
            scope.launch {
                // Activity Result delivery normally happens before ON_RESUME. Leave a short grace
                // window for OEM scheduling, then clear only an owner that no callback claimed.
                delay(EXPORT_RESULT_RESUME_GRACE_MILLIS)
                if (
                    container.configurationExportCoordinator.activeSnapshotId.value !=
                    waitingSnapshotId
                ) {
                    return@launch
                }
                val abandoned = container.configurationExportCoordinator.complete(
                    snapshotId = waitingSnapshotId,
                    cancelled = true,
                ) {
                    container.configurationExportCoordinator.runExclusive {
                        withContext(NonCancellable) {
                            pendingConfigExport.discard(waitingSnapshotId)
                        }
                    }
                }
                if (abandoned) {
                    if (pendingConfigExportId == waitingSnapshotId) {
                        pendingConfigExportId = null
                    }
                    settingsOperationInProgress = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun importBrandImage(
        kind: BrandImageKind,
        uri: android.net.Uri,
        updateDraft: Boolean,
        onImported: (String?) -> Unit = {},
    ) {
        scope.launch {
            val stagedImage = container.externalDocumentStager.newTarget("image")
            val importResult = try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        container.externalDocumentStager.copy(
                            uri = uri,
                            target = stagedImage,
                            maximumBytes = ConfigArchiveManager.MAX_ASSET_BYTES,
                        )
                    }
                    container.brandingOperationCoordinator.runExclusive {
                        container.brandImageImporter.importImage(
                            android.net.Uri.fromFile(stagedImage),
                            kind,
                        )
                    }
                }
            } finally {
                discardStagedDocument(container, stagedImage)
            }
            importResult.rethrowCancellation()
            val imported = importResult.getOrElse { error ->
                val failure = when (error) {
                    is ExternalDocumentTooLargeException -> ImageImportError.FILE_TOO_LARGE
                    is SecurityException -> ImageImportError.ACCESS_DENIED
                    else -> ImageImportError.STORAGE_ERROR
                }
                ImageImportResult.Failure(failure)
            }
            when (val result = imported) {
                is ImageImportResult.Success -> {
                    if (updateDraft) {
                        onboardingDraft = when (kind) {
                            BrandImageKind.LOGO ->
                                onboardingDraft.copy(logoUri = result.relativePath)
                            BrandImageKind.SPLASH ->
                                onboardingDraft.copy(splashUri = result.relativePath)
                        }
                    }
                    onImported(result.relativePath)
                    snackbarHostState.showSnackbar(successMessage)
                }
                is ImageImportResult.Failure -> {
                    if (updateDraft) {
                        onboardingDraft = when (kind) {
                            BrandImageKind.LOGO -> onboardingDraft.copy(logoUri = "")
                            BrandImageKind.SPLASH -> onboardingDraft.copy(splashUri = "")
                        }
                    }
                    onImported(null)
                    snackbarHostState.showSnackbar(
                        imageFailureMessage.format(result.error.name),
                    )
                }
            }
        }
    }

    fun removeBrandImage(
        kind: BrandImageKind,
        session: BrandAssetEditSession,
        onRemoved: (Boolean) -> Unit,
    ) {
        scope.launch {
            val result = runCatching {
                container.brandingOperationCoordinator.runExclusive {
                    withContext(Dispatchers.IO) {
                        session.remove(kind)
                    }
                }
            }
            result.rethrowCancellation()
            onRemoved(result.isSuccess)
            snackbarHostState.showSnackbar(
                if (result.isSuccess) {
                    successMessage
                } else {
                    failureMessage.format(
                        result.exceptionOrNull()?.message.orEmpty(),
                    )
                },
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.STARTUP,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.STARTUP) {
                StartupBrandScreen(
                    branding = config?.branding ?: ConfigDefaults.config.branding,
                    imageImporter = container.brandImageImporter,
                )
            }
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    draft = onboardingDraft,
                    onDraftChange = { onboardingDraft = it },
                    onTestUrl = { showWebTest = true },
                    onFinish = { password ->
                        scope.launch {
                            val newConfigResult = runCatching {
                                onboardingDraft.toConfig(
                                    logoPath = null,
                                    splashPath = null,
                                )
                            }
                            if (newConfigResult.isFailure) {
                                snackbarHostState.showSnackbar(
                                    failureMessage.format(
                                        newConfigResult.exceptionOrNull()?.message.orEmpty(),
                                    ),
                                )
                                return@launch
                            }

                            if (password.isEmpty()) {
                                container.adminAuthenticator.clear()
                            } else {
                                val passwordChars = password.toCharArray()
                                val passwordResult = try {
                                    container.adminAuthenticator.setPassword(passwordChars)
                                } finally {
                                    passwordChars.fill('\u0000')
                                }
                                if (passwordResult !is PasswordSetResult.Success) {
                                    snackbarHostState.showSnackbar(
                                        failureMessage.format("Invalid administrator password"),
                                    )
                                    return@launch
                                }
                            }

                            val storeResult = runCatching {
                                container.brandingOperationCoordinator.runExclusive {
                                    val base = newConfigResult.getOrThrow()
                                    val candidate = base.copy(
                                        branding = base.branding.copy(
                                            logoRelativePath = onboardingDraft.logoUri
                                                .takeIf {
                                                    it == ConfigArchiveManager.LOGO_FILE
                                                }
                                                ?.takeIf {
                                                    container.brandImageImporter.resolve(it) != null
                                                },
                                            splashRelativePath = onboardingDraft.splashUri
                                                .takeIf {
                                                    it == ConfigArchiveManager.SPLASH_FILE
                                                }
                                                ?.takeIf {
                                                    container.brandImageImporter.resolve(it) != null
                                                },
                                        ),
                                    )
                                    container.configRepository.replace(candidate)
                                }
                            }
                            storeResult.rethrowCancellation()
                            if (storeResult.isFailure) {
                                snackbarHostState.showSnackbar(
                                    failureMessage.format(
                                        storeResult.exceptionOrNull()?.message.orEmpty(),
                                    ),
                                )
                                return@launch
                            }
                            val stored = storeResult.getOrThrow()
                            container.diagnosticLog.record(
                                "config",
                                "Onboarding completed",
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                stored.runtime.bootStartEnabled
                            ) {
                                notificationPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS,
                                )
                            }
                            navigateAsRoot(navController, Routes.KIOSK)
                        }
                    },
                    onLocaleApplied = { selected ->
                        onboardingLocaleOverride = selected.name
                        onLocaleChanged(selected.toAppLocale())
                    },
                    onLogoSelected = {
                        onboardingImageImports++
                        importBrandImage(
                            BrandImageKind.LOGO,
                            it,
                            updateDraft = true,
                            onImported = {
                                onboardingImageImports =
                                    (onboardingImageImports - 1).coerceAtLeast(0)
                            },
                        )
                    },
                    onSplashSelected = {
                        onboardingImageImports++
                        importBrandImage(
                            BrandImageKind.SPLASH,
                            it,
                            updateDraft = true,
                            onImported = {
                                onboardingImageImports =
                                    (onboardingImageImports - 1).coerceAtLeast(0)
                            },
                        )
                    },
                    imageImporter = container.brandImageImporter,
                    imageImportInProgress = onboardingImageImports > 0,
                )
            }
            composable(Routes.KIOSK) {
                val activeConfig = config
                if (activeConfig == null || !activeConfig.onboardingCompleted) {
                    StartupBrandScreen(
                        branding = activeConfig?.branding ?: ConfigDefaults.config.branding,
                        imageImporter = container.brandImageImporter,
                    )
                } else {
                    KioskScreen(
                        config = activeConfig,
                        imageImporter = container.brandImageImporter,
                        controller = webController,
                        authenticator = container.adminAuthenticator,
                        registerTvAdminEntryHandler = registerTvAdminEntryHandler,
                        onOpenSettings = {
                            if (!tryBeginSettingsOperation()) {
                                return@KioskScreen
                            }
                            scope.launch {
                                val session = runCatching {
                                    container.brandingOperationCoordinator.runExclusive {
                                        val current = container.configRepository.get()
                                        withContext(Dispatchers.IO) {
                                            BrandAssetEditSession.recoverIfNeeded(
                                                root = container.brandingDirectory,
                                                currentConfig = current,
                                            )
                                            BrandAssetEditSession.begin(
                                                container.brandingDirectory,
                                            )
                                        }
                                    }
                                }
                                session.rethrowCancellation()
                                if (session.isSuccess) {
                                    settingsAssetSession = session.getOrThrow()
                                    navController.navigate(Routes.SETTINGS) {
                                        launchSingleTop = true
                                    }
                                } else {
                                    snackbarHostState.showSnackbar(
                                        failureMessage.format(
                                            session.exceptionOrNull()?.message.orEmpty(),
                                        ),
                                    )
                                }
                                settingsOperationInProgress = false
                            }
                        },
                        onStateChange = { state ->
                            container.diagnosticLog.record(
                                category = "web",
                                message = state.toDiagnosticMessage(),
                                level = state.diagnosticLevel(),
                            )
                        },
                    )
                }
            }
            composable(Routes.SETTINGS) {
                val activeConfig = config
                if (activeConfig != null) {
                    LaunchedEffect(Unit) {
                        if (settingsAssetSession == null) {
                            val restoredSession = runCatching {
                                container.brandingOperationCoordinator.runExclusive {
                                    withContext(Dispatchers.IO) {
                                        BrandAssetEditSession.begin(
                                            container.brandingDirectory,
                                        )
                                    }
                                }
                            }
                            restoredSession.rethrowCancellation()
                            if (restoredSession.isSuccess) {
                                settingsAssetSession = restoredSession.getOrThrow()
                                settingsOperationInProgress = false
                            } else {
                                snackbarHostState.showSnackbar(
                                    failureMessage.format(
                                        restoredSession.exceptionOrNull()?.message.orEmpty(),
                                    ),
                                )
                                navController.popBackStack()
                            }
                        }
                    }
                    val assetSession = settingsAssetSession
                    if (assetSession == null) {
                        StartupBrandScreen(
                            branding = activeConfig.branding,
                            imageImporter = container.brandImageImporter,
                        )
                    } else {
                    SettingsScreen(
                        initialConfig = activeConfig,
                        onSave = { candidate ->
                            if (!tryBeginSettingsOperation()) {
                                return@SettingsScreen
                            }
                            scope.launch {
                                val result = runCatching {
                                    container.brandingOperationCoordinator.runExclusive {
                                        val validated =
                                            candidate.withResolvedBrandAssets(container)
                                        try {
                                            withContext(Dispatchers.IO) {
                                                assetSession.prepareCommit(validated)
                                            }
                                            val stored = container.configRepository.replace(validated)
                                            withContext(NonCancellable + Dispatchers.IO) {
                                                assetSession.commit()
                                            }
                                            stored
                                        } catch (error: Throwable) {
                                            val current =
                                                reconcileBrandingTransaction(container)
                                            if (
                                                error !is CancellationException &&
                                                current == validated
                                            ) {
                                                checkNotNull(current)
                                            } else {
                                                settingsAssetSession = null
                                                if (
                                                    error !is CancellationException &&
                                                    current != null
                                                ) {
                                                    settingsAssetSession = runCatching {
                                                        withContext(Dispatchers.IO) {
                                                            BrandAssetEditSession.begin(
                                                                container.brandingDirectory,
                                                            )
                                                        }
                                                    }.getOrNull()
                                                }
                                                throw error
                                            }
                                        }
                                    }
                                }
                                result.rethrowCancellation()
                                if (result.isFailure && settingsAssetSession == null) {
                                    navController.popBackStack()
                                }
                                if (result.isSuccess) {
                                    settingsAssetSession = null
                                    val stored = result.getOrThrow()
                                    container.diagnosticLog.record(
                                        "config",
                                        "Settings updated",
                                    )
                                    if (Build.VERSION.SDK_INT >=
                                        Build.VERSION_CODES.TIRAMISU &&
                                        stored.runtime.bootStartEnabled
                                    ) {
                                        notificationPermissionLauncher.launch(
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        )
                                    }
                                    navController.navigate(Routes.KIOSK) {
                                        popUpTo(Routes.KIOSK) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                                settingsOperationInProgress = false
                                showOperationResult(
                                    result = result,
                                    snackbar = snackbarHostState,
                                    success = successMessage,
                                    failurePattern = failureMessage,
                                )
                            }
                        },
                        onDismiss = {
                            if (!tryBeginSettingsOperation()) {
                                return@SettingsScreen
                            }
                            scope.launch {
                                val rollbackResult = runCatching {
                                    container.brandingOperationCoordinator.runExclusive {
                                        withContext(Dispatchers.IO) {
                                            assetSession.rollback()
                                        }
                                    }
                                }
                                rollbackResult.rethrowCancellation()
                                if (rollbackResult.isSuccess) {
                                    settingsAssetSession = null
                                    navController.popBackStack()
                                }
                                settingsOperationInProgress = false
                                if (rollbackResult.isFailure) {
                                    snackbarHostState.showSnackbar(
                                        failureMessage.format(
                                            rollbackResult.exceptionOrNull()
                                                ?.message
                                                .orEmpty(),
                                        ),
                                    )
                                }
                            }
                        },
                        onLogoSelected = { uri, onImported ->
                            importBrandImage(
                                BrandImageKind.LOGO,
                                uri,
                                updateDraft = false,
                                onImported = onImported,
                            )
                        },
                        onSplashSelected = { uri, onImported ->
                            importBrandImage(
                                BrandImageKind.SPLASH,
                                uri,
                                updateDraft = false,
                                onImported = onImported,
                            )
                        },
                        onLogoRemoved = { onRemoved ->
                            removeBrandImage(
                                kind = BrandImageKind.LOGO,
                                session = assetSession,
                                onRemoved = onRemoved,
                            )
                        },
                        onSplashRemoved = { onRemoved ->
                            removeBrandImage(
                                kind = BrandImageKind.SPLASH,
                                session = assetSession,
                                onRemoved = onRemoved,
                            )
                        },
                        imageImporter = container.brandImageImporter,
                        onChangePassword = { password ->
                            if (!tryBeginSettingsOperation()) {
                                return@SettingsScreen
                            }
                            scope.launch {
                                val characters = password.toCharArray()
                                val passwordUpdate = runCatching {
                                    try {
                                        container.adminAuthenticator.setPassword(characters)
                                    } finally {
                                        characters.fill('\u0000')
                                    }
                                }
                                passwordUpdate.rethrowCancellation()
                                settingsOperationInProgress = false
                                snackbarHostState.showSnackbar(
                                    if (
                                        passwordUpdate.getOrNull() is
                                        PasswordSetResult.Success
                                    ) {
                                        successMessage
                                    } else {
                                        failureMessage.format(
                                            passwordUpdate.exceptionOrNull()
                                                ?.message
                                                ?: "Invalid administrator password",
                                        )
                                    },
                                )
                            }
                        },
                        operationInProgress = settingsInteractionBlocked,
                        onMaintenanceAction = { action, settingsDraft ->
                            if (!tryBeginSettingsOperation()) {
                                return@SettingsScreen
                            }
                            val dispatch = {
                                handleMaintenanceAction(
                                    action = action,
                                    context = context,
                                    container = container,
                                    webController = webController,
                                    navController = navController,
                                    scope = scope,
                                    snackbarHostState = snackbarHostState,
                                    exportConfig = {
                                        exportConfigLauncher.launch(
                                            "kioskrelay-${BuildConfig.VERSION_NAME}.kioskrelay",
                                        )
                                    },
                                    importConfig = {
                                        importConfigLauncher.launch(
                                            arrayOf(
                                                CONFIG_MIME_TYPE,
                                                "application/zip",
                                                "application/octet-stream",
                                            ),
                                        )
                                    },
                                    exportDiagnostics = {
                                        exportDiagnosticsLauncher.launch(
                                            "kioskrelay-diagnostics.txt",
                                        )
                                    },
                                    onDiagnosticsChanged = { diagnosticsVersion++ },
                                    onOperationFinished = {
                                        settingsOperationInProgress = false
                                    },
                                )
                            }
                            if (action == MaintenanceAction.EXPORT_CONFIG) {
                                val snapshotId = pendingConfigExport.newSnapshotId()
                                scope.launch {
                                    var pickerLaunched = false
                                    val preparation = runCatching {
                                        container.configurationExportCoordinator
                                            .runExclusive {
                                                try {
                                                    container.brandingOperationCoordinator
                                                        .runExclusive {
                                                            val exportCandidate =
                                                                settingsDraft
                                                                    .withResolvedBrandAssets(
                                                                        container,
                                                                    )
                                                            withContext(Dispatchers.IO) {
                                                                pendingConfigExport.prepare(
                                                                    snapshotId = snapshotId,
                                                                    config = exportCandidate,
                                                                    assetRoot =
                                                                        container
                                                                            .brandingDirectory,
                                                                )
                                                            }
                                                        }
                                                    check(
                                                        container
                                                            .configurationExportCoordinator
                                                            .activate(snapshotId),
                                                    ) {
                                                        "Another configuration export is active"
                                                    }
                                                    withContext(Dispatchers.IO) {
                                                        pendingConfigExport.markLaunched(
                                                            snapshotId,
                                                        )
                                                    }
                                                    pendingConfigExportId = snapshotId
                                                    dispatch()
                                                    pickerLaunched = true
                                                } finally {
                                                    if (!pickerLaunched) {
                                                        try {
                                                            withContext(
                                                                NonCancellable +
                                                                    Dispatchers.IO,
                                                            ) {
                                                                pendingConfigExport.discard(
                                                                    snapshotId,
                                                                )
                                                            }
                                                        } finally {
                                                            container
                                                                .configurationExportCoordinator
                                                                .deactivate(snapshotId)
                                                        }
                                                    }
                                                }
                                            }
                                    }
                                    preparation.rethrowCancellation()
                                    if (preparation.isFailure) {
                                        if (pendingConfigExportId == snapshotId) {
                                            pendingConfigExportId = null
                                        }
                                        settingsOperationInProgress = false
                                        snackbarHostState.showSnackbar(
                                            failureMessage.format(
                                                preparation.exceptionOrNull()
                                                    ?.message
                                                    .orEmpty(),
                                            ),
                                        )
                                    }
                                }
                            } else if (
                                action == MaintenanceAction.RELOAD ||
                                action == MaintenanceAction.IMPORT_CONFIG ||
                                action == MaintenanceAction.RESET_DEFAULTS
                            ) {
                                scope.launch {
                                    val rollbackResult = runCatching {
                                        container.brandingOperationCoordinator.runExclusive {
                                            withContext(Dispatchers.IO) {
                                                assetSession.rollback()
                                            }
                                        }
                                    }
                                    rollbackResult.rethrowCancellation()
                                    if (rollbackResult.isFailure) {
                                        settingsOperationInProgress = false
                                        snackbarHostState.showSnackbar(
                                            failureMessage.format(
                                                rollbackResult.exceptionOrNull()
                                                    ?.message
                                                    .orEmpty(),
                                            ),
                                        )
                                        return@launch
                                    }
                                    settingsAssetSession = null
                                    if (
                                        action == MaintenanceAction.IMPORT_CONFIG ||
                                        action == MaintenanceAction.RESET_DEFAULTS
                                    ) {
                                        navController.popBackStack(
                                            Routes.KIOSK,
                                            inclusive = false,
                                        )
                                    }
                                    val dispatchResult = runCatching { dispatch() }
                                    if (dispatchResult.isFailure) {
                                        settingsOperationInProgress = false
                                        snackbarHostState.showSnackbar(
                                            failureMessage.format(
                                                dispatchResult.exceptionOrNull()
                                                    ?.message
                                                    .orEmpty(),
                                            ),
                                        )
                                    } else if (action == MaintenanceAction.RELOAD) {
                                        settingsOperationInProgress = false
                                    }
                                }
                            } else {
                                val dispatchResult = runCatching { dispatch() }
                                if (
                                    action != MaintenanceAction.EXPORT_DIAGNOSTICS ||
                                    dispatchResult.isFailure
                                ) {
                                    settingsOperationInProgress = false
                                }
                                if (dispatchResult.isFailure) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            failureMessage.format(
                                                dispatchResult.exceptionOrNull()
                                                    ?.message
                                                    .orEmpty(),
                                            ),
                                        )
                                    }
                                }
                            }
                        },
                    )
                    }
                }
            }
            composable(Routes.DIAGNOSTICS) {
                @Suppress("UNUSED_VARIABLE")
                val refresh = diagnosticsVersion
                DiagnosticsScreen(
                    device = remember(context) { deviceDiagnostics(context) },
                    events = container.diagnosticLog.snapshot(),
                    onBack = { navController.popBackStack() },
                    onExport = {
                        exportDiagnosticsLauncher.launch("kioskrelay-diagnostics.txt")
                    },
                    onClear = {
                        container.diagnosticLog.clear()
                        diagnosticsVersion++
                    },
                )
            }
        }
    }

    if (showWebTest) {
        WebTestDialog(
            url = onboardingDraft.url,
            allowHttp = onboardingDraft.allowHttp,
            orientation = onboardingDraft.orientation,
            onResult = { succeeded ->
                onboardingDraft = onboardingDraft.copy(
                    webTestResult = if (succeeded) {
                        WebTestResult.SUCCEEDED
                    } else {
                        WebTestResult.FAILED
                    },
                )
            },
            onDismiss = { showWebTest = false },
        )
    }
}

private fun OnboardingDraft.toConfig(
    logoPath: String?,
    splashPath: String?,
): KioskRelayConfig {
    val validation = UrlPolicy.validateInitialUrl(url, allowHttp)
    require(validation.isValid) { "Invalid dashboard URL" }
    val normalizedUrl = requireNotNull(validation.normalizedUrl)
    val origin = requireNotNull(validation.origin)
    return ConfigRules.validate(
        ConfigRules.canonicalize(
            ConfigDefaults.config.copy(
                locale = when (locale) {
                    SetupLocale.SYSTEM -> AppLocale.SYSTEM
                    SetupLocale.ZH_CN -> AppLocale.ZH_CN
                    SetupLocale.ENGLISH -> AppLocale.EN
                    SetupLocale.ZH_TW -> AppLocale.ZH_TW
                    SetupLocale.SPANISH -> AppLocale.ES
                    SetupLocale.JAPANESE -> AppLocale.JA
                    SetupLocale.KOREAN -> AppLocale.KO
                },
                onboardingCompleted = true,
                branding = ConfigDefaults.config.branding.copy(
                    productName = productName,
                    logoRelativePath = logoPath,
                    splashRelativePath = splashPath,
                    primaryColorArgb = primaryColor,
                    backgroundColorArgb = backgroundColor,
                ),
                webView = ConfigDefaults.config.webView.copy(
                    initialUrl = normalizedUrl,
                    allowedOrigins = setOf(origin),
                    allowHttp = allowHttp,
                    orientation = when (orientation) {
                        SetupOrientation.LANDSCAPE -> ScreenOrientation.LANDSCAPE
                        SetupOrientation.PORTRAIT -> ScreenOrientation.PORTRAIT
                        SetupOrientation.FOLLOW_SYSTEM -> ScreenOrientation.SENSOR
                    },
                    fullscreen = fullscreen,
                    keepScreenOn = keepScreenOn,
                    refreshOnNetworkRecovery = reloadOnNetworkRecovery,
                ),
                runtime = ConfigDefaults.config.runtime.copy(
                    bootStartEnabled = bootLaunchEnabled,
                    bootDelaySeconds = bootDelaySeconds,
                ),
            ),
        ),
    )
}

private fun SetupLocale.toAppLocale(): AppLocale = when (this) {
    SetupLocale.SYSTEM -> AppLocale.SYSTEM
    SetupLocale.ZH_CN -> AppLocale.ZH_CN
    SetupLocale.ENGLISH -> AppLocale.EN
    SetupLocale.ZH_TW -> AppLocale.ZH_TW
    SetupLocale.SPANISH -> AppLocale.ES
    SetupLocale.JAPANESE -> AppLocale.JA
    SetupLocale.KOREAN -> AppLocale.KO
}

private fun KioskRelayConfig.withResolvedBrandAssets(
    container: AppContainer,
): KioskRelayConfig {
    val withAssets = copy(
        onboardingCompleted = true,
        branding = branding.copy(
            logoRelativePath = branding.logoRelativePath
                ?.takeIf { it == ConfigArchiveManager.LOGO_FILE }
                ?.takeIf { container.brandImageImporter.resolve(it) != null },
            splashRelativePath = branding.splashRelativePath
                ?.takeIf { it == ConfigArchiveManager.SPLASH_FILE }
                ?.takeIf { container.brandImageImporter.resolve(it) != null },
        ),
    )
    return ConfigRules.validate(ConfigRules.canonicalize(withAssets))
}

private fun handleMaintenanceAction(
    action: MaintenanceAction,
    context: Context,
    container: AppContainer,
    webController: io.github.kioskrelay.web.WebViewController,
    navController: androidx.navigation.NavHostController,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState,
    exportConfig: () -> Unit,
    importConfig: () -> Unit,
    exportDiagnostics: () -> Unit,
    onDiagnosticsChanged: () -> Unit,
    onOperationFinished: () -> Unit,
) {
    when (action) {
        MaintenanceAction.RELOAD -> {
            webController.reload()
            navController.popBackStack(Routes.KIOSK, inclusive = false)
        }
        MaintenanceAction.CLEAR_CACHE -> {
            runCatching {
                WebView(context).apply {
                    clearCache(true)
                    destroy()
                }
            }
            container.diagnosticLog.record("maintenance", "WebView cache cleared")
        }
        MaintenanceAction.CLEAR_COOKIES -> {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            container.diagnosticLog.record("maintenance", "Cookies cleared")
        }
        MaintenanceAction.CLEAR_WEB_DATA -> {
            WebStorage.getInstance().deleteAllData()
            runCatching {
                WebView(context).apply {
                    clearCache(true)
                    clearHistory()
                    clearFormData()
                    destroy()
                }
            }
            container.diagnosticLog.record("maintenance", "WebView site data cleared")
        }
        MaintenanceAction.EXPORT_CONFIG -> exportConfig()
        MaintenanceAction.IMPORT_CONFIG -> importConfig()
        MaintenanceAction.VIEW_DIAGNOSTICS -> {
            navController.navigate(Routes.DIAGNOSTICS) { launchSingleTop = true }
        }
        MaintenanceAction.EXPORT_DIAGNOSTICS -> exportDiagnostics()
        MaintenanceAction.RESET_DEFAULTS -> {
            scope.launch {
                val result = runCatching {
                    container.brandingOperationCoordinator.runExclusive {
                        withContext(NonCancellable) {
                            container.configRepository.reset()
                            container.adminAuthenticator.clear()
                            withContext(Dispatchers.IO) {
                                deleteBrandingFiles(container.brandingDirectory)
                            }
                            container.diagnosticLog.clear()
                        }
                    }
                }
                result.rethrowCancellation()
                if (result.isSuccess) {
                    navigateAsRoot(navController, Routes.ONBOARDING)
                    onDiagnosticsChanged()
                }
                onOperationFinished()
                snackbarHostState.showSnackbar(
                    if (result.isSuccess) {
                        context.getString(R.string.operation_succeeded)
                    } else {
                        context.getString(
                            R.string.operation_failed,
                            result.exceptionOrNull()?.message.orEmpty(),
                        )
                    },
                )
            }
        }
    }
}

private fun deleteBrandingFiles(directory: File) {
    if (!directory.isDirectory) return
    directory.listFiles()?.forEach { file ->
        if (file.isFile && file.name in setOf(
                ConfigArchiveManager.LOGO_FILE,
                ConfigArchiveManager.SPLASH_FILE,
            )
        ) {
            file.delete()
        }
    }
}

private suspend fun reconcileBrandingTransaction(
    container: AppContainer,
): KioskRelayConfig? = withContext(NonCancellable) {
    val current = runCatching {
        container.configRepository.get()
    }.getOrNull() ?: return@withContext null
    val recovered = runCatching {
        withContext(Dispatchers.IO) {
            BrandAssetEditSession.recoverIfNeeded(
                root = container.brandingDirectory,
                currentConfig = current,
            )
        }
    }
    current.takeIf { recovered.isSuccess }
}

private suspend fun discardStagedDocument(
    container: AppContainer,
    stagedDocument: File,
) {
    val failure = withContext(NonCancellable + Dispatchers.IO) {
        runCatching {
            container.externalDocumentStager.discard(stagedDocument)
        }.exceptionOrNull()
    }
    failure?.let {
        container.diagnosticLog.record(
            category = "storage",
            message = "Unable to remove a private staging file",
            level = DiagnosticLevel.WARNING,
        )
    }
}

private fun Result<*>.rethrowCancellation() {
    (exceptionOrNull() as? CancellationException)?.let { throw it }
}

private suspend fun showOperationResult(
    result: Result<*>,
    snackbar: SnackbarHostState,
    success: String,
    failurePattern: String,
) {
    snackbar.showSnackbar(
        if (result.isSuccess) {
            success
        } else {
            failurePattern.format(result.exceptionOrNull()?.message.orEmpty())
        },
    )
}

private fun navigateAsRoot(
    navController: androidx.navigation.NavHostController,
    route: String,
) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
        launchSingleTop = true
    }
}

private fun deviceDiagnostics(context: Context): DeviceDiagnostics {
    val packageName = WebViewCompat.getCurrentWebViewPackage(context)?.let {
        "${it.packageName} ${it.versionName}"
    } ?: "Unavailable"
    return DeviceDiagnostics(
        appVersion = BuildConfig.VERSION_NAME,
        androidVersion = Build.VERSION.RELEASE,
        apiLevel = Build.VERSION.SDK_INT,
        device = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        webViewVersion = packageName,
    )
}

private fun DeviceDiagnostics.asExportHeader(): String = buildString {
    appendLine("KioskRelay $appVersion")
    appendLine("Android $androidVersion (API $apiLevel)")
    appendLine("Device $device")
    appendLine("WebView $webViewVersion")
}

private fun KioskUiState.toDiagnosticMessage(): String = when (this) {
    is KioskUiState.CompatibilityWarning -> "WebView compatibility: $version"
    KioskUiState.Starting -> "Starting"
    is KioskUiState.Loading -> "Loading $url"
    is KioskUiState.Online -> "Online $url"
    is KioskUiState.Offline -> "Offline ${lastUrl.orEmpty()}"
    is KioskUiState.PageError -> "PageError $errorCode $description ${url.orEmpty()}"
    is KioskUiState.BackoffRetry -> "Retry attempt=$attempt delay=$delayMillis url=$url"
    is KioskUiState.RendererGone -> "RendererGone crashed=$didCrash ${lastUrl.orEmpty()}"
    is KioskUiState.Fatal -> "Fatal $reason ${lastUrl.orEmpty()}"
}

private fun KioskUiState.diagnosticLevel(): DiagnosticLevel = when (this) {
    is KioskUiState.CompatibilityWarning,
    KioskUiState.Starting,
    is KioskUiState.Loading,
    is KioskUiState.Online,
    -> DiagnosticLevel.INFO
    is KioskUiState.BackoffRetry,
    is KioskUiState.Offline,
    is KioskUiState.RendererGone,
    -> DiagnosticLevel.WARNING
    is KioskUiState.PageError,
    is KioskUiState.Fatal,
    -> DiagnosticLevel.ERROR
}

private const val CONFIG_MIME_TYPE = "application/vnd.kioskrelay.config+zip"
private const val EXPORT_RESULT_RESUME_GRACE_MILLIS = 500L

private val OnboardingDraftSaver = mapSaver(
    save = { draft ->
        mapOf(
            "locale" to draft.locale.name,
            "productName" to draft.productName,
            "primaryColor" to draft.primaryColor,
            "backgroundColor" to draft.backgroundColor,
            "logoUri" to draft.logoUri,
            "splashUri" to draft.splashUri,
            "url" to draft.url,
            "allowHttp" to draft.allowHttp,
            "orientation" to draft.orientation.name,
            "fullscreen" to draft.fullscreen,
            "keepScreenOn" to draft.keepScreenOn,
            "reloadOnNetworkRecovery" to draft.reloadOnNetworkRecovery,
            "bootLaunchEnabled" to draft.bootLaunchEnabled,
            "bootDelaySeconds" to draft.bootDelaySeconds,
            "webTestResult" to draft.webTestResult.name,
        )
    },
    restore = { values ->
        OnboardingDraft(
            locale = SetupLocale.valueOf(values.getValue("locale") as String),
            productName = values.getValue("productName") as String,
            primaryColor = values.getValue("primaryColor") as Long,
            backgroundColor = values.getValue("backgroundColor") as Long,
            logoUri = values.getValue("logoUri") as String,
            splashUri = values.getValue("splashUri") as String,
            url = values.getValue("url") as String,
            allowHttp = values.getValue("allowHttp") as Boolean,
            orientation = SetupOrientation.valueOf(
                values.getValue("orientation") as String,
            ),
            fullscreen = values.getValue("fullscreen") as Boolean,
            keepScreenOn = values.getValue("keepScreenOn") as Boolean,
            reloadOnNetworkRecovery =
                values.getValue("reloadOnNetworkRecovery") as Boolean,
            bootLaunchEnabled = values.getValue("bootLaunchEnabled") as Boolean,
            bootDelaySeconds = values.getValue("bootDelaySeconds") as Int,
            webTestResult = WebTestResult.valueOf(
                values.getValue("webTestResult") as String,
            ),
        )
    },
)
