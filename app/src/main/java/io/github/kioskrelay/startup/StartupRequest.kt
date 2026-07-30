package io.github.kioskrelay.startup

import android.content.Context
import io.github.kioskrelay.KioskRelayApplication
import io.github.kioskrelay.data.ConfigRepository

data class StartupRequest(
    val enabled: Boolean,
    val delaySeconds: Int,
) {
    init {
        require(delaySeconds in 0..MAX_BOOT_DELAY_SECONDS) {
            "Boot delay must be between 0 and $MAX_BOOT_DELAY_SECONDS seconds"
        }
    }

    companion object {
        const val MAX_BOOT_DELAY_SECONDS = 60
        val Disabled = StartupRequest(enabled = false, delaySeconds = 0)
    }
}

fun interface StartupRequestProvider {
    suspend fun load(): StartupRequest
}

class ConfigRepositoryStartupRequestProvider(
    private val repository: ConfigRepository,
) : StartupRequestProvider {
    override suspend fun load(): StartupRequest {
        val config = repository.get()
        return StartupRequest(
            enabled = config.onboardingCompleted && config.runtime.bootStartEnabled,
            delaySeconds = config.runtime.bootDelaySeconds.coerceIn(
                minimumValue = 0,
                maximumValue = StartupRequest.MAX_BOOT_DELAY_SECONDS,
            ),
        )
    }
}

/**
 * Optional integration hook. The default provider resolves [KioskRelayApplication.container], so
 * the standard application needs no manual registration. Tests or alternate applications can
 * replace either dependency during Application.onCreate.
 */
object StartupRuntime {
    @Volatile
    private var providerFactory: ((Context) -> StartupRequestProvider)? = null

    @Volatile
    private var fallbackNotifier: StartupFallbackNotifier = SystemStartupFallbackNotifier

    fun configure(
        providerFactory: ((Context) -> StartupRequestProvider)? = null,
        fallbackNotifier: StartupFallbackNotifier = SystemStartupFallbackNotifier,
    ) {
        this.providerFactory = providerFactory
        this.fallbackNotifier = fallbackNotifier
    }

    internal fun provider(context: Context): StartupRequestProvider {
        providerFactory?.let { return it(context.applicationContext) }
        val application = context.applicationContext as? KioskRelayApplication
            ?: return StartupRequestProvider { StartupRequest.Disabled }
        return ConfigRepositoryStartupRequestProvider(application.container.configRepository)
    }

    internal fun notifier(): StartupFallbackNotifier = fallbackNotifier
}
