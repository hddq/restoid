package io.github.hddq.restoid.data

import android.content.SharedPreferences
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
        repository = OperationLogRepository(fakePrefs)
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
