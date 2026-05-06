package io.github.hddq.restoid.model

import io.github.hddq.restoid.work.RunTasksConfig
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
    val name: String,
    val intervalHours: Int,
    val config: RunTasksConfig,
    val triggerConditions: TriggerConditions = TriggerConditions(),
    val lastRunTimestamp: Long? = null,
    val isEnabled: Boolean = true
)
