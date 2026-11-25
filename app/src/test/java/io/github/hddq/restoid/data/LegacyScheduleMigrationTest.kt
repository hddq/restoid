package io.github.hddq.restoid.data

import io.github.hddq.restoid.model.Schedule
import io.github.hddq.restoid.model.TaskPreset
import io.github.hddq.restoid.model.TriggerConditions
import io.github.hddq.restoid.work.BackupTypeSelection
import io.github.hddq.restoid.work.RunTasksConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

class LegacyScheduleMigrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private fun sampleLegacySchedulesJson(
        id: String = "sched-1",
        name: String = "Nightly Backup",
        intervalHours: Int = 24,
        lastRunTimestamp: Long? = 1700000000000L,
        isEnabled: Boolean = true
    ): String {
        val lastRunField = if (lastRunTimestamp != null) "$lastRunTimestamp" else "null"
        return """
            [
              {
                "id": "$id",
                "name": "$name",
                "intervalHours": $intervalHours,
                "config": {
                  "backupEnabled": true,
                  "backupTypes": {
                    "apk": true,
                    "data": true
                  },
                  "selectedPackageNames": ["com.example.app"],
                  "unlockRepo": false,
                  "forgetSnapshots": true,
                  "pruneRepo": true,
                  "checkRepo": false,
                  "readData": false,
                  "keepLast": 7,
                  "keepDaily": 7,
                  "keepWeekly": 4,
                  "keepMonthly": 6,
                  "forgetAllHosts": false,
                  "forgetHosts": ["Pixel 8"]
                },
                "triggerConditions": {
                  "requireBatteryNotLow": true,
                  "requireCharging": true,
                  "requireUnmeteredNetwork": true
                },
                "lastRunTimestamp": $lastRunField,
                "isEnabled": $isEnabled
              }
            ]
        """.trimIndent()
    }

    @Test
    fun `migrates legacy schedules json to presets and schedules with deterministic preset ID`() = runTest {
        val metadataRoot = tempFolder.newFolder("metadata")
        val repoId = "repo-legacy"
        val repoDir = File(metadataRoot, repoId).apply { mkdirs() }
        File(repoDir, MetadataRepository.SCHEDULES_FILE_NAME).writeText(sampleLegacySchedulesJson())

        val repository = MetadataRepository(metadataRoot)

        val schedules = repository.getSchedules(repoId)
        val presets = repository.getTaskPresets(repoId)

        val expectedPresetId = UUID.nameUUIDFromBytes("legacy_schedule_sched-1".toByteArray(Charsets.UTF_8)).toString()

        assertEquals(1, presets.size)
        val preset = presets.single()
        assertEquals(expectedPresetId, preset.id)
        assertEquals("Nightly Backup", preset.name)
        assertEquals(1700000000000L, preset.lastRunTimestamp)
        assertTrue(preset.config.backupEnabled)
        assertEquals(listOf("com.example.app"), preset.config.selectedPackageNames)
        assertTrue(preset.config.forgetSnapshots)
        assertTrue(preset.config.pruneRepo)
        assertFalse(preset.config.forgetAllHosts)
        assertEquals(listOf("Pixel 8"), preset.config.forgetHosts)

        assertEquals(1, schedules.size)
        val schedule = schedules.single()
        assertEquals("sched-1", schedule.id)
        assertEquals(expectedPresetId, schedule.presetId)
        assertNull(schedule.name)
        assertEquals(24, schedule.intervalHours)
        assertEquals(1700000000000L, schedule.lastRunTimestamp)
        assertTrue(schedule.isEnabled)
        assertEquals(
            TriggerConditions(
                requireBatteryNotLow = true,
                requireCharging = true,
                requireUnmeteredNetwork = true
            ),
            schedule.triggerConditions
        )
    }

    @Test
    fun `migration is idempotent across repeated runs and fresh repository instances`() = runTest {
        val metadataRoot = tempFolder.newFolder("metadata")
        val repoId = "repo-idempotent"
        val repoDir = File(metadataRoot, repoId).apply { mkdirs() }
        File(repoDir, MetadataRepository.SCHEDULES_FILE_NAME).writeText(sampleLegacySchedulesJson())

        val repo1 = MetadataRepository(metadataRoot)
        val firstPresets = repo1.getTaskPresets(repoId)
        val firstSchedules = repo1.getSchedules(repoId)

        // Run migration directly again and read via a brand new MetadataRepository instance
        val migratedAgain = LegacyScheduleMigration.migrateIfNeeded(repoDir, json) { file, text ->
            file.writeText(text)
        }
        assertFalse(migratedAgain)

        val repo2 = MetadataRepository(metadataRoot)
        assertEquals(firstPresets, repo2.getTaskPresets(repoId))
        assertEquals(firstSchedules, repo2.getSchedules(repoId))
    }

    @Test
    fun `partial failure recovery does not duplicate presets if presets json was already written`() = runTest {
        val metadataRoot = tempFolder.newFolder("metadata")
        val repoId = "repo-partial"
        val repoDir = File(metadataRoot, repoId).apply { mkdirs() }

        val expectedPresetId = UUID.nameUUIDFromBytes("legacy_schedule_sched-1".toByteArray(Charsets.UTF_8)).toString()
        val preExistingPreset = TaskPreset(
            id = expectedPresetId,
            name = "Nightly Backup",
            config = RunTasksConfig(
                backupEnabled = true,
                backupTypes = BackupTypeSelection(apk = true, data = true),
                selectedPackageNames = listOf("com.example.app")
            ),
            lastRunTimestamp = 1700000000000L
        )
        val unrelatedPreset = TaskPreset(
            id = "custom-preset-99",
            name = "Weekly Check",
            config = RunTasksConfig(backupEnabled = false, checkRepo = true)
        )
        File(repoDir, MetadataRepository.PRESETS_FILE_NAME).writeText(
            json.encodeToString(listOf(unrelatedPreset, preExistingPreset))
        )
        // Simulate crash before schedules.json was rewritten
        File(repoDir, MetadataRepository.SCHEDULES_FILE_NAME).writeText(sampleLegacySchedulesJson())

        val repository = MetadataRepository(metadataRoot)
        val presets = repository.getTaskPresets(repoId)
        val schedules = repository.getSchedules(repoId)

        assertEquals(2, presets.size)
        assertEquals(1, presets.count { it.id == expectedPresetId })
        assertNotNull(presets.find { it.id == "custom-preset-99" })
        assertEquals(1, schedules.size)
        assertEquals(expectedPresetId, schedules.single().presetId)
        assertNull(schedules.single().name)
    }

    @Test
    fun `new format schedules json is left untouched`() = runTest {
        val metadataRoot = tempFolder.newFolder("metadata")
        val repoId = "repo-v2"
        val repoDir = File(metadataRoot, repoId).apply { mkdirs() }

        val v2Schedule = Schedule(
            id = "new-sched-1",
            presetId = "preset-abc",
            name = "Evening Run",
            intervalHours = 12,
            isEnabled = true
        )
        File(repoDir, MetadataRepository.SCHEDULES_FILE_NAME).writeText(json.encodeToString(listOf(v2Schedule)))

        val migrated = LegacyScheduleMigration.migrateIfNeeded(repoDir, json) { file, text ->
            file.writeText(text)
        }
        assertFalse(migrated)

        val repository = MetadataRepository(metadataRoot)
        val loaded = repository.getSchedules(repoId)
        assertEquals(listOf(v2Schedule), loaded)
        assertTrue(repository.getTaskPresets(repoId).isEmpty())
    }
}
