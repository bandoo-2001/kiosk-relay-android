package io.github.kioskrelay.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import io.github.kioskrelay.ui.RemoteOutlinedButton as OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.kioskrelay.ui.initialFocus
import io.github.kioskrelay.R
import io.github.kioskrelay.web.KioskUiState
import io.github.kioskrelay.web.WebViewVersionFrame
import io.github.kioskrelay.web.WebViewHost
import io.github.kioskrelay.web.WebViewRuntimeConfig

internal data class WebTestDialogLayout(
    val dialogWidth: Dp,
    val dialogHeight: Dp,
    val previewWidth: Dp,
    val previewHeight: Dp,
)

internal fun calculateWebTestDialogLayout(
    screenWidth: Dp,
    screenHeight: Dp,
    orientation: SetupOrientation,
): WebTestDialogLayout {
    val physicalLandscape = screenWidth >= screenHeight
    val dialogWidth = minOf(
        screenWidth * if (physicalLandscape) 0.92f else 0.94f,
        if (physicalLandscape) 1_200.dp else 720.dp,
    )
    val dialogHeight = minOf(
        screenHeight * 0.92f,
        if (physicalLandscape) 900.dp else 1_100.dp,
    )
    val previewAreaWidth = maxOf(dialogWidth - 40.dp, 96.dp)
    val previewAreaHeight = maxOf(dialogHeight - 164.dp, 96.dp)

    val longSide = maxOf(screenWidth.value, screenHeight.value)
    val shortSide = minOf(screenWidth.value, screenHeight.value).coerceAtLeast(1f)
    val targetAspectRatio = when (orientation) {
        SetupOrientation.LANDSCAPE -> longSide / shortSide
        SetupOrientation.PORTRAIT -> shortSide / longSide
        SetupOrientation.FOLLOW_SYSTEM ->
            screenWidth.value / screenHeight.value.coerceAtLeast(1f)
    }

    val heightWhenWidthLimited = previewAreaWidth / targetAspectRatio
    val previewWidth: Dp
    val previewHeight: Dp
    if (heightWhenWidthLimited <= previewAreaHeight) {
        previewWidth = previewAreaWidth
        previewHeight = heightWhenWidthLimited
    } else {
        previewHeight = previewAreaHeight
        previewWidth = previewAreaHeight * targetAspectRatio
    }

    return WebTestDialogLayout(
        dialogWidth = dialogWidth,
        dialogHeight = dialogHeight,
        previewWidth = previewWidth,
        previewHeight = previewHeight,
    )
}

@Composable
fun WebTestDialog(
    url: String,
    allowHttp: Boolean,
    orientation: SetupOrientation,
    onResult: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val runtimeConfig = remember(url, allowHttp) {
        WebViewRuntimeConfig.forPageProbe(url, allowHttp)
    }
    if (runtimeConfig == null) {
        LaunchedEffect(Unit) {
            onResult(false)
            onDismiss()
        }
        return
    }
    var state by remember { mutableStateOf<KioskUiState>(KioskUiState.Starting) }

    LaunchedEffect(state) {
        when (state) {
            is KioskUiState.Online -> onResult(true)
            is KioskUiState.Offline,
            is KioskUiState.PageError,
            is KioskUiState.Fatal,
            -> onResult(false)
            else -> Unit
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val layout = calculateWebTestDialogLayout(
                screenWidth = maxWidth,
                screenHeight = maxHeight,
                orientation = orientation,
            )
            Surface(
                modifier = Modifier
                    .size(layout.dialogWidth, layout.dialogHeight)
                    .testTag("web-test-dialog"),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.web_test_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    WebViewVersionFrame(
                        modifier = Modifier
                            .size(layout.previewWidth, layout.previewHeight)
                            .align(Alignment.CenterHorizontally)
                            .testTag("web-test-preview"),
                    ) {
                        WebViewHost(
                            config = runtimeConfig,
                            modifier = Modifier.fillMaxSize(),
                            onStateChange = { state = it },
                        )
                        if (
                            state is KioskUiState.Starting ||
                            state is KioskUiState.Loading ||
                            state is KioskUiState.BackoffRetry
                        ) {
                            CircularProgressIndicator()
                        }
                        val failureDetail = when (val current = state) {
                            is KioskUiState.Fatal -> current.reason
                            is KioskUiState.PageError -> current.description
                            else -> null
                        }
                        failureDetail?.let {
                            Surface(modifier = Modifier.fillMaxSize()) {
                                Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text(it)
                                }
                            }
                        }
                    }
                    Text(
                        when (state) {
                            is KioskUiState.CompatibilityWarning -> stringResource(R.string.web_engine_title)
                            is KioskUiState.Online -> stringResource(R.string.test_succeeded)
                            is KioskUiState.Offline,
                            is KioskUiState.PageError,
                            is KioskUiState.Fatal,
                            -> stringResource(R.string.test_failed)
                            else -> stringResource(R.string.web_test_in_progress)
                        },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.initialFocus()) {
                            Text(stringResource(R.string.close))
                        }
                    }
                }
            }
        }
    }
}
