package io.github.hddq.restoid.ui.shared

import io.github.hddq.restoid.model.BackupDetail

enum class AppSortOrder {
    NAME,
    SIZE
}

fun sortBackupDetails(
    details: List<BackupDetail>,
    order: AppSortOrder
): List<BackupDetail> = when (order) {
    AppSortOrder.NAME -> details.sortedWith(
        compareBy<BackupDetail> { it.appInfo.name.lowercase() }
            .thenBy { it.appInfo.packageName }
    )
    AppSortOrder.SIZE -> details.sortedWith(
        compareByDescending<BackupDetail> { it.backupSize ?: 0L }
            .thenBy { it.appInfo.name.lowercase() }
    )
}
