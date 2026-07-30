package io.github.kioskrelay.feature.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPasswordValidationTest {
    @Test
    fun emptyPasswordAndConfirmation_areAccepted() {
        assertTrue(isOptionalAdministratorPasswordValid("", ""))
    }

    @Test
    fun configuredPassword_requiresValidLengthAndMatchingConfirmation() {
        assertTrue(isOptionalAdministratorPasswordValid("secure1", "secure1"))
        assertFalse(isOptionalAdministratorPasswordValid("short", "short"))
        assertFalse(isOptionalAdministratorPasswordValid("secure1", "different"))
        assertFalse(isOptionalAdministratorPasswordValid("", "secure1"))
    }
}
