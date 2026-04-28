package io.github.hddq.restoid.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.topjohnwu.superuser.Shell
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.OperationRuntimeRepository
import io.github.hddq.restoid.data.OperationRuntimeState
import io.github.hddq.restoid.data.OperationType
import io.github.hddq.restoid.ui.shared.OperationProgress
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

class OperationWorkRepository(
    context: Context,
    private val requestStore: OperationRequestStore,
    private val runtimeRepository: OperationRuntimeRepository
) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val enqueueMutex = Mutex()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val operationState: StateFlow<OperationRuntimeState> = runtimeRepository.state

    suspend fun enqueueBackup(request: BackupWorkRequest): Boolean {
        return enqueueOperation(
            operationType = OperationType.BACKUP,
            requestIdProvider = { requestStore.saveBackupRequest(request) }
        )
    }

    suspend fun enqueueRestore(request: RestoreWorkRequest): Boolean {
        return enqueueOperation(
            operationType = OperationType.RESTORE,
            requestIdProvider = { requestStore.saveRestoreRequest(request) }
        )
    }

    suspend fun enqueueRunTasks(request: RunTasksWorkRequest): Boolean {
        return enqueueOperation(
            operationType = OperationType.RUN_TASKS,
            requestIdProvider = { requestStore.saveRunTasksRequest(request) }
        )
    }

    suspend fun enqueueMaintenance(request: MaintenanceWorkRequest): Boolean {
        return enqueueOperation(
            operationType = OperationType.MAINTENANCE,
            requestIdProvider = { requestStore.saveMaintenanceRequest(request) }
        )
    }

    fun clearFinished(operationType: OperationType? = null) {
        runtimeRepository.clearFinished(operationType)
    }

    suspend fun reconcileStateWithWorkManager() {
        if (!hasActiveWork()) {
            runtimeRepository.clearStaleRunningState()
        }
    }

    fun cancelCurrentOperation() {
        val current = runtimeRepository.state.value
        val operationType = current.operationType ?: return
        if (!current.isRunning) return
        if (current.stopRequested) return

        runtimeRepository.markStopRequested(
            operationType,
            current.progress.copy(
                stageTitle = appContext.getString(R.string.progress_stopping),
                isFinished = false
            )
        )

        repositoryScope.launch {
            workManager.cancelUniqueWork(OperationWorkContract.UNIQUE_WORK_NAME)
            workManager.cancelAllWorkByTag(OperationWorkContract.TAG_HEAVY_OPERATION)
            runCatching { Shell.getCachedShell()?.close() }
            finalizeCancellationIfNeeded(operationType)
        }
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

        val interruptedSummary = appContext.getString(R.string.operation_interrupted)
        runtimeRepository.markFinished(
            operationType = operationType,
            success = false,
            progress = state.progress.copy(
                stageTitle = appContext.getString(
                    R.string.progress_operation_failed,
                    operationLabel(operationType)
                ),
                isFinished = true,
                error = interruptedSummary,
                finalSummary = interruptedSummary
            )
        )
    }

    private fun operationLabel(operationType: OperationType): String {
        return when (operationType) {
            OperationType.BACKUP -> appContext.getString(R.string.operation_backup)
            OperationType.RUN_TASKS -> appContext.getString(R.string.operation_run_tasks)
            OperationType.RESTORE -> appContext.getString(R.string.operation_restore)
            OperationType.MAINTENANCE -> appContext.getString(R.string.operation_maintenance)
        }
    }

    private suspend fun enqueueOperation(
        operationType: OperationType,
        requestIdProvider: () -> String
    ): Boolean = enqueueMutex.withLock {
        withContext(Dispatchers.IO) {
            val current = runtimeRepository.state.value
            if (current.isRunning || current.stopRequested || hasActiveWork()) {
                return@withContext false
            }

            val requestId = requestIdProvider()
            val initialProgress = OperationProgress(stageTitle = appContext.getString(R.string.progress_initializing))
            runtimeRepository.markEnqueued(operationType, initialProgress)

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
                requestStore.deleteRequest(requestId)
                runtimeRepository.markFinished(
                    operationType,
                    success = false,
                    progress = OperationProgress(
                        isFinished = true,
                        error = e.message ?: appContext.getString(R.string.error_operation_already_running),
                        finalSummary = e.message ?: appContext.getString(R.string.summary_operation_already_running)
                    )
                )
                false
            }
        }
    }

    private suspend fun hasActiveWork(): Boolean = withContext(Dispatchers.IO) {
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

    private companion object {
        const val CANCEL_FINALIZE_RETRY_COUNT = 30
        const val CANCEL_FINALIZE_RETRY_DELAY_MS = 100L
    }
}
