package io.github.hddq.restoid.data

import android.content.Context
import androidx.work.*
import io.github.hddq.restoid.model.Schedule
import io.github.hddq.restoid.work.ScheduleWorker
import java.util.concurrent.TimeUnit

class ScheduleRepository(
    private val context: Context,
    private val metadataRepository: MetadataRepository,
    private val repositoriesRepository: RepositoriesRepository
) {
    private val workManager = WorkManager.getInstance(context)

    suspend fun getSchedules(repoId: String): List<Schedule> {
        return metadataRepository.getSchedules(repoId)
    }

    suspend fun saveSchedule(repoKey: String, repoId: String, schedule: Schedule) {
        val schedules = getSchedules(repoId).toMutableList()
        val index = schedules.indexOfFirst { it.id == schedule.id }
        
        val scheduleToSave = if (index != -1) {
            schedule.copy(lastRunTimestamp = schedules[index].lastRunTimestamp)
        } else {
            schedule
        }

        if (index != -1) {
            schedules[index] = scheduleToSave
        } else {
            schedules.add(scheduleToSave)
        }
        metadataRepository.saveSchedules(repoId, schedules)
        updateWorkManager(repoKey, scheduleToSave)
    }

    suspend fun toggleSchedule(repoKey: String, repoId: String, scheduleId: String, isEnabled: Boolean) {
        val schedules = getSchedules(repoId).toMutableList()
        val index = schedules.indexOfFirst { it.id == scheduleId }
        if (index != -1) {
            val updatedSchedule = schedules[index].copy(isEnabled = isEnabled)
            schedules[index] = updatedSchedule
            metadataRepository.saveSchedules(repoId, schedules)
            updateWorkManager(repoKey, updatedSchedule)
        }
    }

    suspend fun deleteSchedule(repoKey: String, repoId: String, scheduleId: String) {
        val schedules = getSchedules(repoId).filter { it.id != scheduleId }
        metadataRepository.saveSchedules(repoId, schedules)
        cancelWork(scheduleId)
    }

    private fun updateWorkManager(repoKey: String, schedule: Schedule) {
        if (schedule.isEnabled) {
            val networkType = if (schedule.triggerConditions.requireUnmeteredNetwork)
                NetworkType.UNMETERED else NetworkType.CONNECTED
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .setRequiresBatteryNotLow(schedule.triggerConditions.requireBatteryNotLow)
                .setRequiresCharging(schedule.triggerConditions.requireCharging)
                .build()

            var initialDelay = 0L
            if (schedule.lastRunTimestamp != null) {
                val elapsedMillis = System.currentTimeMillis() - schedule.lastRunTimestamp
                val intervalMillis = TimeUnit.HOURS.toMillis(schedule.intervalHours.toLong())
                val remainingMillis = intervalMillis - elapsedMillis
                if (remainingMillis > 0) {
                    initialDelay = remainingMillis
                }
            }

            val workRequest = PeriodicWorkRequestBuilder<ScheduleWorker>(
                schedule.intervalHours.toLong(), TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        ScheduleWorker.KEY_REPO_KEY to repoKey,
                        ScheduleWorker.KEY_SCHEDULE_ID to schedule.id
                    )
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                getWorkName(schedule.id),
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        } else {
            cancelWork(schedule.id)
        }
    }

    private fun cancelWork(scheduleId: String) {
        workManager.cancelUniqueWork(getWorkName(scheduleId))
    }

    private fun getWorkName(scheduleId: String): String = "schedule_$scheduleId"

    fun runNow(repoKey: String, schedule: Schedule) {
        val workRequest = OneTimeWorkRequestBuilder<ScheduleWorker>()
            .setInputData(
                workDataOf(
                    ScheduleWorker.KEY_REPO_KEY to repoKey,
                    ScheduleWorker.KEY_SCHEDULE_ID to schedule.id
                )
            )
            .build()
        workManager.enqueue(workRequest)
    }

    suspend fun reconcileAllSchedules() {
        val reposList = repositoriesRepository.repositories.value
        for (repository in reposList) {
            val repoKey = repositoriesRepository.repositoryKey(repository)
            val repoId = repository.id ?: continue
            val schedules = getSchedules(repoId)
            for (schedule in schedules) {
                updateWorkManager(repoKey, schedule)
            }
        }
    }
}
