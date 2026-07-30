package io.github.kioskrelay.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import io.github.kioskrelay.config.ConfigDefaults
import io.github.kioskrelay.config.ConfigRules
import io.github.kioskrelay.config.KioskRelayConfig
import io.github.kioskrelay.data.ConfigProtoMapper.toDomain
import io.github.kioskrelay.data.ConfigProtoMapper.toProto
import io.github.kioskrelay.proto.KioskRelayConfigProto
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface ConfigRepository {
    val config: Flow<KioskRelayConfig>

    suspend fun get(): KioskRelayConfig

    suspend fun update(
        transform: (KioskRelayConfig) -> KioskRelayConfig,
    ): KioskRelayConfig

    suspend fun replace(config: KioskRelayConfig): KioskRelayConfig

    suspend fun reset(): KioskRelayConfig
}

class ProtoConfigRepository(
    private val dataStore: DataStore<KioskRelayConfigProto>,
) : ConfigRepository {
    override val config: Flow<KioskRelayConfig> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(ConfigProtoMapper.defaultProto)
            } else {
                throw exception
            }
        }
        .map { proto ->
            runCatching { proto.toDomain() }.getOrDefault(ConfigDefaults.config)
        }

    override suspend fun get(): KioskRelayConfig = config.first()

    override suspend fun update(
        transform: (KioskRelayConfig) -> KioskRelayConfig,
    ): KioskRelayConfig {
        return dataStore.updateData { current ->
            val currentDomain = runCatching { current.toDomain() }
                .getOrDefault(ConfigDefaults.config)
            transform(currentDomain).validated().toProto()
        }.toDomain()
    }

    override suspend fun replace(config: KioskRelayConfig): KioskRelayConfig {
        val validated = config.validated()
        return dataStore.updateData { validated.toProto() }.toDomain()
    }

    override suspend fun reset(): KioskRelayConfig {
        val defaults = ConfigDefaults.config
        return dataStore.updateData { defaults.toProto() }.toDomain()
    }

    private fun KioskRelayConfig.validated(): KioskRelayConfig =
        ConfigRules.validate(ConfigRules.canonicalize(this))

    companion object {
        private const val FILE_NAME = "kiosk_relay_config.pb"

        fun create(context: Context): ProtoConfigRepository {
            val appContext = context.applicationContext
            val store = DataStoreFactory.create(
                serializer = KioskRelayConfigSerializer,
                corruptionHandler = ReplaceFileCorruptionHandler {
                    ConfigProtoMapper.defaultProto
                },
                produceFile = { appContext.dataStoreFile(FILE_NAME) },
            )
            return ProtoConfigRepository(store)
        }
    }
}
