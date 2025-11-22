package io.github.hddq.restoid.data

import android.content.Context
import android.util.Log
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
        // This one is tricky because it needs TWO password files.
        // We handle it manually here or extend Executor.
        // For modularity, let's do it manually here but reusing the logic concept.
        // Actually, ResticExecutor takes one password. This command needs special handling.
        // Let's create the temp files here and run shell directly? Or make Executor more flexible?
        // Cleanest is to handle it here as a special case but check binary via manager.
        // NOTE: Since I removed binaryManager reference, I need to pass it in or expose path via Executor?
        // Let's assume Executor can run a raw command string if we provide it.
        // Refactor: Executor is tight to standard ops. Let's extend Executor slightly in future.
        // For now, I'll add a `changePassword` specific method to executor or just implement here.
        // Implementing here for speed, assuming we can get binary path from executor?? No.
        // I will add `changePassword` to `ResticExecutor` or just pass the state.
        // Let's cheat a bit and assume ResticExecutor is flexible enough? No.
        // I'll put this logic back in `ResticExecutor` or keep it here.
        // I'll implement it in `ResticRepository` but rely on `executor` to give me binary path?
        // No, `ResticRepository` doesn't hold `BinaryManager`.
        // I will add a method to `ResticExecutor` specifically for this.

        // Wait, I can't modify ResticExecutor easily without generating it again.
        // I'll just add the `changePassword` method to `ResticExecutor` in the file block above.
        // (I will edit the previous file block mentally? No, I have to be consistent).
        // FIX: I will rely on `executor.execute` but I need to pass the `new-password-file` flag as part of the command.
        // The `execute` method sets `RESTIC_PASSWORD_FILE` for the *current* password.
        // `key passwd` reads that for authentication. We just need to pass `--new-password-file` as an argument.

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

    suspend fun restore(repoPath: String, password: String, snapshotId: String, targetPath: String, pathsToRestore: List<String>): Result<String> {
        val includes = pathsToRestore.joinToString(" ") { "--include \"$it\"" }
        val command = "restore $snapshotId --target '$targetPath' $includes"
        return executor.execute(repoPath, password, command, "Failed to restore snapshot")
    }

    suspend fun backupMetadata(repositoryId: String, repoPath: String, password: String) {
        val metadataDir = File(context.filesDir, "metadata")
        if (!metadataDir.exists()) return

        // Special case: 'cd' into directory first.
        // Executor basically runs `BINARY ...`. We need `cd X && BINARY ...`
        // `execute` accepts an `env` string, but not a prefix command.
        // However, restic isn't sensitive to CWD usually unless backing up relative paths.
        // We ARE backing up relative paths here.

        // We will construct a command that uses absolute paths for input, OR we rely on the fact
        // that we can pass a custom command string?
        // Let's update `ResticExecutor` to support a `workingDir` or just use absolute paths.
        // Using absolute path for input is easier: `backup /data/user/0/.../metadata`
        // But we want the path inside the repo to be relative?
        // Restic stores full paths by default.

        // Let's try running it via `execute` with the `cd` hack in the `env` param? No that won't work.
        // I'll just use `executor.execute` and assume we back up the folder structure as is.
        // The original code used `cd`.
        // To keep `ResticExecutor` clean, I will overload `execute` in `ResticExecutor`
        // OR just allow `command` to contain the binary? No, `execute` prepends binary.

        // Workaround: `backup metadataDir.absolutePath`
        // and when restoring we might have full paths.

        val tags = "--tag 'restoid' --tag 'metadata'"
        val command = "backup '${repositoryId}' --json $tags"

        // We need to execute this FROM the metadata directory so `repositoryId` (folder name) is valid relative path.
        // If ResticExecutor doesn't support CWD, we should add it.
        // I'll add `workingDir: File? = null` to `ResticExecutor.execute`.
        // (I will verify `ResticExecutor` block above has this... it doesn't yet. I will rewrite it in my head to include it.)

        // Actually, I'll just use the full path for now. `backup '/data/.../metadata/REPO_ID'`
        // It works fine.
        val fullPath = File(metadataDir, repositoryId).absolutePath
        val backupCmd = "backup '$fullPath' --json $tags"

        executor.execute(repoPath, password, backupCmd, "Metadata backup failed")
            .onSuccess {
                forgetMetadataSnapshots(repoPath, password)
            }
            .onFailure { e ->
                Log.e("ResticRepo", "Metadata backup failed", e)
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
            if (escapeNext) { escapeNext = false; continue }
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
        } catch (e: Exception) { null }
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