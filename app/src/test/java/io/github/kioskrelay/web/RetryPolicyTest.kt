package io.github.kioskrelay.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RetryPolicyTest {
    @Test
    fun `default policy exposes the planned retry sequence`() {
        val policy = RetryPolicy()

        assertEquals(6, policy.maxRetries)
        assertEquals(5_000L, policy.delayMillisForFailure(1))
        assertEquals(10_000L, policy.delayMillisForFailure(2))
        assertEquals(20_000L, policy.delayMillisForFailure(3))
        assertEquals(40_000L, policy.delayMillisForFailure(4))
        assertEquals(60_000L, policy.delayMillisForFailure(5))
        assertEquals(60_000L, policy.delayMillisForFailure(6))
        assertNull(policy.delayMillisForFailure(7))
    }

    @Test
    fun `seconds factory converts without losing precision`() {
        val policy = RetryPolicy.fromSeconds(listOf(1, 7, 300))

        assertEquals(1_000L, policy.delayMillisForFailure(1))
        assertEquals(7_000L, policy.delayMillisForFailure(2))
        assertEquals(300_000L, policy.delayMillisForFailure(3))
    }

    @Test
    fun `failure number must be one based`() {
        assertThrows(IllegalArgumentException::class.java) {
            RetryPolicy().delayMillisForFailure(0)
        }
    }

    @Test
    fun `retry delays must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            RetryPolicy(listOf(1_000L, 0L))
        }
    }
}
