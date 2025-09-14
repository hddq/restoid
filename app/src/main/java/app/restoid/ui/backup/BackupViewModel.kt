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

            // Pre-flight checks
            val selectedApps = _apps.value.filter { it.isSelected }
            if (selectedApps.isEmpty()) {
                _backupLogs.value = listOf("Error: No apps selected. Pick something!")
                _isBackingUp.value = false
                return@launch
            }
            val backupOptions = _backupTypes.value
            if (!backupOptions.apk && !backupOptions.data && !backupOptions.deviceProtectedData && !backupOptions.externalData && !backupOptions.obb && !backupOptions.media) {
                _backupLogs.value = listOf("Error: No backup types selected. Nothing to do.")
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

            // --- Backup Logic Starts ---
            val logOutput = mutableListOf("Starting backup for ${selectedApps.size} app(s)...")
            _backupLogs.value = logOutput.toList()

            // --- Generate file list for restic ---
            val pathsToBackup = mutableListOf<String>()
            selectedApps.forEach { app ->
                val pkg = app.packageName
                // The `pm path` gives the full path to base.apk. The actual install location is its parent dir.
                if (backupOptions.apk) File(app.apkPath).parentFile?.absolutePath?.let { pathsToBackup.add(it) }
                if (backupOptions.data) pathsToBackup.add("/data/data/$pkg")
                if (backupOptions.deviceProtectedData) pathsToBackup.add("/data/user_de/0/$pkg")
                if (backupOptions.externalData) pathsToBackup.add("/storage/emulated/0/Android/data/$pkg")
                if (backupOptions.obb) pathsToBackup.add("/storage/emulated/0/Android/obb/$pkg")
                if (backupOptions.media) pathsToBackup.add("/storage/emulated/0/Android/media/$pkg")
            }

            // Filter out paths that don't actually exist on the system
            val existingPathsToBackup = pathsToBackup.filter {
                Shell.su("[ -e '$it' ]").exec().isSuccess
            }

            if (existingPathsToBackup.isEmpty()) {
                _backupLogs.value = listOf("Error: Selected backup types resulted in no existing files/directories to back up.")
                _isBackingUp.value = false
                return@launch
            }

            logOutput.add("✅ Found ${existingPathsToBackup.size} locations to back up.")
            logOutput.add("🚀 Starting restic backup...")
            _backupLogs.value = logOutput.toList()

            // --- Run restic backup using --files-from and a temporary file ---
            val resticBinPath = resticState.path
            val sanitizedPassword = password.replace("'", "'\\''")
            // Create a temp file to hold the list of paths to back up. This is more reliable than stdin pipes.
            val fileList = File.createTempFile("restic-files-", ".txt", application.cacheDir)

            try {
                fileList.writeText(existingPathsToBackup.joinToString("\n"))

                val command = "$resticBinPath -r '$selectedRepoPath' backup --files-from '${fileList.absolutePath}' --verbose=2 --tag ${selectedApps.joinToString(",") { it.packageName }}"
                val stdout = mutableListOf<String>()
                val stderr = mutableListOf<String>()

                val result = Shell.su("RESTIC_PASSWORD='$sanitizedPassword' $command")
                    .to(stdout, stderr)
                    .exec()

                // --- Log results ---
                logOutput.add("--- Backup Finished (Exit Code: ${result.code}) ---")
                logOutput.add("--- STDOUT ---")
                logOutput.addAll(stdout.ifEmpty { listOf("(empty)") })
                logOutput.add("--- STDERR ---")
                logOutput.addAll(stderr.ifEmpty { listOf("(empty)") })
                _backupLogs.value = logOutput.toList()

            } catch (e: Exception) {
                // Catch any exceptions during file writing or command execution
                logOutput.add("--- FATAL ERROR ---")
                logOutput.add(e.message ?: "An unknown exception occurred.")
                _backupLogs.value = logOutput.toList()
            } finally {
                // Ensure the temp file is always deleted and we stop the backup state
                fileList.delete()
                _isBackingUp.value = false
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

