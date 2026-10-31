package io.github.hddq.restoid.work

import android.content.Context
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.AppInfoRepository
import io.github.hddq.restoid.data.OperationLockManager
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.RepositoryBackendType
import io.github.hddq.restoid.data.ResticBinaryManager
import io.github.hddq.restoid.data.ResticRepository
import io.github.hddq.restoid.data.ResticState
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.model.AppMetadata
import io.github.hddq.restoid.model.RestoidMetadata
import io.github.hddq.restoid.model.CustomDirectoryMetadata
import io.github.hddq.restoid.ui.shared.OperationProgress
import io.github.hddq.restoid.util.ResticOutputParser
import io.github.hddq.restoid.util.buildResticOptionFlags
import io.github.hddq.restoid.util.buildShellEnvironmentPrefix
import io.github.hddq.restoid.util.shellQuote
import kotlinx.serialization.json.Json
import java.io.File

class BackupOperationRunner(
    private val context: Context,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticBinaryManager: ResticBinaryManager,
    private val resticRepository: ResticRepository,
    private val appInfoRepository: AppInfoRepository,
    private val operationLockManager: OperationLockManager
) {

    suspend fun run(
        request: BackupWorkRequest,
        onProgress: (OperationProgress) -> Unit,
        shouldStop: () -> Boolean = { false },
        stageContext: OperationStageContext = OperationStageContext(
            completedStagesBefore = 0,
            totalStages = 3
        )
    ): OperationRunResult {
        fun throwIfCancelled() {
            if (shouldStop()) {
                throw OperationCancelledException(context.getString(R.string.operation_interrupted))
            }
        }

        val startTime = System.currentTimeMillis()
        var progressState = OperationProgress(stageTitle = context.getString(R.string.progress_initializing))
        onProgress(progressState)
        throwIfCancelled()

        var operationLockAcquired = false
        var fileList: File? = null
        var passwordFile: File? = null
        var restoidMetadataFile: File? = null
        var stagingRoot: File? = null
        var isSuccess = false
        var summary = ""
        var finalSummaryProgress: OperationProgress? = null
        var repoPath: String? = null
        var repositoryEnvironment: Map<String, String> = emptyMap()
        var repositoryResticOptions: Map<String, String> = emptyMap()
        var password: String? = null
        var snapshotId: String? = null
        var repositoryId: String? = null
        val totalStages = 3
        var currentStage = 1

        try {
            throwIfCancelled()
            val selectedApps = appInfoRepository.getAppInfoForPackages(request.selectedPackageNames)
            val errorState = preflightChecks(request, selectedApps)
            if (errorState != null) {
                return OperationRunResult(success = false, progress = errorState)
            }

            val repository = repositoriesRepository.getRepositoryByKey(request.repositoryKey)
                ?: throw IllegalStateException(context.getString(R.string.error_no_backup_repository_selected))

            operationLockManager.acquire(repository.backendType)
            operationLockAcquired = true

            val resticState = resticBinaryManager.resticState.value as ResticState.Installed
            repositoryId = repository.id
            if (repositoryId == null) throw IllegalStateException(context.getString(R.string.backup_error_repository_id_not_found))

            val currentRepoPath = repository.path
            repositoryEnvironment = repositoriesRepository.getExecutionEnvironmentVariables(request.repositoryKey)
            repositoryResticOptions = repositoriesRepository.getExecutionResticOptions(request.repositoryKey)
            val currentPassword = repositoriesRepository.getRepositoryPassword(request.repositoryKey)
                ?: throw IllegalStateException(context.getString(R.string.error_password_not_found_for_repository))
            repoPath = currentRepoPath
            password = currentPassword

            val emitStageProgress: (String, Float, Float) -> Unit = { stageTitle, stagePercentage, overallPercentage ->
                progressState = progressState.copy(
                    stageTitle = stageTitle,
                    stagePercentage = stagePercentage,
                    overallPercentage = overallPercentage,
                    elapsedTime = (System.currentTimeMillis() - startTime) / 1000,
                    isFinished = false
                )
                onProgress(progressState)
            }

            emitStageProgress(
                context.getString(
                    R.string.backup_stage_preparing,
                    stageContext.absoluteStage(currentStage),
                    stageContext.totalStages
                ),
                0f,
                0f
            )
            throwIfCancelled()

            val (pathsToBackup, excludePatterns, metadata) = prepareBackupData(
                selectedApps,
                request.backupTypes,
                request.appBackupTypes,
                request.customDirectories,
                shouldStop
            )
            restoidMetadataFile = File(context.cacheDir, "restoid.json")
            val json = Json { prettyPrint = true }
            restoidMetadataFile.writeText(json.encodeToString(metadata))
            pathsToBackup.add(0, restoidMetadataFile.absolutePath)

            if (pathsToBackup.size <= 1 && metadata.apps.values.none { "permissions" in it.types } && metadata.customDirectories.isEmpty()) {
                throw IllegalStateException(context.getString(R.string.backup_error_no_files_selected_for_items))
            }

            currentStage = 2
            val stage2Title = context.getString(
                R.string.backup_stage_running,
                stageContext.absoluteStage(currentStage),
                stageContext.totalStages
            )
            emitStageProgress(stage2Title, 0f, (currentStage - 1f) / totalStages)
            throwIfCancelled()

            val mediaRwRegex = Regex("^/mnt/media_rw/([A-Fa-f0-9-]+)(?:/(.*))?$")
            val effectivePathsToBackup = if (!Shell.getShell().isRoot && pathsToBackup.any { mediaRwRegex.matches(it) }) {
                val root = File(context.cacheDir, "restic-staging")
                Shell.cmd("rm -rf ${shellQuote(root.absolutePath)}").exec()
                val mediaRwDir = File(root, "mnt/media_rw").also { it.mkdirs() }
                stagingRoot = root
                val linkedUuids = mutableSetOf<String>()
                pathsToBackup.distinct().flatMap { path ->
                    val match = mediaRwRegex.matchEntire(path)
                    if (match != null) {
                        val uuid = match.groupValues[1]
                        val subPath = match.groupValues[2].trim('/')
                        if (linkedUuids.add(uuid)) {
                            val linkTarget = "/mnt/media_rw/$uuid"
                            val linkFile = File(mediaRwDir, uuid)
                            android.system.Os.symlink(linkTarget, linkFile.absolutePath)
                        }
                        if (subPath.isNotEmpty()) {
                            listOf("mnt/media_rw/$uuid/$subPath")
                        } else {
                            val entries = File("/mnt/media_rw/$uuid").list()
                                ?: throw IllegalStateException("Failed to read directory contents of /mnt/media_rw/$uuid")
                            entries.map { entry -> "mnt/media_rw/$uuid/$entry" }
                        }
                    } else {
                        listOf(path)
                    }
                }
            } else {
                pathsToBackup.distinct()
            }

            fileList = File.createTempFile("restic-files-", ".raw", context.cacheDir)
            fileList.writeText(
                effectivePathsToBackup.distinct().joinToString(separator = "\u0000", postfix = "\u0000")
            )

            passwordFile = File.createTempFile("restic-pass", ".tmp", context.cacheDir)
            passwordFile.writeText(password)

            val tags = listOf("restoid", "backup")
            val tagFlags = tags.joinToString(" ") { "--tag '$it'" }
            val excludeFlags = excludePatterns.distinct().joinToString(" ") { pattern -> "--exclude $pattern" }
            val envPrefix = buildShellEnvironmentPrefix(repositoryEnvironment)
            val resticOptionFlags = buildResticOptionFlags(repositoryResticOptions)

            val command = buildString {
                stagingRoot?.let { root ->
                    append("cd ").append(shellQuote(root.absolutePath)).append(" && ")
                }
                if (envPrefix.isNotEmpty()) append(envPrefix).append(' ')
                append("RESTIC_PASSWORD_FILE=").append(shellQuote(passwordFile.absolutePath)).append(' ')
                append("RESTIC_CACHE_DIR=").append(shellQuote(File(context.cacheDir, if (com.topjohnwu.superuser.Shell.getShell().isRoot) "restic" else "restic-user").absolutePath)).append(' ')
                append(shellQuote(resticState.path)).append(' ')
                append("--retry-lock 5s ")
                if (resticOptionFlags.isNotEmpty()) append(resticOptionFlags).append(' ')
                append("-r ")
                append(shellQuote(currentRepoPath)).append(' ')
                append("backup --files-from-raw ")
                append(shellQuote(fileList.absolutePath))
                append(" --json --verbose=2 ")
                append(tagFlags)
                append(' ')
                append(excludeFlags)
            }

            val errorMessages = mutableListOf<String>()
            var isOnlySafeErrors = true
            fun parseErrorJsonLine(line: String) {
                try {
                    val json = org.json.JSONObject(line)
                    if (json.optString("message_type") == "error") {
                        val item = json.optString("item")
                        val message = json.optJSONObject("error")?.optString("message") ?: ""
                        val isSafe = item == "/data/user/0" && message.contains("user.serial")
                        if (!isSafe) {
                            isOnlySafeErrors = false
                            errorMessages.add("Error in $item: $message")
                        }
                    }
                } catch (_: Exception) {}
            }

            val stdoutCallback = object : CallbackList<String>(Runnable::run) {
                override fun onAddElement(line: String) {
                    parseErrorJsonLine(line)

                    ResticOutputParser.parse(line, context)?.let { progressUpdate ->
                        if (progressUpdate.isFinished) {
                            finalSummaryProgress = progressUpdate
                            snapshotId = progressUpdate.snapshotId
                        }
                        progressState = progressUpdate.copy(
                            isFinished = false,
                            stageTitle = stage2Title,
                            overallPercentage = (currentStage - 1 + progressUpdate.stagePercentage) / totalStages,
                            elapsedTime = (System.currentTimeMillis() - startTime) / 1000
                        )
                        onProgress(progressState)
                    }
                }
            }
            val stderr = mutableListOf<String>()
            val stderrCallback = object : CallbackList<String>(Runnable::run) {
                override fun onAddElement(line: String) {
                    stderr.add(line)
                    parseErrorJsonLine(line)
                }
            }
            val result = Shell.cmd(command).to(stdoutCallback, stderrCallback).exec()
            throwIfCancelled()

            val isResticSuccess = result.isSuccess || (result.code == 3 && isOnlySafeErrors)
            if (!isResticSuccess || snapshotId == null) {
                val errorOutput = if (errorMessages.isNotEmpty()) errorMessages.joinToString("\n") else stderr.joinToString("\n")
                throw IllegalStateException(
                    if (errorOutput.isEmpty()) {
                        context.getString(R.string.backup_error_command_failed_with_code, result.code)
                    } else {
                        errorOutput
                    }
                )
            }

            currentStage = 3
            val stage3Title = context.getString(
                R.string.backup_stage_finalizing,
                stageContext.absoluteStage(currentStage),
                stageContext.totalStages
            )
            emitStageProgress(stage3Title, 0f, (currentStage - 1f) / totalStages)
            throwIfCancelled()

            try {
                val metadataDir = File(context.filesDir, "metadata/$repositoryId")
                if (!metadataDir.exists()) metadataDir.mkdirs()
                val destFile = File(metadataDir, "$snapshotId.json")

                val apkDirByPackage = selectedApps.mapNotNull { app ->
                    val apkDir = app.apkPaths.firstOrNull()?.let { File(it).parentFile?.absolutePath }
                    if (apkDir != null) app.packageName to apkDir else null
                }.toMap()

                val finalMetadata = resticRepository.populateMetadataSizes(
                    repoPath = currentRepoPath,
                    password = currentPassword,
                    snapshotId = snapshotId!!,
                    metadata = metadata,
                    apkDirByPackage = apkDirByPackage,
                    environmentVariables = repositoryEnvironment,
                    resticOptions = repositoryResticOptions
                ).getOrDefault(metadata)

                val jsonPretty = Json { prettyPrint = true }
                destFile.writeText(jsonPretty.encodeToString(finalMetadata))
            } catch (_: Exception) {
                summary += "\n" + context.getString(R.string.backup_warning_metadata_not_saved)
            }

            resticRepository.backupMetadata(
                repositoryId,
                currentRepoPath,
                currentPassword,
                repositoryEnvironment,
                repositoryResticOptions
            )
            throwIfCancelled()
            emitStageProgress(stage3Title, 1f, 1f)

            isSuccess = true
            summary = finalSummaryProgress?.finalSummary
                ?: context.resources.getQuantityString(
                    R.plurals.backup_summary_success_items,
                    selectedApps.size,
                    selectedApps.size
                )
        } catch (e: OperationCancelledException) {
            isSuccess = false
            summary = context.getString(R.string.operation_interrupted)
        } catch (e: Exception) {
            isSuccess = false
            summary = context.getString(R.string.error_fatal_with_message, e.message ?: "")
        } finally {
            if (operationLockAcquired) {
                operationLockManager.release()
            }
            fileList?.delete()
            passwordFile?.delete()
            restoidMetadataFile?.delete()
            stagingRoot?.let { root ->
                Shell.cmd("rm -rf ${shellQuote(root.absolutePath)}").exec()
            }
        }

        val finalProgress = progressState.copy(
            isFinished = true,
            error = if (!isSuccess) summary else null,
            finalSummary = summary,
            filesNew = finalSummaryProgress?.filesNew ?: 0,
            filesChanged = finalSummaryProgress?.filesChanged ?: 0,
            dataAdded = finalSummaryProgress?.dataAdded ?: 0,
            totalDuration = finalSummaryProgress?.totalDuration ?: ((System.currentTimeMillis() - startTime) / 1000.0),
            elapsedTime = (System.currentTimeMillis() - startTime) / 1000
        )

        if (isSuccess && repoPath != null && password != null) {
            resticRepository.refreshSnapshots(
                repoPath,
                password,
                repositoryEnvironment,
                repositoryResticOptions
            )
        }

        return OperationRunResult(success = isSuccess, progress = finalProgress)
    }

    private suspend fun prepareBackupData(
        selectedApps: List<AppInfo>,
        backupTypes: BackupTypeSelection,
        appBackupTypes: Map<String, BackupTypeSelection>,
        customDirectories: List<String>,
        shouldStop: () -> Boolean
    ): Triple<MutableList<String>, List<String>, RestoidMetadata> {
        fun throwIfCancelled() {
            if (shouldStop()) {
                throw OperationCancelledException(context.getString(R.string.operation_interrupted))
            }
        }

        val pathsToBackup = mutableListOf<String>()
        val excludePatterns = mutableListOf<String>()
        val appMetadataMap = mutableMapOf<String, AppMetadata>()

        selectedApps.forEach { app ->
            throwIfCancelled()
            val effectiveBackupTypes = appBackupTypes[app.packageName] ?: backupTypes
            val backupTypesList = mutableListOf<String>().apply {
                if (effectiveBackupTypes.apk) add("apk")
                if (effectiveBackupTypes.data) add("data")
                if (effectiveBackupTypes.deviceProtectedData) add("user_de")
                if (effectiveBackupTypes.externalData) add("external_data")
                if (effectiveBackupTypes.obb) add("obb")
                if (effectiveBackupTypes.media) add("media")
                if (effectiveBackupTypes.permissions) add("permissions")
            }
            val appPaths = generateFilePathsForApp(app, effectiveBackupTypes)
            val existingAppPaths = appPaths.filter { Shell.cmd("[ -e ${shellQuote(it)} ]").exec().isSuccess }
            pathsToBackup.addAll(existingAppPaths)

            if (effectiveBackupTypes.data) {
                excludePatterns.add("'/data/user/${getCurrentUserId()}/${app.packageName}/cache'")
                excludePatterns.add("'/data/user/${getCurrentUserId()}/${app.packageName}/code_cache'")
            }
            if (effectiveBackupTypes.externalData) {
                excludePatterns.add("'/storage/emulated/${getCurrentUserId()}/Android/data/${app.packageName}/cache'")
            }

            throwIfCancelled()
            val grantedRuntimePermissions = if (effectiveBackupTypes.permissions) {
                appInfoRepository.getGrantedRuntimePermissions(app.packageName)
            } else {
                emptyList()
            }
            appMetadataMap[app.packageName] = AppMetadata(
                size = 0L,
                types = backupTypesList,
                versionCode = app.versionCode,
                versionName = app.versionName,
                appName = app.name,
                grantedRuntimePermissions = grantedRuntimePermissions,
                detailedSizes = emptyMap()
            )
        }
        val customDirectoryMetadataMap = mutableMapOf<String, CustomDirectoryMetadata>()
        customDirectories.forEach { dirUriStr ->
            throwIfCancelled()
            val realPath = io.github.hddq.restoid.util.StorageUtils.resolveCustomDirectoryPath(dirUriStr)
                ?: throw IllegalStateException(context.getString(R.string.custom_directories_unresolvable, dirUriStr))
            // Resolve path for shell in case it's on an external SD card or USB drive
            val shellPath = io.github.hddq.restoid.util.StorageUtils.resolvePathForShell(realPath)
            val existing = Shell.cmd("[ -e ${shellQuote(shellPath)} ]").exec().isSuccess
            if (!existing) {
                throw IllegalStateException(context.getString(R.string.custom_directory_not_found, realPath))
            }
            pathsToBackup.add(shellPath)
            customDirectoryMetadataMap[shellPath] = CustomDirectoryMetadata(size = 0L)
        }
        val metadata = RestoidMetadata(apps = appMetadataMap, customDirectories = customDirectoryMetadataMap)
        return Triple(pathsToBackup, excludePatterns, metadata)
    }

    private fun preflightChecks(
        request: BackupWorkRequest,
        selectedApps: List<AppInfo>
    ): OperationProgress? {
        if (selectedApps.isEmpty() && request.customDirectories.isEmpty()) {
            return OperationProgress(
                isFinished = true,
                error = context.getString(R.string.error_no_items_selected),
                finalSummary = context.getString(R.string.summary_no_items_selected)
            )
        }

        if (request.customDirectories.isNotEmpty() && !Shell.getShell().isRoot && !hasAllFilesAccess()) {
            return OperationProgress(
                isFinished = true,
                error = context.getString(R.string.error_all_files_access_required),
                finalSummary = context.getString(R.string.summary_all_files_access_required)
            )
        }

        if (selectedApps.isNotEmpty() && !Shell.getShell().isRoot) {
            return OperationProgress(
                isFinished = true,
                error = context.getString(R.string.root_access_denied),
                finalSummary = context.getString(R.string.root_access_denied)
            )
        }

        val hasAnyBackupType = selectedApps.any { app ->
            val backupOptions = request.appBackupTypes[app.packageName] ?: request.backupTypes
            backupOptions.apk ||
                    backupOptions.data ||
                    backupOptions.deviceProtectedData ||
                    backupOptions.externalData ||
                    backupOptions.obb ||
                    backupOptions.media ||
                    backupOptions.permissions
        } || request.customDirectories.isNotEmpty()
        if (!hasAnyBackupType) {
            return OperationProgress(
                isFinished = true,
                error = context.getString(R.string.error_no_backup_types_selected),
                finalSummary = context.getString(R.string.summary_no_backup_types_selected)
            )
        }

        if (resticBinaryManager.resticState.value !is ResticState.Installed) {
            return OperationProgress(
                isFinished = true,
                error = context.getString(R.string.error_restic_not_installed),
                finalSummary = context.getString(R.string.summary_restic_binary_not_installed)
            )
        }

        val repository = repositoriesRepository.getRepositoryByKey(request.repositoryKey)
        if (repository == null) {
            return OperationProgress(
                isFinished = true,
                error = context.getString(R.string.error_no_backup_repository_selected),
                finalSummary = context.getString(R.string.summary_no_backup_repository_selected)
            )
        }

        if (repositoriesRepository.getRepositoryPassword(request.repositoryKey) == null) {
            return OperationProgress(
                isFinished = true,
                error = context.getString(R.string.error_password_not_found_for_repository),
                finalSummary = context.getString(R.string.summary_password_not_found)
            )
        }

        if (repository.backendType == RepositoryBackendType.SFTP && !repositoriesRepository.hasSftpCredentials(request.repositoryKey)) {
            return OperationProgress(
                isFinished = true,
                error = context.getString(R.string.error_sftp_password_not_found_for_repository),
                finalSummary = context.getString(R.string.summary_sftp_password_not_found)
            )
        }

        if (
            repository.backendType == RepositoryBackendType.REST &&
            repository.restAuthRequired &&
            !repositoriesRepository.hasRestCredentials(request.repositoryKey)
        ) {
            return OperationProgress(
                isFinished = true,
                error = context.getString(R.string.error_rest_credentials_not_found_for_repository),
                finalSummary = context.getString(R.string.summary_rest_credentials_not_found)
            )
        }

        if (
            repository.backendType == RepositoryBackendType.S3 &&
            repository.s3AuthRequired &&
            !repositoriesRepository.hasS3Credentials(request.repositoryKey)
        ) {
            return OperationProgress(
                isFinished = true,
                error = context.getString(R.string.error_s3_credentials_not_found_for_repository),
                finalSummary = context.getString(R.string.summary_s3_credentials_not_found)
            )
        }

        return null
    }

    private fun hasAllFilesAccess(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    private fun getCurrentUserId(): Int {
        return try {
            android.os.UserHandle::class.java.getMethod("myUserId").invoke(null) as Int
        } catch (_: Exception) {
            android.os.Process.myUid() / 100000
        }
    }

    private fun generateFilePathsForApp(app: AppInfo, backupTypes: BackupTypeSelection): List<String> {
        val currentUserId = getCurrentUserId()
        return mutableListOf<String>().apply {
            if (backupTypes.apk) {
                app.apkPaths.firstOrNull()?.let { path ->
                    File(path).parentFile?.absolutePath?.let { add(it) }
                }
            }
            if (backupTypes.data) add("/data/user/$currentUserId/${app.packageName}")
            if (backupTypes.deviceProtectedData) add("/data/user_de/$currentUserId/${app.packageName}")
            if (backupTypes.externalData) add("/storage/emulated/$currentUserId/Android/data/${app.packageName}")
            if (backupTypes.obb) add("/storage/emulated/$currentUserId/Android/obb/${app.packageName}")
            if (backupTypes.media) add("/storage/emulated/$currentUserId/Android/media/${app.packageName}")
        }
    }
}
