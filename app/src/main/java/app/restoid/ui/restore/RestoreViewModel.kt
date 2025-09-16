package app.restoid.ui.restore

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restoid.data.AppInfoRepository
import app.restoid.data.NotificationRepository
import app.restoid.data.RepositoriesRepository
import app.restoid.data.ResticRepository
import app.restoid.data.ResticState
import app.restoid.data.SnapshotInfo
import app.restoid.model.AppInfo
import app.restoid.model.BackupDetail
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

// Simplified progress state for restore
data class RestoreProgress(
    val currentAction: String = "Initializing...",
    val error: String? = null,
    val isFinished: Boolean = false,
    val finalSummary: String = ""
)

// UI state for selecting what to restore
data class RestoreTypes(
    val apk: Boolean = true,
    val data: Boolean = true,
    val deviceProtectedData: Boolean = true,
    val externalData: Boolean = false,
    val obb: Boolean = false,
    val media: Boolean = false
)

class RestoreViewModel(
    private val application: Application,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticRepository: ResticRepository,
    private val appInfoRepository: AppInfoRepository,
    private val notificationRepository: NotificationRepository,
    val snapshotId: String
) : ViewModel() {

    private val _snapshot = MutableStateFlow<SnapshotInfo?>(null)
    val snapshot = _snapshot.asStateFlow()

    private val _backupDetails = MutableStateFlow<List<BackupDetail>>(emptyList())
    val backupDetails = _backupDetails.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _restoreTypes = MutableStateFlow(RestoreTypes())
    val restoreTypes = _restoreTypes.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring = _isRestoring.asStateFlow()

    private val _restoreProgress = MutableStateFlow(RestoreProgress())
    val restoreProgress = _restoreProgress.asStateFlow()

    private var restoreJob: Job? = null

    init {
        if (snapshotId.isNotBlank()) {
            loadSnapshotDetails(snapshotId)
        }
    }

    private fun loadSnapshotDetails(snapshotId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _backupDetails.value = emptyList()
            try {
                val repoPath = repositoriesRepository.selectedRepository.first()
                val password = repoPath?.let { repositoriesRepository.getRepositoryPassword(it) }

                if (repoPath != null && password != null) {
                    val result = resticRepository.getSnapshots(repoPath, password)
                    result.fold(
                        onSuccess = { snapshots ->
                            val foundSnapshot = snapshots.find { it.id.startsWith(snapshotId) }
                            _snapshot.value = foundSnapshot
                            foundSnapshot?.let { processSnapshot(it) }
                        },
                        onFailure = { _error.value = it.message }
                    )
                } else {
                    _error.value = "Repository or password not found"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun processSnapshot(snapshot: SnapshotInfo) {
        val packageNames = snapshot.tags.map { it.split('|').first() }

        if (packageNames.isEmpty()) {
            _backupDetails.value = emptyList()
            return
        }

        val appInfos = appInfoRepository.getAppInfoForPackages(packageNames)
        val appInfoMap = appInfos.associateBy { it.packageName }

        val details = snapshot.tags.map { tag ->
            val parts = tag.split('|')
            val packageName = parts.getOrNull(0) ?: ""
            val versionName = parts.getOrNull(1)
            val backupSize = parts.getOrNull(2)?.toLongOrNull()

            val appInfo = appInfoMap[packageName]
            val items = findBackedUpItems(snapshot, packageName)

            val finalAppInfo = appInfo ?: AppInfo(
                name = packageName,
                packageName = packageName,
                versionName = versionName ?: "N/A",
                icon = application.packageManager.defaultActivityIcon,
                apkPath = "",
                isSelected = true
            )

            BackupDetail(finalAppInfo, items, versionName, backupSize)
        }
        _backupDetails.value = details.sortedBy { it.appInfo.name.lowercase() }
    }


    private fun findBackedUpItems(snapshot: SnapshotInfo, pkg: String): List<String> {
        val items = mutableListOf<String>()
        snapshot.paths.forEach { path ->
            when {
                (path.startsWith("/data/app/") && path.contains(pkg)) -> if (!items.contains("APK")) items.add("APK")
                path == "/data/data/$pkg" -> if (!items.contains("Data")) items.add("Data")
                path == "/data/user_de/0/$pkg" -> if (!items.contains("Device Protected Data")) items.add("Device Protected Data")
                path == "/storage/emulated/0/Android/data/$pkg" -> if (!items.contains("External Data")) items.add("External Data")
                path == "/storage/emulated/0/Android/obb/$pkg" -> if (!items.contains("OBB")) items.add("OBB")
                path == "/storage/emulated/0/Android/media/$pkg" -> if (!items.contains("Media")) items.add("Media")
            }
        }
        return if (items.isNotEmpty()) items else listOf("Unknown items")
    }

    fun startRestore() {
        if (_isRestoring.value) return

        restoreJob = viewModelScope.launch(Dispatchers.IO) {
            _isRestoring.value = true
            updateProgress("Starting restore...")

            var isSuccess = false
            var summary = ""
            var tempRestoreDir: File? = null

            try {
                // --- Pre-flight checks ---
                val resticState = resticRepository.resticState.value
                val selectedRepoPath = repositoriesRepository.selectedRepository.value
                val currentSnapshot = _snapshot.value

                if (resticState !is ResticState.Installed || selectedRepoPath == null || currentSnapshot == null) {
                    throw IllegalStateException("Pre-restore checks failed: Restic not ready or no repo/snapshot selected.")
                }
                val password = repositoriesRepository.getRepositoryPassword(selectedRepoPath)
                    ?: throw IllegalStateException("Password not found for repository.")

                // --- Restore Logic ---
                // For now, restore only the first app's APK as requested.
                val firstAppPkg = currentSnapshot.tags.firstOrNull()?.split("|")?.first()
                    ?: throw IllegalStateException("Snapshot has no tagged apps to restore.")

                val apkPathToRestore = currentSnapshot.paths.find { it.startsWith("/data/app/") && it.contains(firstAppPkg) }
                    ?: throw IllegalStateException("Could not find APK path for $firstAppPkg in snapshot.")

                tempRestoreDir = File(application.cacheDir, "restic-restore-${System.currentTimeMillis()}").also { it.mkdirs() }
                updateProgress("Restoring APK for $firstAppPkg...")

                val restoreResult = resticRepository.restore(
                    repoPath = selectedRepoPath,
                    password = password,
                    snapshotId = currentSnapshot.id,
                    targetPath = tempRestoreDir.absolutePath,
                    pathsToRestore = listOf(apkPathToRestore)
                )

                restoreResult.fold(
                    onSuccess = {
                        updateProgress("Installing app...")
                        // The restored files will be in tempRestoreDir/<original_path>
                        val restoredContentDir = File(tempRestoreDir, apkPathToRestore.drop(1))
                        val apkFile = restoredContentDir.walk().find { it.isFile && it.extension == "apk" }

                        if (apkFile != null) {
                            val installResult = Shell.cmd("pm install -r -d '${apkFile.absolutePath}'").exec()
                            if (installResult.isSuccess) {
                                isSuccess = true
                                summary = "Successfully restored and installed $firstAppPkg."
                            } else {
                                isSuccess = false
                                summary = "Restore successful, but failed to install APK. Error: ${installResult.err.joinToString("\\n")}"
                            }
                        } else {
                            isSuccess = false
                            summary = "Restore successful, but couldn't find APK in the restored files."
                        }
                    },
                    onFailure = {
                        isSuccess = false
                        summary = "Restic restore command failed: ${it.message}"
                    }
                )
            } catch (e: Exception) {
                isSuccess = false
                summary = "A fatal error occurred: ${e.message}"
            } finally {
                tempRestoreDir?.deleteRecursively()
                _isRestoring.value = false
                val finalProgress = _restoreProgress.value.copy(
                    isFinished = true,
                    error = if (!isSuccess) summary else null,
                    finalSummary = summary
                )
                _restoreProgress.value = finalProgress
            }
        }
    }

    private fun updateProgress(currentAction: String) {
        _restoreProgress.update { it.copy(currentAction = currentAction) }
    }

    fun onDone() {
        _restoreProgress.value = RestoreProgress()
    }

    fun toggleRestoreAppSelection(packageName: String) {
        _backupDetails.update { currentDetails ->
            currentDetails.map { detail ->
                if (detail.appInfo.packageName == packageName) {
                    detail.copy(appInfo = detail.appInfo.copy(isSelected = !detail.appInfo.isSelected))
                } else {
                    detail
                }
            }
        }
    }

    fun toggleAllRestoreSelection() {
        _backupDetails.update { currentDetails ->
            val shouldSelectAll = currentDetails.any { !it.appInfo.isSelected }
            currentDetails.map { detail ->
                detail.copy(appInfo = detail.appInfo.copy(isSelected = shouldSelectAll))
            }
        }
    }

    fun setRestoreApk(value: Boolean) = _restoreTypes.update { it.copy(apk = value) }
    fun setRestoreData(value: Boolean) = _restoreTypes.update { it.copy(data = value) }
    fun setRestoreDeviceProtectedData(value: Boolean) = _restoreTypes.update { it.copy(deviceProtectedData = value) }
    fun setRestoreExternalData(value: Boolean) = _restoreTypes.update { it.copy(externalData = value) }
    fun setRestoreObb(value: Boolean) = _restoreTypes.update { it.copy(obb = value) }
    fun setRestoreMedia(value: Boolean) = _restoreTypes.update { it.copy(media = value) }
}

