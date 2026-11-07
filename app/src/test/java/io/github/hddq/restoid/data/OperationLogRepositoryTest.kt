package io.github.hddq.restoid.data

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OperationLogRepositoryTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var repository: OperationLogRepository

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        repository = OperationLogRepository(fakePrefs, Dispatchers.Unconfined)
    }

    @Test
    fun addLog_persistsAndPrependsNewestEntry() {
        val entry1 = repository.addLog(
            operationType = OperationType.BACKUP,
            scheduleName = "Nightly",
            startTimeMillis = 1000L,
            endTimeMillis = 2000L,
            durationSeconds = 1L,
            success = true,
            stageTitle = "Backup Complete",
            summary = "Added 10 MB",
            error = null
        )

        val entry2 = repository.addLog(
            operationType = OperationType.MAINTENANCE,
            scheduleName = null,
            startTimeMillis = 3000L,
            endTimeMillis = 4000L,
            durationSeconds = 1L,
            success = false,
            stageTitle = "Maintenance Failed",
            summary = "Failed to unlock",
            error = "Lock exists"
        )

        val logs = repository.logs.value
        assertEquals(2, logs.size)
        assertEquals(entry2.id, logs[0].id)
        assertEquals(entry1.id, logs[1].id)
        assertEquals(OperationType.MAINTENANCE, logs[0].operationType)
        assertEquals("Nightly", logs[1].scheduleName)

        // Verify persistence by reloading from the same preferences
        val reloadedRepo = OperationLogRepository(fakePrefs, Dispatchers.Unconfined)
        val reloadedLogs = reloadedRepo.logs.value
        assertEquals(2, reloadedLogs.size)

        val restoredEntry2 = reloadedLogs[0]
        assertEquals(entry2.id, restoredEntry2.id)
        assertEquals(OperationType.MAINTENANCE, restoredEntry2.operationType)
        assertEquals(false, restoredEntry2.success)
        assertEquals("Maintenance Failed", restoredEntry2.stageTitle)
        assertEquals("Failed to unlock", restoredEntry2.summary)
        assertEquals("Lock exists", restoredEntry2.error)
        assertEquals(1L, restoredEntry2.durationSeconds)

        val restoredEntry1 = reloadedLogs[1]
        assertEquals(entry1.id, restoredEntry1.id)
        assertEquals(OperationType.BACKUP, restoredEntry1.operationType)
        assertEquals("Nightly", restoredEntry1.scheduleName)
        assertEquals(true, restoredEntry1.success)
        assertEquals("Backup Complete", restoredEntry1.stageTitle)
        assertEquals("Added 10 MB", restoredEntry1.summary)
        assertNull(restoredEntry1.error)
    }

    @Test
    fun addLog_truncatesLongFields() {
        val longText = "a".repeat(5000)
        val entry = repository.addLog(
            operationType = OperationType.BACKUP,
            scheduleName = longText,
            success = false,
            stageTitle = longText,
            summary = longText,
            error = longText
        )

        assertEquals(OperationLogRepository.MAX_FIELD_LENGTH, entry.stageTitle.length)
        assertEquals(OperationLogRepository.MAX_FIELD_LENGTH, entry.summary.length)
        assertEquals(OperationLogRepository.MAX_FIELD_LENGTH, entry.error?.length)
        assertEquals(OperationLogRepository.MAX_FIELD_LENGTH, entry.scheduleName?.length)

        val reloadedRepo = OperationLogRepository(fakePrefs, Dispatchers.Unconfined)
        val restored = reloadedRepo.getLogById(entry.id)
        assertNotNull(restored)
        assertEquals(OperationLogRepository.MAX_FIELD_LENGTH, restored?.summary?.length)
        assertEquals(OperationLogRepository.MAX_FIELD_LENGTH, restored?.error?.length)
    }

    @Test
    fun getLogById_returnsMatchingEntry() {
        val entry = repository.addLog(
            operationType = OperationType.RESTORE,
            success = true,
            stageTitle = "Restore Complete",
            summary = "Restored 5 apps"
        )

        val found = repository.getLogById(entry.id)
        assertNotNull(found)
        assertEquals(entry.id, found?.id)
        assertEquals(OperationType.RESTORE, found?.operationType)

        assertNull(repository.getLogById("non_existent_id"))
    }

    @Test
    fun clearLogs_removesAllEntries() {
        repository.addLog(
            operationType = OperationType.BACKUP,
            success = true,
            stageTitle = "Backup Complete",
            summary = "Added 50 MB"
        )
        assertEquals(1, repository.logs.value.size)

        repository.clearLogs()
        assertTrue(repository.logs.value.isEmpty())
        assertNull(fakePrefs.getString("logs", null))
    }

    @Test
    fun addLog_capsAtMaxEntries() {
        for (i in 1..120) {
            repository.addLog(
                operationType = OperationType.BACKUP,
                success = true,
                stageTitle = "Run $i",
                summary = "Summary $i"
            )
        }

        val logs = repository.logs.value
        assertEquals(OperationLogRepository.MAX_LOG_ENTRIES, logs.size)
        assertEquals("Run 120", logs.first().stageTitle)
    }

    @Test
    fun saveActiveLog_durableSavesToStoredStateForCrashRecovery() {
        val logId = "recovery-run-1"

        // 1. Initial record saved durably before starting runner
        repository.saveActiveLog(
            id = logId,
            operationType = OperationType.BACKUP,
            scheduleName = "Nightly",
            startTimeMillis = 1000L,
            endTimeMillis = 1000L,
            durationSeconds = 0L,
            stageTitle = "Initializing...",
            summary = "Operation was interrupted.",
            error = "Operation was interrupted.",
            durable = true
        )

        // Durable write must call commit() synchronously
        assertTrue(fakePrefs.lastOperationWasCommit)

        // Hidden from live completed logs while running in the current process
        assertTrue(repository.logs.value.isEmpty())

        // 2. Simulate process kill (SIGKILL) - the live in-memory cache is lost.
        // A new process starts and initializes SharedPreferences strictly from stored disk state.
        val storedDiskPrefs = fakePrefs.createFromStoredDiskState()
        val recoveredRepo = OperationLogRepository(storedDiskPrefs, Dispatchers.Unconfined)

        assertEquals(1, recoveredRepo.logs.value.size)
        val recovered = recoveredRepo.logs.value.first()
        assertEquals(logId, recovered.id)
        assertEquals(OperationType.BACKUP, recovered.operationType)
        assertEquals("Nightly", recovered.scheduleName)
        assertEquals(false, recovered.success)
        assertEquals("Initializing...", recovered.stageTitle)
        assertEquals("Operation was interrupted.", recovered.error)

        // Recovery persists the entry into completed logs and clears the active checkpoint
        assertNotNull(storedDiskPrefs.getString(OperationLogRepository.KEY_LOGS, null))
        assertNull(storedDiskPrefs.getString(OperationLogRepository.KEY_ACTIVE_LOG, null))
    }

    @Test
    fun saveActiveLog_progressUpdatesActiveLogWithoutReencodingCompletedHistory() {
        // Pre-populate completed history
        repository.addLog(
            operationType = OperationType.BACKUP,
            success = true,
            stageTitle = "Old run",
            summary = "Old summary"
        )
        fakePrefs.flushApplyToDisk()
        val originalHistoryJson = fakePrefs.getString(OperationLogRepository.KEY_LOGS, null)
        assertNotNull(originalHistoryJson)

        val activeId = "active-run-2"
        // Progress update with durable = false
        repository.saveActiveLog(
            id = activeId,
            operationType = OperationType.BACKUP,
            startTimeMillis = 1000L,
            endTimeMillis = 15000L,
            durationSeconds = 14L,
            stageTitle = "Backing up Apps (4/10)",
            summary = "Operation was interrupted.",
            error = "Operation was interrupted.",
            durable = false
        )

        // Completed history key was NOT rewritten or re-encoded during progress update
        assertEquals(originalHistoryJson, fakePrefs.getString(OperationLogRepository.KEY_LOGS, null))

        // Only the single active log checkpoint was written
        assertNotNull(fakePrefs.getString(OperationLogRepository.KEY_ACTIVE_LOG, null))

        // Live logs list still displays only completed history
        assertEquals(1, repository.logs.value.size)
        assertEquals("Old run", repository.logs.value.first().stageTitle)
    }

    @Test
    fun completeActiveLog_finalizesActiveRunAndClearsActiveCheckpoint() {
        val activeId = "active-run-3"
        repository.saveActiveLog(
            id = activeId,
            operationType = OperationType.BACKUP,
            startTimeMillis = 1000L,
            endTimeMillis = 5000L,
            durationSeconds = 4L,
            stageTitle = "Backing up",
            summary = "Operation was interrupted.",
            error = "Operation was interrupted.",
            durable = true
        )

        repository.completeActiveLog(
            id = activeId,
            operationType = OperationType.BACKUP,
            startTimeMillis = 1000L,
            endTimeMillis = 30000L,
            durationSeconds = 29L,
            success = true,
            stageTitle = "Backup Complete",
            summary = "Added 42 MB",
            error = null
        )

        // Active checkpoint is cleared
        assertNull(fakePrefs.getString(OperationLogRepository.KEY_ACTIVE_LOG, null))

        // Completed history contains the finalized run
        assertEquals(1, repository.logs.value.size)
        val finalEntry = repository.logs.value.first()
        assertEquals(activeId, finalEntry.id)
        assertEquals(true, finalEntry.success)
        assertEquals("Backup Complete", finalEntry.stageTitle)
        assertEquals(29L, finalEntry.durationSeconds)
        assertEquals("Added 42 MB", finalEntry.summary)
        assertNull(finalEntry.error)
    }
}

/**
 * In-memory SharedPreferences implementation for JVM unit tests that distinguishes
 * between the live in-memory cache and persisted storage on disk.
 *
 * Calling [commit] synchronously flushes changes to [diskStorage].
 * Calling [apply] updates only [liveCache] immediately, simulating Android's
 * asynchronous disk write queue.
 */
private class FakeSharedPreferences(
    initialData: Map<String, Any?> = emptyMap()
) : SharedPreferences {
    private val diskStorage = mutableMapOf<String, Any?>().apply { putAll(initialData) }
    private val liveCache = mutableMapOf<String, Any?>().apply { putAll(initialData) }

    var lastOperationWasCommit: Boolean = false
        private set

    /**
     * Simulates restarting the process by returning a new [FakeSharedPreferences] instance
     * populated only with data that was durably written to [diskStorage].
     */
    fun createFromStoredDiskState(): FakeSharedPreferences {
        return FakeSharedPreferences(diskStorage)
    }

    /**
     * Simulates the background worker or OS flushing pending [apply] writes to disk.
     */
    fun flushApplyToDisk() {
        diskStorage.clear()
        diskStorage.putAll(liveCache)
    }

    override fun getAll(): MutableMap<String, *> = liveCache.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = liveCache[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST") (liveCache[key] as? MutableSet<String> ?: defValues)
    override fun getInt(key: String?, defValue: Int): Int = liveCache[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = liveCache[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = liveCache[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = liveCache[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = liveCache.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FakeEditor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class FakeEditor : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) temp[key] = values
            return this
        }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }
        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) removed.add(key)
            return this
        }
        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }
        override fun commit(): Boolean {
            apply()
            flushPendingToDisk()
            lastOperationWasCommit = true
            return true
        }
        override fun apply() {
            if (clear) liveCache.clear()
            removed.forEach { liveCache.remove(it) }
            liveCache.putAll(temp)
            lastOperationWasCommit = false
        }
        private fun flushPendingToDisk() {
            if (clear) diskStorage.clear()
            removed.forEach { diskStorage.remove(it) }
            diskStorage.putAll(temp)
        }
    }
}
