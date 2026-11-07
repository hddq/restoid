package io.github.hddq.restoid.model

import io.github.hddq.restoid.work.BackupTypeSelection
import io.github.hddq.restoid.work.BackupWorkRequest
import io.github.hddq.restoid.work.RestoreTypeSelection
import io.github.hddq.restoid.work.RestoreWorkRequest
import io.github.hddq.restoid.work.RunTasksConfig
import io.github.hddq.restoid.work.RunTasksWorkRequest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomDirectoryTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun customDirectory_defaults() {
        val dir = CustomDirectory(uri = "/storage/emulated/0/Documents")
        assertEquals("/storage/emulated/0/Documents", dir.uri)
        assertTrue(dir.isSelected)
        assertFalse(dir.isExcluded)
    }

    @Test
    fun customDirectory_excludedCategory() {
        val dir = CustomDirectory(uri = "/storage/emulated/0/Downloads", isSelected = true, isExcluded = true)
        assertTrue(dir.isExcluded)
    }

    @Test
    fun backupWorkRequest_backwardCompatibility_missingExcludedField() {
        // Simulating JSON produced by older app versions without excludedCustomDirectories
        val legacyJson = """
            {
                "repositoryKey": "repo-123",
                "backupTypes": {},
                "selectedPackageNames": ["com.example.app"],
                "customDirectories": ["/sdcard/Music"]
            }
        """.trimIndent()

        val request = json.decodeFromString<BackupWorkRequest>(legacyJson)
        assertEquals(listOf("/sdcard/Music"), request.customDirectories)
        assertTrue(request.excludedCustomDirectories.isEmpty())
    }

    @Test
    fun backupWorkRequest_serializationWithExcludedDirectories() {
        val request = BackupWorkRequest(
            repositoryKey = "repo-123",
            backupTypes = BackupTypeSelection(),
            selectedPackageNames = listOf("com.example.app"),
            customDirectories = listOf("/sdcard/Music"),
            excludedCustomDirectories = listOf("/sdcard/Music/temp")
        )

        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<BackupWorkRequest>(encoded)

        assertEquals(listOf("/sdcard/Music"), decoded.customDirectories)
        assertEquals(listOf("/sdcard/Music/temp"), decoded.excludedCustomDirectories)
    }

    @Test
    fun runTasksConfig_backwardCompatibility_missingExcludedField() {
        val legacyJson = """
            {
                "backupEnabled": true,
                "backupTypes": {},
                "selectedPackageNames": [],
                "customDirectories": ["/sdcard/DCIM"],
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

        val config = json.decodeFromString<RunTasksConfig>(legacyJson)
        assertEquals(listOf("/sdcard/DCIM"), config.customDirectories)
        assertTrue(config.excludedCustomDirectories.isEmpty())
    }

    @Test
    fun runTasksConfig_serializationWithExcludedDirectories() {
        val config = RunTasksConfig(
            backupEnabled = true,
            backupTypes = BackupTypeSelection(),
            selectedPackageNames = emptyList(),
            customDirectories = listOf("/sdcard/DCIM"),
            excludedCustomDirectories = listOf("/sdcard/DCIM/.thumbnails"),
            unlockRepo = false,
            forgetSnapshots = false,
            pruneRepo = false,
            checkRepo = false,
            readData = false,
            keepLast = 1,
            keepDaily = 1,
            keepWeekly = 1,
            keepMonthly = 1
        )

        val encoded = json.encodeToString(config)
        val decoded = json.decodeFromString<RunTasksConfig>(encoded)

        assertEquals(listOf("/sdcard/DCIM"), decoded.customDirectories)
        assertEquals(listOf("/sdcard/DCIM/.thumbnails"), decoded.excludedCustomDirectories)
    }

    @Test
    fun restoreWorkRequest_serializationWithExcludedDirectories() {
        val request = RestoreWorkRequest(
            repositoryKey = "repo-1",
            snapshotId = "snap-1",
            restoreTypes = RestoreTypeSelection(),
            allowDowngrade = false,
            selectedApps = emptyList(),
            selectedCustomDirectories = listOf("/sdcard/Documents"),
            excludedCustomDirectories = listOf("/sdcard/Documents/cache")
        )

        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<RestoreWorkRequest>(encoded)

        assertEquals(listOf("/sdcard/Documents"), decoded.selectedCustomDirectories)
        assertEquals(listOf("/sdcard/Documents/cache"), decoded.excludedCustomDirectories)
    }
}
