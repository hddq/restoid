package io.github.hddq.restoid.data

import io.github.hddq.restoid.model.BackupTag
import io.github.hddq.restoid.model.BackupTagRules
import io.github.hddq.restoid.model.TaskPreset
import io.github.hddq.restoid.work.RunTasksConfig
import io.github.hddq.restoid.work.toWorkRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TagRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createFixture(): Pair<TagRepository, TaskPresetRepository> {
        val metadataRoot = tempFolder.newFolder()
        val metadataRepository = MetadataRepository(metadataRoot)
        val scheduleRepository = ScheduleRepository(
            metadataRepository = metadataRepository,
            repositoriesRepository = null,
            workManagerProvider = null
        )
        val taskPresetRepository = TaskPresetRepository(
            metadataRepository = metadataRepository,
            scheduleRepository = scheduleRepository,
            enqueueRunTasks = { true }
        )
        val tagRepository = TagRepository(
            metadataRepository = metadataRepository,
            taskPresetRepository = taskPresetRepository
        )
        return tagRepository to taskPresetRepository
    }

    @Test
    fun `BackupTagRules validates empty whitespace comma and reserved tags case-insensitively`() {
        assertEquals(BackupTagRules.TagValidationError.EMPTY, BackupTagRules.validateTagName(""))
        assertEquals(BackupTagRules.TagValidationError.EMPTY, BackupTagRules.validateTagName("   "))
        assertEquals(BackupTagRules.TagValidationError.CONTAINS_COMMA, BackupTagRules.validateTagName("a,b"))
        assertEquals(BackupTagRules.TagValidationError.RESERVED, BackupTagRules.validateTagName("restoid"))
        assertEquals(BackupTagRules.TagValidationError.RESERVED, BackupTagRules.validateTagName("BACKUP"))
        assertEquals(BackupTagRules.TagValidationError.RESERVED, BackupTagRules.validateTagName(" Metadata "))
        assertNull(BackupTagRules.validateTagName("  nightly-apps  "))
    }

    @Test
    fun `BackupTagRules filterUserTags and sanitizeBackupTags exclude reserved invalid and duplicates`() {
        assertEquals(
            listOf("work", "photos"),
            BackupTagRules.filterUserTags(listOf("restoid", "BACKUP", "work", " ", "photos", "work", "metadata"))
        )
        assertEquals(
            listOf("work", "photos"),
            BackupTagRules.sanitizeBackupTags(listOf(" work ", "a,b", "restoid", "BACKUP", "", "photos", "work"))
        )
    }

    @Test
    fun `TagRepository create rejects duplicates updates color and deletes tag from presets`() = runTest {
        val (tagRepo, presetRepo) = createFixture()
        val repoId = "repo-1"
        val blue = BackupTagRules.SUGGESTED_COLOR_ARGBS[6]
        val green = BackupTagRules.SUGGESTED_COLOR_ARGBS[3]

        val created = tagRepo.createTag(repoId, "  Work  ", blue)
        assertTrue(created.isSuccess)
        assertEquals(BackupTag(name = "Work", colorArgb = blue), created.getOrNull())

        val duplicate = tagRepo.createTag(repoId, "work", green)
        assertTrue(duplicate.isFailure)

        val updated = tagRepo.updateTagColor(repoId, "Work", green)
        assertTrue(updated.isSuccess)
        assertEquals(BackupTag(name = "Work", colorArgb = green), updated.getOrNull())
        assertEquals(listOf(BackupTag(name = "Work", colorArgb = green)), tagRepo.getTags(repoId))

        presetRepo.savePreset(
            repoId,
            TaskPreset(
                id = "p-1",
                name = "Daily",
                config = RunTasksConfig(backupEnabled = true, backupTags = listOf(" Work ", "personal"))
            )
        )
        presetRepo.savePreset(
            repoId,
            TaskPreset(
                id = "p-2",
                name = "Other",
                config = RunTasksConfig(backupEnabled = true, backupTags = listOf("personal"))
            )
        )

        val deleted = tagRepo.deleteTag(repoId, "Work")
        assertTrue(deleted.isSuccess)
        assertTrue(tagRepo.getTags(repoId).isEmpty())
        assertEquals(listOf("personal"), presetRepo.getPresetById(repoId, "p-1")?.config?.backupTags)
        assertEquals(listOf("personal"), presetRepo.getPresetById(repoId, "p-2")?.config?.backupTags)
    }

    @Test
    fun `RunTasksConfig toWorkRequest sanitizes and preserves backupTags`() {
        val config = RunTasksConfig(
            backupEnabled = true,
            backupTags = listOf("  work ", "restoid", "a,b", "photos", "work", "BACKUP")
        )

        val request = config.toWorkRequest(repositoryKey = "local:/repo", hasRoot = true)

        assertEquals(listOf("work", "photos"), request.backupTags)
    }
}
