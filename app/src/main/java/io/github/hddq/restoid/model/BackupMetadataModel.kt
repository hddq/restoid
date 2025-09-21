package io.github.hddq.restoid.model

import kotlinx.serialization.Serializable

/**
 * Represents the top-level structure of the `restoid.json` metadata file.
 * It contains a map of applications that were included in the snapshot.
 *
 * @param apps A map where the key is the package name (e.g., "com.example.app") and
 * the value is an [AppMetadata] object containing details about the backup for that app.
 */
@Serializable
data class RestoidMetadata(
    val apps: Map<String, AppMetadata>
)

/**
 * Contains metadata for a single application within a snapshot.
 * This information helps in understanding what was backed up for each app.
 *
 * @param size The total calculated size of the backed-up files for this app in bytes.
 * @param types A list of strings indicating which parts of the app were backed up
 * (e.g., "apk", "data", "user_de").
 * @param versionCode The version code of the app at the time of backup.
 * @param versionName The version name of the app at the time of backup.
 */
@Serializable
data class AppMetadata(
    val size: Long,
    val types: List<String>,
    val versionCode: Long,
    val versionName: String
)
