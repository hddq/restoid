package app.restoid.data

import java.io.File

/**
 * Represents a local restic repository.
 * @param path The absolute path to the repository directory.
 * @param name A user-friendly name, derived from the path.
 */
data class LocalRepository(
    val path: String,
    val name: String = File(path).name
)
