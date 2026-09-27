package com.kmmm_engineering.alwayscalendar.network

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.Executor

enum class NetworkKind { NONE, WIFI, CELLULAR }

data class NetworkStatus(
    val kind: NetworkKind,
    /** Signal bars in 0..3 (4 stages). 0 = weakest / none. */
    val level: Int = 0,
)

object ConnectivityMonitor {
    fun observe(context: Context): Flow<NetworkStatus> = callbackFlow {
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val telephony = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        fun wifiLevelFromRssi(rssi: Int): Int {
            // 4 stages: 0..3
            return WifiManager.calculateSignalLevel(rssi, 4).coerceIn(0, 3)
        }

        @Suppress("DEPRECATION")
        fun readWifiRssiLevel(): Int? {
            val wm = wifiManager ?: return null
            return runCatching {
                val rssi = wm.connectionInfo?.rssi ?: return null
                if (rssi == -127 || rssi == Int.MIN_VALUE) return null
                wifiLevelFromRssi(rssi)
            }.getOrNull()
        }

        fun capsSignalLevel(caps: NetworkCapabilities): Int? {
            val ss = caps.signalStrength
            if (ss == Int.MIN_VALUE) return null
            // dBm-ish thresholds → 0..3
            return when {
                ss >= -55 -> 3
                ss >= -66 -> 2
                ss >= -77 -> 1
                else -> 0
            }
        }

        fun cellularLevelFromSignalStrength(ss: SignalStrength?): Int? {
            if (ss == null) return null
            // SignalStrength.level is 0..4 → map to 0..3
            val raw = ss.level.coerceIn(0, 4)
            return when (raw) {
                0 -> 0
                1 -> 0
                2 -> 1
                3 -> 2
                else -> 3
            }
        }

        @SuppressLint("MissingPermission")
        fun readCellularLevel(): Int? {
            val tm = telephony ?: return null
            return runCatching {
                cellularLevelFromSignalStrength(tm.signalStrength)
            }.getOrNull()
        }

        fun read(): NetworkStatus {
            val network = cm.activeNetwork ?: return NetworkStatus(NetworkKind.NONE)
            val caps = cm.getNetworkCapabilities(network) ?: return NetworkStatus(NetworkKind.NONE)
            return when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    val level = readWifiRssiLevel()
                        ?: capsSignalLevel(caps)
                        ?: 2
                    NetworkStatus(NetworkKind.WIFI, level.coerceIn(0, 3))
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    val level = readCellularLevel()
                        ?: capsSignalLevel(caps)
                        ?: 2
                    NetworkStatus(NetworkKind.CELLULAR, level.coerceIn(0, 3))
                }
                else -> NetworkStatus(NetworkKind.NONE)
            }
        }

        fun emit() {
            trySend(read())
        }

        emit()

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = emit()
            override fun onLost(network: Network) = emit()
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) = emit()
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { cm.registerNetworkCallback(request, networkCallback) }

        val wifiReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) = emit()
        }
        val wifiFilter = IntentFilter().apply {
            addAction(WifiManager.RSSI_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                appContext.registerReceiver(wifiReceiver, wifiFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                appContext.registerReceiver(wifiReceiver, wifiFilter)
            }
        }

        var telephonyCallback: Any? = null
        @Suppress("DEPRECATION")
        var phoneStateListener: PhoneStateListener? = null

        if (Build.VERSION.SDK_INT >= 31) {
            val tm = telephony
            if (tm != null) {
                val cb = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
                    override fun onSignalStrengthsChanged(signalStrength: SignalStrength) = emit()
                }
                val executor = Executor { it.run() }
                runCatching {
                    tm.registerTelephonyCallback(executor, cb)
                    telephonyCallback = cb
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) = emit()
            }
            runCatching {
                telephony?.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
                phoneStateListener = listener
            }
        }

        awaitClose {
            runCatching { cm.unregisterNetworkCallback(networkCallback) }
            runCatching { appContext.unregisterReceiver(wifiReceiver) }
            if (Build.VERSION.SDK_INT >= 31) {
                val cb = telephonyCallback as? TelephonyCallback
                if (cb != null) runCatching { telephony?.unregisterTelephonyCallback(cb) }
            } else {
                @Suppress("DEPRECATION")
                val listener = phoneStateListener
                if (listener != null) {
                    runCatching {
                        telephony?.listen(listener, PhoneStateListener.LISTEN_NONE)
                    }
                }
            }
        }
    }
}
