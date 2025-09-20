package app.restoid.data

import android.content.Context
import android.os.Build
import app.restoid.model.ResticConfig
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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

    private val _snapshots = MutableStateFlow<List<SnapshotInfo>>(emptyList())
    val snapshots = _snapshots.asStateFlow()

    private val resticFile = File(context.filesDir, "restic")
    private val resticReleaseVersion = "0.18.0"

    // Check the status of the restic binary by executing it
    suspend fun checkResticStatus() {
        withContext(Dispatchers.IO) {
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
                _resticState.value = ResticState.NotInstalled
            }
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

    // Download, decompress, and verify the restic binary
    suspend fun downloadAndInstallRestic() {
        withContext(Dispatchers.IO) {
            try {
                // Determine architecture based on device ABI
                val arch = getArchForRestic()
                if (arch == null) {
                    _resticState.value = ResticState.Error("Unsupported device architecture")
                    return@withContext
                }

                // Download the bz2 compressed file and report progress
                val url = URL("https://github.com/restic/restic/releases/download/v$resticReleaseVersion/restic_${resticReleaseVersion}_linux_$arch.bz2")
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

                // Switch to extracting state
                _resticState.value = ResticState.Extracting

                // Decompress the file
                FileInputStream(bz2File).use { fileInput ->
                    BZip2CompressorInputStream(fileInput).use { bzip2Input ->
                        FileOutputStream(resticFile).use { fileOutput ->
                            bzip2Input.copyTo(fileOutput)
                        }
                    }
                }

                // Make the binary executable and clean up
                resticFile.setExecutable(true)
                bz2File.delete()

                // Verify installation by checking the version
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
            val id = extractJsonField(jsonObj, "short_id") ?: extractJsonField(jsonObj, "id")?.take(8) ?: return null
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
