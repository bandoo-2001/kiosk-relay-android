package io.github.kioskrelay.startup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StartupLaunchModeTest {
    @Test
    fun `disabled request never launches`() {
        assertEquals(
            StartupLaunchMode.DISABLED,
            StartupLaunchMode.resolve(apiLevel = 24, enabled = false),
        )
        assertEquals(
            StartupLaunchMode.DISABLED,
            StartupLaunchMode.resolve(apiLevel = 36, enabled = false),
        )
    }

    @Test
    fun `android 7 through 9 use direct launch`() {
        assertEquals(
            StartupLaunchMode.DIRECT_ACTIVITY,
            StartupLaunchMode.resolve(apiLevel = 24, enabled = true),
        )
        assertEquals(
            StartupLaunchMode.DIRECT_ACTIVITY,
            StartupLaunchMode.resolve(apiLevel = 28, enabled = true),
        )
    }

    @Test
    fun `android 10 and newer use notification fallback`() {
        assertEquals(
            StartupLaunchMode.NOTIFICATION_FALLBACK,
            StartupLaunchMode.resolve(apiLevel = 29, enabled = true),
        )
        assertEquals(
            StartupLaunchMode.NOTIFICATION_FALLBACK,
            StartupLaunchMode.resolve(apiLevel = 36, enabled = true),
        )
    }

    @Test
    fun `android 11 launches only with background launch authorization`() {
        assertEquals(
            StartupLaunchMode.DIRECT_ACTIVITY,
            StartupLaunchMode.resolve(30, enabled = true, overlayGranted = true),
        )
        assertEquals(
            StartupLaunchMode.NOTIFICATION_FALLBACK,
            StartupLaunchMode.resolve(30, enabled = true, overlayGranted = false),
        )
        assertEquals(
            StartupLaunchMode.DISABLED,
            StartupLaunchMode.resolve(30, enabled = false, overlayGranted = true),
        )
    }

    @Test
    fun `boot delay is bounded to one minute`() {
        StartupRequest(enabled = true, delaySeconds = 60)

        assertThrows(IllegalArgumentException::class.java) {
            StartupRequest(enabled = true, delaySeconds = 61)
        }
    }
}
