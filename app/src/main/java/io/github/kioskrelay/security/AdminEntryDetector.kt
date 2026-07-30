package io.github.kioskrelay.security

import android.os.SystemClock
import android.view.KeyEvent

/**
 * Stateful detector for the unobtrusive administrator entry gestures.
 *
 * Touch screens use five taps within three seconds. TV devices use the fixed
 * D-pad sequence: up, up, down, down, left, right, left, right, center.
 */
class AdminEntryDetector(
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private val tapTimes = ArrayDeque<Long>()
    private var keyIndex = 0

    fun registerTap(): Boolean {
        val now = clock()
        while (tapTimes.isNotEmpty() && now - tapTimes.first() > TAP_WINDOW_MILLIS) {
            tapTimes.removeFirst()
        }
        tapTimes.addLast(now)
        if (tapTimes.size < REQUIRED_TAPS) return false
        tapTimes.clear()
        return true
    }

    fun registerKey(keyCode: Int): Boolean {
        val expected = TV_KEY_SEQUENCE[keyIndex]
        keyIndex = when {
            keyCode == expected && keyIndex == TV_KEY_SEQUENCE.lastIndex -> {
                return true.also { keyIndex = 0 }
            }
            keyCode == expected -> keyIndex + 1
            keyCode == TV_KEY_SEQUENCE.first() -> 1
            else -> 0
        }
        return false
    }

    fun registerKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return false
        return registerKey(event.keyCode)
    }

    companion object {
        const val REQUIRED_TAPS = 5
        const val TAP_WINDOW_MILLIS = 3_000L

        val TV_KEY_SEQUENCE = intArrayOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
        )
    }
}
