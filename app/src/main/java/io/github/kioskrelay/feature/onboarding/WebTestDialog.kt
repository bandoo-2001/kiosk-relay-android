package io.github.kioskrelay.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kioskrelay.R
import io.github.kioskrelay.web.KioskUiState
import io.github.kioskrelay.web.WebViewHost
import io.github.kioskrelay.web.WebViewRuntimeConfig
import androidx.compose.ui.res.stringResource

@Composable
fun WebTestDialog(
    url: String,
    allowHttp: Boolean,
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.web_test_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    WebViewHost(
                        config = runtimeConfig,
                        onStateChange = { state = it },
                    )
                    if (
                        state is KioskUiState.Starting ||
                        state is KioskUiState.Loading ||
                        state is KioskUiState.BackoffRetry
                    ) {
                        CircularProgressIndicator()
                    }
                }
                Text(
                    when (state) {
                        is KioskUiState.Online -> stringResource(R.string.test_succeeded)
                        is KioskUiState.Offline,
                        is KioskUiState.PageError,
                        is KioskUiState.Fatal,
                        -> stringResource(R.string.test_failed)
                        else -> stringResource(R.string.web_test_in_progress)
                    },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}
