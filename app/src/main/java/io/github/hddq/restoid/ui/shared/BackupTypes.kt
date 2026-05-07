package io.github.hddq.restoid.ui.shared

import io.github.hddq.restoid.work.BackupTypeSelection

data class BackupTypes(
    val apk: Boolean = true,
    val data: Boolean = true,
    val deviceProtectedData: Boolean = true,
    val externalData: Boolean = false,
    val obb: Boolean = false,
    val media: Boolean = false
) {
    fun toSelection(): BackupTypeSelection {
        return BackupTypeSelection(
            apk = apk,
            data = data,
            deviceProtectedData = deviceProtectedData,
            externalData = externalData,
            obb = obb,
            media = media
        )
    }

    fun anyEnabled(): Boolean {
        return apk || data || deviceProtectedData || externalData || obb || media
    }
}

fun BackupTypeSelection.toUiModel(): BackupTypes {
    return BackupTypes(
        apk = apk,
        data = data,
        deviceProtectedData = deviceProtectedData,
        externalData = externalData,
        obb = obb,
        media = media
    )
}
