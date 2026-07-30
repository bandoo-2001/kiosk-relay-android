package io.github.kioskrelay.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigurationExportCoordinatorTest {
    @Test
    fun activeExport_blocksSecondOwnerUntilApplicationScopedCompletion() = runBlocking {
        val coordinator = ConfigurationExportCoordinator()
        val releaseCopy = CompletableDeferred<Unit>()

        assertTrue(coordinator.activate("first"))
        assertFalse(coordinator.activate("second"))
        assertTrue(
            coordinator.complete(
                snapshotId = "first",
                cancelled = false,
            ) {
                releaseCopy.await()
            },
        )
        assertEquals("first", coordinator.activeSnapshotId.value)

        releaseCopy.complete(Unit)
        val outcome = withTimeout(5_000L) {
            coordinator.latestOutcome.filterNotNull().first()
        }

        assertNull(coordinator.activeSnapshotId.value)
        assertEquals(ConfigurationExportStatus.SUCCEEDED, outcome.status)
        assertEquals(outcome, coordinator.consumeOutcome(outcome.generation))
        assertNull(coordinator.latestOutcome.value)
        assertTrue(coordinator.activate("second"))
    }

    @Test
    fun failedCompletion_releasesOwnerAndReportsFailure() = runBlocking {
        val coordinator = ConfigurationExportCoordinator()
        assertTrue(coordinator.activate("snapshot"))
        assertTrue(
            coordinator.complete(
                snapshotId = "snapshot",
                cancelled = false,
            ) {
                error("provider failed")
            },
        )

        val outcome = withTimeout(5_000L) {
            coordinator.latestOutcome.filterNotNull().first()
        }

        assertNull(coordinator.activeSnapshotId.value)
        assertEquals(ConfigurationExportStatus.FAILED, outcome.status)
        assertEquals("provider failed", outcome.errorMessage)
    }
}
