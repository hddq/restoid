package io.github.hddq.restoid.data

import io.github.hddq.restoid.model.TaskPreset
import io.github.hddq.restoid.work.OperationWorkRepository
import io.github.hddq.restoid.work.RunTasksWorkRequest
import io.github.hddq.restoid.work.toWorkRequest
import java.util.UUID

class TaskPresetRepository internal constructor(
    private val metadataRepository: MetadataRepository,
    private val enqueueRunTasks: suspend (RunTasksWorkRequest) -> Boolean
) {
    constructor(
        metadataRepository: MetadataRepository,
        operationWorkRepository: OperationWorkRepository
    ) : this(
        metadataRepository = metadataRepository,
        enqueueRunTasks = operationWorkRepository::enqueueRunTasks
    )

    suspend fun getPresets(repoId: String): List<TaskPreset> {
        return metadataRepository.getTaskPresets(repoId)
    }

    suspend fun getPresetById(repoId: String, presetId: String): TaskPreset? {
        return getPresets(repoId).firstOrNull { it.id == presetId }
    }

    suspend fun savePreset(repoId: String, preset: TaskPreset): TaskPreset {
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
        metadataRepository.saveTaskPresets(repoId, presets)
        return presetToSave
    }

    suspend fun duplicatePreset(repoId: String, presetId: String, copyName: String): TaskPreset? {
        val original = getPresetById(repoId, presetId) ?: return null
        val duplicated = original.copy(
            id = UUID.randomUUID().toString(),
            name = copyName,
            lastRunTimestamp = null
        )
        return savePreset(repoId, duplicated)
    }

    suspend fun deletePreset(repoId: String, presetId: String) {
        val remaining = getPresets(repoId).filterNot { it.id == presetId }
        metadataRepository.saveTaskPresets(repoId, remaining)
    }

    suspend fun updateLastRunTimestamp(
        repoId: String,
        presetId: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val presets = getPresets(repoId).toMutableList()
        val index = presets.indexOfFirst { it.id == presetId }
        if (index != -1) {
            presets[index] = presets[index].copy(lastRunTimestamp = timestamp)
            metadataRepository.saveTaskPresets(repoId, presets)
        }
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
