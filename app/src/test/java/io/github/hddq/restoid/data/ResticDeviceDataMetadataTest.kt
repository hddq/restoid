package io.github.hddq.restoid.data

import android.content.ContextWrapper
import io.github.hddq.restoid.model.AppMetadata
import io.github.hddq.restoid.model.CustomDirectoryMetadata
import io.github.hddq.restoid.model.DeviceDataMetadata
import io.github.hddq.restoid.model.RestoidMetadata
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ResticDeviceDataMetadataTest {

    private class FakeContext : ContextWrapper(null)

    private fun createRepository(): ResticRepository {
        val context = FakeContext()
        val binaryManager = ResticBinaryManager(
            context = context,
            nativeLibDirProvider = { File(".") },
            stringResolver = { "unknown" }
        )
        val executor = ResticExecutor(context = context, binaryManager = binaryManager)
        return ResticRepository(context = context, executor = executor)
    }

    @Test
    fun applySnapshotSizesToMetadata_updatesDeviceDataSizesAndPreservesItemCounts() {
        val repository = createRepository()
        val initial = RestoidMetadata(
            apps = mapOf(
                "com.example.app" to AppMetadata(
                    size = 100L,
                    types = listOf("apk"),
                    versionCode = 1L,
                    versionName = "1.0"
                )
            ),
            customDirectories = mapOf(
                "/sdcard/Docs" to CustomDirectoryMetadata(size = 512L, isDirectory = true)
            ),
            deviceData = mapOf(
                "contacts" to DeviceDataMetadata(size = 0L, itemCount = 42),
                "call_history" to DeviceDataMetadata(size = 256L, itemCount = 15)
            )
        )

        val updated = repository.applySnapshotSizesToMetadata(
            metadata = initial,
            appSizes = emptyMap(),
            customDirSizes = emptyMap(),
            deviceDataSizes = mapOf(
                "contacts" to 8192L,
                "calendar" to 4096L,
                "call_history" to 0L
            )
        )

        assertEquals(DeviceDataMetadata(size = 8192L, itemCount = 42), updated.deviceData["contacts"])
        assertEquals(DeviceDataMetadata(size = 256L, itemCount = 15), updated.deviceData["call_history"])
        assertEquals(DeviceDataMetadata(size = 4096L, itemCount = 0), updated.deviceData["calendar"])
        assertEquals(100L, updated.apps["com.example.app"]?.size)
        assertEquals(512L, updated.customDirectories["/sdcard/Docs"]?.size)
    }
}
