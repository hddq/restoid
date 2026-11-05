package io.github.hddq.restoid.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class OperationLogRepository(
    private val prefs: SharedPreferences
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val _logs = MutableStateFlow(loadLogs())
    val logs = _logs.asStateFlow()

    fun addLog(
        operationType: OperationType,
        scheduleName: String? = null,
        startTimeMillis: Long = System.currentTimeMillis(),
        endTimeMillis: Long = System.currentTimeMillis(),
        durationSeconds: Long = 0L,
        success: Boolean,
        stageTitle: String,
        summary: String,
        error: String? = null
    ): OperationLogEntry {
        val entry = OperationLogEntry(
            id = UUID.randomUUID().toString(),
            operationType = operationType,
            scheduleName = scheduleName,
            startTimeMillis = startTimeMillis,
            endTimeMillis = endTimeMillis,
            durationSeconds = durationSeconds,
            success = success,
            stageTitle = stageTitle,
            summary = summary,
            error = error
        )
        val updated = (listOf(entry) + _logs.value).take(MAX_LOG_ENTRIES)
        updateLogs(updated)
        return entry
    }

    fun getLogById(id: String): OperationLogEntry? {
        return _logs.value.find { it.id == id }
    }

    fun clearLogs() {
        updateLogs(emptyList())
    }

    private fun loadLogs(): List<OperationLogEntry> {
        val raw = prefs.getString(KEY_LOGS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<OperationLogEntry>>(raw)
        }.getOrDefault(emptyList())
    }

    private fun updateLogs(newLogs: List<OperationLogEntry>) {
        _logs.value = newLogs
        if (newLogs.isEmpty()) {
            prefs.edit().remove(KEY_LOGS).apply()
            return
        }
        val encoded = runCatching { json.encodeToString(newLogs) }.getOrNull()
        prefs.edit().apply {
            if (encoded != null) {
                putString(KEY_LOGS, encoded)
            } else {
                remove(KEY_LOGS)
            }
            apply()
        }
    }

    companion object {
        const val PREFS_NAME = "operation_logs"
        const val KEY_LOGS = "logs"
        const val MAX_LOG_ENTRIES = 100
    }
}
