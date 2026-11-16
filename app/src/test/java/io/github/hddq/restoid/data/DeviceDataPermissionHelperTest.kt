package io.github.hddq.restoid.data

import android.Manifest
import io.github.hddq.restoid.model.DeviceDataType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDataPermissionHelperTest {

    @Test
    fun requiredPermissions_matchBackupAndRestoreModes() {
        assertEquals(
            listOf(Manifest.permission.READ_CONTACTS),
            DeviceDataPermissionHelper.requiredPermissionsForType(DeviceDataType.CONTACTS, isRestore = false)
        )
        assertEquals(
            listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
            DeviceDataPermissionHelper.requiredPermissionsForType(DeviceDataType.CONTACTS, isRestore = true)
        )
        assertEquals(
            listOf(Manifest.permission.READ_CALL_LOG),
            DeviceDataPermissionHelper.requiredPermissionsForType(DeviceDataType.CALL_HISTORY, isRestore = false)
        )
        assertEquals(
            listOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG),
            DeviceDataPermissionHelper.requiredPermissionsForType(DeviceDataType.CALL_HISTORY, isRestore = true)
        )
        assertEquals(
            listOf(Manifest.permission.READ_CALENDAR),
            DeviceDataPermissionHelper.requiredPermissionsForType(DeviceDataType.CALENDAR, isRestore = false)
        )
        assertEquals(
            listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
            DeviceDataPermissionHelper.requiredPermissionsForType(DeviceDataType.CALENDAR, isRestore = true)
        )
    }

    @Test
    fun selectPermissionsToPrompt_returnsEmptyWhenAllMissingWereDenied() {
        val missing = listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALENDAR
        )
        assertEquals(
            missing,
            DeviceDataPermissionHelper.selectPermissionsToPrompt(missing, emptySet())
        )
        assertEquals(
            listOf(Manifest.permission.READ_CALENDAR),
            DeviceDataPermissionHelper.selectPermissionsToPrompt(
                missing,
                setOf(Manifest.permission.READ_CONTACTS)
            )
        )
        assertTrue(
            DeviceDataPermissionHelper.selectPermissionsToPrompt(
                missing,
                missing.toSet()
            ).isEmpty()
        )
    }
}
