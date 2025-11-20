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