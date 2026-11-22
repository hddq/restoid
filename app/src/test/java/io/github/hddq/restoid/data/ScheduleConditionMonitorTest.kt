package io.github.hddq.restoid.data

import android.net.NetworkCapabilities
import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

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

        val blockedWifi = wifiUnmetered.copy(isBlocked = true)
        assertTrue(blockedWifi.isBlocked)
        assertFalse(blockedWifi.isUnmeteredMet)
    }

    @Test
    fun evaluateNetworkCapabilities_requiresStrictNotMeteredMatchingWorkManager() {
        val temporarilyNotMeteredCapabilities = setOf(
            NetworkCapabilities.NET_CAPABILITY_INTERNET,
            NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED
        )
        val cellularTempUnmetered = evaluateNetworkCapabilities(
            hasCapability = { it in temporarilyNotMeteredCapabilities },
            hasTransport = { it == NetworkCapabilities.TRANSPORT_CELLULAR }
        )
        assertTrue(cellularTempUnmetered.isConnected)
        assertFalse(cellularTempUnmetered.isUnmetered)
        assertFalse(cellularTempUnmetered.isUnmeteredMet)
        assertEquals(NetworkTransport.CELLULAR, cellularTempUnmetered.transport)

        val strictlyNotMeteredCapabilities = setOf(
            NetworkCapabilities.NET_CAPABILITY_INTERNET,
            NetworkCapabilities.NET_CAPABILITY_NOT_METERED
        )
        val wifiUnmetered = evaluateNetworkCapabilities(
            hasCapability = { it in strictlyNotMeteredCapabilities },
            hasTransport = { it == NetworkCapabilities.TRANSPORT_WIFI }
        )
        assertTrue(wifiUnmetered.isConnected)
        assertTrue(wifiUnmetered.isUnmetered)
        assertTrue(wifiUnmetered.isUnmeteredMet)
        assertEquals(NetworkTransport.WIFI, wifiUnmetered.transport)
    }

    @Test
    fun defaultNetworkCallbackState_tracksBlockedStatusAndIgnoresStaleNetworks() {
        val state = DefaultNetworkCallbackState<String>()
        val wifiUnmetered = NetworkConditionStatus(
            isConnected = true,
            isUnmetered = true,
            transport = NetworkTransport.WIFI
        )
        val cellularMetered = NetworkConditionStatus(
            isConnected = true,
            isUnmetered = false,
            transport = NetworkTransport.CELLULAR
        )

        assertNull(state.onAvailable("wifi-1"))

        val activeWifi = state.onCapabilitiesChanged("wifi-1", wifiUnmetered)
        assertEquals(wifiUnmetered, activeWifi)
        assertTrue(activeWifi.isUnmeteredMet)

        val stillUnblocked = state.onBlockedStatusChanged("wifi-1", false)
        assertEquals(wifiUnmetered, stillUnblocked)

        val blocked = state.onBlockedStatusChanged("wifi-1", true)
        assertNotNull(blocked)
        assertTrue(blocked!!.isBlocked)
        assertFalse(blocked.isUnmeteredMet)

        val restored = state.onBlockedStatusChanged("wifi-1", false)
        assertEquals(wifiUnmetered, restored)
        assertTrue(restored!!.isUnmeteredMet)

        // Switching to a new default network clears previous network tracking
        assertNull(state.onAvailable("cell-2"))
        val activeCellular = state.onCapabilitiesChanged("cell-2", cellularMetered)
        assertEquals(cellularMetered, activeCellular)
        assertFalse(activeCellular.isBlocked)

        // Late callbacks for the old network are ignored
        assertNull(state.onBlockedStatusChanged("wifi-1", true))
        assertNull(state.onLost("wifi-1"))

        // Losing the active network transitions to disconnected
        val lost = state.onLost("cell-2")
        assertNotNull(lost)
        assertFalse(lost!!.isConnected)
        assertFalse(lost.isBlocked)
        assertFalse(lost.isUnmeteredMet)
        assertEquals(NetworkTransport.NONE, lost.transport)

        val unavailable = state.onUnavailable()
        assertFalse(unavailable.isConnected)
        assertFalse(unavailable.isBlocked)
        assertEquals(NetworkTransport.NONE, unavailable.transport)
    }

    @Test
    fun defaultNetworkCallbackState_preservesBlockedStateDeliveredBeforeCapabilities() {
        val state = DefaultNetworkCallbackState<String>()
        val wifiUnmetered = NetworkConditionStatus(
            isConnected = true,
            isUnmetered = true,
            transport = NetworkTransport.WIFI
        )

        assertNull(state.onAvailable("wifi-1"))

        val blockedBeforeCapabilities = state.onBlockedStatusChanged("wifi-1", true)
        assertNotNull(blockedBeforeCapabilities)
        assertTrue(blockedBeforeCapabilities!!.isBlocked)
        assertFalse(blockedBeforeCapabilities.isUnmeteredMet)

        val capabilitiesWhileBlocked = state.onCapabilitiesChanged("wifi-1", wifiUnmetered)
        assertTrue(capabilitiesWhileBlocked.isConnected)
        assertTrue(capabilitiesWhileBlocked.isUnmetered)
        assertTrue(capabilitiesWhileBlocked.isBlocked)
        assertFalse(capabilitiesWhileBlocked.isUnmeteredMet)

        val unblocked = state.onBlockedStatusChanged("wifi-1", false)
        assertEquals(wifiUnmetered, unblocked)
        assertTrue(unblocked!!.isUnmeteredMet)
    }

    @Test
    fun conditionSnapshotAccumulator_serializesSnapshotOrderAcrossThreads() {
        val initialBattery = BatteryConditionStatus(
            levelPercent = 50,
            isBatteryNotLow = true,
            chargingState = ChargingState.NOT_CHARGING
        )
        val initialNetwork = NetworkConditionStatus(
            isConnected = false,
            isUnmetered = false,
            transport = NetworkTransport.NONE
        )

        // Deterministic interleaving: thread 2 cannot overtake thread 1's onSnapshot emission
        val emitted = Collections.synchronizedList(mutableListOf<TriggerConditionStatusSnapshot>())
        val firstEmitStarted = CountDownLatch(1)
        val releaseFirstEmit = CountDownLatch(1)
        var firstCall = true

        val accumulator = ConditionSnapshotAccumulator(
            initialBattery = initialBattery,
            initialNetwork = initialNetwork,
            onSnapshot = { snapshot ->
                if (firstCall) {
                    firstCall = false
                    firstEmitStarted.countDown()
                    releaseFirstEmit.await(2, TimeUnit.SECONDS)
                }
                emitted.add(snapshot)
            }
        )

        val updatedBattery = initialBattery.copy(levelPercent = 80, chargingState = ChargingState.CHARGING)
        val updatedNetwork = NetworkConditionStatus(
            isConnected = true,
            isUnmetered = true,
            transport = NetworkTransport.WIFI
        )

        val batteryThread = thread(start = true) {
            accumulator.updateBattery(updatedBattery)
        }
        assertTrue(firstEmitStarted.await(2, TimeUnit.SECONDS))

        val networkThread = thread(start = true) {
            accumulator.updateNetwork(updatedNetwork)
        }
        // Wait until networkThread is blocked waiting for accumulator's lock (or timeout)
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(500)
        while (networkThread.state != Thread.State.BLOCKED && System.nanoTime() < deadlineNanos) {
            Thread.yield()
        }
        releaseFirstEmit.countDown()

        batteryThread.join(2_000)
        networkThread.join(2_000)

        assertEquals(2, emitted.size)
        assertEquals(
            TriggerConditionStatusSnapshot(battery = updatedBattery, network = initialNetwork),
            emitted[0]
        )
        assertEquals(
            TriggerConditionStatusSnapshot(battery = updatedBattery, network = updatedNetwork),
            emitted[1]
        )
    }

    @Test
    fun conditionSnapshotAccumulator_emitsMonotonicSnapshotsUnderConcurrentStress() {
        val emitted = Collections.synchronizedList(mutableListOf<TriggerConditionStatusSnapshot>())
        val accumulator = ConditionSnapshotAccumulator(
            initialBattery = BatteryConditionStatus(
                levelPercent = 0,
                isBatteryNotLow = false,
                chargingState = ChargingState.NOT_CHARGING
            ),
            initialNetwork = NetworkConditionStatus(
                isConnected = false,
                isUnmetered = false,
                transport = NetworkTransport.NONE
            ),
            onSnapshot = { emitted.add(it) }
        )

        val iterations = 200
        val startGate = CountDownLatch(1)
        val batteryThread = thread(start = true) {
            startGate.await(2, TimeUnit.SECONDS)
            for (i in 1..iterations) {
                accumulator.updateBattery(
                    BatteryConditionStatus(
                        levelPercent = i,
                        isBatteryNotLow = true,
                        chargingState = ChargingState.CHARGING
                    )
                )
            }
        }
        val networkThread = thread(start = true) {
            startGate.await(2, TimeUnit.SECONDS)
            for (i in 1..iterations) {
                accumulator.updateNetwork(
                    NetworkConditionStatus(
                        isConnected = true,
                        isUnmetered = i % 2 == 0,
                        transport = if (i == iterations) NetworkTransport.WIFI else NetworkTransport.CELLULAR
                    )
                )
            }
        }

        startGate.countDown()
        batteryThread.join(5_000)
        networkThread.join(5_000)

        assertEquals(iterations * 2, emitted.size)
        var maxBatterySeen = 0
        for (snapshot in emitted) {
            val level = snapshot.battery.levelPercent ?: 0
            assertTrue(
                "Battery level regressed from $maxBatterySeen to $level",
                level >= maxBatterySeen
            )
            maxBatterySeen = level
        }
        assertEquals(iterations, emitted.last().battery.levelPercent)
        assertEquals(NetworkTransport.WIFI, emitted.last().network.transport)
        assertTrue(emitted.last().network.isUnmeteredMet)
    }
}
