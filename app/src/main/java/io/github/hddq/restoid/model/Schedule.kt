package io.github.hddq.restoid.model

import io.github.hddq.restoid.work.RunTasksWorkRequest
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Schedule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val intervalHours: Int,
    val config: RunTasksWorkRequest,
    val lastRunTimestamp: Long? = null,
    val isEnabled: Boolean = true
)
