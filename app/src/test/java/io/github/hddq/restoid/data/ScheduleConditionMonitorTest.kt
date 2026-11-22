package io.github.hddq.restoid.data

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleConditionMonitorTest {

    @Test
    fun evaluateBatteryStatus_normalDischargingLevelIsNotLowAndNotCharging() {
        val status = evaluateBatteryStatus(
            status = BatteryManager.BATTERY_STATUS_DISCHARGING,
            level = 82,
            scale = 100,
            plugged = 0,
            hasBatteryLowExtra = true,
            batteryLowExtra = false
        )

        assertEquals(82, status.levelPercent)
        assertEquals(true, status.isBatteryNotLow)
        assertEquals(ChargingState.NOT_CHARGING, status.chargingState)
        assertEquals(false, status.isChargingMet)
        assertEquals(ChargingPowerSource.UNKNOWN, status.powerSource)
    }

    @Test
    fun evaluateBatteryStatus_usesBatteryLowExtraWhenPresent() {
        val lowStatus = evaluateBatteryStatus(
            status = BatteryManager.BATTERY_STATUS_DISCHARGING,
            level = 18,
            scale = 100,
            plugged = 0,
            hasBatteryLowExtra = true,
            batteryLowExtra = true
        )

        assertEquals(18, lowStatus.levelPercent)
        assertEquals(false, lowStatus.isBatteryNotLow)

        val pluggedLowLevelOkay = evaluateBatteryStatus(
            status = BatteryManager.BATTERY_STATUS_CHARGING,
            level = 10,
            scale = 100,
            plugged = BatteryManager.BATTERY_PLUGGED_AC,
            hasBatteryLowExtra = true,
            batteryLowExtra = false
        )

        assertEquals(10, pluggedLowLevelOkay.levelPercent)
        assertEquals(true, pluggedLowLevelOkay.isBatteryNotLow)
        assertEquals(ChargingState.CHARGING, pluggedLowLevelOkay.chargingState)
        assertEquals(true, pluggedLowLevelOkay.isChargingMet)
        assertEquals(ChargingPowerSource.AC, pluggedLowLevelOkay.powerSource)
    }

    @Test
    fun evaluateBatteryStatus_fallsBackToWorkManager15PercentThresholdWithoutExtra() {
        val at15 = evaluateBatteryStatus(
            status = BatteryManager.BATTERY_STATUS_DISCHARGING,
            level = 15,
            scale = 100,
            plugged = 0,
            hasBatteryLowExtra = false,
            batteryLowExtra = false
        )
        assertEquals(false, at15.isBatteryNotLow)

        val at16 = evaluateBatteryStatus(
            status = BatteryManager.BATTERY_STATUS_DISCHARGING,
            level = 16,
            scale = 100,
            plugged = 0,
            hasBatteryLowExtra = false,
            batteryLowExtra = false
        )
        assertEquals(true, at16.isBatteryNotLow)
    }

    @Test
    fun evaluateBatteryStatus_detectsChargingPowerSourcesAndFullAndPluggedNotCharging() {
        val usb = evaluateBatteryStatus(
            status = BatteryManager.BATTERY_STATUS_CHARGING,
            level = 55,
            scale = 100,
            plugged = BatteryManager.BATTERY_PLUGGED_USB,
            hasBatteryLowExtra = true,
            batteryLowExtra = false
        )
        assertEquals(ChargingState.CHARGING, usb.chargingState)
        assertEquals(ChargingPowerSource.USB, usb.powerSource)
        assertEquals(true, usb.isChargingMet)

        val wireless = evaluateBatteryStatus(
            status = BatteryManager.BATTERY_STATUS_CHARGING,
            level = 70,
            scale = 100,
            plugged = BatteryManager.BATTERY_PLUGGED_WIRELESS,
            hasBatteryLowExtra = true,
            batteryLowExtra = false
        )
        assertEquals(ChargingPowerSource.WIRELESS, wireless.powerSource)
        assertEquals(true, wireless.isChargingMet)

        val dock = evaluateBatteryStatus(
            status = BatteryManager.BATTERY_STATUS_CHARGING,
            level = 70,
            scale = 100,
            plugged = 0,
            hasBatteryLowExtra = true,
            batteryLowExtra = false,
            isDockPlugged = true
        )
        assertEquals(ChargingPowerSource.DOCK, dock.powerSource)
        assertEquals(true, dock.isChargingMet)

        val full = evaluateBatteryStatus(
            status = BatteryManager.BATTERY_STATUS_FULL,
            level = 100,
            scale = 100,
            plugged = BatteryManager.BATTERY_PLUGGED_AC,
            hasBatteryLowExtra = true,
            batteryLowExtra = false
        )
        assertEquals(ChargingState.FULL, full.chargingState)
        assertEquals(true, full.isChargingMet)

        val pluggedNotCharging = evaluateBatteryStatus(
            status = BatteryManager.BATTERY_STATUS_NOT_CHARGING,
            level = 80,
            scale = 100,
            plugged = BatteryManager.BATTERY_PLUGGED_AC,
            hasBatteryLowExtra = true,
            batteryLowExtra = false
        )
        assertEquals(ChargingState.PLUGGED_NOT_CHARGING, pluggedNotCharging.chargingState)
        assertEquals(false, pluggedNotCharging.isChargingMet)
    }

    @Test
    fun evaluateBatteryStatus_unknownStatusMatchesWorkManagerNotLowAndUnknownCharging() {
        val unknown = evaluateBatteryStatus(
            status = BatteryManager.BATTERY_STATUS_UNKNOWN,
            level = -1,
            scale = -1,
            plugged = 0,
            hasBatteryLowExtra = false,
            batteryLowExtra = false
        )

        assertNull(unknown.levelPercent)
        assertEquals(true, unknown.isBatteryNotLow)
        assertEquals(ChargingState.UNKNOWN, unknown.chargingState)
        assertNull(unknown.isChargingMet)
    }

    @Test
    fun evaluateNetworkStatus_handlesWifiMeteredCellularEthernetVpnAndOffline() {
        val wifiUnmetered = evaluateNetworkStatus(
            hasActiveNetwork = true,
            hasInternet = true,
            isNotMetered = true,
            hasWifi = true,
            hasEthernet = false,
            hasCellular = false,
            hasVpn = false
        )
        assertTrue(wifiUnmetered.isConnected)
        assertTrue(wifiUnmetered.isUnmetered)
        assertTrue(wifiUnmetered.isUnmeteredMet)
        assertEquals(NetworkTransport.WIFI, wifiUnmetered.transport)

        val wifiMetered = evaluateNetworkStatus(
            hasActiveNetwork = true,
            hasInternet = true,
            isNotMetered = false,
            hasWifi = true,
            hasEthernet = false,
            hasCellular = false,
            hasVpn = false
        )
        assertTrue(wifiMetered.isConnected)
        assertFalse(wifiMetered.isUnmetered)
        assertFalse(wifiMetered.isUnmeteredMet)
        assertEquals(NetworkTransport.WIFI, wifiMetered.transport)

        val cellularMetered = evaluateNetworkStatus(
            hasActiveNetwork = true,
            hasInternet = true,
            isNotMetered = false,
            hasWifi = false,
            hasEthernet = false,
            hasCellular = true,
            hasVpn = false
        )
        assertEquals(NetworkTransport.CELLULAR, cellularMetered.transport)
        assertFalse(cellularMetered.isUnmeteredMet)

        val ethernetUnmetered = evaluateNetworkStatus(
            hasActiveNetwork = true,
            hasInternet = true,
            isNotMetered = true,
            hasWifi = false,
            hasEthernet = true,
            hasCellular = false,
            hasVpn = false
        )
        assertEquals(NetworkTransport.ETHERNET, ethernetUnmetered.transport)
        assertTrue(ethernetUnmetered.isUnmeteredMet)

        val vpnUnmetered = evaluateNetworkStatus(
            hasActiveNetwork = true,
            hasInternet = true,
            isNotMetered = true,
            hasWifi = false,
            hasEthernet = false,
            hasCellular = false,
            hasVpn = true
        )
        assertEquals(NetworkTransport.VPN, vpnUnmetered.transport)
        assertTrue(vpnUnmetered.isUnmeteredMet)

        val offline = evaluateNetworkStatus(
            hasActiveNetwork = false,
            hasInternet = false,
            isNotMetered = true,
            hasWifi = true,
            hasEthernet = false,
            hasCellular = false,
            hasVpn = false
        )
        assertFalse(offline.isConnected)
        assertFalse(offline.isUnmeteredMet)
        assertEquals(NetworkTransport.NONE, offline.transport)

        val noInternet = evaluateNetworkStatus(
            hasActiveNetwork = true,
            hasInternet = false,
            isNotMetered = true,
            hasWifi = true,
            hasEthernet = false,
            hasCellular = false,
            hasVpn = false
        )
        assertFalse(noInternet.isConnected)
        assertFalse(noInternet.isUnmeteredMet)
        assertEquals(NetworkTransport.NONE, noInternet.transport)
    }
}
