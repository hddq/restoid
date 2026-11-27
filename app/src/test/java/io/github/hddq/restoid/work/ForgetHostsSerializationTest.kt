package io.github.hddq.restoid.work

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgetHostsSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun runTasksConfig_serializesAndDeserializesForgetHosts() {
        val config = RunTasksConfig(
            backupEnabled = false,
            backupTypes = BackupTypeSelection(),
            selectedPackageNames = emptyList(),
            unlockRepo = false,
            forgetSnapshots = true,
            pruneRepo = true,
            checkRepo = false,
            readData = false,
            keepLast = 5,
            keepDaily = 7,
            keepWeekly = 4,
            keepMonthly = 6,
            forgetAllHosts = false,
            forgetHosts = listOf("Pixel 8", "Galaxy Tab"),
            forgetTags = listOf("daily", "photos")
        )

        val encoded = json.encodeToString(config)
        val decoded = json.decodeFromString<RunTasksConfig>(encoded)

        assertFalse(decoded.forgetAllHosts)
        assertEquals(listOf("Pixel 8", "Galaxy Tab"), decoded.forgetHosts)
        assertEquals(listOf("daily", "photos"), decoded.forgetTags)
    }

    @Test
    fun runTasksConfig_backwardCompatibility_defaultsToAllHosts() {
        val legacyJson = """
            {
                "backupEnabled": false,
                "backupTypes": {},
                "selectedPackageNames": [],
                "unlockRepo": false,
                "forgetSnapshots": true,
                "pruneRepo": false,
                "checkRepo": false,
                "readData": false,
                "keepLast": 5,
                "keepDaily": 7,
                "keepWeekly": 4,
                "keepMonthly": 6
            }
        """.trimIndent()

        val decoded = json.decodeFromString<RunTasksConfig>(legacyJson)

        assertTrue(decoded.forgetAllHosts)
        assertTrue(decoded.forgetHosts.isEmpty())
    }

    @Test
    fun runTasksWorkRequest_backwardCompatibility_defaultsToAllHosts() {
        val legacyJson = """
            {
                "repositoryKey": "local:/repo",
                "backupEnabled": false,
                "backupTypes": {},
                "selectedPackageNames": [],
                "unlockRepo": false,
                "forgetSnapshots": true,
                "pruneRepo": false,
                "checkRepo": false,
                "readData": false,
                "keepLast": 3,
                "keepDaily": 0,
                "keepWeekly": 0,
                "keepMonthly": 0
            }
        """.trimIndent()

        val decoded = json.decodeFromString<RunTasksWorkRequest>(legacyJson)

        assertTrue(decoded.forgetAllHosts)
        assertTrue(decoded.forgetHosts.isEmpty())
    }

    @Test
    fun maintenanceWorkRequest_serializesAndBackwardCompatDefaultsToAllHosts() {
        val request = MaintenanceWorkRequest(
            repositoryKey = "local:/repo",
            checkRepo = false,
            pruneRepo = false,
            unlockRepo = false,
            readData = false,
            forgetSnapshots = true,
            keepLast = 2,
            keepDaily = 0,
            keepWeekly = 0,
            keepMonthly = 0,
            forgetAllHosts = false,
            forgetHosts = listOf("Pixel-8")
        )

        val decoded = json.decodeFromString<MaintenanceWorkRequest>(json.encodeToString(request))
        assertFalse(decoded.forgetAllHosts)
        assertEquals(listOf("Pixel-8"), decoded.forgetHosts)

        val legacyJson = """
            {
                "repositoryKey": "local:/repo",
                "checkRepo": false,
                "pruneRepo": false,
                "unlockRepo": false,
                "readData": false,
                "forgetSnapshots": true,
                "keepLast": 2,
                "keepDaily": 0,
                "keepWeekly": 0,
                "keepMonthly": 0
            }
        """.trimIndent()

        val legacyDecoded = json.decodeFromString<MaintenanceWorkRequest>(legacyJson)
        assertTrue(legacyDecoded.forgetAllHosts)
        assertTrue(legacyDecoded.forgetHosts.isEmpty())
        assertTrue(legacyDecoded.forgetTags.isEmpty())
    }
}
