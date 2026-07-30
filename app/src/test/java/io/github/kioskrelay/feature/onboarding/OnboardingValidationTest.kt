package io.github.kioskrelay.feature.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingValidationTest {
    @Test
    fun httpsIsAcceptedByDefault() {
        assertTrue(isValidDashboardUrl("https://example.com/dashboard", allowHttp = false))
    }

    @Test
    fun httpRequiresExplicitOptIn() {
        assertFalse(isValidDashboardUrl("http://192.168.1.20:8080", allowHttp = false))
        assertTrue(isValidDashboardUrl("http://192.168.1.20:8080", allowHttp = true))
    }

    @Test
    fun userInfoAndUnknownSchemesAreRejected() {
        assertFalse(isValidDashboardUrl("https://admin:secret@example.com", allowHttp = false))
        assertFalse(isValidDashboardUrl("file:///sdcard/index.html", allowHttp = true))
    }
}
