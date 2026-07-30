package io.github.kioskrelay.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val PASSWORD_MIN_LENGTH = 6
private const val PASSWORD_MAX_LENGTH = 64
private const val DEFAULT_ITERATIONS = 200_000
private const val SALT_BYTES = 16
private const val HASH_BITS = 256
private const val FAILURES_BEFORE_LOCK = 5

data class AdminCredentialRecord(
    val salt: ByteArray,
    val hash: ByteArray,
    val iterations: Int,
    val failedAttempts: Int = 0,
    val lockLevel: Int = 0,
    val lockedUntilEpochMillis: Long = 0,
) {
    fun defensiveCopy(): AdminCredentialRecord = copy(
        salt = salt.copyOf(),
        hash = hash.copyOf(),
    )
}

interface AdminCredentialStore {
    fun read(): AdminCredentialRecord?

    fun write(record: AdminCredentialRecord)

    fun clear()
}

class SharedPreferencesAdminCredentialStore(
    context: Context,
) : AdminCredentialStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun read(): AdminCredentialRecord? {
        val saltValue = preferences.getString(KEY_SALT, null) ?: return null
        val hashValue = preferences.getString(KEY_HASH, null) ?: return null
        return try {
            AdminCredentialRecord(
                salt = Base64.decode(saltValue, Base64.NO_WRAP),
                hash = Base64.decode(hashValue, Base64.NO_WRAP),
                iterations = preferences.getInt(KEY_ITERATIONS, DEFAULT_ITERATIONS),
                failedAttempts = preferences.getInt(KEY_FAILED_ATTEMPTS, 0),
                lockLevel = preferences.getInt(KEY_LOCK_LEVEL, 0),
                lockedUntilEpochMillis = preferences.getLong(KEY_LOCKED_UNTIL, 0),
            ).takeIf {
                it.salt.size == SALT_BYTES &&
                    it.hash.size == HASH_BITS / 8 &&
                    it.iterations in 100_000..1_000_000 &&
                    it.failedAttempts in 0 until FAILURES_BEFORE_LOCK &&
                    it.lockLevel in 0..LOCK_DURATIONS_SECONDS.size
            }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override fun write(record: AdminCredentialRecord) {
        check(
            preferences.edit()
                .putString(KEY_SALT, Base64.encodeToString(record.salt, Base64.NO_WRAP))
                .putString(KEY_HASH, Base64.encodeToString(record.hash, Base64.NO_WRAP))
                .putInt(KEY_ITERATIONS, record.iterations)
                .putInt(KEY_FAILED_ATTEMPTS, record.failedAttempts)
                .putInt(KEY_LOCK_LEVEL, record.lockLevel)
                .putLong(KEY_LOCKED_UNTIL, record.lockedUntilEpochMillis)
                .commit(),
        ) {
            "Unable to persist administrator credential"
        }
    }

    override fun clear() {
        check(preferences.edit().clear().commit()) {
            "Unable to clear administrator credential"
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "kiosk_relay_admin_credential"
        const val KEY_SALT = "salt"
        const val KEY_HASH = "hash"
        const val KEY_ITERATIONS = "iterations"
        const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        const val KEY_LOCK_LEVEL = "lock_level"
        const val KEY_LOCKED_UNTIL = "locked_until"
    }
}

fun interface EpochClock {
    fun nowEpochMillis(): Long
}

sealed interface PasswordSetResult {
    data object Success : PasswordSetResult

    data class InvalidLength(
        val minimum: Int = PASSWORD_MIN_LENGTH,
        val maximum: Int = PASSWORD_MAX_LENGTH,
    ) : PasswordSetResult
}

sealed interface AuthenticationResult {
    data object Success : AuthenticationResult

    data object NotConfigured : AuthenticationResult

    data class InvalidInput(
        val minimum: Int = PASSWORD_MIN_LENGTH,
        val maximum: Int = PASSWORD_MAX_LENGTH,
    ) : AuthenticationResult

    data class InvalidCredential(
        val remainingAttemptsBeforeLock: Int,
    ) : AuthenticationResult

    data class Locked(
        val remainingMillis: Long,
        val lockLevel: Int,
    ) : AuthenticationResult
}

interface AdminAuthenticator {
    suspend fun hasPassword(): Boolean

    suspend fun setPassword(password: CharArray): PasswordSetResult

    suspend fun verify(password: CharArray): AuthenticationResult

    suspend fun clear()
}

class Pbkdf2AdminAuthenticator(
    private val store: AdminCredentialStore,
    private val clock: EpochClock = EpochClock(System::currentTimeMillis),
    private val secureRandom: SecureRandom = SecureRandom(),
) : AdminAuthenticator {
    private val mutex = Mutex()

    override suspend fun hasPassword(): Boolean = withContext(Dispatchers.Default) {
        mutex.withLock { store.read() != null }
    }

    override suspend fun setPassword(password: CharArray): PasswordSetResult =
        withContext(Dispatchers.Default) {
            if (!password.hasValidLength()) {
                return@withContext PasswordSetResult.InvalidLength()
            }
            mutex.withLock {
                val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
                val hash = derive(password, salt, DEFAULT_ITERATIONS)
                store.write(
                    AdminCredentialRecord(
                        salt = salt,
                        hash = hash,
                        iterations = DEFAULT_ITERATIONS,
                    ),
                )
                PasswordSetResult.Success
            }
        }

    override suspend fun verify(password: CharArray): AuthenticationResult =
        withContext(Dispatchers.Default) {
            if (!password.hasValidLength()) {
                return@withContext AuthenticationResult.InvalidInput()
            }
            mutex.withLock {
                val record = store.read()
                    ?: return@withLock AuthenticationResult.NotConfigured
                val now = clock.nowEpochMillis()
                if (record.lockedUntilEpochMillis > now) {
                    return@withLock AuthenticationResult.Locked(
                        remainingMillis = record.lockedUntilEpochMillis - now,
                        lockLevel = record.lockLevel,
                    )
                }

                val candidate = derive(password, record.salt, record.iterations)
                val matches = try {
                    MessageDigest.isEqual(record.hash, candidate)
                } finally {
                    candidate.fill(0)
                }
                if (matches) {
                    store.write(
                        record.copy(
                            failedAttempts = 0,
                            lockLevel = 0,
                            lockedUntilEpochMillis = 0,
                        ),
                    )
                    return@withLock AuthenticationResult.Success
                }

                val failures = record.failedAttempts + 1
                if (failures < FAILURES_BEFORE_LOCK) {
                    store.write(
                        record.copy(
                            failedAttempts = failures,
                            lockedUntilEpochMillis = 0,
                        ),
                    )
                    return@withLock AuthenticationResult.InvalidCredential(
                        remainingAttemptsBeforeLock = FAILURES_BEFORE_LOCK - failures,
                    )
                }

                val nextLevel = (record.lockLevel + 1)
                    .coerceAtMost(LOCK_DURATIONS_SECONDS.size)
                val lockMillis = LOCK_DURATIONS_SECONDS[nextLevel - 1] * 1_000L
                store.write(
                    record.copy(
                        failedAttempts = 0,
                        lockLevel = nextLevel,
                        lockedUntilEpochMillis = now + lockMillis,
                    ),
                )
                AuthenticationResult.Locked(
                    remainingMillis = lockMillis,
                    lockLevel = nextLevel,
                )
            }
        }

    override suspend fun clear() {
        withContext(Dispatchers.Default) {
            mutex.withLock { store.clear() }
        }
    }

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, HASH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun CharArray.hasValidLength(): Boolean =
        size in PASSWORD_MIN_LENGTH..PASSWORD_MAX_LENGTH
}

private val LOCK_DURATIONS_SECONDS = intArrayOf(30, 60, 120, 240, 300)
