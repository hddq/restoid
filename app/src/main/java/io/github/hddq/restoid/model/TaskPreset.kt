package io.github.hddq.restoid.model

import io.github.hddq.restoid.work.RunTasksConfig
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class TaskPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val config: RunTasksConfig,
    val lastRunTimestamp: Long? = null
)
