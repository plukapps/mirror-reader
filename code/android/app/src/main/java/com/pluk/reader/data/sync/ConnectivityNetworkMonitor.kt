package com.pluk.reader.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.pluk.reader.domain.sync.NetworkMonitor
import com.pluk.reader.domain.sync.NetworkType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PRF-023: la red por defecto de este dispositivo, con `ConnectivityManager.registerDefaultNetworkCallback`.
 * Wi-Fi o cable (`TRANSPORT_WIFI`, `TRANSPORT_ETHERNET`) cuentan como [NetworkType.WIFI]. Escucha mientras viva la
 * app: la callback no hace trabajo, solo actualiza el valor.
 */
@Singleton
class ConnectivityNetworkMonitor @Inject constructor(
    @ApplicationContext context: Context,
) : NetworkMonitor {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val _network = MutableStateFlow(connectivity.currentType())
    override val network: StateFlow<NetworkType> = _network.asStateFlow()

    init {
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                _network.value = capabilities.toType()
            }

            override fun onLost(network: Network) {
                _network.value = NetworkType.NONE
            }
        })
    }

    private fun ConnectivityManager.currentType(): NetworkType =
        getNetworkCapabilities(activeNetwork)?.toType() ?: NetworkType.NONE

    private fun NetworkCapabilities.toType(): NetworkType = when {
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.WIFI
        else -> NetworkType.OTHER
    }
}
