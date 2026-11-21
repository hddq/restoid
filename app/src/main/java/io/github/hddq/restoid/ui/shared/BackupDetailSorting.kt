package io.github.hddq.restoid.ui.shared

import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.model.BackupDetail

enum class AppSortOrder {
    NAME_ASCENDING,
    NAME_DESCENDING,
    SIZE_ASCENDING,
    SIZE_DESCENDING,
    FIRST_INSTALL_TIME_ASCENDING,
    FIRST_INSTALL_TIME_DESCENDING
}

fun sortBackupDetails(
    details: List<BackupDetail>,
    order: AppSortOrder
): List<BackupDetail> = when (order) {
    AppSortOrder.NAME_ASCENDING -> details.sortedWith(
        compareBy<BackupDetail> { it.appInfo.name.lowercase() }
            .thenBy { it.appInfo.packageName }
    )
    AppSortOrder.NAME_DESCENDING -> details.sortedWith(
        compareByDescending<BackupDetail> { it.appInfo.name.lowercase() }
            .thenBy { it.appInfo.packageName }
    )
    AppSortOrder.SIZE_ASCENDING -> details.sortedWith(
        compareBy<BackupDetail> { it.backupSize ?: 0L }
            .thenBy { it.appInfo.name.lowercase() }
    )
    AppSortOrder.SIZE_DESCENDING -> details.sortedWith(
        compareByDescending<BackupDetail> { it.backupSize ?: 0L }
            .thenBy { it.appInfo.name.lowercase() }
    )
    AppSortOrder.FIRST_INSTALL_TIME_ASCENDING -> details.sortedWith(
        compareBy<BackupDetail> { it.appInfo.firstInstallTime }
            .thenBy { it.appInfo.name.lowercase() }
    )
    AppSortOrder.FIRST_INSTALL_TIME_DESCENDING -> details.sortedWith(
        compareByDescending<BackupDetail> { it.appInfo.firstInstallTime }
            .thenBy { it.appInfo.name.lowercase() }
    )
}

fun sortApps(
    apps: List<AppInfo>,
    order: AppSortOrder
): List<AppInfo> = when (order) {
    AppSortOrder.NAME_ASCENDING -> apps.sortedWith(
        compareBy<AppInfo> { it.name.lowercase() }
            .thenBy { it.packageName }
    )
    AppSortOrder.NAME_DESCENDING -> apps.sortedWith(
        compareByDescending<AppInfo> { it.name.lowercase() }
            .thenBy { it.packageName }
    )
    AppSortOrder.FIRST_INSTALL_TIME_ASCENDING -> apps.sortedWith(
        compareBy<AppInfo> { it.firstInstallTime }
            .thenBy { it.name.lowercase() }
    )
    AppSortOrder.FIRST_INSTALL_TIME_DESCENDING -> apps.sortedWith(
        compareByDescending<AppInfo> { it.firstInstallTime }
            .thenBy { it.name.lowercase() }
    )
    // Backup configuration has no size data, so keep a deterministic fallback.
    AppSortOrder.SIZE_ASCENDING,
    AppSortOrder.SIZE_DESCENDING -> apps.sortedWith(
        compareBy<AppInfo> { it.name.lowercase() }
            .thenBy { it.packageName }
    )
}
