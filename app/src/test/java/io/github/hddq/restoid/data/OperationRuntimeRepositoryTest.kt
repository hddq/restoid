package io.github.hddq.restoid.data

import io.github.hddq.restoid.testutil.FakeSharedPreferences
import io.github.hddq.restoid.ui.shared.OperationProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OperationRuntimeRepositoryTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var repository: OperationRuntimeRepository

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        repository = OperationRuntimeRepository(fakePrefs) { "Operation was interrupted" }
    }

    @Test
    fun markEnqueued_persistsStateWithMetadata() {
        val initialProgress = OperationProgress(stageTitle = "Initializing")
        repository.markEnqueued(
            operationType = OperationType.BACKUP,
            initialProgress = initialProgress,
            startTimeMillis = 12345L,
            scheduleName = "Daily Backup"
        )

        val state = repository.state.value
        assertTrue(state.isRunning)
        assertEquals(OperationType.BACKUP, state.operationType)
        assertEquals(12345L, state.startTimeMillis)
        assertEquals("Daily Backup", state.scheduleName)
        assertEquals("Initializing", state.progress.stageTitle)

        // Verify reloaded state from prefs preserves metadata
        val reloaded = OperationRuntimeRepository(fakePrefs) { "Operation was interrupted" }
        val restoredState = reloaded.state.value
        assertTrue(restoredState.isRunning)
        assertEquals(OperationType.BACKUP, restoredState.operationType)
        assertEquals(12345L, restoredState.startTimeMillis)
        assertEquals("Daily Backup", restoredState.scheduleName)
    }

    @Test
    fun markProgress_preservesMetadata() {
        repository.markEnqueued(
            operationType = OperationType.RUN_TASKS,
            initialProgress = OperationProgress(stageTitle = "Starting"),
            startTimeMillis = 5000L,
            scheduleName = "Nightly Maintenance"
        )

        repository.markProgress(
            operationType = OperationType.RUN_TASKS,
            progress = OperationProgress(stageTitle = "Processing Files", overallPercentage = 50f)
        )

        val state = repository.state.value
        assertTrue(state.isRunning)
        assertEquals(5000L, state.startTimeMillis)
        assertEquals("Nightly Maintenance", state.scheduleName)
        assertEquals("Processing Files", state.progress.stageTitle)
        assertEquals(50f, state.progress.overallPercentage)
    }

    @Test
    fun clearStaleRunningState_recoversAndReturnsInterruptedState() {
        repository.markEnqueued(
            operationType = OperationType.BACKUP,
            initialProgress = OperationProgress(stageTitle = "Backing up items"),
            startTimeMillis = 1000L,
            scheduleName = "My Schedule"
        )

        val clearedState = repository.clearStaleRunningState()
        assertNotNull(clearedState)
        assertFalse(clearedState!!.isRunning)
        assertEquals(false, clearedState.success)
        assertEquals("Operation was interrupted", clearedState.progress.error)
        assertEquals("Operation was interrupted", clearedState.progress.finalSummary)
        assertEquals(OperationType.BACKUP, clearedState.operationType)
        assertEquals("My Schedule", clearedState.scheduleName)
        assertEquals(1000L, clearedState.startTimeMillis)

        // Calling clearStaleRunningState again returns null since it's no longer running
        val secondCall = repository.clearStaleRunningState()
        assertNull(secondCall)
    }

    @Test
    fun clearStaleRunningState_returnsNullWhenIdle() {
        assertNull(repository.clearStaleRunningState())
    }
}
