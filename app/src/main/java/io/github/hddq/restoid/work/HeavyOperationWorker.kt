package io.github.hddq.restoid.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import io.github.hddq.restoid.R
import io.github.hddq.restoid.RestoidApplication
import io.github.hddq.restoid.data.NotificationRepository
import io.github.hddq.restoid.data.OperationType
import io.github.hddq.restoid.ui.shared.OperationProgress
import kotlinx.coroutines.CancellationException
import kotlin.math.abs

class HeavyOperationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val app = appContext.applicationContext as RestoidApplication
    private val notificationRepository = app.notificationRepository
    private val emitMonitor = Any()

    override suspend fun doWork(): Result {
        val operationType = OperationWorkContract.parseOperationType(
            inputData.getString(OperationWorkContract.INPUT_OPERATION_TYPE)
        ) ?: return Result.failure()

        val requestId = inputData.getString(OperationWorkContract.INPUT_REQUEST_ID)
            ?: return Result.failure()

        val initialProgress = OperationProgress(stageTitle = applicationContext.getString(R.string.progress_initializing))
        setForeground(createForegroundInfo(operationType, initialProgress))
        app.operationRuntimeRepository.markEnqueued(operationType, initialProgress)
        setProgress(OperationWorkContract.progressToData(operationType, initialProgress))

        val notifier = WorkerProgressNotifier(operationType)
        val shouldStop = { isStopped || app.operationRuntimeRepository.state.value.stopRequested }

        val runResult = try {
            when (operationType) {
                OperationType.BACKUP -> {
                    val request = app.operationRequestStore.loadBackupRequest(requestId)
                    val runner = BackupOperationRunner(
                        context = applicationContext,
                        repositoriesRepository = app.repositoriesRepository,
                        resticBinaryManager = app.resticBinaryManager,
                        resticRepository = app.resticRepository,
                        appInfoRepository = app.appInfoRepository,
                        operationLockManager = app.operationLockManager
                    )
                    runner.run(request, notifier::onProgress, shouldStop)
                }

                OperationType.RESTORE -> {
                    val request = app.operationRequestStore.loadRestoreRequest(requestId)
                    val runner = RestoreOperationRunner(
                        context = applicationContext,
                        repositoriesRepository = app.repositoriesRepository,
                        resticBinaryManager = app.resticBinaryManager,
                        resticRepository = app.resticRepository,
                        metadataRepository = app.metadataRepository,
                        operationLockManager = app.operationLockManager
                    )
                    runner.run(request, notifier::onProgress, shouldStop)
                }

                OperationType.MAINTENANCE -> {
                    val request = app.operationRequestStore.loadMaintenanceRequest(requestId)
                    val runner = MaintenanceOperationRunner(
                        context = applicationContext,
                        repositoriesRepository = app.repositoriesRepository,
                        resticBinaryManager = app.resticBinaryManager,
                        resticRepository = app.resticRepository,
                        operationLockManager = app.operationLockManager
                    )
                    runner.run(request, notifier::onProgress, shouldStop)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException || isStopped) {
                cancelledRunResult(operationType)
            } else {
                val failedProgress = OperationProgress(
                    stageTitle = applicationContext.getString(R.string.progress_operation_failed, operationName(operationType)),
                    isFinished = true,
                    error = applicationContext.getString(R.string.error_fatal_with_message, e.message ?: ""),
                    finalSummary = applicationContext.getString(R.string.error_fatal_with_message, e.message ?: "")
                )
                OperationRunResult(success = false, progress = failedProgress)
            }
        } finally {
            app.operationRequestStore.deleteRequest(requestId)
        }

        val interruptedSummary = applicationContext.getString(R.string.operation_interrupted)
        val finalResult = when {
            runResult.success -> runResult
            runResult.progress.error == interruptedSummary || runResult.progress.finalSummary == interruptedSummary -> runResult
            isStopped || app.operationRuntimeRepository.state.value.stopRequested -> cancelledRunResult(operationType)
            else -> runResult
        }

        notifier.forceNotify(finalResult.progress)
        app.operationRuntimeRepository.markFinished(operationType, finalResult.success, finalResult.progress)
        setProgress(OperationWorkContract.progressToData(operationType, finalResult.progress))

        notificationRepository.showOperationFinishedNotification(
            operationName(operationType),
            finalResult.success,
            finalResult.progress.finalSummary
        )

        val output = OperationWorkContract.outputToData(operationType, finalResult.success, finalResult.progress)
        return if (finalResult.success) Result.success(output) else Result.failure(output)
    }

    private fun cancelledRunResult(operationType: OperationType): OperationRunResult {
        val summary = applicationContext.getString(R.string.operation_interrupted)
        return OperationRunResult(
            success = false,
            progress = OperationProgress(
                stageTitle = applicationContext.getString(
                    R.string.progress_operation_failed,
                    operationName(operationType)
                ),
                isFinished = true,
                error = summary,
                finalSummary = summary
            )
        )
    }

    private fun operationName(operationType: OperationType): String {
        return when (operationType) {
            OperationType.BACKUP -> applicationContext.getString(R.string.operation_backup)
            OperationType.RESTORE -> applicationContext.getString(R.string.operation_restore)
            OperationType.MAINTENANCE -> applicationContext.getString(R.string.operation_maintenance)
        }
    }

    private fun createForegroundInfo(operationType: OperationType, progress: OperationProgress): ForegroundInfo {
        val notification = notificationRepository.buildOperationProgressNotification(
            operationName(operationType),
            progress
        )
        return ForegroundInfo(
            NotificationRepository.PROGRESS_NOTIFICATION_ID,
            notification,
            foregroundServiceTypeDataSync()
        )
    }

    private inner class WorkerProgressNotifier(
        private val operationType: OperationType
    ) {
        private var lastUpdateAtMs: Long = 0L
        private var lastOverallPercentage: Float = -1f
        private var lastStageTitle: String = ""

        fun onProgress(progress: OperationProgress) {
            if (!shouldEmit(progress)) return
            emit(progress)
        }

        fun forceNotify(progress: OperationProgress) {
            emit(progress)
        }

        private fun emit(progress: OperationProgress) {
            synchronized(emitMonitor) {
                lastUpdateAtMs = System.currentTimeMillis()
                lastOverallPercentage = progress.overallPercentage
                lastStageTitle = progress.stageTitle

                app.operationRuntimeRepository.markProgress(operationType, progress)
                setForegroundAsync(createForegroundInfo(operationType, progress))
                setProgressAsync(OperationWorkContract.progressToData(operationType, progress))
            }
        }

        private fun shouldEmit(progress: OperationProgress): Boolean {
            if (progress.isFinished) return true
            val now = System.currentTimeMillis()
            if (now - lastUpdateAtMs >= PROGRESS_UPDATE_MIN_INTERVAL_MS) return true
            if (abs(progress.overallPercentage - lastOverallPercentage) >= PROGRESS_DELTA_THRESHOLD) return true
            if (progress.stageTitle != lastStageTitle) return true
            return false
        }
    }

    private companion object {
        const val PROGRESS_UPDATE_MIN_INTERVAL_MS = 1000L
        const val PROGRESS_DELTA_THRESHOLD = 0.02f
    }

    @Suppress("DEPRECATION")
    private fun foregroundServiceTypeDataSync(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
    }
}
