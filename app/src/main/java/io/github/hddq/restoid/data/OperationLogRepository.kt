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
    private var activeLogId: String? = null
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
    ): OperationLogEntry = completeActiveLog(
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

    fun saveActiveLog(
        id: String,
        operationType: OperationType,
        scheduleName: String? = null,
        startTimeMillis: Long = System.currentTimeMillis(),
        endTimeMillis: Long = System.currentTimeMillis(),
        durationSeconds: Long = 0L,
        stageTitle: String,
        summary: String,
        error: String? = null,
        durable: Boolean = false
    ): OperationLogEntry = synchronized(lock) {
        ensureLoadedLocked()
        activeLogId = id
        val entry = OperationLogEntry(
            id = id,
            operationType = operationType,
            scheduleName = scheduleName?.take(MAX_FIELD_LENGTH),
            startTimeMillis = startTimeMillis,
            endTimeMillis = endTimeMillis,
            durationSeconds = durationSeconds,
            success = false,
            stageTitle = stageTitle.take(MAX_FIELD_LENGTH),
            summary = summary.take(MAX_FIELD_LENGTH),
            error = error?.take(MAX_FIELD_LENGTH)
        )
        val encoded = runCatching { json.encodeToString(entry) }.getOrNull()
        val editor = prefs.edit()
        if (encoded != null) {
            editor.putString(KEY_ACTIVE_LOG, encoded)
        } else {
            editor.remove(KEY_ACTIVE_LOG)
        }
        if (durable) {
            editor.commit()
        } else {
            editor.apply()
        }
        entry
    }

    fun completeActiveLog(
        id: String,
        operationType: OperationType,
        scheduleName: String? = null,
        startTimeMillis: Long = System.currentTimeMillis(),
        endTimeMillis: Long = System.currentTimeMillis(),
        durationSeconds: Long = 0L,
        success: Boolean,
        stageTitle: String,
        summary: String,
        error: String? = null
    ): OperationLogEntry = synchronized(lock) {
        ensureLoadedLocked()
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
        val current = _logs.value
        val existingIndex = current.indexOfFirst { it.id == id }
        val updated = if (existingIndex >= 0) {
            current.toMutableList().apply { set(existingIndex, entry) }
        } else {
            (listOf(entry) + current).take(MAX_LOG_ENTRIES)
        }
        _logs.value = updated
        val clearActive = activeLogId == id || loadActiveLogLocked()?.id == id
        if (clearActive) {
            activeLogId = null
        }
        val encoded = runCatching { json.encodeToString(updated) }.getOrNull()
        prefs.edit().apply {
            if (encoded != null) {
                putString(KEY_LOGS, encoded)
            } else {
                remove(KEY_LOGS)
            }
            if (clearActive) {
                remove(KEY_ACTIVE_LOG)
            }
            apply()
        }
        entry
    }

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
        inProgress: Boolean = false,
        durable: Boolean = false
    ): OperationLogEntry {
        return if (inProgress) {
            saveActiveLog(
                id = id,
                operationType = operationType,
                scheduleName = scheduleName,
                startTimeMillis = startTimeMillis,
                endTimeMillis = endTimeMillis,
                durationSeconds = durationSeconds,
                stageTitle = stageTitle,
                summary = summary,
                error = error,
                durable = durable
            )
        } else {
            completeActiveLog(
                id = id,
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
        }
    }

    fun getLogById(id: String): OperationLogEntry? = synchronized(lock) {
        ensureLoadedLocked()
        _logs.value.find { it.id == id } ?: loadActiveLogLocked()?.takeIf { it.id == id }
    }

    fun clearLogs() = synchronized(lock) {
        ensureLoadedLocked()
        _logs.value = emptyList()
        prefs.edit().remove(KEY_LOGS).apply()
    }

    private fun ensureLoadedLocked() {
        if (!isLoaded) {
            val loadedLogs = loadLogsLocked()
            val activeLog = loadActiveLogLocked()
            if (activeLog != null && activeLog.id != activeLogId) {
                val existingIndex = loadedLogs.indexOfFirst { it.id == activeLog.id }
                val merged = if (existingIndex >= 0) {
                    loadedLogs.toMutableList().apply { set(existingIndex, activeLog) }
                } else {
                    (listOf(activeLog) + loadedLogs).take(MAX_LOG_ENTRIES)
                }
                _logs.value = merged
                val encoded = runCatching { json.encodeToString(merged) }.getOrNull()
                prefs.edit().apply {
                    if (encoded != null) {
                        putString(KEY_LOGS, encoded)
                    } else {
                        remove(KEY_LOGS)
                    }
                    remove(KEY_ACTIVE_LOG)
                    apply()
                }
            } else {
                _logs.value = loadedLogs
            }
            isLoaded = true
        }
    }

    private fun loadLogsLocked(): List<OperationLogEntry> {
        val raw = prefs.getString(KEY_LOGS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<OperationLogEntry>>(raw)
        }.getOrDefault(emptyList())
    }

    private fun loadActiveLogLocked(): OperationLogEntry? {
        val raw = prefs.getString(KEY_ACTIVE_LOG, null) ?: return null
        return runCatching {
            json.decodeFromString<OperationLogEntry>(raw)
        }.getOrNull()
    }

    companion object {
        const val PREFS_NAME = "operation_logs"
        const val KEY_LOGS = "logs"
        const val KEY_ACTIVE_LOG = "active_log"
        const val MAX_LOG_ENTRIES = 100
        const val MAX_FIELD_LENGTH = 4000
    }
}
