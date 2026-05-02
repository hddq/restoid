package io.github.hddq.restoid.data

import kotlinx.serialization.Serializable
import java.io.File

/**
 * Represents a restic repository.
 * @param path The repository specification understood by restic.
 * @param name A user-friendly name, derived from the path.
 * @param id The unique ID of the restic repository.
 */
@Serializable
enum class RepositoryBackendType {
    LOCAL,
    SFTP,
    REST,
    S3
}

@Serializable
data class LocalRepository(
    val path: String,
    val backendType: RepositoryBackendType = RepositoryBackendType.LOCAL,
    val name: String = defaultRepositoryName(path, backendType),
    val id: String? = null,
    val restAuthRequired: Boolean = false,
    val s3AuthRequired: Boolean = false,
    val sftpKeyAuthRequired: Boolean = false,
    val sftpKeyPassphraseRequired: Boolean = false,
    val environmentVariables: Map<String, String> = emptyMap(),
    val resticOptions: Map<String, String> = emptyMap()
)

private fun defaultRepositoryName(path: String, backendType: RepositoryBackendType): String {
    val trimmed = path.trim().trimEnd('/')
    if (trimmed.isEmpty()) return path

    return when (backendType) {
        RepositoryBackendType.LOCAL -> File(trimmed).name.ifBlank { trimmed }
        RepositoryBackendType.SFTP,
        RepositoryBackendType.REST,
        RepositoryBackendType.S3 -> {
            val segment = trimmed.substringAfterLast('/').substringAfterLast(':')
            if (segment.isBlank()) trimmed else segment
        }
    }
}
