package io.github.hddq.restoid.data

import io.github.hddq.restoid.model.TaskPreset
import io.github.hddq.restoid.work.RunTasksConfig
import io.github.hddq.restoid.work.RunTasksWorkRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TaskPresetRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createPresetRepository(
        enqueueResult: Boolean = true
    ): TaskPresetRepository {
        val metadataRoot = tempFolder.newFolder()
        val metadataRepository = MetadataRepository(metadataRoot)
        return TaskPresetRepository(
            metadataRepository = metadataRepository,
            enqueueRunTasks = { enqueueResult }
        )
    }

    @Test
    fun `savePreset creates updates and preserves existing lastRunTimestamp`() = runTest {
        val presetRepo = createPresetRepository()
        val repoId = "repo-1"

        val initial = TaskPreset(
            id = "p-1",
            name = "Nightly",
            config = RunTasksConfig(backupEnabled = false, checkRepo = true),
            lastRunTimestamp = 1_700_000_000_000L
        )
        presetRepo.savePreset(repoId, initial)

        // Update preset name/config with null lastRunTimestamp -> must preserve original timestamp
        val edited = initial.copy(
            name = "Nightly Updated",
            config = RunTasksConfig(backupEnabled = false, checkRepo = true, pruneRepo = true),
            lastRunTimestamp = null
        )
        val saved = presetRepo.savePreset(repoId, edited)

        assertNotNull(saved)
        assertEquals("Nightly Updated", saved!!.name)
        assertEquals(1_700_000_000_000L, saved.lastRunTimestamp)
        assertTrue(saved.config.pruneRepo)

        val loaded = presetRepo.getPresetById(repoId, "p-1")
        assertEquals(saved, loaded)
    }

    @Test
    fun `duplicatePreset copies config with new ID and cleared lastRunTimestamp`() = runTest {
        val presetRepo = createPresetRepository()
        val repoId = "repo-1"

        val original = TaskPreset(
            id = "p-1",
            name = "Full Backup",
            config = RunTasksConfig(backupEnabled = true, selectedPackageNames = listOf("com.a")),
            lastRunTimestamp = 999_999L
        )
        presetRepo.savePreset(repoId, original)

        val copy = presetRepo.duplicatePreset(repoId, "p-1", "Full Backup (Copy)")

        assertNotNull(copy)
        assertNotEquals(original.id, copy!!.id)
        assertEquals("Full Backup (Copy)", copy.name)
        assertEquals(original.config, copy.config)
        assertNull(copy.lastRunTimestamp)
        assertEquals(2, presetRepo.getPresets(repoId).size)
    }

    @Test
    fun `deletePreset removes target preset and preserves others`() = runTest {
        val presetRepo = createPresetRepository()
        val repoId = "repo-1"

        val presetA = TaskPreset(id = "p-a", name = "Preset A", config = RunTasksConfig(checkRepo = true))
        val presetB = TaskPreset(id = "p-b", name = "Preset B", config = RunTasksConfig(pruneRepo = true))
        presetRepo.savePreset(repoId, presetA)
        presetRepo.savePreset(repoId, presetB)

        presetRepo.deletePreset(repoId, presetA.id)

        assertEquals(listOf(presetB), presetRepo.getPresets(repoId))
    }

    @Test
    fun `runPresetNow enqueues request with presetName and updates lastRunTimestamp only when enqueued`() = runTest {
        val metadataRoot = tempFolder.newFolder()
        val metadataRepository = MetadataRepository(metadataRoot)
        val capturedRequests = mutableListOf<RunTasksWorkRequest>()
        var shouldSucceed = false
        val presetRepo = TaskPresetRepository(
            metadataRepository = metadataRepository,
            enqueueRunTasks = { req ->
                capturedRequests.add(req)
                shouldSucceed
            }
        )

        val repoId = "repo-1"
        val preset = TaskPreset(
            id = "p-run",
            name = "Quick Check",
            config = RunTasksConfig(backupEnabled = false, checkRepo = true)
        )
        presetRepo.savePreset(repoId, preset)

        // When enqueue fails (blocked), lastRunTimestamp stays null
        val blockedResult = presetRepo.runPresetNow("local:/repo", repoId, preset, hasRoot = false)
        assertFalse(blockedResult)
        assertNull(presetRepo.getPresetById(repoId, preset.id)?.lastRunTimestamp)

        // When enqueue succeeds, presetName is attached and lastRunTimestamp is updated
        shouldSucceed = true
        val okResult = presetRepo.runPresetNow("local:/repo", repoId, preset, hasRoot = false)
        assertTrue(okResult)
        assertEquals("Quick Check", capturedRequests.last().presetName)
        assertNull(capturedRequests.last().scheduleName)
        assertNotNull(presetRepo.getPresetById(repoId, preset.id)?.lastRunTimestamp)
    }
}
