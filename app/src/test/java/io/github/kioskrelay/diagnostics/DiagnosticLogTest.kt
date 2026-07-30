package io.github.kioskrelay.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticLogTest {
    @Test
    fun ringDropsOldestAndRedactsSecretsAndUrlDetails() {
        var now = 10L
        val log = DiagnosticRingLog(capacity = 2) { now++ }

        log.record("network", "first")
        log.record(
            "web",
            "GET https://example.com/page?token=abc Cookie: session=secret",
        )
        log.record("auth", "Authorization: Bearer top-secret")

        val events = log.snapshot()
        assertEquals(2, events.size)
        assertTrue(events.first().message.contains("https://example.com"))
        assertFalse(events.first().message.contains("?token=abc"))
        assertFalse(log.exportText().contains("top-secret"))
        assertFalse(log.exportText().contains("session=secret"))
    }
}
