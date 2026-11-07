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
    fun upsertLog_inProgressPersistsForCrashRecoveryAndUpdatesInPlaceUntilFinalized() {
        val logId = "run-123"

        // 1. Operation starts (inProgress = true)
        repository.upsertLog(
            id = logId,
            operationType = OperationType.BACKUP,
            startTimeMillis = 1000L,
            endTimeMillis = 1000L,
            durationSeconds = 0L,
            success = false,
            stageTitle = "Initializing...",
            summary = "Operation was interrupted.",
            error = "Operation was interrupted.",
            inProgress = true
        )

        // Hidden from live completed logs while running in the current process
        assertTrue(repository.logs.value.isEmpty())

        // 2. Progress updates stage and duration in place
        repository.upsertLog(
            id = logId,
            operationType = OperationType.BACKUP,
            startTimeMillis = 1000L,
            endTimeMillis = 16000L,
            durationSeconds = 15L,
            success = false,
            stageTitle = "Backing up Apps (3/10)",
            summary = "Operation was interrupted.",
            error = "Operation was interrupted.",
            inProgress = true
        )

        // If the app is killed now and reloaded in a new process, the interrupted log is present
        val reloadedAfterKill = OperationLogRepository(fakePrefs, Dispatchers.Unconfined)
        assertEquals(1, reloadedAfterKill.logs.value.size)
        val killedEntry = reloadedAfterKill.logs.value.first()
        assertEquals(logId, killedEntry.id)
        assertEquals(false, killedEntry.success)
        assertEquals("Backing up Apps (3/10)", killedEntry.stageTitle)
        assertEquals(15L, killedEntry.durationSeconds)
        assertEquals("Operation was interrupted.", killedEntry.error)

        // 3. If instead the operation finishes normally (inProgress = false), it updates in place and becomes visible
        repository.upsertLog(
            id = logId,
            operationType = OperationType.BACKUP,
            startTimeMillis = 1000L,
            endTimeMillis = 31000L,
            durationSeconds = 30L,
            success = true,
            stageTitle = "Backup Complete",
            summary = "Added 42 MB",
            error = null,
            inProgress = false
        )

        assertEquals(1, repository.logs.value.size)
        val finalEntry = repository.logs.value.first()
        assertEquals(logId, finalEntry.id)
        assertEquals(true, finalEntry.success)
        assertEquals("Backup Complete", finalEntry.stageTitle)
        assertEquals(30L, finalEntry.durationSeconds)
        assertEquals("Added 42 MB", finalEntry.summary)
        assertNull(finalEntry.error)
    }
}

/**
 * Minimal in-memory SharedPreferences implementation for JVM unit tests.
 */
private class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = data.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST") (data[key] as? MutableSet<String> ?: defValues)
    override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = data.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FakeEditor(data)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private class FakeEditor(private val backingData: MutableMap<String, Any?>) : SharedPreferences.Editor {
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
            return true
        }
        override fun apply() {
            if (clear) backingData.clear()
            removed.forEach { backingData.remove(it) }
            backingData.putAll(temp)
        }
    }
}
