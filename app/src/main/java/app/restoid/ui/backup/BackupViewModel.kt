package app.restoid.ui.backup

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restoid.data.NotificationRepository
import app.restoid.data.RepositoriesRepository
import app.restoid.data.ResticRepository
import app.restoid.data.ResticState
import app.restoid.model.AppInfo
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import java.io.File

// Data class to hold the state of backup types
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
    private val resticRepository: ResticRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps = _apps.asStateFlow()

    private val _backupTypes = MutableStateFlow(BackupTypes())
    val backupTypes = _backupTypes.asStateFlow()

    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp = _isBackingUp.asStateFlow()

    private val _backupLogs = MutableStateFlow<List<String>>(emptyList())
    val backupLogs = _backupLogs.asStateFlow()

    private val _backupProgress = MutableStateFlow(0)
    val backupProgress = _backupProgress.asStateFlow()

    private val _currentBackupFile = MutableStateFlow<String?>(null)
    val currentBackupFile = _currentBackupFile.asStateFlow()


    init {
        loadInstalledAppsWithRoot()
    }

    fun startBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            notificationRepository.showBackupStartingNotification()
            _isBackingUp.value = true
            _backupLogs.value = emptyList()
            _backupProgress.value = 0
            _currentBackupFile.value = null
            var isSuccess = false
            var summary = ""
            val fileList = File.createTempFile("restic-files-", ".txt", application.cacheDir)
            val logFile = File.createTempFile("restic-log-", ".txt", application.cacheDir)

            try {
                // Pre-flight checks
                val selectedApps = _apps.value.filter { it.isSelected }
                if (selectedApps.isEmpty()) {
                    summary = "No apps were selected."
                    _backupLogs.value = listOf("Error: No apps selected. Pick something!")
                    isSuccess = false
                    return@launch
                }
                val backupOptions = _backupTypes.value
                if (!backupOptions.apk && !backupOptions.data && !backupOptions.deviceProtectedData && !backupOptions.externalData && !backupOptions.obb && !backupOptions.media) {
                    summary = "No backup types were selected."
                    _backupLogs.value = listOf("Error: No backup types selected. Nothing to do.")
                    isSuccess = false
                    return@launch
                }
                val resticState = resticRepository.resticState.value
                if (resticState !is ResticState.Installed) {
                    summary = "Restic binary is not installed."
                    _backupLogs.value = listOf("Error: Restic is not installed.")
                    isSuccess = false
                    return@launch
                }
                val selectedRepoPath = repositoriesRepository.selectedRepository.value
                if (selectedRepoPath == null) {
                    summary = "No backup repository is selected."
                    _backupLogs.value = listOf("Error: No backup repository selected.")
                    isSuccess = false
                    return@launch
                }
                val password = repositoriesRepository.getRepositoryPassword(selectedRepoPath)
                if (password == null) {
                    summary = "Could not find the password for the repository."
                    _backupLogs.value = listOf("Error: Password for repository not found.")
                    isSuccess = false
                    return@launch
                }

                // --- Generate file list for restic ---
                val pathsToBackup = mutableListOf<String>()
                selectedApps.forEach { app ->
                    val pkg = app.packageName
                    if (backupOptions.apk) File(app.apkPath).parentFile?.absolutePath?.let { pathsToBackup.add(it) }
                    if (backupOptions.data) pathsToBackup.add("/data/data/$pkg")
                    if (backupOptions.deviceProtectedData) pathsToBackup.add("/data/user_de/0/$pkg")
                    if (backupOptions.externalData) pathsToBackup.add("/storage/emulated/0/Android/data/$pkg")
                    if (backupOptions.obb) pathsToBackup.add("/storage/emulated/0/Android/obb/$pkg")
                    if (backupOptions.media) pathsToBackup.add("/storage/emulated/0/Android/media/$pkg")
                }

                val existingPathsToBackup = pathsToBackup.filter {
                    Shell.su("[ -e '$it' ]").exec().isSuccess
                }

                if (existingPathsToBackup.isEmpty()) {
                    summary = "Found no files to back up for the selected apps."
                    _backupLogs.value = listOf("Error: Selected backup types resulted in no existing files/directories to back up.")
                    isSuccess = false
                    return@launch
                }

                fileList.writeText(existingPathsToBackup.joinToString("\n"))

                // --- Live Log Reading Job ---
                val readerJob = launch {
                    val reader = logFile.bufferedReader()
                    while (_isBackingUp.value) {
                        val line = reader.readLine()
                        if (line != null) {
                            launch(Dispatchers.Main) { _backupLogs.value += line }
                            try {
                                val json = JSONObject(line)
                                if (json.optString("message_type") == "status") {
                                    val percent = json.optDouble("percent_done", 0.0)
                                    val progress = (percent * 100).toInt()
                                    val totalFiles = json.optInt("total_files")
                                    val filesDone = json.optInt("files_done")
                                    val currentFiles = json.optJSONArray("current_files")
                                    val currentFile = if (currentFiles != null && currentFiles.length() > 0) {
                                        File(currentFiles.getString(0)).name
                                    } else { null }

                                    launch(Dispatchers.Main) {
                                        _backupProgress.value = progress
                                        _currentBackupFile.value = currentFile
                                    }
                                    notificationRepository.updateBackupProgress(progress, "File $filesDone of $totalFiles")
                                }
                            } catch (e: JSONException) {
                                // Not a JSON progress line, just a regular log
                            }
                        } else {
                            delay(200) // Wait for more data to be written
                        }
                    }
                    reader.close()
                }

                // --- Restic Command Execution ---
                launch(Dispatchers.Main) {
                    _backupLogs.value += "✅ Found ${existingPathsToBackup.size} locations to back up."
                    _backupLogs.value += "🚀 Starting restic backup..."
                }

                val command = "${resticState.path} -r '$selectedRepoPath' backup --files-from '${fileList.absolutePath}' --json --tag ${selectedApps.joinToString(",") { it.packageName }}"
                val sanitizedPassword = password.replace("'", "'\\''")
                val fullCommand = "RESTIC_PASSWORD='$sanitizedPassword' $command > ${logFile.absolutePath} 2>&1"

                val result = Shell.su(fullCommand).exec()

                _isBackingUp.value = false // Signal reader to stop
                readerJob.join() // Wait for reader to finish

                isSuccess = result.isSuccess

                // --- Parse Summary ---
                val logLines = logFile.readLines()
                val summaryJsonString = logLines.lastOrNull { it.trim().startsWith("{\"message_type\":\"summary\"") }
                summary = if (isSuccess && summaryJsonString != null) {
                    try {
                        val json = JSONObject(summaryJsonString)
                        "Added ${json.optString("files_new", "N/A")} files, processed ${json.optString("total_files_processed", "N/A")} (${json.optString("data_added_packed", "N/A")})."
                    } catch (e: JSONException) {
                        "Backed up ${selectedApps.size} app(s) successfully."
                    }
                } else if (isSuccess) {
                    "Backed up ${selectedApps.size} app(s) successfully."
                } else {
                    "Restic command failed with exit code ${result.code}."
                }
                launch(Dispatchers.Main) { _backupLogs.value += "--- Backup Finished (Exit Code: ${result.code}) ---" }

            } catch (e: Exception) {
                _backupLogs.value += "--- FATAL ERROR ---"
                _backupLogs.value += (e.message ?: "An unknown exception occurred.")
                summary = "A fatal error occurred: ${e.message}"
                isSuccess = false
            } finally {
                fileList.delete()
                logFile.delete()
                _isBackingUp.value = false
                notificationRepository.showBackupFinishedNotification(isSuccess, summary)
            }
        }
    }


    private fun loadInstalledAppsWithRoot() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = application.packageManager
            val packageNamesResult = Shell.su("pm list packages -3").exec()

            if (packageNamesResult.isSuccess) {
                _apps.value = packageNamesResult.out
                    .map { it.removePrefix("package:").trim() }
                    .mapNotNull { packageName ->
                        try {
                            val pathResult = Shell.su("pm path $packageName").exec()
                            if (pathResult.isSuccess && pathResult.out.isNotEmpty()) {
                                val apkPath = pathResult.out.first().removePrefix("package:").trim()
                                val packageInfo: PackageInfo? = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_META_DATA)
                                packageInfo?.applicationInfo?.let { appInfo ->
                                    appInfo.sourceDir = apkPath
                                    appInfo.publicSourceDir = apkPath
                                    AppInfo(
                                        name = appInfo.loadLabel(pm).toString(),
                                        packageName = appInfo.packageName,
                                        icon = appInfo.loadIcon(pm),
                                        apkPath = apkPath
                                    )
                                }
                            } else null
                        } catch (e: Exception) {
                            null // Ignore packages that can't be processed
                        }
                    }
                    .sortedBy { it.name.lowercase() }
            } else {
                _apps.value = emptyList()
            }
        }
    }

    fun toggleAppSelection(packageName: String) {
        _apps.update { currentApps ->
            currentApps.map { app ->
                if (app.packageName == packageName) {
                    app.copy(isSelected = !app.isSelected)
                } else {
                    app
                }
            }
        }
    }

    fun toggleAll() {
        _apps.update { currentApps ->
            val shouldSelectAll = currentApps.any { !it.isSelected }
            currentApps.map { it.copy(isSelected = shouldSelectAll) }
        }
    }

    fun setBackupApk(value: Boolean) = _backupTypes.update { it.copy(apk = value) }
    fun setBackupData(value: Boolean) = _backupTypes.update { it.copy(data = value) }
    fun setBackupDeviceProtectedData(value: Boolean) = _backupTypes.update { it.copy(deviceProtectedData = value) }
    fun setBackupExternalData(value: Boolean) = _backupTypes.update { it.copy(externalData = value) }
    fun setBackupObb(value: Boolean) = _backupTypes.update { it.copy(obb = value) }
    fun setBackupMedia(value: Boolean) = _backupTypes.update { it.copy(media = value) }
}

