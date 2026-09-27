package com.kmmm_engineering.alwayscalendar.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class NetworkKind { NONE, WIFI, CELLULAR }

data class NetworkStatus(
    val kind: NetworkKind,
    /** Approximate bars 0..4. */
    val level: Int = 0,
)

object ConnectivityMonitor {
    fun observe(context: Context): Flow<NetworkStatus> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        fun signalLevel(caps: NetworkCapabilities): Int {
            val ss = caps.signalStrength
            return when {
                ss == Int.MIN_VALUE -> 3
                ss >= -50 -> 4
                ss >= -60 -> 3
                ss >= -70 -> 2
                ss >= -80 -> 1
                else -> 0
            }
        }

        fun read(): NetworkStatus {
            val network = cm.activeNetwork ?: return NetworkStatus(NetworkKind.NONE)
            val caps = cm.getNetworkCapabilities(network) ?: return NetworkStatus(NetworkKind.NONE)
            return when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                    NetworkStatus(NetworkKind.WIFI, signalLevel(caps))
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                    NetworkStatus(NetworkKind.CELLULAR, signalLevel(caps))
                else -> NetworkStatus(NetworkKind.NONE)
            }
        }

        trySend(read())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(read())
            }
            override fun onLost(network: Network) {
                trySend(read())
            }
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                trySend(read())
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { cm.registerNetworkCallback(request, callback) }
        awaitClose {
            runCatching { cm.unregisterNetworkCallback(callback) }
        }
    }
}
