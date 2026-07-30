package io.github.kioskrelay.security

import java.security.SecureRandom
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminAuthenticatorTest {
    @Test
    fun setAndVerifyPassword_storesOnlySaltedHash() = runBlocking {
        val store = MemoryCredentialStore()
        val authenticator = newAuthenticator(store)

        assertEquals(PasswordSetResult.Success, authenticator.setPassword(chars("correct1")))
        assertTrue(authenticator.hasPassword())
        assertEquals(AuthenticationResult.Success, authenticator.verify(chars("correct1")))

        val record = requireNotNull(store.read())
        assertEquals(16, record.salt.size)
        assertFalse(record.hash.contentEquals("correct1".toByteArray()))
        assertEquals(200_000, record.iterations)
    }

    @Test
    fun passwordLength_isEnforced() = runBlocking {
        val authenticator = newAuthenticator(MemoryCredentialStore())

        assertTrue(authenticator.setPassword(chars("12345")) is PasswordSetResult.InvalidLength)
        assertTrue(authenticator.setPassword(CharArray(65) { 'a' }) is PasswordSetResult.InvalidLength)
        assertFalse(authenticator.hasPassword())
    }

    @Test
    fun fiveFailuresLockAndSubsequentBatchesIncreaseDelay() = runBlocking {
        val store = MemoryCredentialStore()
        val clock = MutableClock(1_000L)
        val authenticator = newAuthenticator(store, clock)
        authenticator.setPassword(chars("correct1"))

        repeat(4) { index ->
            val result = authenticator.verify(chars("incorrect"))
            assertEquals(
                4 - index,
                (result as AuthenticationResult.InvalidCredential)
                    .remainingAttemptsBeforeLock,
            )
        }
        val firstLock = authenticator.verify(chars("incorrect")) as AuthenticationResult.Locked
        assertEquals(30_000L, firstLock.remainingMillis)
        assertEquals(1, firstLock.lockLevel)

        clock.advance(30_000L)
        repeat(4) { authenticator.verify(chars("incorrect")) }
        val secondLock = authenticator.verify(chars("incorrect")) as AuthenticationResult.Locked
        assertEquals(60_000L, secondLock.remainingMillis)
        assertEquals(2, secondLock.lockLevel)
    }

    @Test
    fun successAfterLockExpires_resetsEscalation() = runBlocking {
        val store = MemoryCredentialStore()
        val clock = MutableClock(1_000L)
        val authenticator = newAuthenticator(store, clock)
        authenticator.setPassword(chars("correct1"))
        repeat(5) { authenticator.verify(chars("incorrect")) }

        clock.advance(30_000L)
        assertEquals(AuthenticationResult.Success, authenticator.verify(chars("correct1")))

        repeat(5) { authenticator.verify(chars("incorrect")) }
        val lock = authenticator.verify(chars("incorrect"))
        assertTrue(lock is AuthenticationResult.Locked)
        assertEquals(1, (lock as AuthenticationResult.Locked).lockLevel)
    }

    private fun newAuthenticator(
        store: AdminCredentialStore,
        clock: MutableClock = MutableClock(1_000L),
    ): Pbkdf2AdminAuthenticator = Pbkdf2AdminAuthenticator(
        store = store,
        clock = clock,
        secureRandom = SecureRandom(byteArrayOf(1, 2, 3, 4)),
    )

    private fun chars(value: String): CharArray = value.toCharArray()

    private class MutableClock(
        private var current: Long,
    ) : EpochClock {
        override fun nowEpochMillis(): Long = current

        fun advance(millis: Long) {
            current += millis
        }
    }

    private class MemoryCredentialStore : AdminCredentialStore {
        private var record: AdminCredentialRecord? = null

        override fun read(): AdminCredentialRecord? = record?.defensiveCopy()

        override fun write(record: AdminCredentialRecord) {
            this.record = record.defensiveCopy()
        }

        override fun clear() {
            record = null
        }
    }
}
