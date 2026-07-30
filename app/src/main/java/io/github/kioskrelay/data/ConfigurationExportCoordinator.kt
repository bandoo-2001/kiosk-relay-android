package io.github.kioskrelay.data

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ConfigurationExportStatus {
    SUCCEEDED,
    CANCELLED,
    FAILED,
}

data class ConfigurationExportOutcome(
    val generation: Long,
    val status: ConfigurationExportStatus,
    val errorMessage: String? = null,
)

/**
 * Serializes export preparation and provider copies independently from managed branding files.
 *
 * A slow or broken external document provider may hold this lock while receiving a copy, but it
 * must never block startup branding recovery or normal kiosk operation. Active export ownership
 * and result handling live at application scope so an Activity recreation cannot launch a second
 * picker or strand the UI while the first result is being copied.
 */
class ConfigurationExportCoordinator {
    private val mutex = Mutex()
    private val stateLock = Any()
    private val operationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val outcomeGeneration = AtomicLong()
    private val _activeSnapshotId = MutableStateFlow<String?>(null)
    private val _latestOutcome = MutableStateFlow<ConfigurationExportOutcome?>(null)
    private var completionStarted = false

    val activeSnapshotId: StateFlow<String?> = _activeSnapshotId.asStateFlow()
    val latestOutcome: StateFlow<ConfigurationExportOutcome?> = _latestOutcome.asStateFlow()

    suspend fun <T> runExclusive(block: suspend () -> T): T =
        mutex.withLock { block() }

    fun activate(snapshotId: String): Boolean = synchronized(stateLock) {
        if (_activeSnapshotId.value != null) {
            false
        } else {
            completionStarted = false
            _activeSnapshotId.value = snapshotId
            true
        }
    }

    fun restore(snapshotId: String): Boolean = synchronized(stateLock) {
        when (val active = _activeSnapshotId.value) {
            null -> {
                completionStarted = false
                _activeSnapshotId.value = snapshotId
                true
            }
            snapshotId -> true
            else -> false
        }
    }

    fun deactivate(snapshotId: String) {
        synchronized(stateLock) {
            if (_activeSnapshotId.value == snapshotId && !completionStarted) {
                _activeSnapshotId.value = null
            }
        }
    }

    fun complete(
        snapshotId: String,
        cancelled: Boolean,
        block: suspend () -> Unit,
    ): Boolean {
        val accepted = synchronized(stateLock) {
            if (_activeSnapshotId.value != snapshotId || completionStarted) {
                false
            } else {
                completionStarted = true
                true
            }
        }
        if (!accepted) return false

        operationScope.launch {
            val result = runCatching { block() }
            val outcome = when {
                result.isFailure -> ConfigurationExportOutcome(
                    generation = outcomeGeneration.incrementAndGet(),
                    status = ConfigurationExportStatus.FAILED,
                    errorMessage = result.exceptionOrNull()?.message.orEmpty(),
                )
                cancelled -> ConfigurationExportOutcome(
                    generation = outcomeGeneration.incrementAndGet(),
                    status = ConfigurationExportStatus.CANCELLED,
                )
                else -> ConfigurationExportOutcome(
                    generation = outcomeGeneration.incrementAndGet(),
                    status = ConfigurationExportStatus.SUCCEEDED,
                )
            }
            synchronized(stateLock) {
                if (_activeSnapshotId.value == snapshotId) {
                    _activeSnapshotId.value = null
                    completionStarted = false
                    _latestOutcome.value = outcome
                }
            }
        }
        return true
    }

    fun consumeOutcome(generation: Long): ConfigurationExportOutcome? =
        synchronized(stateLock) {
            _latestOutcome.value
                ?.takeIf { it.generation == generation }
                ?.also { _latestOutcome.value = null }
        }
}
