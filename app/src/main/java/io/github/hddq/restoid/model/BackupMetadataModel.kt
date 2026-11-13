package io.github.hddq.restoid.model

import kotlinx.serialization.Serializable

@Serializable
data class CustomDirectoryMetadata(
    val size: Long,
    /** Null for snapshots made before the item type was recorded. */
    val isDirectory: Boolean? = null
)

/**
 * Represents the top-level structure of the `restoid.json` metadata file.
 * It contains a map of applications that were included in the snapshot.
 *
 * @param apps A map where the key is the package name (e.g., "com.example.app") and
 * the value is an [AppMetadata] object containing details about the backup for that app.
 */
@Serializable
data class RestoidMetadata(
    val apps: Map<String, AppMetadata>,
    val customDirectories: Map<String, CustomDirectoryMetadata> = emptyMap()
)

@Serializable
data class AppOpPermission(
    val op: String,
    val isUidMode: Boolean = false
)

/**
 * Special app access and privileged OS grants recorded per application at backup time.
 */
@Serializable
data class SpecialAccessMetadata(
    val allowedAppOps: List<AppOpPermission> = emptyList(),
    val activeDeviceAdmins: List<String> = emptyList(),
    val enabledNotificationListeners: List<String> = emptyList(),
    val notificationPolicyAccess: Boolean = false,
    val ignoringBatteryOptimizations: Boolean = false
) {
    fun isEmpty(): Boolean =
        allowedAppOps.isEmpty() &&
            activeDeviceAdmins.isEmpty() &&
            enabledNotificationListeners.isEmpty() &&
            !notificationPolicyAccess &&
            !ignoringBatteryOptimizations
}

/**
 * Contains metadata for a single application within a snapshot.
 * This information helps in understanding what was backed up for each app.
 *
 * @param size The total calculated size of the backed-up files for this app in bytes.
 * @param types A list of strings indicating which parts of the app were backed up
 * (e.g., "apk", "data", "user_de").
 * @param versionCode The version code of the app at the time of backup.
 * @param versionName The version name of the app at the time of backup.
 * @param grantedRuntimePermissions A list of dangerous/runtime permissions that were granted
 * for this app at backup time (e.g., camera, location).
 * @param specialAccess Special app access grants (AppOps, device admin, notification listeners,
 * DND policy access, unrestricted battery) recorded for this app at backup time.
 * @param enabledAccessibilityServices Enabled accessibility service components belonging to this app.
 * @param detailedSizes A map detailing the specific sizes of backed up components (e.g. "apk" to bytes)
 * calculated from the restic snapshot.
 */
@Serializable
data class AppMetadata(
    val size: Long,
    val types: List<String>,
    val versionCode: Long,
    val versionName: String,
    val appName: String? = null,
    val grantedRuntimePermissions: List<String> = emptyList(),
    val specialAccess: SpecialAccessMetadata? = null,
    val enabledAccessibilityServices: List<String> = emptyList(),
    val detailedSizes: Map<String, Long> = emptyMap()
) {
    fun hasSpecialAccessBackup(): Boolean = specialAccess?.isEmpty() == false

    fun hasAccessibilityBackup(): Boolean = enabledAccessibilityServices.isNotEmpty()

    companion object {
        val METADATA_ONLY_BACKUP_TYPES = setOf(
            "permissions",
            "special_access",
            "accessibility"
        )
    }
}
