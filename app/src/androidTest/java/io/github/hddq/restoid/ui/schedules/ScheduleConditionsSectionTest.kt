package io.github.hddq.restoid.ui.schedules

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.BatteryConditionStatus
import io.github.hddq.restoid.data.ChargingPowerSource
import io.github.hddq.restoid.data.ChargingState
import io.github.hddq.restoid.data.NetworkConditionStatus
import io.github.hddq.restoid.data.NetworkTransport
import io.github.hddq.restoid.data.TriggerConditionStatusSnapshot
import io.github.hddq.restoid.model.TriggerConditions
import io.github.hddq.restoid.ui.theme.RestoidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleConditionsSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysCurrentStateAndMetStatusBelowEveryCondition() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val met = context.getString(R.string.condition_status_met)
        val notMet = context.getString(R.string.condition_status_not_met)
        val expectedBattery = context.getString(
            R.string.condition_status_format,
            context.getString(R.string.condition_battery_level, 84),
            met
        )
        val expectedCharging = context.getString(
            R.string.condition_status_format,
            context.getString(R.string.condition_state_not_charging),
            notMet
        )
        val expectedNetwork = context.getString(
            R.string.condition_status_format,
            context.getString(R.string.condition_state_network_wifi_unmetered),
            met
        )

        composeTestRule.setContent {
            RestoidTheme(dynamicColor = false) {
                ScheduleConditionsSection(
                    triggerConditions = TriggerConditions(
                        requireBatteryNotLow = true,
                        requireCharging = true,
                        requireUnmeteredNetwork = false
                    ),
                    conditionStatus = TriggerConditionStatusSnapshot(
                        battery = BatteryConditionStatus(
                            levelPercent = 84,
                            isBatteryNotLow = true,
                            chargingState = ChargingState.NOT_CHARGING,
                            powerSource = ChargingPowerSource.UNKNOWN
                        ),
                        network = NetworkConditionStatus(
                            isConnected = true,
                            isUnmetered = true,
                            transport = NetworkTransport.WIFI
                        )
                    ),
                    onRequireBatteryNotLowChange = {},
                    onRequireChargingChange = {},
                    onRequireUnmeteredNetworkChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText(expectedBattery).assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedCharging).assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedNetwork).assertIsDisplayed()
    }

    @Test
    fun displaysLowBatteryChargingUsbAndMeteredMobileStates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val met = context.getString(R.string.condition_status_met)
        val notMet = context.getString(R.string.condition_status_not_met)
        val expectedBattery = context.getString(
            R.string.condition_status_format,
            context.getString(R.string.condition_battery_level_low, 12),
            notMet
        )
        val expectedCharging = context.getString(
            R.string.condition_status_format,
            context.getString(R.string.condition_state_charging_usb),
            met
        )
        val expectedNetwork = context.getString(
            R.string.condition_status_format,
            context.getString(R.string.condition_state_network_cellular_metered),
            notMet
        )

        composeTestRule.setContent {
            RestoidTheme(dynamicColor = false) {
                ScheduleConditionsSection(
                    triggerConditions = TriggerConditions(),
                    conditionStatus = TriggerConditionStatusSnapshot(
                        battery = BatteryConditionStatus(
                            levelPercent = 12,
                            isBatteryNotLow = false,
                            chargingState = ChargingState.CHARGING,
                            powerSource = ChargingPowerSource.USB
                        ),
                        network = NetworkConditionStatus(
                            isConnected = true,
                            isUnmetered = false,
                            transport = NetworkTransport.CELLULAR
                        )
                    ),
                    onRequireBatteryNotLowChange = {},
                    onRequireChargingChange = {},
                    onRequireUnmeteredNetworkChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText(expectedBattery).assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedCharging).assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedNetwork).assertIsDisplayed()
    }

    @Test
    fun displaysBlockedNetworkAsNotMet() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val notMet = context.getString(R.string.condition_status_not_met)
        val expectedNetwork = context.getString(
            R.string.condition_status_format,
            context.getString(R.string.condition_state_network_blocked),
            notMet
        )

        composeTestRule.setContent {
            RestoidTheme(dynamicColor = false) {
                ScheduleConditionsSection(
                    triggerConditions = TriggerConditions(requireUnmeteredNetwork = true),
                    conditionStatus = TriggerConditionStatusSnapshot(
                        battery = BatteryConditionStatus(
                            levelPercent = 90,
                            isBatteryNotLow = true,
                            chargingState = ChargingState.CHARGING,
                            powerSource = ChargingPowerSource.AC
                        ),
                        network = NetworkConditionStatus(
                            isConnected = true,
                            isUnmetered = true,
                            transport = NetworkTransport.WIFI,
                            isBlocked = true
                        )
                    ),
                    onRequireBatteryNotLowChange = {},
                    onRequireChargingChange = {},
                    onRequireUnmeteredNetworkChange = {}
                )
            }
        }

        composeTestRule.onNodeWithText(expectedNetwork).assertIsDisplayed()
    }
}
