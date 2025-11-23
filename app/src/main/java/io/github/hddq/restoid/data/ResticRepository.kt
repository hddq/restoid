package io.github.hddq.restoid.data

import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import io.github.hddq.restoid.model.ResticConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * High-level repository logic.
 * Uses [ResticExecutor] to run commands and maps output to data models.
 * Uses [ResticBinaryManager] mostly to check state availability.
 */
class ResticRepository(
    private val context: Context,
    private val executor: ResticExecutor
) {

    private val _snapshots = MutableStateFlow<List<SnapshotInfo>?>(null)
    val snapshots = _snapshots.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    fun clearSnapshots() {
        _snapshots.value = null
    }

    suspend fun getSnapshots(repoPath: String, password: String): Result<List<SnapshotInfo>> {
        return withContext(Dispatchers.IO) {
            val result = executor.execute(
                repoPath = repoPath,
                password = password,
                command = "snapshots --json",
                failureMessage = "Failed to load snapshots"
            )

            result.map { output ->
                val snapshots = parseSnapshotsJson(output)
                _snapshots.value = snapshots
                snapshots
            }
        }
    }

    suspend fun refreshSnapshots(repoPath: String, password: String) {
        getSnapshots(repoPath, password)
    }

    suspend fun check(repoPath: String, password: String, readAllData: Boolean): Result<String> {
        val args = if (readAllData) "check --read-data" else "check"
        return executor.execute(repoPath, password, args, "Failed to check repository")
    }

    suspend fun prune(repoPath: String, password: String): Result<String> {
        return executor.execute(repoPath, password, "prune", "Failed to prune repository")
    }

    suspend fun unlock(repoPath: String, password: String): Result<String> {
        return executor.execute(repoPath, password, "unlock", "Failed to unlock repository")
    }

    suspend fun forget(
        repoPath: String,
        password: String,
        keepLast: Int,
        keepDaily: Int,
        keepWeekly: Int,
        keepMonthly: Int
    ): Result<String> {
        val forgetOptions = buildString {
            if (keepLast > 0) append(" --keep-last $keepLast")
            if (keepDaily > 0) append(" --keep-daily $keepDaily")
            if (keepWeekly > 0) append(" --keep-weekly $keepWeekly")
            if (keepMonthly > 0) append(" --keep-monthly $keepMonthly")
        }

        if (forgetOptions.isBlank()) {
            return Result.failure(Exception("No 'keep' policy was specified for the forget operation."))
        }

        val tagOptions = " --tag 'restoid' --tag 'backup'"
        return executor.execute(repoPath, password, "forget$forgetOptions$tagOptions", "Failed to forget snapshots")
    }

    suspend fun forgetSnapshot(repoPath: String, password: String, snapshotId: String): Result<Unit> {
        return executor.execute(repoPath, password, "forget $snapshotId", "Failed to delete snapshot")
            .map {
                refreshSnapshots(repoPath, password)
                Unit
            }
    }

    suspend fun forgetMetadataSnapshots(repoPath: String, password: String): Result<String> {
        val command = "forget --keep-last 5 --tag 'restoid' --tag 'metadata'"
        return executor.execute(repoPath, password, command, "Failed to forget metadata snapshots")
    }

    suspend fun getConfig(repoPath: String, password: String): Result<ResticConfig> {
        return executor.execute(repoPath, password, "cat config --json", "Failed to get repo config")
            .mapCatching { jsonOutput ->
                json.decodeFromString<ResticConfig>(jsonOutput)
            }
    }

    suspend fun changePassword(repoPath: String, oldPassword: String, newPassword: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val newPassFile = File.createTempFile("restic-new-pass", ".tmp", context.cacheDir)
            try {
                newPassFile.writeText(newPassword)
                val command = "key passwd --new-password-file '${newPassFile.absolutePath}'"
                executor.execute(repoPath, oldPassword, command, "Failed to change password")
                    .map { Unit }
            } finally {
                newPassFile.delete()
            }
        }
    }

    suspend fun restore(
        repoPath: String,
        password: String,
        snapshotId: String,
        targetPath: String,
        pathsToRestore: List<String>
    ): Result<String> {
        val includes = pathsToRestore.joinToString(" ") { "--include \"$it\"" }
        val command = "restore $snapshotId --target '$targetPath' $includes"
        return executor.execute(repoPath, password, command, "Failed to restore snapshot")
    }

    suspend fun backupMetadata(repositoryId: String, repoPath: String, password: String) {
        // Backing up metadata requires specific CD commands to avoid recursive paths in the snapshot.
        // We bypass the executor here to replicate the pre-refactor logic exactly.

        val resticFile = File(context.filesDir, "restic")
        // Simple check if binary exists (we assume it is executable if it exists at this point)
        if (!resticFile.exists()) return

        var passwordFile: File? = null
        try {
            val metadataDir = File(context.filesDir, "metadata")
            if (!metadataDir.exists() || !metadataDir.isDirectory) return

            passwordFile = File.createTempFile("restic-pass-meta", ".tmp", context.cacheDir)
            passwordFile.writeText(password)

            val tags = listOf("restoid", "metadata")
            val tagFlags = tags.joinToString(" ") { "--tag '$it'" }

            // Use 'cd' to ensure relative paths in the backup for simpler restore.
            // This specific structure prevents absolute path recursion in the snapshot.
            val command = "cd '${metadataDir.absolutePath}' && RESTIC_PASSWORD_FILE='${passwordFile.absolutePath}' ${resticFile.absolutePath} -r '$repoPath' backup '$repositoryId' --json $tagFlags"

            withContext(Dispatchers.IO) {
                val result = Shell.cmd(command).exec()
                if (!result.isSuccess) {
                    Log.e("ResticRepo", "Metadata backup failed: ${result.err.joinToString("\n")}")
                } else {
                    // After a successful metadata backup, prune the old ones.
                    val forgetResult = forgetMetadataSnapshots(repoPath, password)
                    if (forgetResult.isFailure) {
                        Log.e("ResticRepo", "Forgetting old metadata snapshots failed: ${forgetResult.exceptionOrNull()?.message}")
                    } else {
                        Log.d("ResticRepo", "Successfully forgot old metadata snapshots.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ResticRepo", "Exception during metadata backup", e)
        } finally {
            passwordFile?.delete()
        }
    }

    // --- JSON Parsing Helpers ---

    private fun parseSnapshotsJson(jsonOutput: String): List<SnapshotInfo> {
        val snapshots = mutableListOf<SnapshotInfo>()
        try {
            val lines = jsonOutput.trim().lines()
            if (lines.size == 1 && lines[0].trim().startsWith("[")) {
                val content = lines[0].trim().removeSurrounding("[", "]")
                if (content.isNotEmpty()) {
                    splitJsonObjects(content).forEach { parseSnapshotObject(it)?.let { s -> snapshots.add(s) } }
                }
            } else {
                lines.forEach { line ->
                    if (line.trim().startsWith("{")) parseSnapshotObject(line.trim())?.let { snapshots.add(it) }
                }
            }
        } catch (e: Exception) {
            // Parsing failed
        }
        return snapshots
    }

    private fun splitJsonObjects(content: String): List<String> {
        val objects = mutableListOf<String>()
        var braceCount = 0
        var start = 0
        var inString = false
        var escapeNext = false

        for (i in content.indices) {
            val char = content[i]
            if (escapeNext) {
                escapeNext = false; continue
            }
            when (char) {
                '\\' -> escapeNext = true
                '"' -> if (!escapeNext) inString = !inString
                '{' -> if (!inString) braceCount++
                '}' -> if (!inString) {
                    braceCount--
                    if (braceCount == 0) {
                        objects.add(content.substring(start, i + 1))
                        start = i + 1
                        while (start < content.length && (content[start] == ',' || content[start].isWhitespace())) start++
                    }
                }
            }
        }
        return objects
    }

    private fun parseSnapshotObject(jsonObj: String): SnapshotInfo? {
        return try {
            val id = extractJsonField(jsonObj, "id") ?: extractJsonField(jsonObj, "short_id") ?: return null
            val time = extractJsonField(jsonObj, "time") ?: "unknown"
            val paths = extractJsonArrayField(jsonObj, "paths")
            val tags = extractJsonArrayField(jsonObj, "tags")
            SnapshotInfo(id, time, paths, tags)
        } catch (e: Exception) {
            null
        }
    }

    private fun extractJsonField(json: String, field: String): String? {
        return Regex("\"$field\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)
    }

    private fun extractJsonArrayField(json: String, field: String): List<String> {
        val match = Regex("\"$field\"\\s*:\\s*\\[([^\\]]*)\\]").find(json)?.groupValues?.get(1) ?: return emptyList()
        if (match.isBlank()) return emptyList()
        return match.split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
    }
}

data class SnapshotInfo(
    val id: String,
    val time: String,
    val paths: List<String>,
    val tags: List<String>
)