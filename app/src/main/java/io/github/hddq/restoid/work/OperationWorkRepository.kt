package io.github.hddq.restoid.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.topjohnwu.superuser.Shell
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.OperationLogRepository
import io.github.hddq.restoid.data.OperationRuntimeRepository
import io.github.hddq.restoid.data.OperationRuntimeState
import io.github.hddq.restoid.data.OperationType
import io.github.hddq.restoid.ui.shared.OperationProgress
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException

internal data class OperationWorkStrings(
    val progressInitializing: String = "Initializing…",
    val progressStopping: String = "Stopping…",
    val operationInterrupted: String = "Operation was interrupted.",
    val errorAlreadyRunning: String = "An operation is already running.",
    val summaryAlreadyRunning: String = "An operation is already in progress.",
    val operationLabel: (OperationType) -> String = { it.name },
    val operationFailedTitle: (String) -> String = { "$it Failed" }
) {
    companion object {
        fun fromContext(context: Context): OperationWorkStrings {
            val appContext = context.applicationContext
            return OperationWorkStrings(
                progressInitializing = appContext.getString(R.string.progress_initializing),
                progressStopping = appContext.getString(R.string.progress_stopping),
                operationInterrupted = appContext.getString(R.string.operation_interrupted),
                errorAlreadyRunning = appContext.getString(R.string.error_operation_already_running),
                summaryAlreadyRunning = appContext.getString(R.string.summary_operation_already_running),
                operationLabel = { type ->
                    when (type) {
                        OperationType.BACKUP -> appContext.getString(R.string.operation_backup)
                        OperationType.RUN_TASKS -> appContext.getString(R.string.operation_run_tasks)
                        OperationType.RESTORE -> appContext.getString(R.string.operation_restore)
                        OperationType.MAINTENANCE -> appContext.getString(R.string.operation_maintenance)
                    }
                },
                operationFailedTitle = { label ->
                    appContext.getString(R.string.progress_operation_failed, label)
                }
            )
        }
    }
}

class OperationWorkRepository internal constructor(
    private val requestStore: OperationRequestStore?,
    private val runtimeRepository: OperationRuntimeRepository,
    private val operationLogRepository: OperationLogRepository,
    private val strings: OperationWorkStrings = OperationWorkStrings(),
    private val workManagerProvider: () -> WorkManager = { error("WorkManager not provided") },
    private val activeWorkChecker: (suspend () -> Boolean)? = null,
    private val workCanceller: (suspend () -> Unit)? = null,
    private val currentTimeProvider: () -> Long = { System.currentTimeMillis() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    constructor(
        context: Context,
        requestStore: OperationRequestStore,
        runtimeRepository: OperationRuntimeRepository,
        operationLogRepository: OperationLogRepository
    ) : this(
        requestStore = requestStore,
        runtimeRepository = runtimeRepository,
        operationLogRepository = operationLogRepository,
        strings = OperationWorkStrings.fromContext(context),
        workManagerProvider = { WorkManager.getInstance(context.applicationContext) }
    )

    private val workManager by lazy { workManagerProvider() }
    private val enqueueMutex = Mutex()
    private val repositoryScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    val operationState: StateFlow<OperationRuntimeState> = runtimeRepository.state

    suspend fun enqueueBackup(request: BackupWorkRequest): Boolean {
        val store = checkNotNull(requestStore) { "OperationRequestStore is required to enqueue work" }
        return enqueueOperation(
            operationType = OperationType.BACKUP,
            requestIdProvider = { store.saveBackupRequest(request) },
            scheduleName = null
        )
    }

    suspend fun enqueueRestore(request: RestoreWorkRequest): Boolean {
        val store = checkNotNull(requestStore) { "OperationRequestStore is required to enqueue work" }
        return enqueueOperation(
            operationType = OperationType.RESTORE,
            requestIdProvider = { store.saveRestoreRequest(request) },
            scheduleName = null
        )
    }

    suspend fun enqueueRunTasks(request: RunTasksWorkRequest): Boolean {
        val store = checkNotNull(requestStore) { "OperationRequestStore is required to enqueue work" }
        return enqueueOperation(
            operationType = OperationType.RUN_TASKS,
            requestIdProvider = { store.saveRunTasksRequest(request) },
            scheduleName = request.scheduleName
        )
    }

    suspend fun enqueueMaintenance(request: MaintenanceWorkRequest): Boolean {
        val store = checkNotNull(requestStore) { "OperationRequestStore is required to enqueue work" }
        return enqueueOperation(
            operationType = OperationType.MAINTENANCE,
            requestIdProvider = { store.saveMaintenanceRequest(request) },
            scheduleName = null
        )
    }

    fun clearFinished(operationType: OperationType? = null) {
        runtimeRepository.clearFinished(operationType)
    }

    suspend fun reconcileStateWithWorkManager() {
        if (!hasActiveWork()) {
            val stale = runtimeRepository.getStaleRunningState() ?: return
            logInterruptedOperation(stale, isStartupRecovery = true)
            runtimeRepository.clearStaleRunningState()
        }
    }

    fun cancelCurrentOperation() {
        val current = runtimeRepository.state.value
        val operationType = current.operationType ?: return
        if (!current.isRunning && !current.stopRequested) return

        runtimeRepository.markStopRequested(
            operationType = operationType,
            progress = current.progress.copy(
                stageTitle = strings.progressStopping,
                isFinished = false
            ),
            updatedTimeMillis = currentTimeProvider()
        )

        repositoryScope.launch {
            cancelUnderlyingWork()
            finalizeCancellationIfNeeded(operationType)
        }
    }

    fun forceStopCurrentOperation() {
        repositoryScope.launch {
            enqueueMutex.withLock {
                val stateBeforeCancel = runtimeRepository.state.value
                val wasRunning = stateBeforeCancel.isRunning || stateBeforeCancel.stopRequested
                cancelUnderlyingWork()
                val currentState = runtimeRepository.state.value
                val targetState = if (currentState.operationType != null) currentState else stateBeforeCancel
                if (wasRunning && targetState.operationType != null && targetState.success != true) {
                    val interruptedSummary = strings.operationInterrupted
                    val finishedProgress = targetState.progress.copy(
                        stageTitle = strings.operationFailedTitle(operationLabel(targetState.operationType)),
                        isFinished = true,
                        error = targetState.progress.error ?: interruptedSummary,
                        finalSummary = if (targetState.progress.finalSummary.isNotBlank()) {
                            targetState.progress.finalSummary
                        } else {
                            interruptedSummary
                        }
                    )
                    logInterruptedOperation(
                        targetState.copy(progress = finishedProgress),
                        isStartupRecovery = false
                    )
                }
                runtimeRepository.forceClear()
            }
        }
    }

    private suspend fun cancelUnderlyingWork() {
        if (workCanceller != null) {
            workCanceller.invoke()
            return
        }
        runCatching {
            workManager.cancelUniqueWork(OperationWorkContract.UNIQUE_WORK_NAME).result.get()
        }
        runCatching {
            workManager.cancelAllWorkByTag(OperationWorkContract.TAG_HEAVY_OPERATION).result.get()
        }
        runCatching { Shell.getCachedShell()?.close() }
    }

    private suspend fun finalizeCancellationIfNeeded(operationType: OperationType) {
        var activeWork = hasActiveWork()
        var attempts = 0
        while (activeWork && attempts < CANCEL_FINALIZE_RETRY_COUNT) {
            delay(CANCEL_FINALIZE_RETRY_DELAY_MS)
            activeWork = hasActiveWork()
            attempts += 1
        }

        val state = runtimeRepository.state.value
        if (state.operationType != operationType) return
        if (!state.isRunning && !state.stopRequested) return
        if (state.success == true && state.progress.isFinished) return

        val interruptedSummary = strings.operationInterrupted
        val finishedProgress = state.progress.copy(
            stageTitle = strings.operationFailedTitle(operationLabel(operationType)),
            isFinished = true,
            error = interruptedSummary,
            finalSummary = interruptedSummary
        )
        logInterruptedOperation(state.copy(progress = finishedProgress), isStartupRecovery = false)
        runtimeRepository.markFinished(
            operationType = operationType,
            success = false,
            progress = finishedProgress,
            finishedTimeMillis = currentTimeProvider()
        )
    }

    private fun logInterruptedOperation(state: OperationRuntimeState, isStartupRecovery: Boolean) {
        val opType = state.operationType ?: return
        val now = currentTimeProvider()
        val startTimeMillis = state.startTimeMillis ?: state.lastUpdatedTimeMillis ?: now
        val elapsedFromProgressMs = state.progress.elapsedTime.coerceAtLeast(0L) * 1000L
        val endTimeMillis = if (isStartupRecovery) {
            val estimatedEndFromElapsed = startTimeMillis + elapsedFromProgressMs
            maxOf(state.lastUpdatedTimeMillis ?: startTimeMillis, estimatedEndFromElapsed)
        } else {
            now
        }
        val durationSeconds = ((endTimeMillis - startTimeMillis) / 1000L)
            .coerceAtLeast(state.progress.elapsedTime.coerceAtLeast(0L))
        val interruptedSummary = strings.operationInterrupted
        val stageTitle = state.progress.stageTitle.ifBlank {
            strings.operationFailedTitle(operationLabel(opType))
        }
        val summary = if (state.progress.finalSummary.isNotBlank()) {
            state.progress.finalSummary
        } else {
            state.progress.error ?: interruptedSummary
        }
        val error = state.progress.error ?: interruptedSummary
        val logId = state.runId ?: "stale_${opType.name}_${state.startTimeMillis ?: 0L}"

        operationLogRepository.addLog(
            id = logId,
            operationType = opType,
            scheduleName = state.scheduleName,
            startTimeMillis = startTimeMillis,
            endTimeMillis = endTimeMillis,
            durationSeconds = durationSeconds,
            success = false,
            stageTitle = stageTitle,
            summary = summary,
            error = error
        )
    }

    private fun operationLabel(operationType: OperationType): String {
        return strings.operationLabel(operationType)
    }

    private suspend fun enqueueOperation(
        operationType: OperationType,
        requestIdProvider: () -> String,
        scheduleName: String? = null
    ): Boolean = enqueueMutex.withLock {
        withContext(ioDispatcher) {
            val current = runtimeRepository.state.value
            if (current.isRunning || current.stopRequested || hasActiveWork()) {
                return@withContext false
            }

            val requestId = requestIdProvider()
            val initialProgress = OperationProgress(stageTitle = strings.progressInitializing)
            val startTimeMillis = currentTimeProvider()
            runtimeRepository.markEnqueued(
                operationType = operationType,
                initialProgress = initialProgress,
                startTimeMillis = startTimeMillis,
                scheduleName = scheduleName,
                runId = requestId
            )

            return@withContext try {
                val workRequest = OneTimeWorkRequestBuilder<HeavyOperationWorker>()
                    .setInputData(
                        androidx.work.Data.Builder()
                            .putString(OperationWorkContract.INPUT_OPERATION_TYPE, operationType.name)
                            .putString(OperationWorkContract.INPUT_REQUEST_ID, requestId)
                            .build()
                    )
                    .addTag(OperationWorkContract.TAG_HEAVY_OPERATION)
                    .build()

                workManager.enqueueUniqueWork(
                    OperationWorkContract.UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    workRequest
                )
                true
            } catch (e: Exception) {
                requestStore?.deleteRequest(requestId)
                runtimeRepository.markFinished(
                    operationType,
                    success = false,
                    progress = OperationProgress(
                        isFinished = true,
                        error = e.message ?: strings.errorAlreadyRunning,
                        finalSummary = e.message ?: strings.summaryAlreadyRunning
                    ),
                    finishedTimeMillis = currentTimeProvider()
                )
                false
            }
        }
    }

    private suspend fun hasActiveWork(): Boolean {
        if (activeWorkChecker != null) {
            return activeWorkChecker.invoke()
        }
        return withContext(ioDispatcher) {
            val infos = runCatching {
                workManager.getWorkInfosForUniqueWork(OperationWorkContract.UNIQUE_WORK_NAME).get()
            }.getOrElse {
                if (it is InterruptedException || it is ExecutionException) {
                    return@withContext false
                }
                throw it
            }
            infos.any { info ->
                info.state == WorkInfo.State.ENQUEUED ||
                    info.state == WorkInfo.State.RUNNING ||
                    info.state == WorkInfo.State.BLOCKED
            }
        }
    }

    private companion object {
        const val CANCEL_FINALIZE_RETRY_COUNT = 30
        const val CANCEL_FINALIZE_RETRY_DELAY_MS = 100L
    }
}
