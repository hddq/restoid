package io.github.hddq.restoid.util

import android.content.Context
import android.os.Build
import android.provider.Settings

object DeviceUtils {

    /**
     * Retrieves the device's human-readable name or model to use as default restic hostname.
     * Order of resolution:
     * 1. Settings.Global.DEVICE_NAME (user-configurable device name in Android Settings)
     * 2. Settings.Secure.bluetooth_name
     * 3. Build.MODEL (e.g. "Pixel 8", "POCO F5")
     * 4. Build.DEVICE (hardware codename)
     * 5. "android" fallback
     */
    fun getDefaultDeviceHostname(context: Context): String {
        try {
            val deviceName = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            if (!deviceName.isNullOrBlank()) {
                return sanitizeHostname(deviceName)
            }
        } catch (_: Exception) {
            // SecurityException or other restricted platform issue
        }

        try {
            @Suppress("DEPRECATION")
            val bluetoothName = Settings.Secure.getString(context.contentResolver, "bluetooth_name")
            if (!bluetoothName.isNullOrBlank()) {
                return sanitizeHostname(bluetoothName)
            }
        } catch (_: Exception) {
            // Ignored
        }

        if (Build.MODEL.isNotBlank()) {
            return sanitizeHostname(Build.MODEL)
        }

        if (Build.DEVICE.isNotBlank()) {
            return sanitizeHostname(Build.DEVICE)
        }

        return "android"
    }

    /**
     * Converts newlines and tabs to spaces, strips remaining ISO control characters (e.g. \u0000, \u001B),
     * and collapses multiple whitespace characters into single spaces.
     */
    fun sanitizeHostname(name: String): String {
        return name
            .replace("\\s+".toRegex(), " ")
            .replace("\\p{Cntrl}".toRegex(), "")
            .trim()
    }
}
