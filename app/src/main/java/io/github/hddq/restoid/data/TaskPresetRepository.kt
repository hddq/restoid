package io.github.hddq.restoid.data

import io.github.hddq.restoid.model.BackupTagRules
import io.github.hddq.restoid.model.TaskPreset
import io.github.hddq.restoid.work.OperationWorkRepository
import io.github.hddq.restoid.work.RunTasksWorkRequest
import io.github.hddq.restoid.work.toWorkRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class TaskPresetRepository internal constructor(
    private val metadataRepository: MetadataRepository,
    private val scheduleRepository: ScheduleRepository,
    private val enqueueRunTasks: suspend (RunTasksWorkRequest) -> Boolean
) {
    private val mutationMutex = Mutex()

    constructor(
        metadataRepository: MetadataRepository,
        scheduleRepository: ScheduleRepository,
        operationWorkRepository: OperationWorkRepository
    ) : this(
        metadataRepository = metadataRepository,
        scheduleRepository = scheduleRepository,
        enqueueRunTasks = operationWorkRepository::enqueueRunTasks
    )

    suspend fun getPresets(repoId: String): List<TaskPreset> {
        return metadataRepository.getTaskPresets(repoId)
    }

    suspend fun getPresetById(repoId: String, presetId: String): TaskPreset? {
        return getPresets(repoId).firstOrNull { it.id == presetId }
    }

    suspend fun savePreset(repoId: String, preset: TaskPreset): TaskPreset? = mutationMutex.withLock {
        savePresetLocked(repoId, preset)
    }

    private suspend fun savePresetLocked(repoId: String, preset: TaskPreset): TaskPreset? {
        val presets = getPresets(repoId).toMutableList()
        val index = presets.indexOfFirst { it.id == preset.id }

        val presetToSave = if (index != -1) {
            preset.copy(lastRunTimestamp = presets[index].lastRunTimestamp)
        } else {
            preset
        }

        if (index != -1) {
            presets[index] = presetToSave
        } else {
            presets.add(presetToSave)
        }
        if (!metadataRepository.saveTaskPresets(repoId, presets)) {
            return null
        }
        return presetToSave
    }

    suspend fun duplicatePreset(repoId: String, presetId: String, copyName: String): TaskPreset? = mutationMutex.withLock {
        val original = getPresetById(repoId, presetId) ?: return@withLock null
        val duplicated = original.copy(
            id = UUID.randomUUID().toString(),
            name = copyName,
            lastRunTimestamp = null
        )
        savePresetLocked(repoId, duplicated)
    }

    suspend fun deletePreset(repoId: String, presetId: String): Int? = mutationMutex.withLock {
        val remaining = getPresets(repoId).filterNot { it.id == presetId }
        if (!metadataRepository.saveTaskPresets(repoId, remaining)) {
            return@withLock null
        }
        scheduleRepository.deleteSchedulesForPreset(repoId, presetId) ?: 0
    }

    suspend fun updateLastRunTimestamp(
        repoId: String,
        presetId: String,
        timestamp: Long = System.currentTimeMillis()
    ) = mutationMutex.withLock {
        val presets = getPresets(repoId).toMutableList()
        val index = presets.indexOfFirst { it.id == presetId }
        if (index != -1) {
            presets[index] = presets[index].copy(lastRunTimestamp = timestamp)
            metadataRepository.saveTaskPresets(repoId, presets)
        }
    }

    suspend fun removeTagFromAllPresets(repoId: String, tagName: String): Int? = mutationMutex.withLock {
        val normalizedTagName = BackupTagRules.normalizeTagName(tagName)
        val presets = getPresets(repoId)
        var updatedCount = 0
        val updatedPresets = presets.map { preset ->
            val filteredBackupTags = preset.config.backupTags.filterNot {
                BackupTagRules.normalizeTagName(it).equals(normalizedTagName, ignoreCase = true)
            }
            val filteredForgetTags = preset.config.forgetTags.filterNot {
                BackupTagRules.normalizeTagName(it).equals(normalizedTagName, ignoreCase = true)
            }
            if (filteredBackupTags.size != preset.config.backupTags.size ||
                filteredForgetTags.size != preset.config.forgetTags.size
            ) {
                updatedCount++
                preset.copy(
                    config = preset.config.copy(
                        backupTags = filteredBackupTags,
                        forgetTags = filteredForgetTags
                    )
                )
            } else {
                preset
            }
        }
        if (updatedCount > 0 && !metadataRepository.saveTaskPresets(repoId, updatedPresets)) {
            return@withLock null
        }
        updatedCount
    }

    suspend fun runPresetNow(
        repoKey: String,
        repoId: String,
        preset: TaskPreset,
        hasRoot: Boolean
    ): Boolean {
        val workRequest = preset.config.toWorkRequest(
            repositoryKey = repoKey,
            hasRoot = hasRoot,
            presetName = preset.name
        )
        val enqueued = enqueueRunTasks(workRequest)
        if (enqueued) {
            updateLastRunTimestamp(repoId, preset.id)
        }
        return enqueued
    }
}
