package io.github.hddq.restoid.work

import io.github.hddq.restoid.util.buildResticExcludeFlags
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcludedCustomDirectoriesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun backupWorkRequest_serializesAndDeserializesWithExclusions() {
        val request = BackupWorkRequest(
            repositoryKey = "local:/repo",
            backupTypes = BackupTypeSelection(),
            selectedPackageNames = listOf("com.example.app"),
            customDirectories = listOf("/storage/emulated/0/DCIM"),
            excludedCustomDirectories = listOf("/storage/emulated/0/DCIM/Camera2")
        )

        val serialized = json.encodeToString(request)
        val deserialized = json.decodeFromString<BackupWorkRequest>(serialized)

        assertEquals(listOf("/storage/emulated/0/DCIM"), deserialized.customDirectories)
        assertEquals(listOf("/storage/emulated/0/DCIM/Camera2"), deserialized.excludedCustomDirectories)
    }

    @Test
    fun backupWorkRequest_backwardCompatibility_missingExcludedFieldDefaultsToEmpty() {
        val legacyJson = """
            {
                "repositoryKey": "local:/repo",
                "backupTypes": {},
                "selectedPackageNames": ["com.example.app"],
                "customDirectories": ["/storage/emulated/0/DCIM"]
            }
        """.trimIndent()

        val deserialized = json.decodeFromString<BackupWorkRequest>(legacyJson)
        assertEquals(listOf("/storage/emulated/0/DCIM"), deserialized.customDirectories)
        assertTrue(deserialized.excludedCustomDirectories.isEmpty())
    }

    @Test
    fun runTasksConfig_backwardCompatibility_missingExcludedFieldDefaultsToEmpty() {
        val legacyJson = """
            {
                "backupEnabled": true,
                "backupTypes": {},
                "selectedPackageNames": [],
                "customDirectories": ["/storage/emulated/0/DCIM"],
                "unlockRepo": false,
                "forgetSnapshots": false,
                "pruneRepo": false,
                "checkRepo": false,
                "readData": false,
                "keepLast": 0,
                "keepDaily": 0,
                "keepWeekly": 0,
                "keepMonthly": 0
            }
        """.trimIndent()

        val deserialized = json.decodeFromString<RunTasksConfig>(legacyJson)
        assertEquals(listOf("/storage/emulated/0/DCIM"), deserialized.customDirectories)
        assertTrue(deserialized.excludedCustomDirectories.isEmpty())
    }

    @Test
    fun runTasksWorkRequest_serializesAndDeserializesWithExclusions() {
        val request = RunTasksWorkRequest(
            repositoryKey = "local:/repo",
            backupEnabled = true,
            backupTypes = BackupTypeSelection(),
            selectedPackageNames = emptyList(),
            customDirectories = listOf("/storage/emulated/0/DCIM"),
            excludedCustomDirectories = listOf("/storage/emulated/0/DCIM/Camera2"),
            unlockRepo = false,
            forgetSnapshots = false,
            pruneRepo = false,
            checkRepo = false,
            readData = false,
            keepLast = 0,
            keepDaily = 0,
            keepWeekly = 0,
            keepMonthly = 0
        )

        val serialized = json.encodeToString(request)
        val deserialized = json.decodeFromString<RunTasksWorkRequest>(serialized)

        assertEquals(listOf("/storage/emulated/0/DCIM"), deserialized.customDirectories)
        assertEquals(listOf("/storage/emulated/0/DCIM/Camera2"), deserialized.excludedCustomDirectories)
    }

    @Test
    fun resticExcludeFlags_matchesUserDCIMScenario() {
        val excluded = listOf("/storage/emulated/0/DCIM/Camera2")
        val flags = buildResticExcludeFlags(excluded)
        assertEquals("--exclude '/storage/emulated/0/DCIM/Camera2'", flags)
    }
}
