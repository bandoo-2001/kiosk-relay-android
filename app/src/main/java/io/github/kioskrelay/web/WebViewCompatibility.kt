package io.github.kioskrelay.web

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.webkit.WebSettings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.webkit.WebViewCompat
import io.github.kioskrelay.R
import io.github.kioskrelay.ui.RemoteButton
import io.github.kioskrelay.ui.RemoteOutlinedButton
import io.github.kioskrelay.ui.initialFocus

// Chrome 80 introduced ?. and ??, both used by the configured dashboard.
// This is a warning baseline, not a guarantee that all webpage features are supported.
internal fun chromiumMajor(userAgent: String): Int? =
    Regex("(?:Chrome|Chromium)/(\\d+)").find(userAgent)?.groupValues?.get(1)?.toIntOrNull()

internal fun needsCompatibilityWarning(major: Int?): Boolean = major == null || major < 80

internal data class WebViewCompatibility(val label: String, val major: Int?, val available: Boolean)

internal fun inspectWebView(context: Context): WebViewCompatibility = runCatching {
    val provider = WebViewCompat.getCurrentWebViewPackage(context)
    // Inspect the real provider UA, never the user-configured webpage UA override.
    val major = chromiumMajor(WebSettings.getDefaultUserAgent(context))
    WebViewCompatibility("${provider?.packageName ?: "WebView"} ${provider?.versionName ?: "?"} (Chromium ${major ?: "?"})", major, true)
}.getOrElse { WebViewCompatibility("WebView unavailable", null, false) }

@Composable
internal fun WebViewCompatibilityNotice(
    report: WebViewCompatibility,
    modifier: Modifier = Modifier,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    Surface(modifier = modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.web_engine_title), style = MaterialTheme.typography.titleLarge)
            Text(report.label)
            Text(stringResource(if (report.available) R.string.web_engine_old else R.string.web_engine_missing))
            RemoteOutlinedButton(
                modifier = Modifier.initialFocus(),
                onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                        .onFailure { Toast.makeText(context, R.string.web_settings_unavailable, Toast.LENGTH_LONG).show() }
                },
            ) { Text(stringResource(R.string.web_system_settings)) }
            if (report.available) {
                RemoteButton(onClick = onContinue) { Text(stringResource(R.string.web_try_anyway)) }
            }
        }
    }
}
