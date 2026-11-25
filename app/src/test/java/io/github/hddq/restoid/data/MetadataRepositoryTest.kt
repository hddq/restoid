package io.github.hddq.restoid.data

import io.github.hddq.restoid.model.RestoidMetadata
import io.github.hddq.restoid.model.TaskPreset
import io.github.hddq.restoid.work.RunTasksConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MetadataRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `pruneOrphanedMetadata deletes stale snapshot files and preserves active snapshots schedules and presets`() = runTest {
        val metadataRoot = tempFolder.newFolder("metadata")
        val repository = MetadataRepository(metadataRoot)
        val repoId = "6102bcf8"
        val repoDir = File(metadataRoot, repoId).apply { mkdirs() }

        val activeId1 = "6fb005a58bd12d7c92ffa63efc0d0747c3db013820203aa6480a23dc11111111"
        val activeId2 = "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899"
        val staleId1 = "1111111122222222333333334444444455555555666666667777777788888888"
        val staleId2 = "deadbeefcafebabe1234567890abcdef1234567890abcdef1234567890abcdef"

        val sampleMetadata = RestoidMetadata(apps = emptyMap(), customDirectories = emptyMap())
        repository.saveMetadataForSnapshot(repoId, activeId1, sampleMetadata)
        repository.saveMetadataForSnapshot(repoId, activeId2, sampleMetadata)
        repository.saveMetadataForSnapshot(repoId, staleId1, sampleMetadata)
        repository.saveMetadataForSnapshot(repoId, staleId2, sampleMetadata)
        File(repoDir, MetadataRepository.SCHEDULES_FILE_NAME).writeText("[]")
        File(repoDir, MetadataRepository.PRESETS_FILE_NAME).writeText("[]")
        File(repoDir, "notes.json").writeText("{}")

        val deleted = repository.pruneOrphanedMetadata(repoId, setOf(activeId1, activeId2))

        assertEquals(2, deleted)
        assertTrue(File(repoDir, "$activeId1.json").exists())
        assertTrue(File(repoDir, "$activeId2.json").exists())
        assertFalse(File(repoDir, "$staleId1.json").exists())
        assertFalse(File(repoDir, "$staleId2.json").exists())
        assertTrue(File(repoDir, MetadataRepository.SCHEDULES_FILE_NAME).exists())
        assertTrue(File(repoDir, MetadataRepository.PRESETS_FILE_NAME).exists())
        assertTrue(File(repoDir, "notes.json").exists())
    }

    @Test
    fun `getAllMetadata excludes schedules json and presets json`() = runTest {
        val metadataRoot = tempFolder.newFolder("metadata")
        val repository = MetadataRepository(metadataRoot)
        val repoId = "6102bcf8"
        val snapshotId = "6fb005a58bd12d7c92ffa63efc0d0747c3db013820203aa6480a23dc11111111"

        val sampleMetadata = RestoidMetadata(apps = emptyMap(), customDirectories = emptyMap())
        repository.saveMetadataForSnapshot(repoId, snapshotId, sampleMetadata)
        repository.saveTaskPresets(
            repoId,
            listOf(TaskPreset(id = "p1", name = "Daily", config = RunTasksConfig(checkRepo = true)))
        )
        repository.saveSchedules(repoId, emptyList())

        val allMetadata = repository.getAllMetadata(repoId)

        assertEquals(setOf(snapshotId), allMetadata.keys)
    }

    @Test
    fun `saveTaskPresets and getTaskPresets round-trip atomically without leaving temp files`() = runTest {
        val metadataRoot = tempFolder.newFolder("metadata")
        val repository = MetadataRepository(metadataRoot)
        val repoId = "6102bcf8"

        val presets = listOf(
            TaskPreset(
                id = "preset-1",
                name = "Nightly Full",
                config = RunTasksConfig(backupEnabled = true, selectedPackageNames = listOf("com.foo.bar"), pruneRepo = true),
                lastRunTimestamp = 123456L
            ),
            TaskPreset(
                id = "preset-2",
                name = "Weekly Check",
                config = RunTasksConfig(backupEnabled = false, checkRepo = true, readData = true)
            )
        )

        assertTrue(repository.saveTaskPresets(repoId, presets))
        assertEquals(presets, repository.getTaskPresets(repoId))

        val repoDir = File(metadataRoot, repoId)
        val files = repoDir.listFiles()?.map { it.name }?.sorted().orEmpty()
        assertEquals(listOf(MetadataRepository.PRESETS_FILE_NAME), files)
    }

    @Test
    fun `pruneOrphanedMetadata returns zero when repo directory does not exist`() = runTest {
        val metadataRoot = tempFolder.newFolder("metadata")
        val repository = MetadataRepository(metadataRoot)

        val deleted = repository.pruneOrphanedMetadata("nonexistent", setOf("abcd1234"))

        assertEquals(0, deleted)
    }

    @Test
    fun `saveMetadataForSnapshot atomically replaces existing metadata without leaving temp files`() = runTest {
        val metadataRoot = tempFolder.newFolder("metadata")
        val repository = MetadataRepository(metadataRoot)
        val repoId = "6102bcf8"
        val snapshotId = "6fb005a58bd12d7c92ffa63efc0d0747c3db013820203aa6480a23dc11111111"

        val initial = RestoidMetadata(apps = emptyMap(), customDirectories = emptyMap(), deviceData = null)
        val updated = RestoidMetadata(
            apps = emptyMap(),
            customDirectories = emptyMap(),
            deviceData = io.github.hddq.restoid.model.DeviceDataMetadata(
                contacts = io.github.hddq.restoid.model.ContactsBackupMetadata(
                    count = 5,
                    size = 1024L,
                    path = "/staging/contacts"
                )
            )
        )

        assertTrue(repository.saveMetadataForSnapshot(repoId, snapshotId, initial))
        assertTrue(repository.saveMetadataForSnapshot(repoId, snapshotId, updated))
        assertEquals(updated, repository.getMetadataForSnapshot(repoId, snapshotId))

        val repoDir = File(metadataRoot, repoId)
        val files = repoDir.listFiles()?.map { it.name }?.sorted().orEmpty()
        assertEquals(listOf("$snapshotId.json"), files)
    }
}
