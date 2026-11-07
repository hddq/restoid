package io.github.hddq.restoid.work

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreWorkRequestTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun defaultBypassVerificationIsTrue() {
        val request = RestoreWorkRequest(
            repositoryKey = "repo-key",
            snapshotId = "snapshot-123",
            restoreTypes = RestoreTypeSelection(apk = true, data = true),
            allowDowngrade = false,
            selectedApps = emptyList()
        )
        assertTrue(request.bypassVerification)
    }

    @Test
    fun serializationPreservesBypassVerification() {
        val request = RestoreWorkRequest(
            repositoryKey = "repo-key",
            snapshotId = "snapshot-123",
            restoreTypes = RestoreTypeSelection(apk = true, data = true),
            allowDowngrade = false,
            selectedApps = emptyList(),
            bypassVerification = false
        )
        val serialized = json.encodeToString(request)
        val deserialized = json.decodeFromString<RestoreWorkRequest>(serialized)

        assertEquals(false, deserialized.bypassVerification)
    }

    @Test
    fun deserializationDefaultsBypassVerificationToTrueWhenMissing() {
        val jsonWithoutBypass = """
            {
                "repositoryKey": "repo-key",
                "snapshotId": "snapshot-123",
                "restoreTypes": {
                    "apk": true,
                    "data": true
                },
                "allowDowngrade": false,
                "selectedApps": []
            }
        """.trimIndent()

        val deserialized = json.decodeFromString<RestoreWorkRequest>(jsonWithoutBypass)
        assertTrue(deserialized.bypassVerification)
    }
}
