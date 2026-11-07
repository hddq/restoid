package io.github.hddq.restoid.data

import android.content.Context
import android.content.SharedPreferences
import io.github.hddq.restoid.R
import io.github.hddq.restoid.ui.shared.OperationProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

data class OperationRuntimeState(
    val operationType: OperationType? = null,
    val isRunning: Boolean = false,
    val success: Boolean? = null,
    val stopRequested: Boolean = false,
    val progress: OperationProgress = OperationProgress(),
    val startTimeMillis: Long? = null,
    val lastUpdatedTimeMillis: Long? = null,
    val scheduleName: String? = null,
    val runId: String? = null
)

class OperationRuntimeRepository(
    private val prefs: SharedPreferences,
    private val interruptedSummaryProvider: () -> String
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        { context.applicationContext.getString(R.string.operation_interrupted) }
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(loadState())
    val state = _state.asStateFlow()

    fun markEnqueued(
        operationType: OperationType,
        initialProgress: OperationProgress,
        startTimeMillis: Long = System.currentTimeMillis(),
        scheduleName: String? = null,
        runId: String = UUID.randomUUID().toString()
    ) {
        updateState(
            OperationRuntimeState(
                operationType = operationType,
                isRunning = true,
                success = null,
                stopRequested = false,
                progress = initialProgress.copy(isFinished = false),
                startTimeMillis = startTimeMillis,
                lastUpdatedTimeMillis = startTimeMillis,
                scheduleName = scheduleName,
                runId = runId
            )
        )
    }

    fun markProgress(
        operationType: OperationType,
        progress: OperationProgress,
        updatedTimeMillis: Long = System.currentTimeMillis()
    ) {
        val current = _state.value
        updateState(
            OperationRuntimeState(
                operationType = operationType,
                isRunning = !progress.isFinished,
                success = null,
                stopRequested = current.stopRequested && current.operationType == operationType,
                progress = progress,
                startTimeMillis = current.startTimeMillis,
                lastUpdatedTimeMillis = updatedTimeMillis,
                scheduleName = current.scheduleName,
                runId = current.runId
            )
        )
    }

    fun updateScheduleName(scheduleName: String?) {
        val current = _state.value
        if (current.scheduleName == scheduleName) return
        updateState(current.copy(scheduleName = scheduleName))
    }

    fun markFinished(
        operationType: OperationType,
        success: Boolean,
        progress: OperationProgress,
        finishedTimeMillis: Long = System.currentTimeMillis()
    ) {
        val current = _state.value
        updateState(
            OperationRuntimeState(
                operationType = operationType,
                isRunning = false,
                success = success,
                stopRequested = false,
                progress = progress.copy(isFinished = true),
                startTimeMillis = current.startTimeMillis,
                lastUpdatedTimeMillis = finishedTimeMillis,
                scheduleName = current.scheduleName,
                runId = current.runId
            )
        )
    }

    fun markStopRequested(
        operationType: OperationType,
        progress: OperationProgress,
        updatedTimeMillis: Long = System.currentTimeMillis()
    ) {
        val current = _state.value
        if (!current.isRunning || current.operationType != operationType) return
        updateState(
            current.copy(
                stopRequested = true,
                progress = progress.copy(isFinished = false),
                lastUpdatedTimeMillis = updatedTimeMillis
            )
        )
    }

    fun clearFinished(operationType: OperationType? = null) {
        val current = _state.value
        if (current.isRunning) return
        if (operationType != null && current.operationType != operationType) return
        updateState(OperationRuntimeState())
    }

    fun forceClear() {
        updateState(OperationRuntimeState())
    }

    fun getStaleRunningState(): OperationRuntimeState? {
        val current = _state.value
        if (!current.isRunning && !current.stopRequested) return null
        val interruptedSummary = interruptedSummaryProvider()
        return current.copy(
            isRunning = false,
            success = false,
            stopRequested = false,
            progress = current.progress.copy(
                isFinished = true,
                error = current.progress.error ?: interruptedSummary,
                finalSummary = if (current.progress.finalSummary.isNotBlank()) {
                    current.progress.finalSummary
                } else {
                    current.progress.error ?: interruptedSummary
                }
            )
        )
    }

    fun clearStaleRunningState(): OperationRuntimeState? {
        val cleared = getStaleRunningState() ?: return null
        updateState(cleared)
        return cleared
    }

    private fun loadState(): OperationRuntimeState {
        val encoded = prefs.getString(KEY_STATE, null) ?: return OperationRuntimeState()
        return runCatching { json.decodeFromString<PersistedState>(encoded).toRuntimeState() }
            .getOrElse { OperationRuntimeState() }
    }

    private fun updateState(newState: OperationRuntimeState) {
        _state.value = newState
        val encoded = runCatching { json.encodeToString(PersistedState.fromRuntimeState(newState)) }.getOrNull()
        if (encoded == null) {
            prefs.edit().remove(KEY_STATE).commit()
            return
        }
        prefs.edit().putString(KEY_STATE, encoded).commit()
    }

    @Serializable
    private data class PersistedState(
        val operationType: String? = null,
        val isRunning: Boolean = false,
        val success: Boolean? = null,
        val stopRequested: Boolean = false,
        val progress: OperationProgress = OperationProgress(),
        val startTimeMillis: Long? = null,
        val lastUpdatedTimeMillis: Long? = null,
        val scheduleName: String? = null,
        val runId: String? = null
    ) {
        fun toRuntimeState(): OperationRuntimeState {
            return OperationRuntimeState(
                operationType = operationType?.let { name -> runCatching { OperationType.valueOf(name) }.getOrNull() },
                isRunning = isRunning,
                success = success,
                stopRequested = stopRequested,
                progress = progress,
                startTimeMillis = startTimeMillis,
                lastUpdatedTimeMillis = lastUpdatedTimeMillis,
                scheduleName = scheduleName,
                runId = runId
            )
        }

        companion object {
            fun fromRuntimeState(state: OperationRuntimeState): PersistedState {
                return PersistedState(
                    operationType = state.operationType?.name,
                    isRunning = state.isRunning,
                    success = state.success,
                    stopRequested = state.stopRequested,
                    progress = state.progress,
                    startTimeMillis = state.startTimeMillis,
                    lastUpdatedTimeMillis = state.lastUpdatedTimeMillis,
                    scheduleName = state.scheduleName,
                    runId = state.runId
                )
            }
        }
    }

    private companion object {
        const val PREFS_NAME = "operation_runtime"
        const val KEY_STATE = "state"
    }
}
