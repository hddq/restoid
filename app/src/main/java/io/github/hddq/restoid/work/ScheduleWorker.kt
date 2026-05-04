package io.github.hddq.restoid.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.hddq.restoid.RestoidApplication
import io.github.hddq.restoid.model.Schedule

class ScheduleWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as RestoidApplication
        val repoKey = inputData.getString(KEY_REPO_KEY) ?: return Result.failure()
        val scheduleId = inputData.getString(KEY_SCHEDULE_ID) ?: return Result.failure()

        val repository = app.repositoriesRepository.getRepositoryByKey(repoKey) ?: return Result.failure()
        val repoId = repository.id ?: return Result.failure()

        val schedules = app.metadataRepository.getSchedules(repoId).toMutableList()
        val scheduleIndex = schedules.indexOfFirst { it.id == scheduleId }
        if (scheduleIndex == -1) return Result.failure()

        val schedule = schedules[scheduleIndex]
        if (!schedule.isEnabled) return Result.success()

        // Trigger the run tasks operation
        val enqueued = app.operationWorkRepository.enqueueRunTasks(schedule.config)

        if (enqueued) {
            // Update last run timestamp
            val updatedSchedule = schedule.copy(lastRunTimestamp = System.currentTimeMillis())
            schedules[scheduleIndex] = updatedSchedule
            app.metadataRepository.saveSchedules(repoId, schedules)
        }

        return Result.success()
    }

    companion object {
        const val KEY_REPO_KEY = "repo_key"
        const val KEY_SCHEDULE_ID = "schedule_id"
    }
}
