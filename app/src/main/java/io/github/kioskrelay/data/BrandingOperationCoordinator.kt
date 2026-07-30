package io.github.kioskrelay.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes every operation that reads, replaces, snapshots, or recovers managed branding files.
 *
 * The coordinator lives in the application container, so a newly recreated Activity cannot
 * recover a transaction while blocking image/ZIP I/O from the previous Activity is still
 * finishing on an IO dispatcher.
 */
class BrandingOperationCoordinator {
    private val mutex = Mutex()

    suspend fun <T> runExclusive(block: suspend () -> T): T =
        mutex.withLock { block() }
}
