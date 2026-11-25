package io.github.hddq.restoid.data

import android.content.Context
import androidx.work.*
import io.github.hddq.restoid.model.Schedule
import io.github.hddq.restoid.work.ScheduleWorker
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

class ScheduleRepository internal constructor(
    private val metadataRepository: MetadataRepository,
    private val repositoriesRepository: RepositoriesRepository?,
    private val workManagerProvider: (() -> WorkManager)?,
    private val onCancelWorkOverride: ((String) -> Unit)? = null
) {
    private companion object {
        val mutationMutex = Mutex()
    }
    constructor(
        context: Context,
        metadataRepository: MetadataRepository,
        repositoriesRepository: RepositoriesRepository
    ) : this(
        metadataRepository = metadataRepository,
        repositoriesRepository = repositoriesRepository,
        workManagerProvider = { WorkManager.getInstance(context) },
        onCancelWorkOverride = null
    )

    private val workManager: WorkManager?
        get() = workManagerProvider?.invoke()

    suspend fun getSchedules(repoId: String): List<Schedule> {
        return metadataRepository.getSchedules(repoId)
    }

    suspend fun getSchedulesForPreset(repoId: String, presetId: String): List<Schedule> {
        return getSchedules(repoId).filter { it.presetId == presetId }
    }

    suspend fun saveSchedule(repoKey: String, repoId: String, schedule: Schedule) = mutationMutex.withLock {
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
        if (metadataRepository.saveSchedules(repoId, schedules)) {
            updateWorkManager(repoKey, scheduleToSave)
        }
    }

    suspend fun toggleSchedule(repoKey: String, repoId: String, scheduleId: String, isEnabled: Boolean) = mutationMutex.withLock {
        val schedules = getSchedules(repoId).toMutableList()
        val index = schedules.indexOfFirst { it.id == scheduleId }
        if (index != -1) {
            val updatedSchedule = schedules[index].copy(isEnabled = isEnabled)
            schedules[index] = updatedSchedule
            if (metadataRepository.saveSchedules(repoId, schedules)) {
                updateWorkManager(repoKey, updatedSchedule)
            }
        }
    }

    suspend fun deleteSchedule(repoKey: String, repoId: String, scheduleId: String) = mutationMutex.withLock {
        val schedules = getSchedules(repoId).filter { it.id != scheduleId }
        if (metadataRepository.saveSchedules(repoId, schedules)) {
            cancelWork(scheduleId)
        }
    }

    suspend fun deleteSchedulesForPreset(repoId: String, presetId: String): Int? = mutationMutex.withLock {
        val existing = getSchedules(repoId)
        val (toDelete, remaining) = existing.partition { it.presetId == presetId }
        if (toDelete.isEmpty()) return@withLock 0

        if (!metadataRepository.saveSchedules(repoId, remaining)) {
            return@withLock null
        }
        for (schedule in toDelete) {
            cancelWork(schedule.id)
        }
        toDelete.size
    }

    suspend fun updateLastRunTimestamp(
        repoId: String,
        scheduleId: String,
        timestamp: Long = System.currentTimeMillis()
    ) = mutationMutex.withLock {
        val schedules = getSchedules(repoId).toMutableList()
        val index = schedules.indexOfFirst { it.id == scheduleId }
        if (index != -1) {
            schedules[index] = schedules[index].copy(lastRunTimestamp = timestamp)
            metadataRepository.saveSchedules(repoId, schedules)
        }
    }

    private fun updateWorkManager(repoKey: String, schedule: Schedule) {
        val wm = workManager ?: return
        if (schedule.isEnabled) {
            val networkType = if (schedule.triggerConditions.requireUnmeteredNetwork)
                NetworkType.UNMETERED else NetworkType.NOT_REQUIRED
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

            wm.enqueueUniquePeriodicWork(
                getWorkName(schedule.id),
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        } else {
            cancelWork(schedule.id)
        }
    }

    private fun cancelWork(scheduleId: String) {
        onCancelWorkOverride?.invoke(scheduleId)
        workManager?.cancelUniqueWork(getWorkName(scheduleId))
    }

    private fun getWorkName(scheduleId: String): String = "schedule_$scheduleId"

    fun runNow(repoKey: String, schedule: Schedule) {
        val wm = workManager ?: return
        val workRequest = OneTimeWorkRequestBuilder<ScheduleWorker>()
            .setInputData(
                workDataOf(
                    ScheduleWorker.KEY_REPO_KEY to repoKey,
                    ScheduleWorker.KEY_SCHEDULE_ID to schedule.id,
                    ScheduleWorker.KEY_FORCE_RUN to true
                )
            )
            .build()
        wm.enqueue(workRequest)
    }

    suspend fun reconcileAllSchedules() {
        val reposRepo = repositoriesRepository ?: return
        val reposList = reposRepo.repositories.value
        for (repository in reposList) {
            val repoKey = reposRepo.repositoryKey(repository)
            val repoId = repository.id ?: continue
            // Reading schedules before presets ensures a newly created preset + schedule pair isn't seen as orphaned.
            val schedules = getSchedules(repoId)
            val validPresetIds = metadataRepository.getTaskPresets(repoId)
                .mapTo(mutableSetOf()) { it.id }
            for (schedule in schedules) {
                if (schedule.presetId !in validPresetIds) {
                    cancelWork(schedule.id)
                } else {
                    updateWorkManager(repoKey, schedule)
                }
            }
        }
    }
}
