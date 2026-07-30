package io.github.kioskrelay.data

import androidx.datastore.core.DataStore
import io.github.kioskrelay.config.AppLocale
import io.github.kioskrelay.config.ConfigDefaults
import io.github.kioskrelay.proto.KioskRelayConfigProto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConfigRepositoryTest {
    @Test
    fun updateCanonicalizesAndPersistsConfig() = runBlocking {
        val store = FakeDataStore(ConfigProtoMapper.defaultProto)
        val repository = ProtoConfigRepository(store)

        val updated = repository.update { current ->
            current.copy(
                locale = AppLocale.ZH_CN,
                onboardingCompleted = true,
                webView = current.webView.copy(
                    initialUrl = "https://EXAMPLE.com:443/dashboard",
                ),
            )
        }

        assertEquals(AppLocale.ZH_CN, updated.locale)
        assertEquals("https://example.com/dashboard", updated.webView.initialUrl)
        assertEquals(setOf("https://example.com"), updated.webView.allowedOrigins)
        assertEquals(updated, repository.get())
    }

    @Test
    fun resetRestoresSafeOnboardingDefaults() = runBlocking {
        val store = FakeDataStore(ConfigProtoMapper.defaultProto)
        val repository = ProtoConfigRepository(store)
        repository.update { current ->
            current.copy(
                onboardingCompleted = true,
                webView = current.webView.copy(
                    initialUrl = "https://display.example.com/",
                ),
            )
        }

        val reset = repository.reset()

        assertEquals(ConfigDefaults.config, reset)
        assertFalse(reset.onboardingCompleted)
    }

    private class FakeDataStore<T>(
        initialValue: T,
    ) : DataStore<T> {
        private val state = MutableStateFlow(initialValue)

        override val data: Flow<T> = state

        override suspend fun updateData(transform: suspend (t: T) -> T): T {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
