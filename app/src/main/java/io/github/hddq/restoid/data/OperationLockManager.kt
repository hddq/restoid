package io.github.hddq.restoid.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.PowerManager

class OperationLockManager(context: Context) {
    private val appContext = context.applicationContext
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    fun acquire(backendType: RepositoryBackendType?) {
        release()

        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$LOCK_TAG_PREFIX:wake").apply {
            setReferenceCounted(false)
            acquire(MAX_WAKE_LOCK_DURATION_MS)
        }

        if (backendType != null && isRemoteBackend(backendType) && isOnWifiTransport()) {
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "$LOCK_TAG_PREFIX:wifi").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    fun release() {
        wifiLock?.let {
            if (it.isHeld) it.release()
        }
        wifiLock = null

        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun isRemoteBackend(backendType: RepositoryBackendType): Boolean {
        return backendType == RepositoryBackendType.SFTP ||
            backendType == RepositoryBackendType.REST ||
            backendType == RepositoryBackendType.S3
    }

    private fun isOnWifiTransport(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private companion object {
        const val LOCK_TAG_PREFIX = "restoid:operation"
        const val MAX_WAKE_LOCK_DURATION_MS = 6 * 60 * 60 * 1000L
    }
}
