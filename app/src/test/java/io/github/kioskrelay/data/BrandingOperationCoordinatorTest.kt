package io.github.kioskrelay.data

import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class BrandingOperationCoordinatorTest {
    @Test
    fun operationsFromRecreatedScreens_areSerialized() = runBlocking {
        val coordinator = BrandingOperationCoordinator()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val events = Collections.synchronizedList(mutableListOf<String>())

        val first = async {
            coordinator.runExclusive {
                events += "first-start"
                firstEntered.complete(Unit)
                releaseFirst.await()
                events += "first-end"
            }
        }
        firstEntered.await()

        val second = async {
            coordinator.runExclusive {
                events += "second"
            }
        }
        yield()
        assertEquals(listOf("first-start"), events.toList())

        releaseFirst.complete(Unit)
        first.await()
        second.await()

        assertEquals(
            listOf("first-start", "first-end", "second"),
            events.toList(),
        )
    }
}
