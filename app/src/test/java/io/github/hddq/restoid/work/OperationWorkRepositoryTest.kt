package io.github.hddq.restoid.work

import io.github.hddq.restoid.data.OperationLogRepository
import io.github.hddq.restoid.data.OperationRuntimeRepository
import io.github.hddq.restoid.data.OperationType
import io.github.hddq.restoid.testutil.FakeSharedPreferences
import io.github.hddq.restoid.ui.shared.OperationProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OperationWorkRepositoryTest {

    private lateinit var runtimePrefs: FakeSharedPreferences
    private lateinit var logPrefs: FakeSharedPreferences
    private lateinit var runtimeRepository: OperationRuntimeRepository
    private lateinit var logRepository: OperationLogRepository

    private var fakeNowMillis: Long = 1_000_000L
    private var hasActiveWork: Boolean = false
    private var onCancelWork: (() -> Unit)? = null

    private lateinit var workRepository: OperationWorkRepository

    @Before
    fun setUp() {
        runtimePrefs = FakeSharedPreferences()
        logPrefs = FakeSharedPreferences()
        runtimeRepository = OperationRuntimeRepository(runtimePrefs) { "Operation was interrupted." }
        logRepository = OperationLogRepository(logPrefs, Dispatchers.Unconfined)
        fakeNowMillis = 1_000_000L
        hasActiveWork = false
        onCancelWork = null

        workRepository = OperationWorkRepository(
            requestStore = null,
            runtimeRepository = runtimeRepository,
            operationLogRepository = logRepository,
            strings = OperationWorkStrings(),
            activeWorkChecker = { hasActiveWork },
            workCanceller = { onCancelWork?.invoke() },
            currentTimeProvider = { fakeNowMillis },
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun reconcileStateWithWorkManager_logsInterruptedOperationWithoutCountingDowntime() = runBlocking {
        // Start scheduled operation at t = 1,000,000
        runtimeRepository.markEnqueued(
            operationType = OperationType.RUN_TASKS,
            initialProgress = OperationProgress(stageTitle = "Initializing"),
            startTimeMillis = 1_000_000L,
            scheduleName = "Nightly",
            runId = "run-123"
        )

        // Emit progress at t = 1,060,000 (60 seconds later)
        runtimeRepository.markProgress(
            operationType = OperationType.RUN_TASKS,
            progress = OperationProgress(stageTitle = "Backing up apps", elapsedTime = 60L),
            updatedTimeMillis = 1_060_000L
        )

        // Simulate app being reopened 6 hours later after process death
        fakeNowMillis = 22_600_000L
        hasActiveWork = false

        workRepository.reconcileStateWithWorkManager()

        assertFalse(runtimeRepository.state.value.isRunning)
        assertEquals(false, runtimeRepository.state.value.success)

        val logs = logRepository.logs.value
        assertEquals(1, logs.size)
        val entry = logs.first()
        assertEquals("run-123", entry.id)
        assertEquals(OperationType.RUN_TASKS, entry.operationType)
        assertEquals("Nightly", entry.scheduleName)
        assertFalse(entry.success)
        assertEquals("Backing up apps", entry.stageTitle)
        assertEquals("Operation was interrupted.", entry.error)
        assertEquals(1_000_000L, entry.startTimeMillis)
        assertEquals(1_060_000L, entry.endTimeMillis)
        assertEquals(60L, entry.durationSeconds)

        // Repeated recovery calls must not add duplicate logs
        workRepository.reconcileStateWithWorkManager()
        assertEquals(1, logRepository.logs.value.size)
    }

    @Test
    fun reconcileStateWithWorkManager_doesNotLogWhenWorkIsStillActive() = runBlocking {
        runtimeRepository.markEnqueued(
            operationType = OperationType.BACKUP,
            initialProgress = OperationProgress(stageTitle = "Running"),
            startTimeMillis = 1_000_000L,
            runId = "run-active"
        )
        hasActiveWork = true

        workRepository.reconcileStateWithWorkManager()

        assertTrue(runtimeRepository.state.value.isRunning)
        assertTrue(logRepository.logs.value.isEmpty())
    }

    @Test
    fun reconcileStateWithWorkManager_isIdempotentIfProcessDiedAfterLogWriteBeforeStateClear() = runBlocking {
        runtimeRepository.markEnqueued(
            operationType = OperationType.BACKUP,
            initialProgress = OperationProgress(stageTitle = "Backing up"),
            startTimeMillis = 1_000_000L,
            runId = "run-crash-mid-recovery"
        )
        // Simulate previous recovery having written the log before dying prior to clearing runtime state
        logRepository.addLog(
            id = "run-crash-mid-recovery",
            operationType = OperationType.BACKUP,
            startTimeMillis = 1_000_000L,
            endTimeMillis = 1_010_000L,
            durationSeconds = 10L,
            success = false,
            stageTitle = "Backing up",
            summary = "Operation was interrupted.",
            error = "Operation was interrupted."
        )

        workRepository.reconcileStateWithWorkManager()

        assertFalse(runtimeRepository.state.value.isRunning)
        assertEquals(1, logRepository.logs.value.size)
        assertEquals("run-crash-mid-recovery", logRepository.logs.value.first().id)
    }

    @Test
    fun forceStopCurrentOperation_doesNotDuplicateWhenWorkerWritesItsOwnLog() = runBlocking {
        runtimeRepository.markEnqueued(
            operationType = OperationType.BACKUP,
            initialProgress = OperationProgress(stageTitle = "Finalizing"),
            startTimeMillis = 1_000_000L,
            runId = "run-force-stop"
        )

        // Simulate worker completing and writing its log during cancellation
        onCancelWork = {
            logRepository.addLog(
                id = "run-force-stop",
                operationType = OperationType.BACKUP,
                startTimeMillis = 1_000_000L,
                endTimeMillis = 1_005_000L,
                durationSeconds = 5L,
                success = true,
                stageTitle = "Backup Complete",
                summary = "Backed up 10 items"
            )
            runtimeRepository.markFinished(
                operationType = OperationType.BACKUP,
                success = true,
                progress = OperationProgress(
                    stageTitle = "Backup Complete",
                    isFinished = true,
                    finalSummary = "Backed up 10 items"
                ),
                finishedTimeMillis = 1_005_000L
            )
        }

        workRepository.forceStopCurrentOperation()

        val logs = logRepository.logs.value
        assertEquals(1, logs.size)
        assertEquals("run-force-stop", logs.first().id)
        assertTrue(logs.first().success)
        assertEquals("Backed up 10 items", logs.first().summary)
    }

    @Test
    fun cancelCurrentOperation_logsOnceEvenIfWorkerAlsoAttemptsFallbackLog() = runBlocking {
        runtimeRepository.markEnqueued(
            operationType = OperationType.RESTORE,
            initialProgress = OperationProgress(stageTitle = "Restoring files"),
            startTimeMillis = 10_000L,
            runId = "run-cancel-1"
        )
        fakeNowMillis = 25_000L
        hasActiveWork = false

        workRepository.cancelCurrentOperation()

        assertEquals(1, logRepository.logs.value.size)
        val entry = logRepository.logs.value.first()
        assertEquals("run-cancel-1", entry.id)
        assertEquals(OperationType.RESTORE, entry.operationType)
        assertFalse(entry.success)
        assertEquals(15L, entry.durationSeconds)
        assertEquals("Operation was interrupted.", entry.error)

        // Simulate worker NonCancellable block running after fallback already logged
        logRepository.addLog(
            id = "run-cancel-1",
            operationType = OperationType.RESTORE,
            startTimeMillis = 10_000L,
            endTimeMillis = 26_000L,
            durationSeconds = 16L,
            success = false,
            stageTitle = "Restore Failed",
            summary = "Operation was interrupted.",
            error = "Operation was interrupted."
        )

        // Still only 1 entry for the run
        assertEquals(1, logRepository.logs.value.size)
    }
}
