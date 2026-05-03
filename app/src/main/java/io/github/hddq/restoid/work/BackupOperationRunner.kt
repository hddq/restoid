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
                shouldStop
            )
            restoidMetadataFile = File(context.cacheDir, "restoid.json")
            val json = Json { prettyPrint = true }
            restoidMetadataFile.writeText(json.encodeToString(metadata))
            pathsToBackup.add(0, restoidMetadataFile.absolutePath)

            if (pathsToBackup.size <= 1) throw IllegalStateException(context.getString(R.string.backup_error_no_files_selected))

            currentStage = 2
            val stage2Title = context.getString(
                R.string.backup_stage_running,
                stageContext.absoluteStage(currentStage),
                stageContext.totalStages
            )
            emitStageProgress(stage2Title, 0f, (currentStage - 1f) / totalStages)
            throwIfCancelled()

            fileList = File.createTempFile("restic-files-", ".txt", context.cacheDir)
            fileList.writeText(pathsToBackup.distinct().joinToString("\n"))

            passwordFile = File.createTempFile("restic-pass", ".tmp", context.cacheDir)
            passwordFile.writeText(password)

            val tags = listOf("restoid", "backup")
            val tagFlags = tags.joinToString(" ") { "--tag '$it'" }
            val excludeFlags = excludePatterns.distinct().joinToString(" ") { pattern -> "--exclude $pattern" }
            val envPrefix = buildShellEnvironmentPrefix(repositoryEnvironment)
            val resticOptionFlags = buildResticOptionFlags(repositoryResticOptions)

            val command = buildString {
                if (envPrefix.isNotEmpty()) append(envPrefix).append(' ')
                append("RESTIC_PASSWORD_FILE=").append(shellQuote(passwordFile.absolutePath)).append(' ')
                append("RESTIC_CACHE_DIR=").append(shellQuote(File(context.cacheDir, "restic").absolutePath)).append(' ')
                append(shellQuote(resticState.path)).append(' ')
                append("--retry-lock 5s ")
                if (resticOptionFlags.isNotEmpty()) append(resticOptionFlags).append(' ')
                append("-r ")
                append(shellQuote(currentRepoPath)).append(' ')
                append("backup --files-from ")
                append(shellQuote(fileList.absolutePath))
                append(" --json --verbose=2 ")
                append(tagFlags)
                append(' ')
                append(excludeFlags)
            }

            val stdoutCallback = object : CallbackList<String>() {
                override fun onAddElement(line: String) {
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
            val result = Shell.cmd(command).to(stdoutCallback, stderr).exec()
            throwIfCancelled()

            if (!result.isSuccess || snapshotId == null) {
                val errorOutput = stderr.joinToString("\n")
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
                restoidMetadataFile.copyTo(destFile, overwrite = true)
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
                    R.plurals.backup_summary_success,
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
        val backupTypesList = mutableListOf<String>().apply {
            if (backupTypes.apk) add("apk")
            if (backupTypes.data) add("data")
            if (backupTypes.deviceProtectedData) add("user_de")
            if (backupTypes.externalData) add("external_data")
            if (backupTypes.obb) add("obb")
            if (backupTypes.media) add("media")
        }

        selectedApps.forEach { app ->
            throwIfCancelled()
            val appPaths = generateFilePathsForApp(app, backupTypes)
            val existingAppPaths = appPaths.filter { Shell.cmd("[ -e '$it' ]").exec().isSuccess }
            pathsToBackup.addAll(existingAppPaths)

            if (backupTypes.data) {
                excludePatterns.add("'/data/data/${app.packageName}/cache'")
                excludePatterns.add("'/data/data/${app.packageName}/code_cache'")
            }
            if (backupTypes.externalData) {
                excludePatterns.add("'/storage/emulated/0/Android/data/${app.packageName}/cache'")
            }

            throwIfCancelled()
            val size = getDirectorySize(existingAppPaths)
            val grantedRuntimePermissions = appInfoRepository.getGrantedRuntimePermissions(app.packageName)
            appMetadataMap[app.packageName] = AppMetadata(
                size = size,
                types = backupTypesList,
                versionCode = app.versionCode,
                versionName = app.versionName,
                grantedRuntimePermissions = grantedRuntimePermissions
            )
        }
        val metadata = RestoidMetadata(apps = appMetadataMap)
        return Triple(pathsToBackup, excludePatterns, metadata)
    }

    private fun preflightChecks(
        request: BackupWorkRequest,
        selectedApps: List<AppInfo>
    ): OperationProgress? {
        if (selectedApps.isEmpty()) {
            return OperationProgress(
                isFinished = true,
                error = context.getString(R.string.error_no_apps_selected),
                finalSummary = context.getString(R.string.summary_no_apps_selected)
            )
        }

        val backupOptions = request.backupTypes
        if (!backupOptions.apk && !backupOptions.data && !backupOptions.deviceProtectedData && !backupOptions.externalData && !backupOptions.obb && !backupOptions.media) {
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

    private fun generateFilePathsForApp(app: AppInfo, backupTypes: BackupTypeSelection): List<String> {
        return mutableListOf<String>().apply {
            if (backupTypes.apk) {
                app.apkPaths.firstOrNull()?.let { path ->
                    File(path).parentFile?.absolutePath?.let { add(it) }
                }
            }
            if (backupTypes.data) add("/data/data/${app.packageName}")
            if (backupTypes.deviceProtectedData) add("/data/user_de/0/${app.packageName}")
            if (backupTypes.externalData) add("/storage/emulated/0/Android/data/${app.packageName}")
            if (backupTypes.obb) add("/storage/emulated/0/Android/obb/${app.packageName}")
            if (backupTypes.media) add("/storage/emulated/0/Android/media/${app.packageName}")
        }
    }

    private fun getDirectorySize(paths: List<String>): Long {
        if (paths.isEmpty()) return 0L
        val command = "du -sb ${paths.joinToString(" ") { "'$it'" }}"
        val result = Shell.cmd(command).exec()
        var totalSize = 0L
        if (result.isSuccess) {
            result.out.forEach { line ->
                totalSize += line.trim().split("\\s+".toRegex()).firstOrNull()?.toLongOrNull() ?: 0L
            }
        }
        return totalSize
    }
}
