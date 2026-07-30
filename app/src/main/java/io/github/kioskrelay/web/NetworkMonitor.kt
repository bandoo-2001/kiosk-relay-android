package io.github.kioskrelay.web

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable

/**
 * Tracks whether Android currently has a default network.
 *
 * It intentionally does not require NET_CAPABILITY_VALIDATED: kiosk deployments frequently use
 * isolated LANs with no internet validation endpoint. The WebView load result remains the source
 * of truth for whether the configured page is usable.
 */
class NetworkMonitor(context: Context) : Closeable {
    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val _isNetworkAvailable = MutableStateFlow(hasDefaultNetwork())
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    private var started = false
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isNetworkAvailable.value = true
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            _isNetworkAvailable.value = true
        }

        override fun onLost(network: Network) {
            _isNetworkAvailable.value = hasDefaultNetwork()
        }

        override fun onUnavailable() {
            _isNetworkAvailable.value = hasDefaultNetwork()
        }
    }

    @Synchronized
    fun start() {
        if (started) return
        _isNetworkAvailable.value = hasDefaultNetwork()
        try {
            connectivityManager.registerDefaultNetworkCallback(callback)
            started = true
        } catch (_: SecurityException) {
            // The manifest should grant ACCESS_NETWORK_STATE. If an OEM denies it, WebView errors
            // still drive the retry state machine instead of crashing the kiosk.
        } catch (_: RuntimeException) {
            // Some vendor builds throw when network callbacks are unavailable during early boot.
        }
    }

    @Synchronized
    override fun close() {
        if (!started) return
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (_: IllegalArgumentException) {
            // Already unregistered by the platform.
        } finally {
            started = false
        }
    }

    private fun hasDefaultNetwork(): Boolean =
        try {
            connectivityManager.activeNetwork != null
        } catch (_: SecurityException) {
            true
        }
}
