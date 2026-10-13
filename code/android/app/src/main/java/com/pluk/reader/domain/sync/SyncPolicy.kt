package com.pluk.reader.domain.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

/** Red por la que sale este dispositivo ahora (PRF-023). */
enum class NetworkType {
    NONE,

    /** Wi-Fi o cable: la red en la que se puede sincronizar con "Solo con Wi-Fi". */
    WIFI,

    /** Datos móviles u otra. */
    OTHER,
}

/** PRF-023: con "Sincronizar solo con Wi-Fi" encendido, solo se sincroniza por Wi-Fi o cable. */
fun canSync(wifiOnly: Boolean, network: NetworkType): Boolean = !wifiOnly || network == NetworkType.WIFI

/** La red actual, que cambia mientras la app corre. */
interface NetworkMonitor {
    val network: StateFlow<NetworkType>
}

/** Preferencia "Sincronizar solo con Wi-Fi" de este dispositivo (PRF-023). Apagada por defecto. */
interface WifiOnlyPreference {
    val wifiOnly: Flow<Boolean>

    suspend fun setWifiOnly(enabled: Boolean)
}

/** Dice si ahora se puede hablar con la nube (PRF-023). Todo lo que sincroniza la consulta antes. */
interface SyncGate {
    suspend fun isOpen(): Boolean

    /** Cada cambio entre abierta y cerrada, empezando por el valor actual. */
    val open: Flow<Boolean>

    companion object {
        /** Siempre abierta: para quien no necesita la preferencia (tests). */
        val ALWAYS_OPEN: SyncGate = object : SyncGate {
            override suspend fun isOpen() = true
            override val open: Flow<Boolean> = flowOf(true)
        }
    }
}

/** [SyncGate] con la preferencia y la red de este dispositivo. */
@Singleton
class NetworkSyncGate @Inject constructor(
    private val preference: WifiOnlyPreference,
    private val monitor: NetworkMonitor,
) : SyncGate {
    override suspend fun isOpen(): Boolean = canSync(preference.wifiOnly.first(), monitor.network.value)

    override val open: Flow<Boolean> =
        combine(preference.wifiOnly, monitor.network, ::canSync).distinctUntilChanged()
}

/** La pasada no corrió porque falta Wi-Fi (PRF-023). Es una falta de conexión: lo pendiente se guarda. */
class WaitingForWifiException : Exception("Esperando Wi-Fi")
