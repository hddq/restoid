package io.github.hddq.restoid.data

import android.content.SharedPreferences
import io.github.hddq.restoid.testutil.FakeSharedPreferences
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
}
