package app.restoid.model

import android.text.format.Formatter

/**
 * A data class to hold the processed information about what was backed up for a specific app
 * within a snapshot.
 *
 * @param appInfo Details about the application (name, icon, etc.).
 * @param backedUpItems A list of strings representing the types of data backed up (e.g., "APK", "Data").
 * @param versionName The version of the app at the time of backup.
 * @param backupSize The total size of the backed up files for this app, in bytes.
 */
data class BackupDetail(
    val appInfo: AppInfo,
    val backedUpItems: List<String>,
    val versionName: String? = null,
    val backupSize: Long? = null
)
