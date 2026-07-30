package io.github.kioskrelay.feature.kiosk

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.kioskrelay.R
import io.github.kioskrelay.config.KioskRelayConfig
import io.github.kioskrelay.data.BrandImageImporter
import io.github.kioskrelay.security.AdminAuthenticator
import io.github.kioskrelay.security.AdminEntryDetector
import io.github.kioskrelay.security.AuthenticationResult
import io.github.kioskrelay.ui.BrandLogo
import io.github.kioskrelay.ui.StartupBrandScreen
import io.github.kioskrelay.web.KioskUiState
import io.github.kioskrelay.web.KioskWebViewHost
import io.github.kioskrelay.web.WebViewController
import kotlinx.coroutines.launch

@Composable
fun KioskScreen(
    config: KioskRelayConfig,
    imageImporter: BrandImageImporter,
    controller: WebViewController,
    authenticator: AdminAuthenticator,
    registerTvAdminEntryHandler: ((() -> Unit)?) -> Unit,
    onOpenSettings: () -> Unit,
    onStateChange: (KioskUiState) -> Unit,
    modifier: Modifier = Modifier,
) {
    var state by remember { mutableStateOf<KioskUiState>(KioskUiState.Starting) }
    var showAuthentication by rememberSaveable { mutableStateOf(false) }
    var checkingAdminRequirement by remember { mutableStateOf(false) }
    val entryDetector = remember { AdminEntryDetector() }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    val requestAdminEntry: () -> Unit = {
        if (!checkingAdminRequirement) {
            checkingAdminRequirement = true
            scope.launch {
                val passwordConfigured = runCatching {
                    authenticator.hasPassword()
                }.getOrDefault(true)
                checkingAdminRequirement = false
                if (passwordConfigured) {
                    showAuthentication = true
                } else {
                    onOpenSettings()
                }
            }
        }
    }

    DisposableEffect(registerTvAdminEntryHandler, authenticator, onOpenSettings) {
        registerTvAdminEntryHandler(requestAdminEntry)
        onDispose { registerTvAdminEntryHandler(null) }
    }

    BackHandler(enabled = true) {
        // At the configured root this intentionally consumes Back so kiosk users cannot leave.
        controller.goBackIfPossible()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                entryDetector.registerKeyEvent(event.nativeKeyEvent).also { matched ->
                    if (matched) requestAdminEntry()
                }
            },
    ) {
        KioskWebViewHost(
            config = config,
            controller = controller,
            onStateChange = {
                state = it
                onStateChange(it)
            },
        )

        KioskStateOverlay(
            state = state,
            config = config,
            imageImporter = imageImporter,
            onRetry = controller::reload,
        )

        // There is deliberately no visible Release affordance over the customer webpage.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(72.dp)
                .pointerInput(entryDetector) {
                    detectTapGestures {
                        if (entryDetector.registerTap()) requestAdminEntry()
                    }
                },
        )
    }

    LaunchedEffect(focusRequester) {
        runCatching { focusRequester.requestFocus() }
    }

    if (showAuthentication) {
        AdminAuthenticationDialog(
            authenticator = authenticator,
            onDismiss = { showAuthentication = false },
            onAuthenticated = {
                showAuthentication = false
                onOpenSettings()
            },
        )
    }
}

@Composable
private fun KioskStateOverlay(
    state: KioskUiState,
    config: KioskRelayConfig,
    imageImporter: BrandImageImporter,
    onRetry: () -> Unit,
) {
    if (state is KioskUiState.Online) return
    if (state is KioskUiState.Starting || state is KioskUiState.Loading) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(state) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                },
        ) {
            StartupBrandScreen(
                branding = config.branding,
                imageImporter = imageImporter,
            )
        }
        return
    }

    val background = Color(config.branding.backgroundColorArgb).copy(alpha = 0.96f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .pointerInput(state) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val stateArtwork = when (state) {
                is KioskUiState.Offline -> R.drawable.offline_state
                is KioskUiState.PageError,
                is KioskUiState.Fatal,
                -> R.drawable.load_error_state
                else -> null
            }
            if (stateArtwork != null) {
                Image(
                    painter = painterResource(stateArtwork),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .heightIn(max = 180.dp),
                )
            } else {
                BrandLogo(
                    branding = config.branding,
                    imageImporter = imageImporter,
                )
            }
            Text(
                config.branding.productName,
                style = MaterialTheme.typography.headlineMedium,
            )
            when (state) {
                KioskUiState.Starting,
                is KioskUiState.Loading,
                -> Unit
                is KioskUiState.Offline -> {
                    Text(
                        config.branding.offlineMessage.ifBlank {
                            stringResource(R.string.default_offline_message)
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Button(onClick = onRetry) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.retry_now))
                    }
                }
                is KioskUiState.PageError -> {
                    Text(
                        config.branding.errorMessage.ifBlank {
                            stringResource(R.string.brand_default_error_message)
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        state.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onRetry) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.retry_now))
                    }
                }
                is KioskUiState.BackoffRetry -> {
                    CircularProgressIndicator(color = Color(config.branding.primaryColorArgb))
                    Text(
                        stringResource(
                            R.string.kiosk_retrying,
                            state.attempt,
                            state.delayMillis / 1_000,
                        ),
                    )
                    OutlinedButton(onClick = onRetry) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.retry_now))
                    }
                }
                is KioskUiState.RendererGone -> {
                    CircularProgressIndicator(color = Color(config.branding.primaryColorArgb))
                    Text(stringResource(R.string.kiosk_renderer_recovering))
                }
                is KioskUiState.Fatal -> {
                    Text(
                        config.branding.errorMessage.ifBlank {
                            stringResource(R.string.brand_default_error_message)
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        state.reason,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onRetry) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.retry_now))
                    }
                }
                is KioskUiState.Online -> Unit
            }
        }
    }
}

@Composable
internal fun AdminAuthenticationDialog(
    authenticator: AdminAuthenticator,
    onDismiss: () -> Unit,
    onAuthenticated: () -> Unit,
) {
    // Keep credentials in memory only; rememberSaveable would serialize them into saved state.
    var password by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<AuthenticationResult?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.admin_unlock_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it.take(64)
                        result = null
                    },
                    label = { Text(stringResource(R.string.administrator_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = result != null,
                    enabled = !checking,
                    singleLine = true,
                    modifier = Modifier.testTag("admin-password"),
                )
                AuthenticationMessage(result)
            }
        },
        confirmButton = {
            Button(
                enabled = !checking && password.isNotEmpty(),
                onClick = {
                    checking = true
                    scope.launch {
                        val characters = password.toCharArray()
                        val verification = try {
                            authenticator.verify(characters)
                        } finally {
                            characters.fill('\u0000')
                        }
                        checking = false
                        result = verification
                        if (verification is AuthenticationResult.Success) {
                            password = ""
                            onAuthenticated()
                        } else {
                            password = ""
                        }
                    }
                },
            ) {
                if (checking) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.unlock))
                }
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
private fun AuthenticationMessage(result: AuthenticationResult?) {
    val message = when (result) {
        null,
        AuthenticationResult.Success,
        -> null
        AuthenticationResult.NotConfigured -> stringResource(R.string.admin_not_configured)
        is AuthenticationResult.InvalidInput -> stringResource(R.string.password_rule)
        is AuthenticationResult.InvalidCredential -> stringResource(
            R.string.admin_invalid_password,
            result.remainingAttemptsBeforeLock,
        )
        is AuthenticationResult.Locked -> stringResource(
            R.string.admin_locked,
            (result.remainingMillis / 1_000L).coerceAtLeast(1L),
        )
    }
    message?.let {
        Text(it, color = MaterialTheme.colorScheme.error)
    }
}
