package io.github.hddq.restoid.work

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import io.github.hddq.restoid.R
import io.github.hddq.restoid.RestoidApplication
import io.github.hddq.restoid.ui.shared.OperationProgress

class ScheduleWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val app = applicationContext as RestoidApplication

    override suspend fun doWork(): Result {
        app.repositoriesRepository.loadRepositories()

        // Show an initial foreground notification for the schedule worker itself
        val initialProgress = OperationProgress(stageTitle = applicationContext.getString(R.string.progress_initializing))
        setForeground(createForegroundInfo(initialProgress))

        val repoKey = inputData.getString(KEY_REPO_KEY) ?: return Result.failure()
        val scheduleId = inputData.getString(KEY_SCHEDULE_ID) ?: return Result.failure()

        val repository = app.repositoriesRepository.getRepositoryByKey(repoKey) ?: return Result.failure()
        val repoId = repository.id ?: return Result.failure()

        val schedules = app.metadataRepository.getSchedules(repoId).toMutableList()
        val scheduleIndex = schedules.indexOfFirst { it.id == scheduleId }
        if (scheduleIndex == -1) return Result.failure()

        val schedule = schedules[scheduleIndex]
        val forceRun = inputData.getBoolean(KEY_FORCE_RUN, false)
        if (!schedule.isEnabled && !forceRun) return Result.success()

        val preset = app.metadataRepository.getTaskPresets(repoId)
            .firstOrNull { it.id == schedule.presetId }
            ?: return Result.failure()

        val effectiveScheduleName = schedule.name?.takeIf { it.isNotBlank() } ?: preset.name
        val workRequest = preset.config.toWorkRequest(
            repositoryKey = repoKey,
            hasRoot = true,
            presetName = preset.name,
            scheduleName = effectiveScheduleName,
            requireUnmeteredNetwork = schedule.triggerConditions.requireUnmeteredNetwork
        )
        val enqueued = app.operationWorkRepository.enqueueRunTasks(workRequest)
        if (!enqueued) {
            return Result.retry()
        }

        // Update last run timestamp on both schedule and linked preset
        val now = System.currentTimeMillis()
        app.scheduleRepository.updateLastRunTimestamp(repoId, schedule.id, now)
        app.taskPresetRepository.updateLastRunTimestamp(repoId, preset.id, now)

        return Result.success()
    }

    private fun createForegroundInfo(progress: OperationProgress): ForegroundInfo {
        val notification = app.notificationRepository.buildOperationProgressNotification(
            applicationContext.getString(R.string.operation_run_tasks),
            progress
        )
        return ForegroundInfo(
            io.github.hddq.restoid.data.NotificationRepository.PROGRESS_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    companion object {
        const val KEY_REPO_KEY = "repo_key"
        const val KEY_SCHEDULE_ID = "schedule_id"
        const val KEY_FORCE_RUN = "force_run"
    }
}
