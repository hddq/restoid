package io.github.hddq.restoid.ui.backup

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hddq.restoid.data.*
import io.github.hddq.restoid.R
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.model.AppMetadata
import io.github.hddq.restoid.model.RestoidMetadata
import io.github.hddq.restoid.ui.shared.OperationProgress
import io.github.hddq.restoid.util.ResticOutputParser
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

data class BackupTypes(
    val apk: Boolean = true,
    val data: Boolean = true,
    val deviceProtectedData: Boolean = true,
    val externalData: Boolean = false,
    val obb: Boolean = false,
    val media: Boolean = false
)

class BackupViewModel(
    private val application: Application,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticBinaryManager: ResticBinaryManager, // Inject Manager
    private val resticRepository: ResticRepository,
    private val notificationRepository: NotificationRepository,
    private val appInfoRepository: AppInfoRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps = _apps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(true)
    val isLoadingApps = _isLoadingApps.asStateFlow()

    private val _backupTypes = MutableStateFlow(BackupTypes())
    val backupTypes = _backupTypes.asStateFlow()

    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp = _isBackingUp.asStateFlow()

    private val _backupProgress = MutableStateFlow(OperationProgress())
    val backupProgress = _backupProgress.asStateFlow()

    private var backupJob: Job? = null

    init {
        _backupTypes.value = preferencesRepository.loadBackupTypes()
        loadInstalledApps()
    }

    fun refreshAppsList() { loadInstalledApps() }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            _apps.value = appInfoRepository.getInstalledUserApps()
            _isLoadingApps.value = false
        }
    }

    fun startBackup() {
        if (_isBackingUp.value) return
        preferencesRepository.saveBackupTypes(_backupTypes.value)

        backupJob = viewModelScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            _isBackingUp.value = true
            _backupProgress.value = OperationProgress(stageTitle = application.getString(R.string.progress_initializing))

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
                val errorState = preflightChecks()
                if (errorState != null) {
                    _backupProgress.value = errorState
                    return@launch
                }

                // Use Manager for state check
                val resticState = resticBinaryManager.resticState.value as ResticState.Installed
                val selectedRepoKey = repositoriesRepository.selectedRepository.value!!
                val repository = repositoriesRepository.getRepositoryByKey(selectedRepoKey)
                    ?: throw IllegalStateException(application.getString(R.string.error_no_backup_repository_selected))
                repositoryId = repository.id

                if (repositoryId == null) throw IllegalStateException(application.getString(R.string.backup_error_repository_id_not_found))

                val currentRepoPath = repository.path
                repositoryEnvironment = repositoriesRepository.getExecutionEnvironmentVariables(selectedRepoKey)
                repositoryResticOptions = repositoriesRepository.getExecutionResticOptions(selectedRepoKey)
                val currentPassword = repositoriesRepository.getRepositoryPassword(selectedRepoKey)!!
                repoPath = currentRepoPath
                password = currentPassword
                val selectedApps = _apps.value.filter { it.isSelected }

                // --- STAGE 1: Preparing backup ---
                updateProgress(application.getString(R.string.backup_stage_preparing, currentStage, totalStages), 0f, 0f, startTime)

                val (pathsToBackup, excludePatterns, metadata) = prepareBackupData(selectedApps)
                restoidMetadataFile = File(application.cacheDir, "restoid.json")
                val json = Json { prettyPrint = true }
                restoidMetadataFile.writeText(json.encodeToString(metadata))
                pathsToBackup.add(0, restoidMetadataFile.absolutePath)

                if (pathsToBackup.size <= 1) throw IllegalStateException(application.getString(R.string.backup_error_no_files_selected))

                // --- STAGE 2: Main Backup ---
                currentStage = 2
                val stage2Title = application.getString(R.string.backup_stage_running, currentStage, totalStages)
                updateProgress(stage2Title, 0f, (currentStage - 1f) / totalStages, startTime)

                fileList = File.createTempFile("restic-files-", ".txt", application.cacheDir)
                fileList.writeText(pathsToBackup.distinct().joinToString("\n"))

                passwordFile = File.createTempFile("restic-pass", ".tmp", application.cacheDir)
                passwordFile.writeText(password)

                val tags = listOf("restoid", "backup")
                val tagFlags = tags.joinToString(" ") { "--tag '$it'" }
                val excludeFlags = excludePatterns.distinct().joinToString(" ") { pattern -> "--exclude $pattern" }
                val envPrefix = io.github.hddq.restoid.util.buildShellEnvironmentPrefix(repositoryEnvironment)
                val resticOptionFlags = io.github.hddq.restoid.util.buildResticOptionFlags(repositoryResticOptions)

                // TODO: This part should ideally move to ResticExecutor too, but it uses fileList logic.
                // For now, we construct the command here using binary path from Manager.
                val command = buildString {
                    if (envPrefix.isNotEmpty()) append(envPrefix).append(' ')
                    append("RESTIC_PASSWORD_FILE=").append(io.github.hddq.restoid.util.shellQuote(passwordFile.absolutePath)).append(' ')
                    append(io.github.hddq.restoid.util.shellQuote(resticState.path)).append(' ')
                    if (resticOptionFlags.isNotEmpty()) append(resticOptionFlags).append(' ')
                    append("-r ")
                    append(io.github.hddq.restoid.util.shellQuote(currentRepoPath)).append(' ')
                    append("backup --files-from ")
                    append(io.github.hddq.restoid.util.shellQuote(fileList.absolutePath))
                    append(" --json --verbose=2 ")
                    append(tagFlags)
                    append(' ')
                    append(excludeFlags)
                }

                val stdoutCallback = object : CallbackList<String>() {
                    override fun onAddElement(line: String) {
                        ResticOutputParser.parse(line, application)?.let { progressUpdate ->
                            if (progressUpdate.isFinished) {
                                finalSummaryProgress = progressUpdate
                                snapshotId = progressUpdate.snapshotId
                            }
                            _backupProgress.update {
                                progressUpdate.copy(
                                    isFinished = false,
                                    stageTitle = stage2Title,
                                    overallPercentage = (currentStage - 1 + progressUpdate.stagePercentage) / totalStages,
                                    elapsedTime = (System.currentTimeMillis() - startTime) / 1000
                                )
                            }
                            notificationRepository.showOperationProgressNotification(application.getString(R.string.operation_backup), _backupProgress.value)
                        }
                    }
                }
                val stderr = mutableListOf<String>()
                val result = Shell.cmd(command).to(stdoutCallback, stderr).exec()

                if (!result.isSuccess || snapshotId == null) {
                    val errorOutput = stderr.joinToString("\n")
                    throw IllegalStateException(if (errorOutput.isEmpty()) application.getString(R.string.backup_error_command_failed_with_code, result.code) else errorOutput)
                }

                // --- STAGE 3: Finalizing backup ---
                currentStage = 3
                val stage3Title = application.getString(R.string.backup_stage_finalizing, currentStage, totalStages)
                updateProgress(stage3Title, 0f, (currentStage - 1f) / totalStages, startTime)

                try {
                    val metadataDir = File(application.filesDir, "metadata/$repositoryId")
                    if (!metadataDir.exists()) metadataDir.mkdirs()
                    val destFile = File(metadataDir, "$snapshotId.json")
                    restoidMetadataFile.copyTo(destFile, overwrite = true)
                } catch (e: Exception) {
                    summary += "\n" + application.getString(R.string.backup_warning_metadata_not_saved)
                }

                resticRepository.backupMetadata(
                    repositoryId,
                    currentRepoPath,
                    currentPassword,
                    repositoryEnvironment,
                    repositoryResticOptions
                )
                updateProgress(stage3Title, 1.0f, 1.0f, startTime)

                isSuccess = true
                summary = finalSummaryProgress?.finalSummary ?: application.getString(R.string.backup_summary_success, selectedApps.size)

            } catch (e: Exception) {
                isSuccess = false
                summary = application.getString(R.string.error_fatal_with_message, e.message ?: "")
            } finally {
                fileList?.delete()
                passwordFile?.delete()
                restoidMetadataFile?.delete()

                _isBackingUp.value = false
                val finalProgress = _backupProgress.value.copy(
                    isFinished = true,
                    error = if (!isSuccess) summary else null,
                    finalSummary = summary,
                    filesNew = finalSummaryProgress?.filesNew ?: 0,
                    filesChanged = finalSummaryProgress?.filesChanged ?: 0,
                    dataAdded = finalSummaryProgress?.dataAdded ?: 0,
                    totalDuration = finalSummaryProgress?.totalDuration ?: ((System.currentTimeMillis() - startTime) / 1000.0)
                )
                _backupProgress.value = finalProgress
                notificationRepository.showOperationFinishedNotification(application.getString(R.string.operation_backup), isSuccess, summary)

                if (isSuccess && repoPath != null && password != null) {
                    launch {
                        resticRepository.refreshSnapshots(
                            repoPath,
                            password,
                            repositoryEnvironment,
                            repositoryResticOptions
                        )
                    }
                }
            }
        }
    }

    private suspend fun prepareBackupData(selectedApps: List<AppInfo>): Triple<MutableList<String>, List<String>, RestoidMetadata> {
        val pathsToBackup = mutableListOf<String>()
        val excludePatterns = mutableListOf<String>()
        val appMetadataMap = mutableMapOf<String, AppMetadata>()
        val backupTypesList = mutableListOf<String>().apply {
            if (_backupTypes.value.apk) add("apk")
            if (_backupTypes.value.data) add("data")
            if (_backupTypes.value.deviceProtectedData) add("user_de")
            if (_backupTypes.value.externalData) add("external_data")
            if (_backupTypes.value.obb) add("obb")
            if (_backupTypes.value.media) add("media")
        }

        selectedApps.forEach { app ->
            val appPaths = generateFilePathsForApp(app)
            val existingAppPaths = appPaths.filter { Shell.cmd("[ -e '$it' ]").exec().isSuccess }
            pathsToBackup.addAll(existingAppPaths)

            if (_backupTypes.value.data) {
                excludePatterns.add("'/data/data/${app.packageName}/cache'")
                excludePatterns.add("'/data/data/${app.packageName}/code_cache'")
            }
            if (_backupTypes.value.externalData) {
                excludePatterns.add("'/storage/emulated/0/Android/data/${app.packageName}/cache'")
            }

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

    private fun preflightChecks(): OperationProgress? {
        val selectedApps = _apps.value.filter { it.isSelected }
        if (selectedApps.isEmpty()) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_no_apps_selected),
                finalSummary = application.getString(R.string.summary_no_apps_selected)
            )
        }

        val backupOptions = _backupTypes.value
        if (!backupOptions.apk && !backupOptions.data && !backupOptions.deviceProtectedData && !backupOptions.externalData && !backupOptions.obb && !backupOptions.media) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_no_backup_types_selected),
                finalSummary = application.getString(R.string.summary_no_backup_types_selected)
            )
        }

        // Use Manager for check
        if (resticBinaryManager.resticState.value !is ResticState.Installed) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_restic_not_installed),
                finalSummary = application.getString(R.string.summary_restic_binary_not_installed)
            )
        }

        val selectedRepoKey = repositoriesRepository.selectedRepository.value
        if (selectedRepoKey == null) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_no_backup_repository_selected),
                finalSummary = application.getString(R.string.summary_no_backup_repository_selected)
            )
        }

        if (repositoriesRepository.getRepositoryByKey(selectedRepoKey) == null) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_no_backup_repository_selected),
                finalSummary = application.getString(R.string.summary_no_backup_repository_selected)
            )
        }

        if (repositoriesRepository.getRepositoryPassword(selectedRepoKey) == null) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_password_not_found_for_repository),
                finalSummary = application.getString(R.string.summary_password_not_found)
            )
        }

        val repository = repositoriesRepository.getRepositoryByKey(selectedRepoKey)
        if (repository?.backendType == RepositoryBackendType.SFTP && !repositoriesRepository.hasSftpPassword(selectedRepoKey)) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_sftp_password_not_found_for_repository),
                finalSummary = application.getString(R.string.summary_sftp_password_not_found)
            )
        }

        return null
    }

    private fun generateFilePathsForApp(app: AppInfo): List<String> {
        val backupOptions = _backupTypes.value
        return mutableListOf<String>().apply {
            if (backupOptions.apk) {
                app.apkPaths.firstOrNull()?.let { path ->
                    File(path).parentFile?.absolutePath?.let { add(it) }
                }
            }
            if (backupOptions.data) add("/data/data/${app.packageName}")
            if (backupOptions.deviceProtectedData) add("/data/user_de/0/${app.packageName}")
            if (backupOptions.externalData) add("/storage/emulated/0/Android/data/${app.packageName}")
            if (backupOptions.obb) add("/storage/emulated/0/Android/obb/${app.packageName}")
            if (backupOptions.media) add("/storage/emulated/0/Android/media/${app.packageName}")
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

    private fun updateProgress(stageTitle: String, stagePercentage: Float = 0f, overallPercentage: Float = 0f, startTime: Long) {
        _backupProgress.update {
            it.copy(
                stageTitle = stageTitle,
                stagePercentage = stagePercentage,
                overallPercentage = overallPercentage,
                elapsedTime = (System.currentTimeMillis() - startTime) / 1000
            )
        }
    }

    fun toggleAppSelection(packageName: String) {
        _apps.update { currentApps ->
            currentApps.map { app ->
                if (app.packageName == packageName) app.copy(isSelected = !app.isSelected) else app
            }
        }
    }

    fun toggleAll() {
        _apps.update { currentApps ->
            val shouldSelectAll = currentApps.any { !it.isSelected }
            currentApps.map { it.copy(isSelected = shouldSelectAll) }
        }
    }

    fun onDone() { _backupProgress.value = OperationProgress() }

    fun setBackupApk(value: Boolean) = _backupTypes.update { it.copy(apk = value) }
    fun setBackupData(value: Boolean) = _backupTypes.update { it.copy(data = value) }
    fun setBackupDeviceProtectedData(value: Boolean) = _backupTypes.update { it.copy(deviceProtectedData = value) }
    fun setBackupExternalData(value: Boolean) = _backupTypes.update { it.copy(externalData = value) }
    fun setBackupObb(value: Boolean) = _backupTypes.update { it.copy(obb = value) }
    fun setBackupMedia(value: Boolean) = _backupTypes.update { it.copy(media = value) }
}
