package io.github.hddq.restoid.data

import io.github.hddq.restoid.model.BackupTag
import io.github.hddq.restoid.model.BackupTagRules
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TagRepository(
    private val metadataRepository: MetadataRepository,
    private val taskPresetRepository: TaskPresetRepository
) {
    private val mutationMutex = Mutex()

    suspend fun getTags(repoId: String): List<BackupTag> {
        return metadataRepository.getTags(repoId)
    }

    suspend fun createTag(repoId: String, name: String, colorArgb: Int): Result<BackupTag> =
        mutationMutex.withLock {
            val validationError = BackupTagRules.validateTagName(name)
            if (validationError != null) {
                return@withLock Result.failure(IllegalArgumentException("Invalid tag name: $validationError"))
            }
            val normalizedName = BackupTagRules.normalizeTagName(name)
            val existing = metadataRepository.getTags(repoId)
            if (existing.any { it.name.equals(normalizedName, ignoreCase = true) }) {
                return@withLock Result.failure(IllegalStateException("Tag already exists: $normalizedName"))
            }
            val created = BackupTag(name = normalizedName, colorArgb = colorArgb)
            val updated = existing + created
            if (!metadataRepository.saveTags(repoId, updated)) {
                return@withLock Result.failure(IllegalStateException("Failed to save tags"))
            }
            Result.success(created)
        }

    suspend fun updateTagColor(repoId: String, name: String, colorArgb: Int): Result<BackupTag> =
        mutationMutex.withLock {
            val normalizedName = BackupTagRules.normalizeTagName(name)
            val existing = metadataRepository.getTags(repoId).toMutableList()
            val index = existing.indexOfFirst { it.name.equals(normalizedName, ignoreCase = true) }
            if (index == -1) {
                return@withLock Result.failure(NoSuchElementException("Tag not found: $normalizedName"))
            }
            val updatedTag = existing[index].copy(colorArgb = colorArgb)
            existing[index] = updatedTag
            if (!metadataRepository.saveTags(repoId, existing)) {
                return@withLock Result.failure(IllegalStateException("Failed to save tags"))
            }
            Result.success(updatedTag)
        }

    suspend fun deleteTag(repoId: String, name: String): Result<Unit> =
        mutationMutex.withLock {
            val normalizedName = BackupTagRules.normalizeTagName(name)
            val existing = metadataRepository.getTags(repoId)
            val remaining = existing.filterNot { it.name.equals(normalizedName, ignoreCase = true) }
            if (remaining.size != existing.size && !metadataRepository.saveTags(repoId, remaining)) {
                return@withLock Result.failure(IllegalStateException("Failed to save tags"))
            }
            if (taskPresetRepository.removeTagFromAllPresets(repoId, normalizedName) == null) {
                return@withLock Result.failure(IllegalStateException("Failed to remove tag from presets"))
            }
            Result.success(Unit)
        }
}
