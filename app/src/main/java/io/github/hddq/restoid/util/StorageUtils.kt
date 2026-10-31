package io.github.hddq.restoid.util

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.topjohnwu.superuser.Shell
import java.io.File
import java.net.URI

data class StorageVolumeItem(
    val path: String,
    val name: String,
    val isPrimary: Boolean
)

object StorageUtils {

    /**
     * Converts a SAF Tree URI (e.g., from a directory picker) into a standard file system path.
     * Handles both Primary storage and External SD cards/USB drives.
     */
    fun getPathFromTreeUri(treeUri: Uri): String? {
        val authority = treeUri.authority ?: return null
        val host = authority.substringAfter('@')
        if (host != "com.android.externalstorage.documents") {
            return null
        }
        val userIdFromAuthority = authority.substringBefore('@', "").takeIf { it.isNotEmpty() && it.all(Char::isDigit) }

        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val split = docId.split(":", limit = 2)
        if (split.size > 1) {
            val type = split[0]
            val path = split[1].trimStart('/')
            val basePath = if (type == "primary") {
                if (userIdFromAuthority != null) {
                    "/storage/emulated/$userIdFromAuthority"
                } else {
                    runCatching { Environment.getExternalStorageDirectory().absolutePath.trimEnd('/') }
                        .getOrDefault("/storage/emulated/0")
                }
            } else {
                // Handle SD cards and USB drives where type is the UUID
                "/storage/$type"
            }
            return if (path.isEmpty()) basePath else "$basePath/$path"
        }
        return null
    }

    /**
     * Normalizes a user-typed or browsed path (or `file://` URI) into a clean absolute filesystem path.
     * Returns null if the input is blank, relative, or a `content://` URI.
     */
    fun normalizeDirectoryPath(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("content://")) return null

        val extractedPath = if (trimmed.startsWith("file://")) {
            runCatching { URI(trimmed).path }.getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?: runCatching { Uri.parse(trimmed).path }.getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                ?: trimmed.removePrefix("file://")
        } else {
            trimmed
        }

        if (!extractedPath.startsWith("/")) return null
        val collapsed = extractedPath.replace(Regex("/+"), "/")
        return if (collapsed == "/") "/" else collapsed.trimEnd('/')
    }

    /**
     * Resolves any stored custom directory entry (direct path, `file://` URI, or legacy `content://` SAF Tree URI)
     * into a normalized filesystem path.
     */
    fun resolveCustomDirectoryPath(entry: String): String? {
        val trimmed = entry.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("content://")) {
            return runCatching { getPathFromTreeUri(Uri.parse(trimmed)) }.getOrNull()
        }
        return normalizeDirectoryPath(trimmed)
    }

    /**
     * Returns a canonical comparison key for a directory entry so equivalent paths
     * (whether added via SAF URI, `file://`, or direct path) are deduplicated accurately.
     */
    fun resolveCustomDirectoryKey(entry: String): String {
        val resolvedPath = resolveCustomDirectoryPath(entry) ?: return entry.trim()
        return runCatching { File(resolvedPath).canonicalPath }.getOrDefault(resolvedPath)
    }

    /**
     * Lists child directory names inside [path], sorted case-insensitively.
     * Returns null when the directory does not exist or cannot be listed.
     */
    fun listChildDirectories(path: String, showHidden: Boolean): List<String>? {
        val dir = File(path)
        val directChildren = runCatching { dir.listFiles() }.getOrNull()
        if (directChildren != null) {
            return directChildren
                .filter { it.isDirectory && (showHidden || !it.name.startsWith(".")) }
                .map { it.name }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
        }

        val isRoot = runCatching { Shell.getShell().isRoot }.getOrDefault(false)
        if (!isRoot) return null

        val checkDir = Shell.cmd("[ -d ${shellQuote(path)} ]").exec().isSuccess
        if (!checkDir) return null

        val findCmd = buildString {
            append("find ").append(shellQuote(path))
            append(" -mindepth 1 -maxdepth 1 -type d -exec basename {} \\;")
        }
        val result = Shell.cmd(findCmd).exec()
        if (!result.isSuccess) return null

        return result.out
            .filter { it.isNotBlank() && (showHidden || !it.startsWith(".")) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
    }

    /**
     * Discovers accessible storage roots: primary shared storage plus mounted external
     * SD cards (`/storage/<UUID>`) and USB OTG drives (`/mnt/media_rw/<UUID>`).
     */
    fun listStorageVolumes(): List<StorageVolumeItem> {
        val volumes = mutableListOf<StorageVolumeItem>()
        val primaryPath = runCatching {
            Environment.getExternalStorageDirectory()?.absolutePath?.trimEnd('/')
        }.getOrNull().takeUnless { it.isNullOrEmpty() } ?: "/storage/emulated/0"

        if (File(primaryPath).isDirectory) {
            volumes.add(
                StorageVolumeItem(
                    path = primaryPath,
                    name = primaryPath,
                    isPrimary = true
                )
            )
        }

        val mountLines = runCatching { File("/proc/mounts").readLines() }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: runCatching { Shell.cmd("cat /proc/mounts").exec().out }.getOrDefault(emptyList())

        val externalUuidRegex = Regex("^/(?:storage|mnt/media_rw)/([A-Fa-f0-9-]+)(?:/.*)?$")
        val discoveredUuids = linkedSetOf<String>()

        for (line in mountLines) {
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 2) continue
            val mountPoint = parts[1].replace("\\040", " ")
            if (mountPoint.startsWith("/storage/emulated") || mountPoint == "/storage/self") continue
            val match = externalUuidRegex.matchEntire(mountPoint) ?: continue
            discoveredUuids.add(match.groupValues[1])
        }

        for (uuid in discoveredUuids) {
            val storageCandidate = "/storage/$uuid"
            val mediaRwCandidate = "/mnt/media_rw/$uuid"
            val bestPath = when {
                File(storageCandidate).let { it.isDirectory && it.canRead() } -> storageCandidate
                File(mediaRwCandidate).let { it.isDirectory && it.canRead() } -> mediaRwCandidate
                File(storageCandidate).isDirectory -> storageCandidate
                File(mediaRwCandidate).isDirectory -> mediaRwCandidate
                runCatching {
                    Shell.cmd("[ -d ${shellQuote(mediaRwCandidate)} ]").exec().isSuccess
                }.getOrDefault(false) -> mediaRwCandidate
                runCatching {
                    Shell.cmd("[ -d ${shellQuote(storageCandidate)} ]").exec().isSuccess
                }.getOrDefault(false) -> storageCandidate
                else -> null
            }
            if (bestPath != null && volumes.none { it.path == bestPath }) {
                volumes.add(
                    StorageVolumeItem(
                        path = bestPath,
                        name = uuid,
                        isPrimary = false
                    )
                )
            }
        }

        return volumes
    }

    /**
     * Dynamically resolves a user-space path (e.g., /storage/UUID/...) to a root-space mount path
     * (e.g., /mnt/media_rw/UUID/...) when running in a root shell.
     *
     * This is necessary for root shells because Shell.FLAG_MOUNT_MASTER places the root shell in the
     * global namespace, where user-specific FUSE/SDCardFS mounts under /storage/UUID may be inaccessible.
     * In rootless mode, the shell runs in the app's user namespace where /storage/UUID is accessible
     * directly via FUSE and /mnt/media_rw is blocked by permissions.
     */
    fun resolvePathForShell(inputPath: String): String {
        val externalUuidPattern = Regex("^/(?:storage|mnt/media_rw)/([A-Fa-f0-9-]+)(/.*)?$")
        val match = externalUuidPattern.find(inputPath)

        if (match != null) {
            val uuid = match.groupValues[1]
            val relativePath = match.groupValues[2]
            val isRoot = Shell.getShell().isRoot

            // 1. In rootless mode, prefer /storage/<UUID> if mounted (e.g. SD cards via FUSE),
            //    since /storage has full getattr permissions for untrusted_app.
            if (!isRoot) {
                val storageBase = "/storage/$uuid"
                val storageCandidate = "$storageBase$relativePath"
                if (Shell.cmd("[ -e ${shellQuote(storageCandidate)} ] || [ -d ${shellQuote(storageBase)} ]").exec().isSuccess) {
                    return storageCandidate
                }
            }

            // 2. Check /proc/mounts to find an accessible mount point for this UUID
            //    (e.g. /mnt/media_rw/<UUID> for root shells or USB OTG drives in rootless mode).
            val mounts = Shell.cmd("cat /proc/mounts").exec().out
            val mountPoints = mounts
                .map { it.split("\\s+".toRegex()) }
                .filter { it.size >= 2 }
                .map { it[1] } // Mount point is the second field
                .filter { it.endsWith("/$uuid") }
                .distinct()

            val accessibleMount = mountPoints.firstOrNull { mountPoint ->
                Shell.cmd("[ -e ${shellQuote("$mountPoint$relativePath")} ] || [ -d ${shellQuote(mountPoint)} ]").exec().isSuccess
            } ?: mountPoints.firstOrNull()

            if (accessibleMount != null) {
                return "$accessibleMount$relativePath"
            }

            // 3. Fallback: Check /mnt/media_rw/<UUID> and /storage/<UUID>
            val mediaRwPath = "/mnt/media_rw/$uuid"
            if (Shell.cmd("[ -d ${shellQuote(mediaRwPath)} ]").exec().isSuccess) {
                return "$mediaRwPath$relativePath"
            }
            val storagePath = "/storage/$uuid"
            if (Shell.cmd("[ -d ${shellQuote(storagePath)} ]").exec().isSuccess) {
                return "$storagePath$relativePath"
            }
        }

        // Return original if it's primary storage or resolution failed
        return inputPath
    }
}