package io.github.hddq.restoid.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.OperationRuntimeRepository
import io.github.hddq.restoid.data.OperationRuntimeState
import io.github.hddq.restoid.data.OperationType
import io.github.hddq.restoid.ui.shared.OperationProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class OperationWorkRepository(
    context: Context,
    private val requestStore: OperationRequestStore,
    private val runtimeRepository: OperationRuntimeRepository
) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val enqueueMutex = Mutex()

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

    private suspend fun enqueueOperation(
        operationType: OperationType,
        requestIdProvider: () -> String
    ): Boolean = enqueueMutex.withLock {
        withContext(Dispatchers.IO) {
            if (hasActiveWork()) {
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
        val infos = workManager.getWorkInfosForUniqueWork(OperationWorkContract.UNIQUE_WORK_NAME).get()
        infos.any { info ->
            info.state == WorkInfo.State.ENQUEUED ||
                info.state == WorkInfo.State.RUNNING ||
                info.state == WorkInfo.State.BLOCKED
        }
    }
}
