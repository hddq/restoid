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
        if (!schedule.isEnabled) return Result.success()

        val hasRoot = app.capabilityManager.isRootGrantedLive()
        val hasStorageAccess = app.capabilityManager.hasStorageAccessSync()

        val appsConfigured = schedule.config.backupEnabled
        val customDirsSwitchEnabled = schedule.config.customDirectoriesBackupEnabled ?: (schedule.config.customDirectories.isNotEmpty() || schedule.config.excludedCustomDirectories.isNotEmpty())
        val customDirsConfigured = customDirsSwitchEnabled && schedule.config.customDirectories.isNotEmpty()
        val anyBackupConfigured = appsConfigured || customDirsConfigured

        val backupApps = appsConfigured && hasRoot
        val backupCustomDirs = customDirsConfigured && hasStorageAccess
        val customDirsAccess = customDirsSwitchEnabled && hasStorageAccess

        // If a configured backup source was omitted due to missing access (e.g. apps without root or dirs without storage),
        // we must skip destructive maintenance (forget/prune) to avoid deleting old snapshots that contain the omitted source.
        val configuredSourceOmitted = (appsConfigured && !hasRoot) || (customDirsConfigured && !hasStorageAccess)

        val selectedPackageNames: List<String>
        val appBackupTypes: Map<String, BackupTypeSelection>
        val customDirectories: List<String>
        val excludedCustomDirectories = if (customDirsAccess) schedule.config.excludedCustomDirectories else emptyList()
        val backupEnabled: Boolean

        if (anyBackupConfigured && !backupApps && !backupCustomDirs) {
            // Backup was configured, but no configured source can run due to missing permissions.
            // Forward configured sources with backupEnabled = true so BackupOperationRunner preflight fails,
            // correctly reporting the missing permission failure and triggering the failed-backup maintenance guard.
            backupEnabled = true
            selectedPackageNames = if (appsConfigured) schedule.config.selectedPackageNames else emptyList()
            appBackupTypes = if (appsConfigured) schedule.config.appBackupTypes else emptyMap()
            customDirectories = if (customDirsConfigured) schedule.config.customDirectories else emptyList()
        } else {
            backupEnabled = backupApps || backupCustomDirs
            selectedPackageNames = if (backupApps) schedule.config.selectedPackageNames else emptyList()
            appBackupTypes = if (backupApps) schedule.config.appBackupTypes else emptyMap()
            customDirectories = if (backupCustomDirs) schedule.config.customDirectories else emptyList()
        }

        // Trigger the run tasks operation
        val workRequest = RunTasksWorkRequest(
            repositoryKey = repoKey,
            backupEnabled = backupEnabled,
            backupTypes = schedule.config.backupTypes,
            selectedPackageNames = selectedPackageNames,
            appBackupTypes = appBackupTypes,
            customDirectories = customDirectories,
            excludedCustomDirectories = excludedCustomDirectories,
            unlockRepo = schedule.config.unlockRepo,
            forgetSnapshots = if (configuredSourceOmitted) false else schedule.config.forgetSnapshots,
            pruneRepo = if (configuredSourceOmitted) false else schedule.config.pruneRepo,
            checkRepo = schedule.config.checkRepo,
            readData = schedule.config.readData,
            keepLast = schedule.config.keepLast,
            keepDaily = schedule.config.keepDaily,
            keepWeekly = schedule.config.keepWeekly,
            keepMonthly = schedule.config.keepMonthly,
            scheduleName = schedule.name,
            requireUnmeteredNetwork = schedule.triggerConditions.requireUnmeteredNetwork
        )
        val enqueued = app.operationWorkRepository.enqueueRunTasks(workRequest)
        if (!enqueued) {
            return Result.retry()
        }

        // Update last run timestamp
        val updatedSchedule = schedule.copy(lastRunTimestamp = System.currentTimeMillis())
        schedules[scheduleIndex] = updatedSchedule
        app.metadataRepository.saveSchedules(repoId, schedules)

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
    }
}
