package io.github.kioskrelay.security

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminEntryDetectorTest {
    @Test
    fun fiveTapsWithinWindowUnlock() {
        var now = 1_000L
        val detector = AdminEntryDetector { now }

        repeat(4) {
            assertFalse(detector.registerTap())
            now += 400L
        }

        assertTrue(detector.registerTap())
    }

    @Test
    fun staleTapsAreDiscarded() {
        var now = 1_000L
        val detector = AdminEntryDetector { now }

        repeat(4) {
            assertFalse(detector.registerTap())
            now += 400L
        }
        now += AdminEntryDetector.TAP_WINDOW_MILLIS

        assertFalse(detector.registerTap())
    }

    @Test
    fun tvSequenceUnlocksAndWrongKeyResets() {
        val detector = AdminEntryDetector()

        assertFalse(detector.registerKey(KeyEvent.KEYCODE_DPAD_LEFT))
        AdminEntryDetector.TV_KEY_SEQUENCE.dropLast(1).forEach {
            assertFalse(detector.registerKey(it))
        }

        assertTrue(detector.registerKey(KeyEvent.KEYCODE_DPAD_CENTER))
    }
}
