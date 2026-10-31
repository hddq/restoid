package io.github.hddq.restoid.util

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.topjohnwu.superuser.Shell
import java.io.File

object StorageUtils {

    /**
     * Converts a SAF Tree URI (e.g., from a directory picker) into a standard file system path.
     * Handles both Primary storage and External SD cards/USB drives.
     */
    fun getPathFromTreeUri(treeUri: Uri): String? {
        if (treeUri.authority != "com.android.externalstorage.documents") {
            return null
        }

        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val split = docId.split(":")
        if (split.size > 1) {
            val type = split[0]
            val path = split[1]
            return if (type == "primary") {
                "${Environment.getExternalStorageDirectory()}/$path"
            } else {
                // Handle SD cards and USB drives where type is the UUID
                "/storage/$type/$path"
            }
        }
        return null
    }

    /**
     * Returns true if the custom-directory entry is a typed direct path
     * (e.g. "file:///storage/emulated/0/Download") rather than a SAF
     * tree URI (e.g. "content://...").
     */
    fun isFileUri(uriString: String): Boolean {
        return uriString.startsWith("file://")
    }

    /**
     * Normalizes user-typed input into a canonical "file://..." custom-directory
     * entry. Accepts both raw filesystem paths ("/storage/emulated/0/Download")
     * and file URIs ("file:///storage/emulated/0/Download").
     * Returns null when the input is not a usable absolute path.
     */
    fun normalizeTypedPathToFileUri(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("content://")) return null
        val path = if (trimmed.startsWith("file://")) {
            trimmed.removePrefix("file://")
        } else {
            trimmed
        }
        if (!path.startsWith("/")) return null
        val collapsed = path.replace(Regex("/+"), "/").trimEnd('/')
        if (collapsed.isEmpty()) return null
        return "file://$collapsed"
    }

    /**
     * Returns the filesystem path for a "file://..." custom-directory entry,
     * or null when the entry is not a file URI.
     */
    fun pathFromFileUri(uriString: String): String? {
        if (!isFileUri(uriString)) return null
        val path = uriString.removePrefix("file://")
        return path.ifEmpty { null }
    }

    /**
     * Lists the names of child directories of [path], sorted alphabetically.
     * Returns null when the path cannot be listed (missing, not a directory,
     * or not readable).
     */
    fun listChildDirectories(path: String, showHidden: Boolean): List<String>? {
        val dir = File(path)
        val children = try {
            dir.listFiles()
        } catch (_: Exception) {
            null
        } ?: return null
        return children
            .filter { it.isDirectory && (showHidden || !it.name.startsWith(".")) }
            .map { it.name }
            .sorted()
    }

    /**
     * Returns the browsable storage roots: the primary shared storage plus any
     * mounted volumes (SD cards, USB drives) discovered via /proc/mounts.
     * Used as the top level of the folder browser because /storage itself is
     * typically not listable by apps.
     */
    fun listStorageRoots(): List<String> {
        val roots = mutableListOf<String>()
        val primary = "/storage/emulated/0"
        if (File(primary).isDirectory) roots.add(primary)
        try {
            File("/proc/mounts").forEachLine { line ->
                val parts = line.split(" ")
                if (parts.size >= 2) {
                    val mount = parts[1].replace("\\040", " ")
                    if (mount.startsWith("/storage/") && mount != "/storage/" &&
                        !mount.startsWith("/storage/emulated")
                    ) {
                        val candidate = "/storage/" + mount.removePrefix("/storage/").substringBefore("/")
                        if (candidate !in roots && File(candidate).isDirectory) {
                            roots.add(candidate)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Fall back to primary storage only.
        }
        return roots.sorted()
    }

    /**
     * Returns a stable comparison key for a custom-directory entry: the
     * canonical filesystem path when the entry resolves to one, otherwise the
     * raw entry string. Two entries with equal keys refer to the same folder
     * regardless of how they were added (SAF tree URI vs typed path,
     * trailing slashes, duplicate separators).
     */
    fun resolveCustomDirectoryKey(uriString: String): String {
        val rawPath = if (isFileUri(uriString)) {
            pathFromFileUri(uriString)
        } else {
            try {
                getPathFromTreeUri(Uri.parse(uriString))
            } catch (_: Exception) {
                null
            }
        } ?: return uriString
        return try {
            File(rawPath).canonicalPath
        } catch (_: Exception) {
            rawPath
        }
    }

    /**
     * Dynamically resolves a user-space path (e.g., /storage/UUID/...) to a root-space mount path
     * (e.g., /mnt/media_rw/UUID/...).
     *
     * This is necessary because Shell.FLAG_MOUNT_MASTER places the shell in the global namespace,
     * where user-specific FUSE/SDCardFS mounts often don't exist or are inaccessible.
     */
    fun resolvePathForShell(inputPath: String): String {
        // Pattern to match /storage/UUID/...
        val uuidPattern = Regex("^/storage/([A-Fa-f0-9-]+)(/.*)?$")
        val match = uuidPattern.find(inputPath)

        if (match != null) {
            val uuid = match.groupValues[1]
            val relativePath = match.groupValues[2]

            // 1. Check /proc/mounts to find where this UUID is actually mounted
            val mounts = Shell.cmd("cat /proc/mounts").exec().out
            val mountPoint = mounts
                .map { it.split("\\s+".toRegex()) }
                .filter { it.size >= 2 }
                .map { it[1] } // Mount point is the second field
                .find { it.endsWith("/$uuid") }

            if (mountPoint != null) {
                return "$mountPoint$relativePath"
            }

            // 2. Fallback: Check specific common locations like /mnt/media_rw
            val mediaRwPath = "/mnt/media_rw/$uuid"
            // Check using shell because Java might not have permission to see /mnt/media_rw
            if (Shell.cmd("[ -d '$mediaRwPath' ]").exec().isSuccess) {
                return "$mediaRwPath$relativePath"
            }
        }

        // Return original if it's primary storage or resolution failed
        return inputPath
    }
}