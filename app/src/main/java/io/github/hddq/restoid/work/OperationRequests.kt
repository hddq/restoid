package io.github.hddq.restoid.work

import io.github.hddq.restoid.model.BackupTagRules
import kotlinx.serialization.Serializable

@Serializable
data class BackupTypeSelection(
    val apk: Boolean = true,
    val data: Boolean = true,
    val deviceProtectedData: Boolean = true,
    val externalData: Boolean = false,
    val obb: Boolean = false,
    val media: Boolean = false,
    val permissions: Boolean = true,
    val specialAccess: Boolean = true,
    val accessibility: Boolean = true
)

@Serializable
data class RestoreTypeSelection(
    val apk: Boolean = true,
    val data: Boolean = true,
    val deviceProtectedData: Boolean = true,
    val externalData: Boolean = false,
    val obb: Boolean = false,
    val media: Boolean = false,
    val permissions: Boolean = true,
    val specialAccess: Boolean = true,
    val accessibility: Boolean = true
)

@Serializable
data class DeviceDataBackupSelection(
    val contacts: Boolean = false,
    val selectAllContactAccounts: Boolean = true,
    val selectedContactAccountKeys: Set<String> = emptySet(),
    val callLogs: Boolean = false,
    val calendar: Boolean = false,
    val selectAllCalendars: Boolean = true,
    val selectedCalendarKeys: Set<String> = emptySet()
) {
    fun anyEnabled(): Boolean = contacts || callLogs || calendar
}

@Serializable
data class DeviceDataRestoreSelection(
    val contacts: Boolean = false,
    // Backwards compatibility: older serialized restore requests omit these fields and expect all accounts/calendars to be restored.
    val selectAllContactAccounts: Boolean = true,
    val selectedContactAccountKeys: Set<String> = emptySet(),
    val callLogs: Boolean = false,
    val calendar: Boolean = false,
    // Backwards compatibility: older serialized restore requests omit these fields and expect all calendars to be restored.
    val selectAllCalendars: Boolean = true,
    val selectedCalendarKeys: Set<String> = emptySet()
) {
    fun anyEnabled(): Boolean = contacts || callLogs || calendar
}

@Serializable
data class RestoreAppSelection(
    val packageName: String,
    val appName: String
)

@Serializable
data class BackupWorkRequest(
    val repositoryKey: String,
    val backupTypes: BackupTypeSelection,
    val selectedPackageNames: List<String>,
    val selectAllApps: Boolean = false,
    val excludedPackageNames: List<String> = emptyList(),
    val appBackupTypes: Map<String, BackupTypeSelection> = emptyMap(),
    val customDirectories: List<String> = emptyList(),
    val excludedCustomDirectories: List<String> = emptyList(),
    val deviceData: DeviceDataBackupSelection = DeviceDataBackupSelection(),
    val backupTags: List<String> = emptyList(),
    val requireUnmeteredNetwork: Boolean = false
)

@Serializable
data class RestoreWorkRequest(
    val repositoryKey: String,
    val snapshotId: String,
    val restoreTypes: RestoreTypeSelection,
    val allowDowngrade: Boolean,
    val selectedApps: List<RestoreAppSelection>,
    val appRestoreTypes: Map<String, RestoreTypeSelection> = emptyMap(),
    val selectedCustomDirectories: List<String> = emptyList(),
    val deviceData: DeviceDataRestoreSelection = DeviceDataRestoreSelection()
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
    val keepMonthly: Int,
    val forgetAllHosts: Boolean = true,
    val forgetHosts: List<String> = emptyList(),
    val forgetTags: List<String> = emptyList()
)

@Serializable
data class RunTasksConfig(
    val backupEnabled: Boolean = false,
    val backupTypes: BackupTypeSelection = BackupTypeSelection(),
    val selectedPackageNames: List<String> = emptyList(),
    val selectAllApps: Boolean = false,
    val excludedPackageNames: List<String> = emptyList(),
    val appBackupTypes: Map<String, BackupTypeSelection> = emptyMap(),
    val customDirectories: List<String> = emptyList(),
    val excludedCustomDirectories: List<String> = emptyList(),
    val deviceData: DeviceDataBackupSelection = DeviceDataBackupSelection(),
    val backupTags: List<String> = emptyList(),
    val unlockRepo: Boolean = false,
    val forgetSnapshots: Boolean = false,
    val pruneRepo: Boolean = false,
    val checkRepo: Boolean = false,
    val readData: Boolean = false,
    val keepLast: Int = 0,
    val keepDaily: Int = 0,
    val keepWeekly: Int = 0,
    val keepMonthly: Int = 0,
    val forgetAllHosts: Boolean = true,
    val forgetHosts: List<String> = emptyList(),
    val forgetTags: List<String> = emptyList()
)

@Serializable
data class RunTasksWorkRequest(
    val repositoryKey: String,
    val backupEnabled: Boolean,
    val backupTypes: BackupTypeSelection,
    val selectedPackageNames: List<String>,
    val selectAllApps: Boolean = false,
    val excludedPackageNames: List<String> = emptyList(),
    val appBackupTypes: Map<String, BackupTypeSelection> = emptyMap(),
    val customDirectories: List<String> = emptyList(),
    val excludedCustomDirectories: List<String> = emptyList(),
    val deviceData: DeviceDataBackupSelection = DeviceDataBackupSelection(),
    val backupTags: List<String> = emptyList(),
    val unlockRepo: Boolean,
    val forgetSnapshots: Boolean,
    val pruneRepo: Boolean,
    val checkRepo: Boolean,
    val readData: Boolean,
    val keepLast: Int,
    val keepDaily: Int,
    val keepWeekly: Int,
    val keepMonthly: Int,
    val forgetAllHosts: Boolean = true,
    val forgetHosts: List<String> = emptyList(),
    val forgetTags: List<String> = emptyList(),
    val presetName: String? = null,
    val scheduleName: String? = null,
    val requireUnmeteredNetwork: Boolean = false
)

fun RunTasksConfig.toWorkRequest(
    repositoryKey: String,
    hasRoot: Boolean,
    presetName: String? = null,
    scheduleName: String? = null,
    requireUnmeteredNetwork: Boolean = false
): RunTasksWorkRequest {
    val backupAppsEnabled = backupEnabled && hasRoot
    val effectiveSelectAllApps = backupAppsEnabled && selectAllApps
    val hasCustomDirectories = customDirectories.isNotEmpty()
    val hasDeviceData = deviceData.anyEnabled()
    val normalizedForgetHosts = if (forgetAllHosts) {
        emptyList()
    } else {
        forgetHosts.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }
    return RunTasksWorkRequest(
        repositoryKey = repositoryKey,
        backupEnabled = backupAppsEnabled || hasCustomDirectories || hasDeviceData,
        backupTypes = backupTypes,
        selectedPackageNames = if (backupAppsEnabled && !effectiveSelectAllApps) selectedPackageNames else emptyList(),
        selectAllApps = effectiveSelectAllApps,
        excludedPackageNames = if (effectiveSelectAllApps) excludedPackageNames.distinct() else emptyList(),
        appBackupTypes = if (backupAppsEnabled) appBackupTypes else emptyMap(),
        customDirectories = customDirectories,
        excludedCustomDirectories = excludedCustomDirectories,
        deviceData = deviceData,
        backupTags = BackupTagRules.sanitizeBackupTags(backupTags),
        unlockRepo = unlockRepo,
        forgetSnapshots = forgetSnapshots,
        pruneRepo = pruneRepo,
        checkRepo = checkRepo,
        readData = readData,
        keepLast = keepLast,
        keepDaily = keepDaily,
        keepWeekly = keepWeekly,
        keepMonthly = keepMonthly,
        forgetAllHosts = forgetAllHosts,
        forgetHosts = normalizedForgetHosts,
        forgetTags = BackupTagRules.sanitizeBackupTags(forgetTags),
        presetName = presetName,
        scheduleName = scheduleName,
        requireUnmeteredNetwork = requireUnmeteredNetwork
    )
}
