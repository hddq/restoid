package io.github.hddq.restoid.ui.shared

import io.github.hddq.restoid.work.BackupTypeSelection
import kotlinx.serialization.Serializable

@Serializable
data class BackupTypes(
    val apk: Boolean = true,
    val data: Boolean = true,
    val deviceProtectedData: Boolean = true,
    val externalData: Boolean = false,
    val obb: Boolean = false,
    val media: Boolean = false,
    val permissions: Boolean = true,
    val specialAccess: Boolean = true,
    val accessibility: Boolean = true
) {
    fun toSelection(): BackupTypeSelection {
        return BackupTypeSelection(
            apk = apk,
            data = data,
            deviceProtectedData = deviceProtectedData,
            externalData = externalData,
            obb = obb,
            media = media,
            permissions = permissions,
            specialAccess = specialAccess,
            accessibility = accessibility
        )
    }

    fun anyEnabled(): Boolean {
        return apk || data || deviceProtectedData || externalData || obb || media ||
            permissions || specialAccess || accessibility
    }
}

fun BackupTypeSelection.toUiModel(): BackupTypes {
    return BackupTypes(
        apk = apk,
        data = data,
        deviceProtectedData = deviceProtectedData,
        externalData = externalData,
        obb = obb,
        media = media,
        permissions = permissions,
        specialAccess = specialAccess,
        accessibility = accessibility
    )
}
