package app.restoid.ui.backup

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restoid.data.RepositoriesRepository
import app.restoid.data.ResticRepository
import app.restoid.data.ResticState
import app.restoid.model.AppInfo
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private val resticRepository: ResticRepository
) : ViewModel() {

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps = _apps.asStateFlow()

    private val _backupTypes = MutableStateFlow(BackupTypes())
    val backupTypes = _backupTypes.asStateFlow()

    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp = _isBackingUp.asStateFlow()

    private val _backupLogs = MutableStateFlow<List<String>>(emptyList())
    val backupLogs = _backupLogs.asStateFlow()

    init {
        loadInstalledAppsWithRoot()
    }

    fun startBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            _isBackingUp.value = true
            _backupLogs.value = emptyList()

            val selectedApps = _apps.value.filter { it.isSelected }
            if (selectedApps.isEmpty()) {
                _backupLogs.value = listOf("Error: No apps were selected for backup. Pick something!")
                _isBackingUp.value = false
                return@launch
            }

            val resticState = resticRepository.resticState.value
            if (resticState !is ResticState.Installed) {
                _backupLogs.value = listOf("Error: Restic is not installed.")
                _isBackingUp.value = false
                return@launch
            }

            val selectedRepoPath = repositoriesRepository.selectedRepository.value
            if (selectedRepoPath == null) {
                _backupLogs.value = listOf("Error: No backup repository selected.")
                _isBackingUp.value = false
                return@launch
            }

            val password = repositoriesRepository.getRepositoryPassword(selectedRepoPath)
            if (password == null) {
                _backupLogs.value = listOf("Error: Password for repository not found.")
                _isBackingUp.value = false
                return@launch
            }

            val resticBinPath = resticState.path

            // Construct a space-separated string of all data paths to be backed up
            // Each path is quoted to handle potential special characters.
            val backupPaths = selectedApps.joinToString(" ") { "'/data/data/${it.packageName}'" }

            val sanitizedPassword = password.replace("'", "'\\''")

            // Build the final command with all the selected app data paths
            val command = "RESTIC_PASSWORD='$sanitizedPassword' $resticBinPath -r '$selectedRepoPath' backup $backupPaths --verbose=2"

            val logOutput = mutableListOf("Starting backup for ${selectedApps.size} app(s)...", "Executing command...")
            _backupLogs.value = logOutput.toList()

            val stdout = mutableListOf<String>()
            val stderr = mutableListOf<String>()

            val result = Shell.su(command).to(stdout, stderr).exec()

            val finalLogs = mutableListOf<String>()
            finalLogs.add("Backup finished with exit code: ${result.code}")
            finalLogs.add("----- STDOUT -----")
            finalLogs.addAll(stdout)
            if (stdout.isEmpty()) finalLogs.add("(empty)")
            finalLogs.add("----- STDERR -----")
            finalLogs.addAll(stderr)
            if (stderr.isEmpty()) finalLogs.add("(empty)")

            _backupLogs.value = finalLogs
            _isBackingUp.value = false
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
                                        icon = appInfo.loadIcon(pm)
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

