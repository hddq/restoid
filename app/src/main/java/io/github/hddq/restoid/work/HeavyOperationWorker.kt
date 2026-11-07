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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.math.abs

class HeavyOperationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val app = appContext.applicationContext as RestoidApplication
    private val notificationRepository = app.notificationRepository
    private val emitMonitor = Any()

    override suspend fun doWork(): Result {
        app.repositoriesRepository.loadRepositories()

        val operationType = OperationWorkContract.parseOperationType(
            inputData.getString(OperationWorkContract.INPUT_OPERATION_TYPE)
        ) ?: return Result.failure()

        val requestId = inputData.getString(OperationWorkContract.INPUT_REQUEST_ID)
            ?: return Result.failure()

        val startTimeMillis = System.currentTimeMillis()
        val initialProgress = OperationProgress(stageTitle = applicationContext.getString(R.string.progress_initializing))
        setForeground(createForegroundInfo(operationType, initialProgress))
        app.operationRuntimeRepository.markEnqueued(operationType, initialProgress)
        setProgress(OperationWorkContract.progressToData(operationType, initialProgress))

        var scheduleName: String? = null
        val notifier = WorkerProgressNotifier(operationType)
        val shouldStop = { isStopped || app.operationRuntimeRepository.state.value.stopRequested }

        // Aborts remote-backend runs when the network drops or turns metered
        // (when unmetered was required) for longer than the grace period.
        // Restic retries failed backend operations for up to 15 minutes each
        // with no off switch, so without this a dead link burns CPU/radio
        // indefinitely. Local backends are exempt by design.
        val lossMonitor = NetworkLossMonitor(applicationContext) {
            val state = app.operationRuntimeRepository.state.value
            if (!state.isRunning || state.operationType != operationType || state.stopRequested) {
                return@NetworkLossMonitor
            }
            app.operationRuntimeRepository.markStopRequested(operationType, state.progress)
            runCatching { com.topjohnwu.superuser.Shell.getCachedShell()?.close() }
        }
        fun armLossMonitor(repositoryKey: String?, requireUnmetered: Boolean) {
            val repository = repositoryKey?.let { app.repositoriesRepository.getRepositoryByKey(it) }
            lossMonitor.configure(isRemoteBackend(repository?.backendType), requireUnmetered)
            lossMonitor.start()
        }

        val runResult = try {
            when (operationType) {
                OperationType.BACKUP -> {
                    val request = app.operationRequestStore.loadBackupRequest(requestId)
                    armLossMonitor(request.repositoryKey, request.requireUnmeteredNetwork)
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

                OperationType.RUN_TASKS -> {
                    val request = app.operationRequestStore.loadRunTasksRequest(requestId)
                    scheduleName = request.scheduleName
                    armLossMonitor(request.repositoryKey, request.requireUnmeteredNetwork)
                    val runner = RunTasksOperationRunner(
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
                    armLossMonitor(request.repositoryKey, requireUnmetered = false)
                    val runner = RestoreOperationRunner(
                        context = applicationContext,
                        repositoriesRepository = app.repositoriesRepository,
                        resticBinaryManager = app.resticBinaryManager,
                        resticRepository = app.resticRepository,
                        metadataRepository = app.metadataRepository,
                        operationLockManager = app.operationLockManager,
                        preferencesRepository = app.preferencesRepository
                    )
                    runner.run(request, notifier::onProgress, shouldStop)
                }

                OperationType.MAINTENANCE -> {
                    val request = app.operationRequestStore.loadMaintenanceRequest(requestId)
                    armLossMonitor(request.repositoryKey, requireUnmetered = false)
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
            lossMonitor.stop()
            app.operationRequestStore.deleteRequest(requestId)
        }

        val interruptedSummary = applicationContext.getString(R.string.operation_interrupted)
        val finalResult = when {
            runResult.success -> runResult
            runResult.progress.error == interruptedSummary || runResult.progress.finalSummary == interruptedSummary -> runResult
            isStopped || app.operationRuntimeRepository.state.value.stopRequested -> cancelledRunResult(operationType)
            else -> runResult
        }

        withContext(NonCancellable) {
            if (app.preferencesRepository.hasPendingVerifierRecovery()) {
                RestoreOperationRunner.restorePendingVerifierSettings(app.preferencesRepository)
            }
            notifier.forceNotify(finalResult.progress)
            app.operationRuntimeRepository.markFinished(operationType, finalResult.success, finalResult.progress)

            val endTimeMillis = System.currentTimeMillis()
            val durationSeconds = ((endTimeMillis - startTimeMillis) / 1000).coerceAtLeast(0)
            val logEntry = app.operationLogRepository.addLog(
                operationType = operationType,
                scheduleName = scheduleName,
                startTimeMillis = startTimeMillis,
                endTimeMillis = endTimeMillis,
                durationSeconds = durationSeconds,
                success = finalResult.success,
                stageTitle = finalResult.progress.stageTitle,
                summary = finalResult.progress.finalSummary,
                error = finalResult.progress.error
            )

            val displayName = if (scheduleName != null) {
                applicationContext.getString(R.string.operation_schedule_name, scheduleName)
            } else {
                operationName(operationType)
            }
            notificationRepository.showOperationFinishedNotification(
                displayName,
                finalResult.success,
                finalResult.progress.finalSummary,
                logId = logEntry.id
            )

            runCatching {
                setProgress(OperationWorkContract.progressToData(operationType, finalResult.progress))
            }
        }

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
            OperationType.RUN_TASKS -> applicationContext.getString(R.string.operation_run_tasks)
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
        return ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    }
}
