package io.github.hddq.restoid.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

enum class ChargingPowerSource {
    AC,
    USB,
    WIRELESS,
    DOCK,
    UNKNOWN
}

enum class ChargingState {
    CHARGING,
    FULL,
    PLUGGED_NOT_CHARGING,
    NOT_CHARGING,
    UNKNOWN
}

data class BatteryConditionStatus(
    val levelPercent: Int?,
    val isBatteryNotLow: Boolean?,
    val chargingState: ChargingState,
    val powerSource: ChargingPowerSource = ChargingPowerSource.UNKNOWN
) {
    val isChargingMet: Boolean?
        get() = when (chargingState) {
            ChargingState.CHARGING,
            ChargingState.FULL -> true
            ChargingState.PLUGGED_NOT_CHARGING,
            ChargingState.NOT_CHARGING -> false
            ChargingState.UNKNOWN -> null
        }
}

enum class NetworkTransport {
    WIFI,
    ETHERNET,
    CELLULAR,
    VPN,
    OTHER,
    NONE
}

data class NetworkConditionStatus(
    val isConnected: Boolean,
    val isUnmetered: Boolean,
    val transport: NetworkTransport
) {
    val isUnmeteredMet: Boolean
        get() = isConnected && isUnmetered
}

data class TriggerConditionStatusSnapshot(
    val battery: BatteryConditionStatus = BatteryConditionStatus(
        levelPercent = null,
        isBatteryNotLow = null,
        chargingState = ChargingState.UNKNOWN
    ),
    val network: NetworkConditionStatus = NetworkConditionStatus(
        isConnected = false,
        isUnmetered = false,
        transport = NetworkTransport.NONE
    )
)

internal const val WORKMANAGER_BATTERY_LOW_THRESHOLD = 0.15f

internal fun evaluateBatteryStatus(
    status: Int,
    level: Int,
    scale: Int,
    plugged: Int,
    hasBatteryLowExtra: Boolean,
    batteryLowExtra: Boolean,
    isDockPlugged: Boolean = false
): BatteryConditionStatus {
    val levelPercent = if (level >= 0 && scale > 0) {
        ((level * 100f) / scale).roundToInt().coerceIn(0, 100)
    } else {
        null
    }

    val isBatteryNotLow = when {
        status == BatteryManager.BATTERY_STATUS_UNKNOWN -> true
        hasBatteryLowExtra -> !batteryLowExtra
        level >= 0 && scale > 0 -> (level.toFloat() / scale.toFloat()) > WORKMANAGER_BATTERY_LOW_THRESHOLD
        else -> null
    }

    val powerSource = when {
        (plugged and BatteryManager.BATTERY_PLUGGED_AC) != 0 -> ChargingPowerSource.AC
        (plugged and BatteryManager.BATTERY_PLUGGED_USB) != 0 -> ChargingPowerSource.USB
        (plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0 -> ChargingPowerSource.WIRELESS
        isDockPlugged -> ChargingPowerSource.DOCK
        else -> ChargingPowerSource.UNKNOWN
    }

    val chargingState = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> ChargingState.CHARGING
        BatteryManager.BATTERY_STATUS_FULL -> ChargingState.FULL
        BatteryManager.BATTERY_STATUS_NOT_CHARGING,
        BatteryManager.BATTERY_STATUS_DISCHARGING -> {
            if (plugged != 0) ChargingState.PLUGGED_NOT_CHARGING else ChargingState.NOT_CHARGING
        }
        else -> ChargingState.UNKNOWN
    }

    return BatteryConditionStatus(
        levelPercent = levelPercent,
        isBatteryNotLow = isBatteryNotLow,
        chargingState = chargingState,
        powerSource = powerSource
    )
}

internal fun evaluateNetworkStatus(
    hasActiveNetwork: Boolean,
    hasInternet: Boolean,
    isNotMetered: Boolean,
    hasWifi: Boolean,
    hasEthernet: Boolean,
    hasCellular: Boolean,
    hasVpn: Boolean
): NetworkConditionStatus {
    if (!hasActiveNetwork || !hasInternet) {
        return NetworkConditionStatus(
            isConnected = false,
            isUnmetered = false,
            transport = NetworkTransport.NONE
        )
    }

    val transport = when {
        hasWifi -> NetworkTransport.WIFI
        hasEthernet -> NetworkTransport.ETHERNET
        hasCellular -> NetworkTransport.CELLULAR
        hasVpn -> NetworkTransport.VPN
        else -> NetworkTransport.OTHER
    }

    return NetworkConditionStatus(
        isConnected = true,
        isUnmetered = isNotMetered,
        transport = transport
    )
}

class ScheduleConditionMonitor(context: Context) {

    private val appContext = context.applicationContext

    fun readCurrentStatus(): TriggerConditionStatusSnapshot {
        val connectivityManager = runCatching {
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        }.getOrNull()
        return TriggerConditionStatusSnapshot(
            battery = readBatteryStatus(appContext),
            network = readNetworkStatus(connectivityManager)
        )
    }

    fun observeStatus(): Flow<TriggerConditionStatusSnapshot> = callbackFlow {
        val connectivityManager = runCatching {
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        }.getOrNull()

        val lock = Any()
        var latestBattery = readBatteryStatus(appContext)
        var latestNetwork = readNetworkStatus(connectivityManager)

        trySend(TriggerConditionStatusSnapshot(battery = latestBattery, network = latestNetwork))

        fun updateBattery(updated: BatteryConditionStatus) {
            val snapshot = synchronized(lock) {
                latestBattery = updated
                TriggerConditionStatusSnapshot(battery = latestBattery, network = latestNetwork)
            }
            trySend(snapshot)
        }

        fun updateNetwork(updated: NetworkConditionStatus) {
            val snapshot = synchronized(lock) {
                latestNetwork = updated
                TriggerConditionStatusSnapshot(battery = latestBattery, network = latestNetwork)
            }
            trySend(snapshot)
        }

        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                val updated = when (action) {
                    Intent.ACTION_BATTERY_CHANGED -> parseBatteryIntent(intent)
                    Intent.ACTION_BATTERY_LOW -> readBatteryStatus(appContext).copy(isBatteryNotLow = false)
                    Intent.ACTION_BATTERY_OKAY -> readBatteryStatus(appContext).copy(isBatteryNotLow = true)
                    Intent.ACTION_POWER_CONNECTED,
                    Intent.ACTION_POWER_DISCONNECTED -> readBatteryStatus(appContext)
                    else -> return
                }
                updateBattery(updated)
            }
        }

        val batteryFilter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
        }

        val receiverRegistered = runCatching {
            ContextCompat.registerReceiver(
                appContext,
                batteryReceiver,
                batteryFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            true
        }.getOrDefault(false)

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateNetwork(readNetworkStatus(connectivityManager))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val active = runCatching { connectivityManager?.activeNetwork }.getOrNull()
                val updated = if (active == null || active == network) {
                    evaluateCapabilities(networkCapabilities)
                } else {
                    readNetworkStatus(connectivityManager)
                }
                updateNetwork(updated)
            }

            override fun onLost(network: Network) {
                val active = runCatching { connectivityManager?.activeNetwork }.getOrNull()
                val updated = if (active == null || active == network) {
                    NetworkConditionStatus(
                        isConnected = false,
                        isUnmetered = false,
                        transport = NetworkTransport.NONE
                    )
                } else {
                    readNetworkStatus(connectivityManager)
                }
                updateNetwork(updated)
            }

            override fun onUnavailable() {
                updateNetwork(
                    NetworkConditionStatus(
                        isConnected = false,
                        isUnmetered = false,
                        transport = NetworkTransport.NONE
                    )
                )
            }

            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                updateNetwork(readNetworkStatus(connectivityManager))
            }
        }

        val networkCallbackRegistered = if (connectivityManager != null) {
            runCatching {
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
                true
            }.getOrDefault(false)
        } else {
            false
        }

        awaitClose {
            if (receiverRegistered) {
                runCatching { appContext.unregisterReceiver(batteryReceiver) }
            }
            if (networkCallbackRegistered && connectivityManager != null) {
                runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
            }
        }
    }.distinctUntilChanged()

    companion object {
        private fun readBatteryStatus(context: Context): BatteryConditionStatus {
            val batteryIntent = runCatching {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }.getOrNull() ?: return BatteryConditionStatus(
                levelPercent = null,
                isBatteryNotLow = null,
                chargingState = ChargingState.UNKNOWN
            )
            return parseBatteryIntent(batteryIntent)
        }

        private fun parseBatteryIntent(intent: Intent): BatteryConditionStatus {
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val hasBatteryLowExtra = intent.hasExtra(BatteryManager.EXTRA_BATTERY_LOW)
            val batteryLowExtra = intent.getBooleanExtra(BatteryManager.EXTRA_BATTERY_LOW, false)
            val isDockPlugged = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                (plugged and BatteryManager.BATTERY_PLUGGED_DOCK) != 0

            return evaluateBatteryStatus(
                status = status,
                level = level,
                scale = scale,
                plugged = plugged,
                hasBatteryLowExtra = hasBatteryLowExtra,
                batteryLowExtra = batteryLowExtra,
                isDockPlugged = isDockPlugged
            )
        }

        private fun readNetworkStatus(connectivityManager: ConnectivityManager?): NetworkConditionStatus {
            if (connectivityManager == null) {
                return NetworkConditionStatus(
                    isConnected = false,
                    isUnmetered = false,
                    transport = NetworkTransport.NONE
                )
            }
            val activeNetwork = runCatching { connectivityManager.activeNetwork }.getOrNull()
                ?: return NetworkConditionStatus(
                    isConnected = false,
                    isUnmetered = false,
                    transport = NetworkTransport.NONE
                )
            val capabilities = runCatching {
                connectivityManager.getNetworkCapabilities(activeNetwork)
            }.getOrNull()
                ?: return NetworkConditionStatus(
                    isConnected = false,
                    isUnmetered = false,
                    transport = NetworkTransport.NONE
                )
            return evaluateCapabilities(capabilities)
        }

        private fun evaluateCapabilities(capabilities: NetworkCapabilities): NetworkConditionStatus {
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isNotMetered = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED))
            return evaluateNetworkStatus(
                hasActiveNetwork = true,
                hasInternet = hasInternet,
                isNotMetered = isNotMetered,
                hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                hasEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
                hasCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
                hasVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            )
        }
    }
}
