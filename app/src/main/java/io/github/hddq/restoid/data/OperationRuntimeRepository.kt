package io.github.hddq.restoid.data

import android.content.Context
import io.github.hddq.restoid.R
import io.github.hddq.restoid.ui.shared.OperationProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class OperationRuntimeState(
    val operationType: OperationType? = null,
    val isRunning: Boolean = false,
    val success: Boolean? = null,
    val progress: OperationProgress = OperationProgress()
)

class OperationRuntimeRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(loadState())
    val state = _state.asStateFlow()

    fun markEnqueued(operationType: OperationType, initialProgress: OperationProgress) {
        updateState(
            OperationRuntimeState(
                operationType = operationType,
                isRunning = true,
                success = null,
                progress = initialProgress.copy(isFinished = false)
            )
        )
    }

    fun markProgress(operationType: OperationType, progress: OperationProgress) {
        updateState(
            OperationRuntimeState(
                operationType = operationType,
                isRunning = !progress.isFinished,
                success = null,
                progress = progress
            )
        )
    }

    fun markFinished(operationType: OperationType, success: Boolean, progress: OperationProgress) {
        updateState(
            OperationRuntimeState(
                operationType = operationType,
                isRunning = false,
                success = success,
                progress = progress.copy(isFinished = true)
            )
        )
    }

    fun clearFinished(operationType: OperationType? = null) {
        val current = _state.value
        if (current.isRunning) return
        if (operationType != null && current.operationType != operationType) return
        updateState(OperationRuntimeState())
    }

    fun clearStaleRunningState() {
        val current = _state.value
        if (!current.isRunning) return
        val interruptedSummary = appContext.getString(R.string.operation_interrupted)
        updateState(
            current.copy(
                isRunning = false,
                success = false,
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
        )
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
            prefs.edit().remove(KEY_STATE).apply()
            return
        }
        prefs.edit().putString(KEY_STATE, encoded).apply()
    }

    @Serializable
    private data class PersistedState(
        val operationType: String? = null,
        val isRunning: Boolean = false,
        val success: Boolean? = null,
        val progress: OperationProgress = OperationProgress()
    ) {
        fun toRuntimeState(): OperationRuntimeState {
            return OperationRuntimeState(
                operationType = operationType?.let { name -> runCatching { OperationType.valueOf(name) }.getOrNull() },
                isRunning = isRunning,
                success = success,
                progress = progress
            )
        }

        companion object {
            fun fromRuntimeState(state: OperationRuntimeState): PersistedState {
                return PersistedState(
                    operationType = state.operationType?.name,
                    isRunning = state.isRunning,
                    success = state.success,
                    progress = state.progress
                )
            }
        }
    }

    private companion object {
        const val PREFS_NAME = "operation_runtime"
        const val KEY_STATE = "state"
    }
}
