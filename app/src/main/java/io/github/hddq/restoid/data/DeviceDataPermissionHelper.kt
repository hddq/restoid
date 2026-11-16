package io.github.hddq.restoid.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.github.hddq.restoid.model.DeviceDataSelection
import io.github.hddq.restoid.model.DeviceDataType

object DeviceDataPermissionHelper {

    fun requiredBackupPermissions(type: DeviceDataType): List<String> = when (type) {
        DeviceDataType.CONTACTS -> listOf(Manifest.permission.READ_CONTACTS)
        DeviceDataType.CALL_HISTORY -> listOf(Manifest.permission.READ_CALL_LOG)
        DeviceDataType.CALENDAR -> listOf(Manifest.permission.READ_CALENDAR)
    }

    fun requiredRestorePermissions(type: DeviceDataType): List<String> = when (type) {
        DeviceDataType.CONTACTS -> listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
        )
        DeviceDataType.CALL_HISTORY -> listOf(
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG
        )
        DeviceDataType.CALENDAR -> listOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
    }

    fun allRuntimePermissions(): Array<String> = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG,
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )

    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasBackupPermissions(context: Context, type: DeviceDataType): Boolean =
        requiredBackupPermissions(type).all { hasPermission(context, it) }

    fun hasRestorePermissions(context: Context, type: DeviceDataType): Boolean =
        requiredRestorePermissions(type).all { hasPermission(context, it) }

    fun missingBackupPermissions(context: Context, selection: DeviceDataSelection): List<String> =
        selection.selectedTypes()
            .flatMap { requiredBackupPermissions(it) }
            .distinct()
            .filterNot { hasPermission(context, it) }

    fun missingRestorePermissions(context: Context, selection: DeviceDataSelection): List<String> =
        selection.selectedTypes()
            .flatMap { requiredRestorePermissions(it) }
            .distinct()
            .filterNot { hasPermission(context, it) }

    fun missingPermissionsFor(
        context: Context,
        selection: DeviceDataSelection,
        isRestore: Boolean
    ): List<String> = if (isRestore) {
        missingRestorePermissions(context, selection)
    } else {
        missingBackupPermissions(context, selection)
    }

    fun hasPermissionsForType(
        context: Context,
        type: DeviceDataType,
        isRestore: Boolean
    ): Boolean = if (isRestore) {
        hasRestorePermissions(context, type)
    } else {
        hasBackupPermissions(context, type)
    }

    fun requiredPermissionsForType(type: DeviceDataType, isRestore: Boolean): List<String> =
        if (isRestore) requiredRestorePermissions(type) else requiredBackupPermissions(type)

    fun selectPermissionsToPrompt(
        missingPermissions: List<String>,
        deniedPermissions: Set<String>
    ): List<String> = missingPermissions.filterNot { it in deniedPermissions }
}
