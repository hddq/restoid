package io.github.hddq.restoid.ui.backup

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hddq.restoid.data.*
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
            _backupProgress.value = OperationProgress(stageTitle = "Initializing...")

            var fileList: File? = null
            var passwordFile: File? = null
            var restoidMetadataFile: File? = null
            var isSuccess = false
            var summary = ""
            var finalSummaryProgress: OperationProgress? = null
            var repoPath: String? = null
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
                val selectedRepoPath = repositoriesRepository.selectedRepository.value!!
                val repository = repositoriesRepository.repositories.value.find { it.path == selectedRepoPath }
                repositoryId = repository?.id

                if (repositoryId == null) throw IllegalStateException("Repository ID not found. Cannot save metadata.")

                repoPath = selectedRepoPath
                password = repositoriesRepository.getRepositoryPassword(selectedRepoPath)!!
                val selectedApps = _apps.value.filter { it.isSelected }

                // --- STAGE 1: Preparing backup ---
                updateProgress("[${currentStage}/${totalStages}] Preparing backup...", 0f, 0f, startTime)

                val (pathsToBackup, excludePatterns, metadata) = prepareBackupData(selectedApps)
                restoidMetadataFile = File(application.cacheDir, "restoid.json")
                val json = Json { prettyPrint = true }
                restoidMetadataFile.writeText(json.encodeToString(metadata))
                pathsToBackup.add(0, restoidMetadataFile.absolutePath)

                if (pathsToBackup.size <= 1) throw IllegalStateException("No files found to back up for the selected apps.")

                // --- STAGE 2: Main Backup ---
                currentStage = 2
                val stage2Title = "[${currentStage}/${totalStages}] Backing up apps..."
                updateProgress(stage2Title, 0f, (currentStage - 1f) / totalStages, startTime)

                fileList = File.createTempFile("restic-files-", ".txt", application.cacheDir)
                fileList.writeText(pathsToBackup.distinct().joinToString("\n"))

                passwordFile = File.createTempFile("restic-pass", ".tmp", application.cacheDir)
                passwordFile.writeText(password)

                val tags = listOf("restoid", "backup")
                val tagFlags = tags.joinToString(" ") { "--tag '$it'" }
                val excludeFlags = excludePatterns.distinct().joinToString(" ") { pattern -> "--exclude $pattern" }

                // TODO: This part should ideally move to ResticExecutor too, but it uses fileList logic.
                // For now, we construct the command here using binary path from Manager.
                val command = "RESTIC_PASSWORD_FILE='${passwordFile.absolutePath}' ${resticState.path} -r '$selectedRepoPath' backup --files-from '${fileList.absolutePath}' --json --verbose=2 $tagFlags $excludeFlags"

                val stdoutCallback = object : CallbackList<String>() {
                    override fun onAddElement(line: String) {
                        ResticOutputParser.parse(line)?.let { progressUpdate ->
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
                            notificationRepository.showOperationProgressNotification("Backup", _backupProgress.value)
                        }
                    }
                }
                val stderr = mutableListOf<String>()
                val result = Shell.cmd(command).to(stdoutCallback, stderr).exec()

                if (!result.isSuccess || snapshotId == null) {
                    val errorOutput = stderr.joinToString("\n")
                    throw IllegalStateException(if (errorOutput.isEmpty()) "Restic command failed with exit code ${result.code}." else errorOutput)
                }

                // --- STAGE 3: Finalizing backup ---
                currentStage = 3
                val stage3Title = "[${currentStage}/${totalStages}] Finalizing backup..."
                updateProgress(stage3Title, 0f, (currentStage - 1f) / totalStages, startTime)

                try {
                    val metadataDir = File(application.filesDir, "metadata/$repositoryId")
                    if (!metadataDir.exists()) metadataDir.mkdirs()
                    val destFile = File(metadataDir, "$snapshotId.json")
                    restoidMetadataFile.copyTo(destFile, overwrite = true)
                } catch (e: Exception) {
                    summary += "\nWarning: Could not save backup metadata file locally."
                }

                resticRepository.backupMetadata(repositoryId, selectedRepoPath, password)
                updateProgress(stage3Title, 1.0f, 1.0f, startTime)

                isSuccess = true
                summary = finalSummaryProgress?.finalSummary ?: "Backed up ${selectedApps.size} app(s)."

            } catch (e: Exception) {
                isSuccess = false
                summary = "A fatal error occurred: ${e.message}"
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
                notificationRepository.showOperationFinishedNotification("Backup", isSuccess, summary)

                if (isSuccess && repoPath != null && password != null) {
                    launch { resticRepository.refreshSnapshots(repoPath, password) }
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
            appMetadataMap[app.packageName] = AppMetadata(
                size = size,
                types = backupTypesList,
                versionCode = app.versionCode,
                versionName = app.versionName
            )
        }
        val metadata = RestoidMetadata(apps = appMetadataMap)
        return Triple(pathsToBackup, excludePatterns, metadata)
    }

    private fun preflightChecks(): OperationProgress? {
        val selectedApps = _apps.value.filter { it.isSelected }
        if (selectedApps.isEmpty()) {
            return OperationProgress(isFinished = true, error = "No apps selected.", finalSummary = "No apps were selected.")
        }

        val backupOptions = _backupTypes.value
        if (!backupOptions.apk && !backupOptions.data && !backupOptions.deviceProtectedData && !backupOptions.externalData && !backupOptions.obb && !backupOptions.media) {
            return OperationProgress(isFinished = true, error = "No backup types selected.", finalSummary = "No backup types were selected.")
        }

        // Use Manager for check
        if (resticBinaryManager.resticState.value !is ResticState.Installed) {
            return OperationProgress(isFinished = true, error = "Restic is not installed.", finalSummary = "Restic binary is not installed.")
        }

        val selectedRepoPath = repositoriesRepository.selectedRepository.value
        if (selectedRepoPath == null) {
            return OperationProgress(isFinished = true, error = "No backup repository selected.", finalSummary = "No backup repository is selected.")
        }

        if (repositoriesRepository.getRepositoryPassword(selectedRepoPath) == null) {
            return OperationProgress(isFinished = true, error = "Password for repository not found.", finalSummary = "Could not find the password.")
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