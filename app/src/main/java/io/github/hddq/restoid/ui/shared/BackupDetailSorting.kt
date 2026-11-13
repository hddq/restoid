package io.github.hddq.restoid.ui.shared

import io.github.hddq.restoid.model.BackupDetail

enum class AppSortOrder {
    NAME_ASCENDING,
    NAME_DESCENDING,
    SIZE_ASCENDING,
    SIZE_DESCENDING
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
}
