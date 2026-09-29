package io.github.kioskrelay.web

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import android.webkit.WebView
import java.util.concurrent.atomic.AtomicBoolean
import java.net.ServerSocket
import java.net.InetAddress
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PageReadinessTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller = WebViewController(scope, pageTimeoutMillis = 5_000L)

    @After fun cleanup() = scope.cancel()

    @Test fun stalledServer_reportsTimeoutInsteadOfWaitingForever() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val runtime = requireNotNull(WebViewRuntimeConfig.forPageProbe("http://127.0.0.1:${server.localPort}/", true))
            compose.setContent {
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                    createSecureWebView(context, runtime, secureWebViewClient(runtime.navigationPolicy, controller)).also {
                        controller.attach(it, runtime)
                    }
                }, onRelease = { controller.detach(it); it.destroy() })
            }
            compose.waitUntil(timeoutMillis = 10_000) { controller.state.value is KioskUiState.Fatal }
            assertTrue((controller.state.value as KioskUiState.Fatal).reason.contains("WebView"))
        }
    }

    @Test fun renderedPage_isReportedOnline() {
        val runtime = requireNotNull(WebViewRuntimeConfig.forPageProbe("https://display.example.com/", false))
        compose.setContent {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                createSecureWebView(context, runtime, secureWebViewClient(runtime.navigationPolicy, controller)).also {
                    controller.attach(it, runtime)
                    it.stopLoading()
                    it.loadDataWithBaseURL(runtime.initialUrl, "<html><body><h1>Ready</h1></body></html>", "text/html", "UTF-8", null)
                }
            }, onRelease = { controller.detach(it); it.destroy() })
        }
        compose.waitUntil(timeoutMillis = 10_000) { controller.state.value is KioskUiState.Online }
    }

    @Test fun emptySpaShell_isNotReportedAsOnline() {
        val runtime = requireNotNull(WebViewRuntimeConfig.forPageProbe("https://display.example.com/", false))
        val finished = AtomicBoolean(false)
        val events = object : SecureWebViewEvents by controller {
            override fun onPageFinished(view: WebView, url: String) {
                controller.onPageFinished(view, url)
                finished.set(true)
            }
        }
        compose.setContent {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                createSecureWebView(context, runtime, secureWebViewClient(runtime.navigationPolicy, events)).also {
                    controller.attach(it, runtime)
                    it.stopLoading()
                    it.loadDataWithBaseURL(runtime.initialUrl, "<html><body><div id='app'></div></body></html>", "text/html", "UTF-8", null)
                }
            }, onRelease = { controller.detach(it); it.destroy() })
        }
        // Wait for onPageFinished: an empty SPA shell must not be declared ready.
        compose.waitUntil(timeoutMillis = 10_000) { finished.get() }
        assertTrue("Empty page incorrectly reported online: ${controller.state.value}", controller.state.value !is KioskUiState.Online)
        compose.waitUntil(timeoutMillis = 10_000) { controller.state.value is KioskUiState.Fatal }
        assertTrue((controller.state.value as KioskUiState.Fatal).reason.contains("WebView"))
    }
}
