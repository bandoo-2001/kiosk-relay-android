package io.github.kioskrelay.web

import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureWebViewInstrumentedTest {
    private var webView: WebView? = null
    private var controllerScope: CoroutineScope? = null

    @After
    fun tearDown() {
        controllerScope?.cancel()
        controllerScope = null
        val view = webView ?: return
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.stopLoading()
            view.destroy()
        }
        webView = null
    }

    @Test
    fun factory_appliesLockedDownApi24CompatibleSettings() {
        val runtime = requireNotNull(
            WebViewRuntimeConfig.forPageProbe(
                rawUrl = "https://display.example.com/",
                allowHttp = false,
            ),
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val client = secureWebViewClient(runtime.navigationPolicy, NoOpEvents)
            val view = createSecureWebView(
                context = ApplicationProvider.getApplicationContext(),
                config = runtime,
                client = client,
            )
            webView = view

            assertTrue(view.settings.javaScriptEnabled)
            assertTrue(view.settings.domStorageEnabled)
            assertFalse(view.settings.allowFileAccess)
            assertFalse(view.settings.allowContentAccess)
            assertFalse(view.settings.javaScriptCanOpenWindowsAutomatically)
            assertFalse(view.settings.supportMultipleWindows())
            assertTrue(view.settings.mixedContentMode == WebSettings.MIXED_CONTENT_NEVER_ALLOW)
            assertFalse(CookieManager.getInstance().acceptThirdPartyCookies(view))
        }
    }

    @Test
    fun committedCrossOriginRedirect_isStoppedAndReported() {
        val blocked = AtomicBoolean(false)
        val runtime = requireNotNull(
            WebViewRuntimeConfig.forPageProbe(
                rawUrl = "https://display.example.com/",
                allowHttp = false,
            ),
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val events = object : SecureWebViewEvents by NoOpEvents {
                override fun onNavigationBlocked(view: WebView, url: String) {
                    blocked.set(true)
                }
            }
            val client = SecureWebViewClient(runtime.navigationPolicy, events)
            val view = createSecureWebView(
                context = ApplicationProvider.getApplicationContext(),
                config = runtime,
                client = client,
            )
            webView = view
            client.onPageStarted(view, "https://evil.example.net/", null)
        }

        assertTrue(blocked.get())
    }

    @Test
    fun repeatedMainFrameFailures_stopAtConfiguredFatalLimit() {
        lateinit var controller: WebViewController
        val runtime = requireNotNull(
            WebViewRuntimeConfig.forPageProbe(
                rawUrl = "https://display.example.invalid/",
                allowHttp = false,
            ),
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            controllerScope = scope
            controller = WebViewController(scope)
            val view = createSecureWebView(
                context = ApplicationProvider.getApplicationContext(),
                config = runtime,
                client = secureWebViewClient(runtime.navigationPolicy, controller),
            )
            webView = view
            controller.attach(view, runtime)
            view.stopLoading()
            repeat(runtime.retryPolicy.maxRetries + 1) { attempt ->
                controller.onPageStarted(view, runtime.initialUrl)
                controller.onMainFrameError(
                    view = view,
                    url = runtime.initialUrl,
                    errorCode = -2,
                    description = "synthetic failure $attempt",
                )
            }
        }

        assertTrue(controller.state.value is KioskUiState.Fatal)
    }

    @Test
    fun networkLoss_movesRuntimeToOfflineWithoutValidatedInternet() {
        lateinit var controller: WebViewController
        val runtime = requireNotNull(
            WebViewRuntimeConfig.forPageProbe(
                rawUrl = "https://display.example.invalid/",
                allowHttp = false,
            ),
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            controllerScope = scope
            controller = WebViewController(scope)
            val view = createSecureWebView(
                context = ApplicationProvider.getApplicationContext(),
                config = runtime,
                client = secureWebViewClient(runtime.navigationPolicy, controller),
            )
            webView = view
            controller.attach(view, runtime)
            view.stopLoading()
            controller.onNetworkAvailabilityChanged(false)
        }

        assertTrue(controller.state.value is KioskUiState.Offline)
    }

    @Test
    fun repeatedRendererLoss_stopsAutomaticRecreation() {
        lateinit var controller: WebViewController
        val runtime = requireNotNull(
            WebViewRuntimeConfig.forPageProbe(
                rawUrl = "https://display.example.invalid/",
                allowHttp = false,
            ),
        )
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            controllerScope = scope
            controller = WebViewController(scope)
            val view = createSecureWebView(
                context = ApplicationProvider.getApplicationContext(),
                config = runtime,
                client = secureWebViewClient(runtime.navigationPolicy, controller),
            )
            webView = view
            controller.attach(view, runtime)
            view.stopLoading()
            repeat(4) {
                controller.onRendererGone(view, didCrash = true)
            }
        }

        assertTrue(controller.state.value is KioskUiState.Fatal)
    }

    private object NoOpEvents : SecureWebViewEvents {
        override fun onPageStarted(view: WebView, url: String) = Unit
        override fun onPageFinished(view: WebView, url: String) = Unit

        override fun onMainFrameError(
            view: WebView,
            url: String?,
            errorCode: Int?,
            description: String,
        ) = Unit

        override fun onNavigationBlocked(view: WebView, url: String) = Unit
        override fun onSslError(view: WebView, url: String?, primaryError: Int) = Unit
        override fun onRendererGone(view: WebView, didCrash: Boolean) = Unit
    }
}
