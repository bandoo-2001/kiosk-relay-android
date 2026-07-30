package io.github.kioskrelay.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewRuntimeConfigTest {
    @Test
    fun `page probe allows only the candidate exact origin`() {
        val candidate = WebViewRuntimeConfig.forPageProbe(
            rawUrl = "https://Example.com/dashboard",
            allowHttp = false,
        )
        assertNotNull(candidate)
        val config = checkNotNull(candidate)

        assertTrue(config.navigationPolicy.isAllowed("https://example.com/next"))
        assertFalse(config.navigationPolicy.isAllowed("https://sub.example.com/next"))
        assertFalse(config.navigationPolicy.isAllowed("http://example.com/next"))
    }

    @Test
    fun `page probe rejects http unless explicitly enabled`() {
        assertNull(
            WebViewRuntimeConfig.forPageProbe(
                rawUrl = "http://192.168.1.10/",
                allowHttp = false,
            ),
        )
        val config = WebViewRuntimeConfig.forPageProbe(
            rawUrl = "http://192.168.1.10/",
            allowHttp = true,
        )
        assertNotNull(config)
    }
}
