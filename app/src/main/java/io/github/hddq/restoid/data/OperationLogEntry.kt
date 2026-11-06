package io.github.hddq.restoid.data

import kotlinx.serialization.Serializable

@Serializable
data class OperationLogEntry(
    val id: String,
    val operationType: OperationType,
    val scheduleName: String? = null,
    val startTimeMillis: Long,
    val endTimeMillis: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0L,
    val success: Boolean,
    val stageTitle: String,
    val summary: String,
    val error: String? = null
)
