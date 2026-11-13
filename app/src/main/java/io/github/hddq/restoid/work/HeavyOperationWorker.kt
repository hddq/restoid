package io.github.hddq.restoid.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
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

        val logId = UUID.randomUUID().toString()
        val startTimeMillis = System.currentTimeMillis()
        val startElapsedMs = SystemClock.elapsedRealtime()
        val interruptedSummary = applicationContext.getString(R.string.operation_interrupted)
        var scheduleName: String? = null

        fun saveInProgressLog(
            progress: OperationProgress,
            timestampMillis: Long = System.currentTimeMillis(),
            durationSeconds: Long = ((SystemClock.elapsedRealtime() - startElapsedMs) / 1000L).coerceAtLeast(0L),
            durable: Boolean = false
        ) {
            app.operationLogRepository.saveActiveLog(
                id = logId,
                operationType = operationType,
                scheduleName = scheduleName,
                startTimeMillis = startTimeMillis,
                endTimeMillis = timestampMillis,
                durationSeconds = durationSeconds,
                stageTitle = progress.stageTitle.ifBlank {
                    applicationContext.getString(R.string.progress_initializing)
                },
                summary = interruptedSummary,
                error = interruptedSummary,
                durable = durable
            )
        }

        val initialProgress = OperationProgress(stageTitle = applicationContext.getString(R.string.progress_initializing))
        val notifier = WorkerProgressNotifier(
            startElapsedMs = startElapsedMs,
            initialProgress = initialProgress,
            monitor = emitMonitor,
            monotonicNowMs = { SystemClock.elapsedRealtime() },
            onSaveInProgressLog = { progress, wallTimestampMs, elapsedSeconds ->
                saveInProgressLog(progress, timestampMillis = wallTimestampMs, durationSeconds = elapsedSeconds)
            },
            onMarkRuntimeProgress = { progress, persist ->
                app.operationRuntimeRepository.markProgress(operationType, progress, persist = persist)
            },
            onUpdateForeground = { progress ->
                setForegroundAsync(createForegroundInfo(operationType, progress))
            },
            onSetWorkProgress = { progress ->
                setProgressAsync(OperationWorkContract.progressToData(operationType, progress))
            }
        )
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
            coroutineScope {
                val tickerJob = launch {
                    while (isActive) {
                        delay(PROGRESS_UPDATE_MIN_INTERVAL_MS)
                        notifier.onTimerTick()
                    }
                }
                try {
                    setForeground(createForegroundInfo(operationType, initialProgress))
                    app.operationRuntimeRepository.markEnqueued(operationType, initialProgress)
                    saveInProgressLog(initialProgress, startTimeMillis, durable = true)
                    setProgress(OperationWorkContract.progressToData(operationType, initialProgress))

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
                            if (scheduleName != null) {
                                saveInProgressLog(initialProgress, startTimeMillis, durable = true)
                            }
                            armLossMonitor(request.repositoryKey, request.requireUnmeteredNetwork)
                            val runner = RunTasksOperationRunner(
                                context = applicationContext,
                                repositoriesRepository = app.repositoriesRepository,
                                resticBinaryManager = app.resticBinaryManager,
                                resticRepository = app.resticRepository,
                                appInfoRepository = app.appInfoRepository,
                                metadataRepository = app.metadataRepository,
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
                                operationLockManager = app.operationLockManager
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
                                metadataRepository = app.metadataRepository,
                                operationLockManager = app.operationLockManager
                            )
                            runner.run(request, notifier::onProgress, shouldStop)
                        }
                    }
                } finally {
                    tickerJob.cancel()
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

        val finalResult = when {
            runResult.success -> runResult
            runResult.progress.error == interruptedSummary || runResult.progress.finalSummary == interruptedSummary -> runResult
            isStopped || app.operationRuntimeRepository.state.value.stopRequested -> cancelledRunResult(operationType)
            else -> runResult
        }

        withContext(NonCancellable) {
            notifier.forceNotify(finalResult.progress)
            app.operationRuntimeRepository.markFinished(operationType, finalResult.success, finalResult.progress)

            val endTimeMillis = System.currentTimeMillis()
            val durationSeconds = ((SystemClock.elapsedRealtime() - startElapsedMs) / 1000L).coerceAtLeast(0L)
            val logEntry = app.operationLogRepository.completeActiveLog(
                id = logId,
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

    private companion object {
        const val PROGRESS_UPDATE_MIN_INTERVAL_MS = 1000L
    }

    @Suppress("DEPRECATION")
    private fun foregroundServiceTypeDataSync(): Int {
        return ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    }
}

internal class WorkerProgressNotifier(
    private val startElapsedMs: Long,
    initialProgress: OperationProgress,
    private val monitor: Any = Any(),
    private val monotonicNowMs: () -> Long,
    private val wallNowMs: () -> Long = { System.currentTimeMillis() },
    private val minNotificationIntervalMs: Long = DEFAULT_NOTIFICATION_MIN_INTERVAL_MS,
    private val notificationDeltaThreshold: Float = DEFAULT_NOTIFICATION_DELTA_THRESHOLD,
    private val checkpointIntervalMs: Long = DEFAULT_CHECKPOINT_INTERVAL_MS,
    private val onSaveInProgressLog: (progress: OperationProgress, wallTimestampMs: Long, elapsedSeconds: Long) -> Unit,
    private val onMarkRuntimeProgress: (progress: OperationProgress, persist: Boolean) -> Unit,
    private val onUpdateForeground: (progress: OperationProgress) -> Unit,
    private val onSetWorkProgress: (progress: OperationProgress) -> Unit
) {
    private var hasEmittedNotification: Boolean = false
    private var lastNotificationAtMs: Long = 0L
    private var lastNotificationPercentage: Float = -1f
    private var lastNotificationStageTitle: String = ""
    private var lastCheckpointAtMs: Long = startElapsedMs
    private var latestProgress: OperationProgress = initialProgress
    private var lastEmittedElapsedTime: Long = 0L
    private var isFinished: Boolean = false

    fun onProgress(progress: OperationProgress) {
        synchronized(monitor) {
            if (isFinished) return
            val nowElapsedMs = monotonicNowMs()
            val normalized = normalizeElapsed(progress, nowElapsedMs)
            latestProgress = normalized
            if (!shouldEmitNotificationLocked(normalized, nowElapsedMs)) return
            emitLocked(
                progress = normalized,
                nowElapsedMs = nowElapsedMs,
                isFinal = false,
                updateForeground = true,
                persistCheckpoint = true
            )
        }
    }

    fun onTimerTick() {
        synchronized(monitor) {
            if (isFinished) return
            val nowElapsedMs = monotonicNowMs()
            val elapsedSeconds = ((nowElapsedMs - startElapsedMs) / 1000L)
                .coerceAtLeast(lastEmittedElapsedTime)
            if (elapsedSeconds <= lastEmittedElapsedTime) return
            val ticked = latestProgress.copy(elapsedTime = elapsedSeconds)
            latestProgress = ticked
            val shouldPersistCheckpoint = nowElapsedMs - lastCheckpointAtMs >= checkpointIntervalMs
            // Timer ticks only advance elapsedTime, which isn't shown in the notification,
            // so skip foreground updates and only persist checkpoints periodically.
            emitLocked(
                progress = ticked,
                nowElapsedMs = nowElapsedMs,
                isFinal = false,
                updateForeground = false,
                persistCheckpoint = shouldPersistCheckpoint
            )
        }
    }

    fun forceNotify(progress: OperationProgress) {
        synchronized(monitor) {
            val nowElapsedMs = monotonicNowMs()
            emitLocked(
                progress = progress,
                nowElapsedMs = nowElapsedMs,
                isFinal = true,
                updateForeground = true,
                persistCheckpoint = true
            )
        }
    }

    private fun normalizeElapsed(progress: OperationProgress, nowElapsedMs: Long): OperationProgress {
        val monotonicElapsed = ((nowElapsedMs - startElapsedMs) / 1000L)
            .coerceAtLeast(lastEmittedElapsedTime)
        return if (progress.elapsedTime != monotonicElapsed) {
            progress.copy(elapsedTime = monotonicElapsed)
        } else {
            progress
        }
    }

    private fun emitLocked(
        progress: OperationProgress,
        nowElapsedMs: Long,
        isFinal: Boolean,
        updateForeground: Boolean,
        persistCheckpoint: Boolean
    ) {
        if (isFinished && !isFinal) return
        if (isFinal) {
            isFinished = true
        }

        if (updateForeground) {
            hasEmittedNotification = true
            lastNotificationAtMs = nowElapsedMs
            lastNotificationPercentage = progress.overallPercentage
            lastNotificationStageTitle = progress.stageTitle
        }
        lastEmittedElapsedTime = maxOf(lastEmittedElapsedTime, progress.elapsedTime)

        if (persistCheckpoint) {
            lastCheckpointAtMs = nowElapsedMs
            if (!isFinal && !progress.isFinished) {
                val elapsedSeconds = ((nowElapsedMs - startElapsedMs) / 1000L).coerceAtLeast(0L)
                onSaveInProgressLog(progress, wallNowMs(), elapsedSeconds)
            }
        }

        onMarkRuntimeProgress(progress, persistCheckpoint)
        if (updateForeground) {
            onUpdateForeground(progress)
        }
        if (persistCheckpoint) {
            onSetWorkProgress(progress)
        }
    }

    private fun shouldEmitNotificationLocked(
        progress: OperationProgress,
        nowElapsedMs: Long
    ): Boolean {
        if (progress.isFinished) return true
        if (!hasEmittedNotification) return true
        if (nowElapsedMs - lastNotificationAtMs >= minNotificationIntervalMs) return true
        if (abs(progress.overallPercentage - lastNotificationPercentage) >= notificationDeltaThreshold) return true
        if (progress.stageTitle != lastNotificationStageTitle) return true
        return false
    }

    companion object {
        const val DEFAULT_NOTIFICATION_MIN_INTERVAL_MS = 1000L
        const val DEFAULT_NOTIFICATION_DELTA_THRESHOLD = 0.02f
        const val DEFAULT_CHECKPOINT_INTERVAL_MS = 15_000L
    }
}
