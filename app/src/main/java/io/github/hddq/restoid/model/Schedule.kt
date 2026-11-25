package io.github.hddq.restoid.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class TriggerConditions(
    val requireBatteryNotLow: Boolean = false,
    val requireCharging: Boolean = false,
    val requireUnmeteredNetwork: Boolean = false
)

@Serializable
data class Schedule(
    val id: String = UUID.randomUUID().toString(),
    val presetId: String,
    val name: String? = null,
    val intervalHours: Int,
    val triggerConditions: TriggerConditions = TriggerConditions(),
    val lastRunTimestamp: Long? = null,
    val isEnabled: Boolean = true
)
