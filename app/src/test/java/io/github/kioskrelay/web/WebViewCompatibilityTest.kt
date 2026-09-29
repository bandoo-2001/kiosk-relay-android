package io.github.kioskrelay.web

import org.junit.Assert.*
import org.junit.Test

class WebViewCompatibilityTest {
    @Test fun realEngineVersion_controlsWarningThreshold() {
        assertEquals(79, chromiumMajor("Mozilla/5.0 (Linux; Android 11; TV) AppleWebKit/537.36 Chrome/79.0.3945.136 Safari/537.36"))
        assertTrue(needsCompatibilityWarning(79))
        assertFalse(needsCompatibilityWarning(80))
        assertFalse(needsCompatibilityWarning(130))
        assertNull(chromiumMajor("VendorBrowser/11"))
        assertTrue(needsCompatibilityWarning(null))
    }
}
