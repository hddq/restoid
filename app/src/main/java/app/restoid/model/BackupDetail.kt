package app.restoid.model

/**
 * A data class to hold the processed information about what was backed up for a specific app
 * within a snapshot.
 *
 * @param appInfo Details about the application (name, icon, etc.).
 * @param backedUpItems A list of strings representing the types of data backed up (e.g., "APK", "Data").
 */
data class BackupDetail(
    val appInfo: AppInfo,
    val backedUpItems: List<String>
)
