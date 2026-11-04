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

data class StorageEntryItem(
    val name: String,
    val isDirectory: Boolean
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
    fun normalizeDirectoryPath(raw: String, trimWhitespace: Boolean = true): String? {
        val candidate = if (trimWhitespace) raw.trim() else raw
        if (candidate.isBlank() || candidate.startsWith("content://")) return null

        val extractedPath = if (candidate.startsWith("file://")) {
            runCatching { URI(candidate).path }.getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?: runCatching { Uri.parse(candidate).path }.getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                ?: candidate.removePrefix("file://")
        } else {
            candidate
        }

        if (!extractedPath.startsWith("/")) return null
        val collapsed = extractedPath.replace(Regex("/+"), "/")
        return if (collapsed == "/") "/" else collapsed.trimEnd('/')
    }

    /**
     * Resolves any stored custom directory entry (direct path, `file://` URI, or legacy `content://` SAF Tree URI)
     * into a normalized filesystem path without stripping significant trailing spaces from directory names.
     */
    fun resolveCustomDirectoryPath(entry: String): String? {
        if (entry.isBlank()) return null
        if (entry.startsWith("content://")) {
            return runCatching { getPathFromTreeUri(Uri.parse(entry)) }.getOrNull()
        }
        return normalizeDirectoryPath(entry, trimWhitespace = false)
    }

    /**
     * Returns a canonical comparison key for a directory entry so equivalent paths
     * (whether added via SAF URI, `file://`, or direct path) are deduplicated accurately.
     */
    fun resolveCustomDirectoryKey(entry: String): String {
        val resolvedPath = resolveCustomDirectoryPath(entry) ?: return entry
        return runCatching { File(resolvedPath).canonicalPath }.getOrDefault(resolvedPath)
    }

    /**
     * Fast non-blocking check whether [pathOrUri] is a directory or file.
     * Returns true if definitely a directory, false if definitely a file,
     * or null if the path cannot be determined by standard File APIs (e.g. root-only path).
     */
    fun isDirectoryFast(pathOrUri: String): Boolean? {
        val path = resolveCustomDirectoryPath(pathOrUri) ?: pathOrUri
        val file = File(path)
        if (file.isDirectory) return true
        if (file.isFile) return false
        return null
    }

    /**
     * Determines whether [pathOrUri] points to a directory (as opposed to a file).
     * Uses standard File checks first, then root shell if needed.
     */
    fun isDirectoryPath(pathOrUri: String): Boolean {
        isDirectoryFast(pathOrUri)?.let { return it }
        val path = resolveCustomDirectoryPath(pathOrUri) ?: pathOrUri
        val isRoot = runCatching { Shell.getShell().isRoot }.getOrDefault(false)
        if (isRoot) {
            val checkDir = runCatching { Shell.cmd("[ -d ${shellQuote(path)} ]").exec().isSuccess }.getOrDefault(false)
            if (checkDir) return true
            val checkFile = runCatching { Shell.cmd("[ -f ${shellQuote(path)} ]").exec().isSuccess }.getOrDefault(false)
            if (checkFile) return false
        }
        return !File(path).name.contains(".")
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

        val findCmd = """find -H ${shellQuote(path)} -mindepth 1 -maxdepth 1 \( -type d -o \( -type l -exec test -d {} \; \) \) -exec sh -c 'name=${'$'}{1##*/}; printf "%s" "${'$'}name" | od -An -tx1 | tr -d " \n"; printf "\n"' sh {} \;"""
        val result = Shell.cmd(findCmd).exec()
        if (!result.isSuccess) return null

        return result.out
            .mapNotNull(::decodeHexFilename)
            .filter { showHidden || !it.startsWith(".") }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
    }

    /**
     * Lists child directories and, when requested, regular files inside [path].
     * Root shell fallback supports paths hidden from the app process.
     */
    fun listChildEntries(path: String, showHidden: Boolean, includeFiles: Boolean): List<StorageEntryItem>? {
        val dir = File(path)
        val directChildren = runCatching { dir.listFiles() }.getOrNull()
        if (directChildren != null) {
            return directChildren
                .filter {
                    (it.isDirectory || (includeFiles && it.isFile)) &&
                        (showHidden || !it.name.startsWith("."))
                }
                .map { StorageEntryItem(it.name, it.isDirectory) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }

        val isRoot = runCatching { Shell.getShell().isRoot }.getOrDefault(false)
        if (!isRoot || !Shell.cmd("[ -d ${shellQuote(path)} ]").exec().isSuccess) return null

        val directoryNames = listChildDirectories(path, showHidden) ?: return null
        val fileNames = if (includeFiles) {
            val result = Shell.cmd(
                """find -H ${shellQuote(path)} -mindepth 1 -maxdepth 1 \( -type f -o \( -type l -exec test -f {} \; \) \) -exec sh -c 'name=${'$'}{1##*/}; printf "%s" "${'$'}name" | od -An -tx1 | tr -d " \n"; printf "\n"' sh {} \;"""
            ).exec()
            if (!result.isSuccess) return null
            val dirSet = directoryNames.toSet()
            result.out
                .mapNotNull(::decodeHexFilename)
                .filter { it !in dirSet && (showHidden || !it.startsWith(".")) }
        } else {
            emptyList()
        }

        return (directoryNames.map { StorageEntryItem(it, true) } + fileNames.map { StorageEntryItem(it, false) })
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    /** Decodes the ASCII hex records emitted by the root-shell find commands. */
    private fun decodeHexFilename(value: String): String? {
        if (value.length % 2 != 0) return null
        return runCatching {
            ByteArray(value.length / 2) { index ->
                val high = Character.digit(value[index * 2], 16)
                val low = Character.digit(value[index * 2 + 1], 16)
                require(high >= 0 && low >= 0)
                ((high shl 4) or low).toByte()
            }.toString(Charsets.UTF_8)
        }.getOrNull()
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
