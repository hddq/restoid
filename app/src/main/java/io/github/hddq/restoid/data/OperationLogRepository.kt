package io.github.hddq.restoid.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class OperationLogRepository(
    private val prefs: SharedPreferences,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    constructor(context: Context, ioDispatcher: CoroutineDispatcher = Dispatchers.IO) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        ioDispatcher
    )

    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true }
    private var isLoaded = false
    private val activeLogIds = mutableSetOf<String>()
    private var allLogs = emptyList<OperationLogEntry>()
    private val _logs = MutableStateFlow<List<OperationLogEntry>>(emptyList())
    val logs = _logs.asStateFlow()

    init {
        CoroutineScope(ioDispatcher).launch {
            synchronized(lock) {
                ensureLoadedLocked()
            }
        }
    }

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
    ): OperationLogEntry = upsertLog(
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

    fun upsertLog(
        id: String,
        operationType: OperationType,
        scheduleName: String? = null,
        startTimeMillis: Long = System.currentTimeMillis(),
        endTimeMillis: Long = System.currentTimeMillis(),
        durationSeconds: Long = 0L,
        success: Boolean,
        stageTitle: String,
        summary: String,
        error: String? = null,
        inProgress: Boolean = false
    ): OperationLogEntry = synchronized(lock) {
        ensureLoadedLocked()
        if (inProgress) {
            activeLogIds.add(id)
        } else {
            activeLogIds.remove(id)
        }
        val entry = OperationLogEntry(
            id = id,
            operationType = operationType,
            scheduleName = scheduleName?.take(MAX_FIELD_LENGTH),
            startTimeMillis = startTimeMillis,
            endTimeMillis = endTimeMillis,
            durationSeconds = durationSeconds,
            success = success,
            stageTitle = stageTitle.take(MAX_FIELD_LENGTH),
            summary = summary.take(MAX_FIELD_LENGTH),
            error = error?.take(MAX_FIELD_LENGTH)
        )
        val existingIndex = allLogs.indexOfFirst { it.id == id }
        val updated = if (existingIndex >= 0) {
            allLogs.toMutableList().apply { set(existingIndex, entry) }
        } else {
            (listOf(entry) + allLogs).take(MAX_LOG_ENTRIES)
        }
        updateLogsLocked(updated)
        entry
    }

    fun getLogById(id: String): OperationLogEntry? = synchronized(lock) {
        ensureLoadedLocked()
        _logs.value.find { it.id == id }
    }

    fun clearLogs() = synchronized(lock) {
        ensureLoadedLocked()
        updateLogsLocked(allLogs.filter { it.id in activeLogIds })
    }

    private fun ensureLoadedLocked() {
        if (!isLoaded) {
            allLogs = loadLogsLocked()
            _logs.value = allLogs.filterNot { it.id in activeLogIds }
            isLoaded = true
        }
    }

    private fun loadLogsLocked(): List<OperationLogEntry> {
        val raw = prefs.getString(KEY_LOGS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<OperationLogEntry>>(raw)
        }.getOrDefault(emptyList())
    }

    private fun updateLogsLocked(newLogs: List<OperationLogEntry>) {
        allLogs = newLogs
        _logs.value = newLogs.filterNot { it.id in activeLogIds }
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
        const val MAX_FIELD_LENGTH = 4000
    }
}
