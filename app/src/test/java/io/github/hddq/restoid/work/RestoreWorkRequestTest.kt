package io.github.hddq.restoid.work

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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

    @Test
    fun minRequiredTargetSdkForInstall_returnsExpectedFloorByAndroidVersion() {
        assertEquals(0, RestoreOperationRunner.minRequiredTargetSdkForInstall(33))
        assertEquals(23, RestoreOperationRunner.minRequiredTargetSdkForInstall(34))
        assertEquals(24, RestoreOperationRunner.minRequiredTargetSdkForInstall(35))
        assertEquals(24, RestoreOperationRunner.minRequiredTargetSdkForInstall(36))
    }

    @Test
    fun orderApkCandidatesForBaseSelection_prioritizesBaseApkBeforeSplits() {
        val files = listOf(
            File("/tmp/app/split_config.arm64_v8a.apk"),
            File("/tmp/app/split_config.xxhdpi.apk"),
            File("/tmp/app/base.apk")
        )

        val ordered = RestoreOperationRunner.orderApkCandidatesForBaseSelection(files)
        assertEquals("base.apk", ordered.first().name)
    }

    @Test
    fun orderApkCandidatesForBaseSelection_prioritizesNonSplitApkWhenBaseNamedDifferently() {
        val files = listOf(
            File("/tmp/app/split_config.en.apk"),
            File("/tmp/app/app-release.apk"),
            File("/tmp/app/split.feature.apk")
        )

        val ordered = RestoreOperationRunner.orderApkCandidatesForBaseSelection(files)
        assertEquals("app-release.apk", ordered.first().name)
    }
}
