package io.github.hddq.restoid.work

import kotlinx.serialization.Serializable

@Serializable
data class BackupTypeSelection(
    val apk: Boolean = true,
    val data: Boolean = true,
    val deviceProtectedData: Boolean = true,
    val externalData: Boolean = false,
    val obb: Boolean = false,
    val media: Boolean = false
)

@Serializable
data class RestoreTypeSelection(
    val apk: Boolean = true,
    val data: Boolean = true,
    val deviceProtectedData: Boolean = true,
    val externalData: Boolean = false,
    val obb: Boolean = false,
    val media: Boolean = false
)

@Serializable
data class RestoreAppSelection(
    val packageName: String,
    val appName: String
)

@Serializable
data class BackupWorkRequest(
    val repositoryKey: String,
    val backupTypes: BackupTypeSelection,
    val selectedPackageNames: List<String>
)

@Serializable
data class RestoreWorkRequest(
    val repositoryKey: String,
    val snapshotId: String,
    val restoreTypes: RestoreTypeSelection,
    val allowDowngrade: Boolean,
    val selectedApps: List<RestoreAppSelection>
)

@Serializable
data class MaintenanceWorkRequest(
    val repositoryKey: String,
    val checkRepo: Boolean,
    val pruneRepo: Boolean,
    val unlockRepo: Boolean,
    val readData: Boolean,
    val forgetSnapshots: Boolean,
    val keepLast: Int,
    val keepDaily: Int,
    val keepWeekly: Int,
    val keepMonthly: Int
)
