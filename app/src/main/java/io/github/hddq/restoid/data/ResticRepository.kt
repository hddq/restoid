package io.github.hddq.restoid.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.topjohnwu.superuser.Shell
import io.github.hddq.restoid.BuildConfig
import io.github.hddq.restoid.model.ResticConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL

// Represents the state of the restic binary
sealed class ResticState {
    object Idle : ResticState() // Not checked yet
    object NotInstalled : ResticState()
    data class Downloading(val progress: Float) : ResticState() // Progress from 0.0 to 1.0
    object Extracting : ResticState() // State for when decompression is in progress
    data class Installed(val path: String, val version: String) : ResticState()
    data class Error(val message: String) : ResticState()
}

class ResticRepository(private val context: Context) {

    private val _resticState = MutableStateFlow<ResticState>(ResticState.Idle)
    val resticState = _resticState.asStateFlow()

    private val _snapshots = MutableStateFlow<List<SnapshotInfo>?>(null)
    val snapshots = _snapshots.asStateFlow()

    private val _latestResticVersion = MutableStateFlow<String?>(null)
    val latestResticVersion = _latestResticVersion.asStateFlow()

    private val resticFile = File(context.filesDir, "restic")
    val stableResticVersion = "0.18.0"
    private val json = Json { ignoreUnknownKeys = true }


    // Check the status of the restic binary by executing it
    suspend fun checkResticStatus() {
        withContext(Dispatchers.IO) {
            if (BuildConfig.IS_BUNDLED && !resticFile.exists()) {
                ensureBundledResticIsReady()
            }

            if (resticFile.exists() && resticFile.canExecute()) {
                // Execute the binary with the 'version' command
                val result = Shell.cmd("${resticFile.absolutePath} version").exec()
                if (result.isSuccess) {
                    // Get the first line of the output, e.g., "restic 0.18.0 ..."
                    val versionOutput = result.out.firstOrNull()?.trim() ?: "Unknown version"
                    _resticState.value = ResticState.Installed(resticFile.absolutePath, versionOutput)
                } else {
                    // If the command fails, the binary might be corrupted
                    _resticState.value = ResticState.Error("Binary corrupted or invalid")
                    resticFile.delete() // Clean up the bad file
                }
            } else {
                if (_resticState.value !is ResticState.Error) {
                    if (BuildConfig.IS_BUNDLED) {
                        _resticState.value = ResticState.Error("Bundled binary not found or not executable.")
                    } else {
                        _resticState.value = ResticState.NotInstalled
                    }
                }
            }
        }
    }

    private suspend fun ensureBundledResticIsReady() {
        withContext(Dispatchers.IO) {
            if (resticFile.exists()) return@withContext

            try {
                _resticState.value = ResticState.Extracting

                // The binary is now in the native library directory as librestic.so
                val nativeLibDir = context.applicationInfo.nativeLibraryDir
                val sourceBinary = File(nativeLibDir, "librestic.so")

                Log.d("ResticRepository", "Looking for bundled restic at: ${sourceBinary.absolutePath}")

                if (!sourceBinary.exists()) {
                    // Fallback: Try to find it in the specific ABI directory
                    val abi = Build.SUPPORTED_ABIS[0]
                    val altSourceBinary = File(nativeLibDir, "../$abi/librestic.so")

                    if (altSourceBinary.exists()) {
                        Log.d("ResticRepository", "Found bundled restic at alternative location: ${altSourceBinary.absolutePath}")
                        // Copy the binary from native lib directory to app's files directory
                        altSourceBinary.inputStream().use { input ->
                            FileOutputStream(resticFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    } else {
                        throw Exception("Bundled restic binary not found in native library directory")
                    }
                } else {
                    // Copy the binary from native lib directory to app's files directory
                    sourceBinary.inputStream().use { input ->
                        FileOutputStream(resticFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                // Make it executable
                resticFile.setExecutable(true)
                Log.d("ResticRepository", "Bundled restic copied to ${resticFile.absolutePath}")

            } catch (e: Exception) {
                Log.e("ResticRepository", "Failed to prepare bundled restic binary", e)
                _resticState.value = ResticState.Error("Failed to prepare bundled binary: ${e.message}")
            }
        }
    }

    suspend fun check(repoPath: String, password: String, readAllData: Boolean): Result<String> {
        return withContext(Dispatchers.IO) {
            val command = if (readAllData) "check --read-data" else "check"
            executeResticCommand(
                repoPath = repoPath,
                password = password,
                command = command,
                failureMessage = "Failed to check repository"
            )
        }
    }

    suspend fun prune(repoPath: String, password: String): Result<String> {
        return withContext(Dispatchers.IO) {
            executeResticCommand(
                repoPath = repoPath,
                password = password,
                command = "prune",
                failureMessage = "Failed to prune repository"
            )
        }
    }

    suspend fun forget(
        repoPath: String,
        password: String,
        keepLast: Int,
        keepDaily: Int,
        keepWeekly: Int,
        keepMonthly: Int
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            val forgetOptions = buildString {
                if (keepLast > 0) append(" --keep-last $keepLast")
                if (keepDaily > 0) append(" --keep-daily $keepDaily")
                if (keepWeekly > 0) append(" --keep-weekly $keepWeekly")
                if (keepMonthly > 0) append(" --keep-monthly $keepMonthly")
            }

            // The forget command requires at least one policy option
            if (forgetOptions.isBlank()) {
                return@withContext Result.failure(Exception("No 'keep' policy was specified for the forget operation."))
            }

            // Always apply to snapshots with 'restoid' and 'backup' tags to protect metadata snapshots
            val tagOptions = " --tag 'restoid' --tag 'backup'"

            executeResticCommand(
                repoPath = repoPath,
                password = password,
                command = "forget$forgetOptions$tagOptions",
                failureMessage = "Failed to forget snapshots"
            )
        }
    }

    suspend fun forgetMetadataSnapshots(repoPath: String, password: String): Result<String> {
        return withContext(Dispatchers.IO) {
            val forgetOptions = "--keep-last 5"
            val tagOptions = "--tag 'restoid' --tag 'metadata'"
            val command = "forget $forgetOptions $tagOptions"
            executeResticCommand(
                repoPath = repoPath,
                password = password,
                command = command,
                failureMessage = "Failed to forget metadata snapshots"
            )
        }
    }

    suspend fun unlock(repoPath: String, password: String): Result<String> {
        return withContext(Dispatchers.IO) {
            executeResticCommand(
                repoPath = repoPath,
                password = password,
                command = "unlock",
                failureMessage = "Failed to unlock repository"
            )
        }
    }

    private suspend fun executeResticCommand(
        repoPath: String,
        password: String,
        command: String,
        failureMessage: String
    ): Result<String> {
        if (resticState.value !is ResticState.Installed) {
            return Result.failure(Exception("Restic not installed"))
        }

        val resticPath = (resticState.value as ResticState.Installed).path
        val passwordFile = File.createTempFile("restic-pass", ".tmp", context.cacheDir)

        try {
            passwordFile.writeText(password)
            val cmd =
                "RESTIC_PASSWORD_FILE='${passwordFile.absolutePath}' $resticPath -r '$repoPath' $command"
            val result = Shell.cmd(cmd).exec()

            return if (result.isSuccess) {
                Result.success(result.out.joinToString("\n"))
            } else {
                val errorOutput = result.err.joinToString("\n")
                val errorMsg = if (errorOutput.isEmpty()) failureMessage else errorOutput
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            return Result.failure(e)
        } finally {
            passwordFile.delete()
        }
    }

    suspend fun backupMetadata(repositoryId: String, repoPath: String, password: String) {
        val resticState = resticState.value
        if (resticState !is ResticState.Installed) return

        var passwordFile: File? = null
        try {
            val metadataDir = File(context.filesDir, "metadata")
            if (!metadataDir.exists() || !metadataDir.isDirectory) return

            passwordFile = File.createTempFile("restic-pass-meta", ".tmp", context.cacheDir)
            passwordFile.writeText(password)

            val tags = listOf("restoid", "metadata")
            val tagFlags = tags.joinToString(" ") { "--tag '$it'" }
            // Use 'cd' to ensure relative paths in the backup for simpler restore
            val command = "cd '${metadataDir.absolutePath}' && RESTIC_PASSWORD_FILE='${passwordFile.absolutePath}' ${resticState.path} -r '$repoPath' backup '$repositoryId' --json $tagFlags"

            // We don't need detailed progress here, just run it
            val result = Shell.cmd(command).exec()
            if (!result.isSuccess) {
                Log.e("ResticRepo", "Metadata backup failed: ${result.err.joinToString("\n")}")
            } else {
                // After a successful metadata backup, prune the old ones.
                // This is a "best-effort" operation, we log errors but don't fail the main backup.
                val forgetResult = forgetMetadataSnapshots(repoPath, password)
                if (forgetResult.isFailure) {
                    Log.e("ResticRepo", "Forgetting old metadata snapshots failed: ${forgetResult.exceptionOrNull()?.message}")
                } else {
                    Log.d("ResticRepo", "Successfully forgot old metadata snapshots.")
                }
            }
        } catch (e: Exception) {
            // Log this error, but don't fail the whole backup process
            Log.e("ResticRepo", "Exception during metadata backup", e)
        } finally {
            passwordFile?.delete()
        }
    }

    suspend fun getConfig(repoPath: String, password: String): Result<ResticConfig> {
        return withContext(Dispatchers.IO) {
            if (resticState.value !is ResticState.Installed) {
                return@withContext Result.failure(Exception("Restic not installed"))
            }

            val resticPath = (resticState.value as ResticState.Installed).path
            val passwordFile = File.createTempFile("restic-pass", ".tmp", context.cacheDir)

            try {
                passwordFile.writeText(password)
                val command =
                    "RESTIC_PASSWORD_FILE='${passwordFile.absolutePath}' $resticPath -r '$repoPath' cat config --json"
                val result = Shell.cmd(command).exec()

                if (result.isSuccess) {
                    val configJson = result.out.joinToString("\n")
                    try {
                        val config = Json { ignoreUnknownKeys = true }.decodeFromString<ResticConfig>(configJson)
                        Result.success(config)
                    } catch (e: Exception) {
                        Result.failure(Exception("Failed to parse repo config: ${e.message}"))
                    }
                } else {
                    val errorOutput = result.err.joinToString("\n")
                    val errorMsg =
                        if (errorOutput.isEmpty()) "Failed to get repo config" else errorOutput
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                passwordFile.delete()
            }
        }
    }

    suspend fun changePassword(
        repoPath: String,
        oldPassword: String,
        newPassword: String
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            if (resticState.value !is ResticState.Installed) {
                return@withContext Result.failure(Exception("Restic not installed"))
            }

            val resticPath = (resticState.value as ResticState.Installed).path

            // Create temporary files for passwords to avoid exposing them in logs or process list
            val oldPasswordFile = File.createTempFile("restic-old-pass", ".tmp", context.cacheDir)
            val newPasswordFile = File.createTempFile("restic-new-pass", ".tmp", context.cacheDir)

            try {
                // Write passwords to the temporary files
                oldPasswordFile.writeText(oldPassword)
                newPasswordFile.writeText(newPassword)

                // Construct the command using --password-file and --new-password-file
                val command = "$resticPath -r '$repoPath' " +
                        "--password-file '${oldPasswordFile.absolutePath}' " +
                        "key passwd --new-password-file '${newPasswordFile.absolutePath}'"

                val result = Shell.cmd(command).exec()

                if (result.isSuccess) {
                    Result.success(Unit)
                } else {
                    val errorOutput = result.err.joinToString("\n")
                    val errorMsg = if (errorOutput.isEmpty()) "Failed to change password" else errorOutput
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                // Ensure temporary files are deleted
                oldPasswordFile.delete()
                newPasswordFile.delete()
            }
        }
    }

    suspend fun fetchLatestResticVersion() {
        withContext(Dispatchers.IO) {
            try {
                _latestResticVersion.value = getLatestResticVersionFromGitHub()
            } catch (e: Exception) {
                Log.e("ResticRepository", "Failed to fetch latest restic version", e)
            }
        }
    }

    private suspend fun getLatestResticVersionFromGitHub(): String = withContext(Dispatchers.IO) {
        val url = URL("https://api.github.com/repos/restic/restic/releases/latest")
        val jsonString = url.readText()
        val jsonElement = json.parseToJsonElement(jsonString)
        val tagName = jsonElement.jsonObject["tag_name"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("tag_name not found in GitHub API response")
        tagName.removePrefix("v")
    }

    suspend fun downloadAndInstallLatestRestic() {
        if (BuildConfig.IS_BUNDLED) {
            _resticState.value = ResticState.Error("Download is not supported in the bundled flavor.")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                _resticState.value = ResticState.Downloading(0f)
                val latestVersion = getLatestResticVersionFromGitHub()
                Log.d("ResticRepository", "Latest restic version found from GitHub: $latestVersion")
                downloadAndInstallRestic(latestVersion)
            } catch (e: Exception) {
                _resticState.value = ResticState.Error("Could not fetch latest version: ${e.message}")
            }
        }
    }

    suspend fun downloadAndInstallRestic(versionToDownload: String = stableResticVersion) {
        if (BuildConfig.IS_BUNDLED) {
            _resticState.value = ResticState.Error("Download is not supported in the bundled flavor.")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val arch = getArchForRestic()
                if (arch == null) {
                    _resticState.value = ResticState.Error("Unsupported device architecture")
                    return@withContext
                }

                _resticState.value = ResticState.Downloading(0f)
                val url = URL("https://github.com/restic/restic/releases/download/v$versionToDownload/restic_${versionToDownload}_linux_$arch.bz2")
                val bz2File = File(context.cacheDir, "restic.bz2")
                val urlConnection = url.openConnection()
                val fileSize = urlConnection.contentLength.toLong()

                urlConnection.getInputStream().use { input ->
                    FileOutputStream(bz2File).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloadedSize = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedSize += bytesRead
                            if (fileSize > 0) {
                                val progress = downloadedSize.toFloat() / fileSize.toFloat()
                                _resticState.value = ResticState.Downloading(progress)
                            }
                        }
                    }
                }

                _resticState.value = ResticState.Extracting
                FileInputStream(bz2File).use { fileInput ->
                    BZip2CompressorInputStream(fileInput).use { bzip2Input ->
                        FileOutputStream(resticFile).use { fileOutput ->
                            bzip2Input.copyTo(fileOutput)
                        }
                    }
                }

                resticFile.setExecutable(true)
                bz2File.delete()
                checkResticStatus()

            } catch (e: Exception) {
                e.printStackTrace()
                _resticState.value = ResticState.Error(e.message ?: "An unknown error occurred")
            }
        }
    }

    suspend fun refreshSnapshots(repoPath: String, password: String) {
        withContext(Dispatchers.IO) {
            getSnapshots(repoPath, password)
        }
    }

    fun clearSnapshots() {
        _snapshots.value = null
    }

    // Execute restic snapshots command for a repository
    suspend fun getSnapshots(repoPath: String, password: String): Result<List<SnapshotInfo>> {
        return withContext(Dispatchers.IO) {
            if (resticState.value !is ResticState.Installed) {
                return@withContext Result.failure(Exception("Restic not installed"))
            }

            val resticPath = (resticState.value as ResticState.Installed).path
            val passwordFile = File.createTempFile("restic-pass", ".tmp", context.cacheDir)

            try {
                passwordFile.writeText(password)
                val command =
                    "RESTIC_PASSWORD_FILE='${passwordFile.absolutePath}' $resticPath -r '$repoPath' snapshots --json"
                val result = Shell.cmd(command).exec()

                if (result.isSuccess) {
                    val snapshots = parseSnapshotsJson(result.out.joinToString("\n"))
                    _snapshots.value = snapshots
                    Result.success(snapshots)
                } else {
                    val errorOutput = result.err.joinToString("\n")
                    val errorMsg =
                        if (errorOutput.isEmpty()) "Failed to load snapshots" else errorOutput
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                passwordFile.delete()
            }
        }
    }

    suspend fun forgetSnapshot(repoPath: String, password: String, snapshotId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            if (resticState.value !is ResticState.Installed) {
                return@withContext Result.failure(Exception("Restic not installed"))
            }

            val resticPath = (resticState.value as ResticState.Installed).path
            val passwordFile = File.createTempFile("restic-pass", ".tmp", context.cacheDir)

            try {
                passwordFile.writeText(password)
                val command =
                    "RESTIC_PASSWORD_FILE='${passwordFile.absolutePath}' $resticPath -r '$repoPath' forget $snapshotId"
                val result = Shell.cmd(command).exec()

                if (result.isSuccess) {
                    refreshSnapshots(repoPath, password)
                    Result.success(Unit)
                } else {
                    val errorOutput = result.err.joinToString("\n")
                    val errorMsg =
                        if (errorOutput.isEmpty()) "Failed to delete snapshot" else errorOutput
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                passwordFile.delete()
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
        return withContext(Dispatchers.IO) {
            if (resticState.value !is ResticState.Installed) {
                return@withContext Result.failure(Exception("Restic not installed"))
            }

            val resticPath = (resticState.value as ResticState.Installed).path
            val passwordFile = File.createTempFile("restic-pass", ".tmp", context.cacheDir)

            try {
                passwordFile.writeText(password)

                // Restic's restore command takes the paths to restore at the end of the command.
                // We use --include because it's more reliable with weird characters.
                val includes = pathsToRestore.joinToString(" ") { "--include \"$it\"" }
                val command =
                    "RESTIC_PASSWORD_FILE='${passwordFile.absolutePath}' $resticPath -r '$repoPath' restore $snapshotId --target '$targetPath' $includes"

                val result = Shell.cmd(command).exec()

                if (result.isSuccess) {
                    Result.success(result.out.joinToString("\n"))
                } else {
                    val errorOutput = result.err.joinToString("\n")
                    val errorMsg =
                        if (errorOutput.isEmpty()) "Failed to restore snapshot" else errorOutput
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                passwordFile.delete()
            }
        }
    }


    private fun parseSnapshotsJson(jsonOutput: String): List<SnapshotInfo> {
        val snapshots = mutableListOf<SnapshotInfo>()
        try {
            // Parse JSON array - each line should be a JSON object
            val lines = jsonOutput.trim().lines()
            if (lines.size == 1 && lines[0].trim().startsWith("[")) {
                // Single line JSON array
                val content = lines[0].trim().removeSurrounding("[", "]")
                if (content.isNotEmpty()) {
                    val objects = splitJsonObjects(content)
                    objects.forEach { obj ->
                        parseSnapshotObject(obj)?.let { snapshots.add(it) }
                    }
                }
            } else {
                // Multiple lines, each could be a JSON object
                lines.forEach { line ->
                    if (line.trim().startsWith("{")) {
                        parseSnapshotObject(line.trim())?.let { snapshots.add(it) }
                    }
                }
            }
        } catch (e: Exception) {
            // If parsing fails, return empty list
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
                escapeNext = false
                continue
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
                        // Skip comma and whitespace
                        while (start < content.length && (content[start] == ',' || content[start].isWhitespace())) {
                            start++
                        }
                    }
                }
            }
        }

        return objects
    }

    private fun parseSnapshotObject(jsonObj: String): SnapshotInfo? {
        try {
            // Prioritize the full ID for accuracy. Fallback to short_id if 'id' is missing.
            val id = extractJsonField(jsonObj, "id") ?: extractJsonField(jsonObj, "short_id") ?: return null
            val time = extractJsonField(jsonObj, "time") ?: "unknown"
            val paths = extractJsonArrayField(jsonObj, "paths")
            val tags = extractJsonArrayField(jsonObj, "tags")
            return SnapshotInfo(id, time, paths, tags)
        } catch (e: Exception) {
            return null
        }
    }

    private fun extractJsonField(json: String, field: String): String? {
        val pattern = "\"$field\"\\s*:\\s*\"([^\"]*)\""
        val regex = Regex(pattern)
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun extractJsonArrayField(json: String, field: String): List<String> {
        val pattern = "\"$field\"\\s*:\\s*\\[([^\\]]*)\\]"
        val regex = Regex(pattern)
        val match = regex.find(json)?.groupValues?.get(1) ?: return emptyList()
        if (match.isBlank()) return emptyList()
        return match.split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotEmpty() }
    }

    // Map Android ABI to restic's architecture name convention
    private fun getArchForRestic(): String? {
        val abi = Build.SUPPORTED_ABIS[0]
        return when {
            abi.startsWith("arm64") -> "arm64"
            abi.startsWith("armeabi") -> "arm"
            abi.startsWith("x86_64") -> "amd64"
            abi.startsWith("x86") -> "386"
            else -> null
        }
    }
}

data class SnapshotInfo(
    val id: String,
    val time: String,
    val paths: List<String>,
    val tags: List<String>
)
