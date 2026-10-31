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
                    Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
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