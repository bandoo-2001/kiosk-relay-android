package io.github.kioskrelay.web

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.github.kioskrelay.config.KioskRelayConfig
import kotlinx.coroutines.flow.collectLatest

@Composable
fun rememberWebViewController(): WebViewController {
    val scope = rememberCoroutineScope()
    return remember(scope) { WebViewController(scope) }
}

@Composable
fun rememberNetworkMonitor(): NetworkMonitor {
    val applicationContext = LocalContext.current.applicationContext
    return remember(applicationContext) { NetworkMonitor(applicationContext) }
}

/**
 * Hosts a single hardened WebView and recreates it when settings change or its renderer exits.
 *
 * The composable owns starting/stopping [networkMonitor], but the monitor itself may be remembered
 * by a parent and inspected elsewhere. [onStateChange] is also suitable for the onboarding "test
 * page" action: Online is success, PageError/Fatal is failure, and BackoffRetry is pending.
 */
@Composable
fun WebViewHost(
    config: WebViewRuntimeConfig,
    modifier: Modifier = Modifier.fillMaxSize(),
    controller: WebViewController = rememberWebViewController(),
    networkMonitor: NetworkMonitor = rememberNetworkMonitor(),
    onStateChange: (KioskUiState) -> Unit = {},
    requestInitialFocus: Boolean = false,
) {
    val networkAvailable by networkMonitor.isNetworkAvailable.collectAsState()
    val recreationKey by controller.recreationKey.collectAsState()
    val latestStateListener by rememberUpdatedState(onStateChange)

    DisposableEffect(networkMonitor) {
        networkMonitor.start()
        onDispose { networkMonitor.close() }
    }

    LaunchedEffect(networkAvailable) {
        controller.onNetworkAvailabilityChanged(networkAvailable)
    }

    LaunchedEffect(controller) {
        controller.state.collectLatest { latestStateListener(it) }
    }

    key(recreationKey, config) {
        AndroidView(
            factory = { context ->
                val client = secureWebViewClient(config.navigationPolicy, controller)
                createSecureWebView(context, config, client).also { view ->
                    controller.attach(view, config)
                    if (requestInitialFocus) view.requestFocus()
                }
            },
            modifier = modifier,
            onReset = null,
            onRelease = { view ->
                releaseWebView(controller, view)
            },
            update = {},
        )
    }
}

/**
 * Convenience overload for screens that already observe the complete stored configuration.
 */
@Composable
fun KioskWebViewHost(
    config: KioskRelayConfig,
    modifier: Modifier = Modifier.fillMaxSize(),
    controller: WebViewController = rememberWebViewController(),
    networkMonitor: NetworkMonitor = rememberNetworkMonitor(),
    onStateChange: (KioskUiState) -> Unit = {},
) {
    val runtimeConfig = remember(config) { WebViewRuntimeConfig.from(config) }
    WebViewHost(
        config = runtimeConfig,
        modifier = modifier,
        controller = controller,
        networkMonitor = networkMonitor,
        onStateChange = onStateChange,
        requestInitialFocus = true,
    )
}

private fun releaseWebView(
    controller: WebViewController,
    view: WebView,
) {
    controller.detach(view)
    view.stopLoading()
    view.webViewClient = WebViewClient()
    view.webChromeClient = WebChromeClient()
    view.removeAllViews()
    view.destroy()
}
