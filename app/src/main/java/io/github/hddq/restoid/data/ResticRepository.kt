package io.github.hddq.restoid.data

import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import io.github.hddq.restoid.R
import io.github.hddq.restoid.model.ResticConfig
import io.github.hddq.restoid.util.buildResticOptionFlags
import io.github.hddq.restoid.util.buildShellEnvironmentPrefix
import io.github.hddq.restoid.util.isValidEnvironmentVariableName
import io.github.hddq.restoid.util.isValidResticOptionName
import io.github.hddq.restoid.util.shellQuote
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

    suspend fun getSnapshots(
        repoPath: String,
        password: String,
        environmentVariables: Map<String, String> = emptyMap(),
        resticOptions: Map<String, String> = emptyMap()
    ): Result<List<SnapshotInfo>> {
        return withContext(Dispatchers.IO) {
            val result = executor.execute(
                repoPath = repoPath,
                password = password,
                command = "snapshots --json",
                failureMessage = context.getString(R.string.restic_failure_load_snapshots),
                environmentVariables = environmentVariables,
                resticOptions = resticOptions
            )

            result.map { output ->
                val snapshots = parseSnapshotsJson(output)
                _snapshots.value = snapshots
                snapshots
            }
        }
    }

    suspend fun refreshSnapshots(
        repoPath: String,
        password: String,
        environmentVariables: Map<String, String> = emptyMap(),
        resticOptions: Map<String, String> = emptyMap()
    ) {
        getSnapshots(repoPath, password, environmentVariables, resticOptions)
    }

    suspend fun check(
        repoPath: String,
        password: String,
        readAllData: Boolean,
        environmentVariables: Map<String, String> = emptyMap(),
        resticOptions: Map<String, String> = emptyMap()
    ): Result<String> {
        val args = if (readAllData) "check --read-data" else "check"
        return executor.execute(
            repoPath,
            password,
            args,
            context.getString(R.string.restic_failure_check_repository),
            environmentVariables = environmentVariables,
            resticOptions = resticOptions
        )
    }

    suspend fun prune(
        repoPath: String,
        password: String,
        environmentVariables: Map<String, String> = emptyMap(),
        resticOptions: Map<String, String> = emptyMap()
    ): Result<String> {
        return executor.execute(
            repoPath,
            password,
            "prune",
            context.getString(R.string.restic_failure_prune_repository),
            environmentVariables = environmentVariables,
            resticOptions = resticOptions
        )
    }

    suspend fun ls(
        repoPath: String,
        password: String,
        snapshotId: String,
        environmentVariables: Map<String, String> = emptyMap(),
        resticOptions: Map<String, String> = emptyMap()
    ): Result<List<String>> {
        return withContext(Dispatchers.IO) {
            val result = executor.execute(
                repoPath = repoPath,
                password = password,
                command = "ls --json $snapshotId",
                failureMessage = context.getString(R.string.restic_failure_load_snapshots),
                environmentVariables = environmentVariables,
                resticOptions = resticOptions
            )

            result.map { output ->
                output.lines().mapNotNull { line ->
                    if (line.trim().startsWith("{")) {
                        extractJsonField(line, "path")
                    } else {
                        null
                    }
                }
            }
        }
    }

    suspend fun populateMetadataSizes(
        repoPath: String,
        password: String,
        snapshotId: String,
        metadata: io.github.hddq.restoid.model.RestoidMetadata,
        apkDirByPackage: Map<String, String> = emptyMap(),
        environmentVariables: Map<String, String> = emptyMap(),
        resticOptions: Map<String, String> = emptyMap()
    ): Result<io.github.hddq.restoid.model.RestoidMetadata> {
        return withContext(Dispatchers.IO) {
            val scanResult = scanSnapshotSizes(
                repoPath = repoPath,
                password = password,
                snapshotId = snapshotId,
                apkDirByPackage = apkDirByPackage,
                customDirectories = metadata.customDirectories.keys,
                environmentVariables = environmentVariables,
                resticOptions = resticOptions
            )
            scanResult.map { scanned ->
                applySnapshotSizesToMetadata(metadata, scanned.appSizes, scanned.customDirSizes)
            }
        }
    }

    suspend fun recoverMetadataFromSnapshot(
        repoPath: String,
        password: String,
        snapshotId: String,
        environmentVariables: Map<String, String> = emptyMap(),
        resticOptions: Map<String, String> = emptyMap()
    ): Result<io.github.hddq.restoid.model.RestoidMetadata> {
        return withContext(Dispatchers.IO) {
            val scanResult = scanSnapshotSizes(
                repoPath = repoPath,
                password = password,
                snapshotId = snapshotId,
                apkDirByPackage = emptyMap(),
                customDirectories = emptySet(),
                environmentVariables = environmentVariables,
                resticOptions = resticOptions
            )
            scanResult.mapCatching { scanned ->
                val metadataPath = scanned.restoidJsonPath
                    ?: throw IllegalStateException("restoid.json not found in snapshot $snapshotId")
                val content = dump(
                    repoPath = repoPath,
                    password = password,
                    snapshotId = snapshotId,
                    filePath = metadataPath,
                    environmentVariables = environmentVariables,
                    resticOptions = resticOptions
                ).getOrThrow()
                val recoveredMetadata = json.decodeFromString<io.github.hddq.restoid.model.RestoidMetadata>(content)
                // If custom directories exist in recovered metadata, compute their sizes from the paths seen
                val customDirSizes = if (recoveredMetadata.customDirectories.isNotEmpty()) {
                    val prefixes = recoveredMetadata.customDirectories.keys.associateWith { "${it.trimEnd('/')}/" }
                    val sizes = mutableMapOf<String, Long>()
                    for ((filePath, fileSize) in scanned.otherFileSizes) {
                        for ((dirPath, prefixWithSlash) in prefixes) {
                            if (filePath == dirPath || filePath.startsWith(prefixWithSlash)) {
                                sizes[dirPath] = (sizes[dirPath] ?: 0L) + fileSize
                                break
                            }
                        }
                    }
                    sizes
                } else {
                    emptyMap()
                }
                applySnapshotSizesToMetadata(recoveredMetadata, scanned.appSizes, customDirSizes)
            }
        }
    }

    private data class SnapshotScanData(
        val restoidJsonPath: String?,
        val appSizes: Map<String, Map<String, Long>>,
        val customDirSizes: Map<String, Long>,
        val otherFileSizes: List<Pair<String, Long>>
    )

    private suspend fun scanSnapshotSizes(
        repoPath: String,
        password: String,
        snapshotId: String,
        apkDirByPackage: Map<String, String>,
        customDirectories: Set<String>,
        environmentVariables: Map<String, String>,
        resticOptions: Map<String, String>
    ): Result<SnapshotScanData> {
        val apkPrefixToPkg = apkDirByPackage.entries.associate { (pkg, dir) ->
            "${dir.trimEnd('/')}/" to pkg
        }
        val customDirPrefixes = customDirectories.associateWith { "${it.trimEnd('/')}/" }

        var restoidJsonPath: String? = null
        val appSizes = mutableMapOf<String, MutableMap<String, Long>>()
        val customDirSizes = mutableMapOf<String, Long>()
        val otherFileSizes = mutableListOf<Pair<String, Long>>()

        val callback = object : CallbackList<String>() {
            override fun onAddElement(line: String) {
                val trimmed = line.trim()
                if (!trimmed.startsWith("{")) return
                try {
                    val jsonObj = org.json.JSONObject(trimmed)
                    if (jsonObj.optString("type") != "file") return
                    val path = jsonObj.optString("path")
                    if (path.isEmpty()) return

                    if (restoidJsonPath == null && path.endsWith("/restoid.json")) {
                        restoidJsonPath = path
                        return
                    }

                    val size = jsonObj.optLong("size", 0L)
                    if (size <= 0L) return

                    val appMatch = classifyAppFilePath(path, apkPrefixToPkg)
                    if (appMatch != null) {
                        val (pkg, component) = appMatch
                        val pkgSizes = appSizes.getOrPut(pkg) { mutableMapOf() }
                        pkgSizes[component] = (pkgSizes[component] ?: 0L) + size
                    } else if (customDirPrefixes.isNotEmpty()) {
                        for ((dirPath, prefixWithSlash) in customDirPrefixes) {
                            if (path == dirPath || path.startsWith(prefixWithSlash)) {
                                customDirSizes[dirPath] = (customDirSizes[dirPath] ?: 0L) + size
                                break
                            }
                        }
                    } else {
                        otherFileSizes.add(path to size)
                    }
                } catch (_: Exception) {
                }
            }
        }

        val result = executor.execute(
            repoPath = repoPath,
            password = password,
            command = "ls --json $snapshotId",
            failureMessage = context.getString(R.string.restic_failure_load_snapshots),
            environmentVariables = environmentVariables,
            resticOptions = resticOptions,
            stdoutCallback = callback
        )

        return result.map {
            SnapshotScanData(
                restoidJsonPath = restoidJsonPath,
                appSizes = appSizes,
                customDirSizes = customDirSizes,
                otherFileSizes = otherFileSizes
            )
        }
    }

    private fun classifyAppFilePath(
        path: String,
        apkPrefixToPkg: Map<String, String>
    ): Pair<String, String>? {
        return when {
            path.startsWith("/data/user/") -> {
                val userEnd = path.indexOf('/', 11)
                if (userEnd > 11) {
                    val pkgEnd = path.indexOf('/', userEnd + 1)
                    if (pkgEnd > userEnd + 1) {
                        path.substring(userEnd + 1, pkgEnd) to "data"
                    } else null
                } else null
            }
            path.startsWith("/data/data/") -> {
                val pkgEnd = path.indexOf('/', 11)
                if (pkgEnd > 11) {
                    path.substring(11, pkgEnd) to "data"
                } else null
            }
            path.startsWith("/data/user_de/") -> {
                val userEnd = path.indexOf('/', 14)
                if (userEnd > 14) {
                    val pkgEnd = path.indexOf('/', userEnd + 1)
                    if (pkgEnd > userEnd + 1) {
                        path.substring(userEnd + 1, pkgEnd) to "user_de"
                    } else null
                } else null
            }
            path.startsWith("/storage/emulated/") -> {
                val userEnd = path.indexOf('/', 18)
                if (userEnd > 18) {
                    when {
                        path.startsWith("/Android/data/", userEnd) -> {
                            val pkgStart = userEnd + 14
                            val pkgEnd = path.indexOf('/', pkgStart)
                            if (pkgEnd > pkgStart) path.substring(pkgStart, pkgEnd) to "external_data" else null
                        }
                        path.startsWith("/Android/obb/", userEnd) -> {
                            val pkgStart = userEnd + 13
                            val pkgEnd = path.indexOf('/', pkgStart)
                            if (pkgEnd > pkgStart) path.substring(pkgStart, pkgEnd) to "obb" else null
                        }
                        path.startsWith("/Android/media/", userEnd) -> {
                            val pkgStart = userEnd + 15
                            val pkgEnd = path.indexOf('/', pkgStart)
                            if (pkgEnd > pkgStart) path.substring(pkgStart, pkgEnd) to "media" else null
                        }
                        else -> null
                    }
                } else null
            }
            path.startsWith("/data/app/") -> {
                for ((prefixWithSlash, pkg) in apkPrefixToPkg) {
                    if (path.startsWith(prefixWithSlash)) {
                        return pkg to "apk"
                    }
                }
                val firstSlash = path.indexOf('/', 10)
                if (firstSlash > 10) {
                    val firstSeg = path.substring(10, firstSlash)
                    val appDirSeg = if (firstSeg.startsWith("~~")) {
                        val secondSlash = path.indexOf('/', firstSlash + 1)
                        if (secondSlash > firstSlash + 1) path.substring(firstSlash + 1, secondSlash) else null
                    } else {
                        firstSeg
                    }
                    if (appDirSeg != null) {
                        val dashIdx = appDirSeg.indexOf('-')
                        if (dashIdx > 0) {
                            appDirSeg.substring(0, dashIdx) to "apk"
                        } else null
                    } else null
                } else null
            }
            else -> null
        }
    }

    private fun applySnapshotSizesToMetadata(
        metadata: io.github.hddq.restoid.model.RestoidMetadata,
        appSizes: Map<String, Map<String, Long>>,
        customDirSizes: Map<String, Long>
    ): io.github.hddq.restoid.model.RestoidMetadata {
        val updatedApps = metadata.apps.mapValues { (packageName, appMeta) ->
            val detailedSizes = appSizes[packageName].orEmpty()
            val totalAppSize = detailedSizes.values.sum()
            appMeta.copy(
                size = if (totalAppSize > 0L) totalAppSize else appMeta.size,
                detailedSizes = if (detailedSizes.isNotEmpty()) detailedSizes else appMeta.detailedSizes
            )
        }
        val updatedCustomDirs = if (customDirSizes.isNotEmpty()) {
            metadata.customDirectories.mapValues { (dirPath, dirMeta) ->
                val calculatedSize = customDirSizes[dirPath]
                if (calculatedSize != null && calculatedSize > 0L) {
                    dirMeta.copy(size = calculatedSize)
                } else {
                    dirMeta
                }
            }
        } else {
            metadata.customDirectories
        }
        return metadata.copy(apps = updatedApps, customDirectories = updatedCustomDirs)
    }

    suspend fun dump(
        repoPath: String,
        password: String,
        snapshotId: String,
        filePath: String,
        environmentVariables: Map<String, String> = emptyMap(),
        resticOptions: Map<String, String> = emptyMap()
    ): Result<String> {
        return executor.execute(
            repoPath = repoPath,
            password = password,
            command = "dump $snapshotId ${shellQuote(filePath)}",
            failureMessage = context.getString(R.string.restic_executor_failure_command),
            environmentVariables = environmentVariables,
            resticOptions = resticOptions
        )
    }

    suspend fun unlock(
        repoPath: String,
        password: String,
        environmentVariables: Map<String, String> = emptyMap(),
        resticOptions: Map<String, String> = emptyMap()
    ): Result<String> {
        return executor.execute(
            repoPath,
            password,
            "unlock",
            context.getString(R.string.restic_failure_unlock_repository),
            environmentVariables = environmentVariables,
            resticOptions = resticOptions
        )
    }

    suspend fun forget(
        repoPath: String,
        password: String,
        keepLast: Int,
        keepDaily: Int,
        keepWeekly: Int,
        keepMonthly: Int,
        environmentVariables: Map<String, String> = emptyMap(),
        resticOptions: Map<String, String> = emptyMap()
    ): Result<String> {
        val forgetOptions = buildString {
            if (keepLast > 0) append(" --keep-last $keepLast")
            if (keepDaily > 0) append(" --keep-daily $keepDaily")
            if (keepWeekly > 0) append(" --keep-weekly $keepWeekly")
            if (keepMonthly > 0) append(" --keep-monthly $keepMonthly")
        }

        if (forgetOptions.isBlank()) {
            return Result.failure(Exception(context.getString(R.string.restic_failure_no_forget_policy)))
        }

        val groupingAndTagOptions = " --group-by host,tags --tag 'restoid' --tag 'backup'"
        return executor.execute(
            repoPath,
            password,
            "forget$forgetOptions$groupingAndTagOptions",
            context.getString(R.string.restic_failure_forget_snapshots),
            environmentVariables = environmentVariables,
            resticOptions = resticOptions
        )
    }

    suspend fun forgetSnapshot(
        repoPath: String,
        password: String,
        snapshotId: String,
        environmentVariables: Map<String, String> = emptyMap(),
        resticOptions: Map<String, String> = emptyMap()
    ): Result<Unit> {
        return executor.execute(
            repoPath,
            password,
            "forget $snapshotId",
            context.getString(R.string.restic_failure_delete_snapshot),
            environmentVariables = environmentVariables,
            resticOptions = resticOptions
        )
            .map {
                refreshSnapshots(repoPath, password, environmentVariables, resticOptions)
                Unit
            }
    }

    suspend fun forgetMetadataSnapshots(
        repoPath: String,
        password: String,
        environmentVariables: Map<String, String> = emptyMap(),
        resticOptions: Map<String, String> = emptyMap()
    ): Result<String> {
        val command = "forget --keep-last 5 --tag 'restoid' --tag 'metadata'"
        return executor.execute(
            repoPath,
            password,
            command,
            context.getString(R.string.restic_failure_forget_metadata),
            environmentVariables = environmentVariables,
            resticOptions = resticOptions
        )
    }

    suspend fun getConfig(
        repoPath: String,
        password: String,
        environmentVariables: Map<String, String> = emptyMap(),
        resticOptions: Map<String, String> = emptyMap()
    ): Result<ResticConfig> {
        return executor.execute(
            repoPath,
            password,
            "cat config --json --no-lock",
            context.getString(R.string.restic_failure_get_repo_config),
            environmentVariables = environmentVariables,
            resticOptions = resticOptions
        )
            .mapCatching { jsonOutput ->
                json.decodeFromString<ResticConfig>(jsonOutput)
            }
    }

    suspend fun changePassword(
        repoPath: String,
        oldPassword: String,
        newPassword: String,
        environmentVariables: Map<String, String> = emptyMap(),
        resticOptions: Map<String, String> = emptyMap()
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val newPassFile = File.createTempFile("restic-new-pass", ".tmp", context.cacheDir)
            try {
                newPassFile.writeText(newPassword)
                val command = "key passwd --new-password-file ${shellQuote(newPassFile.absolutePath)}"
                executor.execute(
                    repoPath,
                    oldPassword,
                    command,
                    context.getString(R.string.restic_failure_change_password),
                    environmentVariables = environmentVariables,
                    resticOptions = resticOptions
                )
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
        pathsToRestore: List<String>,
        environmentVariables: Map<String, String> = emptyMap(),
        resticOptions: Map<String, String> = emptyMap()
    ): Result<String> {
        val includes = pathsToRestore.joinToString(" ") { "--include ${shellQuote(it)}" }
        val command = "restore $snapshotId --target ${shellQuote(targetPath)} $includes"
        return executor.execute(
            repoPath,
            password,
            command,
            context.getString(R.string.restic_failure_restore_snapshot),
            environmentVariables = environmentVariables,
            resticOptions = resticOptions
        )
    }

    suspend fun backupMetadata(
        repositoryId: String,
        repoPath: String,
        password: String,
        environmentVariables: Map<String, String> = emptyMap(),
        resticOptions: Map<String, String> = emptyMap()
    ) {
        // Backing up metadata requires specific CD commands to avoid recursive paths in the snapshot.
        // We bypass the executor here to replicate the pre-refactor logic exactly.

        if (environmentVariables.keys.any { !isValidEnvironmentVariableName(it) }) return
        if (resticOptions.keys.any { !isValidResticOptionName(it) }) return

        val resticPath = executor.binaryPath() ?: return

        var passwordFile: File? = null
        try {
            val metadataDir = File(context.filesDir, "metadata")
            if (!metadataDir.exists() || !metadataDir.isDirectory) return

            passwordFile = File.createTempFile("restic-pass-meta", ".tmp", context.cacheDir)
            passwordFile.writeText(password)

            val tags = listOf("restoid", "metadata")
            val tagFlags = tags.joinToString(" ") { "--tag '$it'" }

            val envPrefix = buildShellEnvironmentPrefix(environmentVariables)
            val optionFlags = buildResticOptionFlags(resticOptions)

            // Use 'cd' to ensure relative paths in the backup for simpler restore.
            // This specific structure prevents absolute path recursion in the snapshot.
            val command = buildString {
                append("cd ").append(shellQuote(metadataDir.absolutePath)).append(" && ")
                if (envPrefix.isNotEmpty()) append(envPrefix).append(' ')
                append("RESTIC_PASSWORD_FILE=").append(shellQuote(passwordFile.absolutePath)).append(' ')
                append("RESTIC_CACHE_DIR=").append(shellQuote(File(context.cacheDir, if (com.topjohnwu.superuser.Shell.getShell().isRoot) "restic" else "restic-user").absolutePath)).append(' ')
                append(shellQuote(resticPath)).append(' ')
                append("--retry-lock 5s ")
                if (optionFlags.isNotEmpty()) append(optionFlags).append(' ')
                append("-r ").append(shellQuote(repoPath)).append(' ')
                append("backup ").append(shellQuote(repositoryId)).append(" --json ").append(tagFlags)
            }

            withContext(Dispatchers.IO) {
                val result = Shell.cmd(command).exec()
                var isOnlySafeErrors = true
                for (line in result.out) {
                    try {
                        val json = org.json.JSONObject(line)
                        if (json.optString("message_type") == "error") {
                            val item = json.optString("item")
                            val message = json.optJSONObject("error")?.optString("message") ?: ""
                            val isSafe = item == "/data/user/0" && message.contains("user.serial")
                            if (!isSafe) {
                                isOnlySafeErrors = false
                                break
                            }
                        }
                    } catch (e: Exception) {}
                }
                
                val isSuccess = result.isSuccess || (result.code == 3 && isOnlySafeErrors)
                if (!isSuccess) {
                    Log.e("ResticRepo", "Metadata backup failed: ${result.err.joinToString("\n")}")
                } else {
                    // After a successful metadata backup, prune the old ones.
                    val forgetResult = forgetMetadataSnapshots(repoPath, password, environmentVariables, resticOptions)
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
            val time = extractJsonField(jsonObj, "time") ?: context.getString(R.string.restic_unknown)
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
