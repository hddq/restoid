package app.restoid.ui.snapshot

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restoid.data.AppInfoRepository
import app.restoid.data.RepositoriesRepository
import app.restoid.data.ResticRepository
import app.restoid.data.SnapshotInfo
import app.restoid.model.AppInfo
import app.restoid.model.BackupDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SnapshotDetailsViewModel(
    private val application: Application,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticRepository: ResticRepository,
    private val appInfoRepository: AppInfoRepository
) : ViewModel() {

    private val _snapshot = MutableStateFlow<SnapshotInfo?>(null)
    val snapshot = _snapshot.asStateFlow()

    private val _backupDetails = MutableStateFlow<List<BackupDetail>>(emptyList())
    val backupDetails = _backupDetails.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _showConfirmForgetDialog = MutableStateFlow(false)
    val showConfirmForgetDialog = _showConfirmForgetDialog.asStateFlow()

    private val _isForgetting = MutableStateFlow(false)
    val isForgetting = _isForgetting.asStateFlow()


    fun loadSnapshotDetails(snapshotId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _backupDetails.value = emptyList() // Clear previous details
            try {
                val repoPath = repositoriesRepository.selectedRepository.first()
                val password = repoPath?.let { repositoriesRepository.getRepositoryPassword(it) }

                if (repoPath != null && password != null) {
                    val result = resticRepository.getSnapshots(repoPath, password)
                    result.fold(
                        onSuccess = { snapshots ->
                            val foundSnapshot = snapshots.find { it.id.startsWith(snapshotId) }
                            _snapshot.value = foundSnapshot
                            foundSnapshot?.let { processSnapshot(it) } // Process the found snapshot
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
        // Use tags as the source of truth for package names. Fallback for older snapshots.
        val packageNames = snapshot.tags.ifEmpty {
            snapshot.paths.mapNotNull { path ->
                val parts = path.split('/')
                // Infer package name from paths like /data/data/com.example.app
                if (parts.size >= 4 && parts[1] == "data" && (parts[2] == "data" || parts[2] == "user_de")) {
                    parts.getOrNull(3)
                } else {
                    null
                }
            }.distinct()
        }

        if (packageNames.isEmpty()) {
            _backupDetails.value = emptyList()
            return
        }

        val appInfos = appInfoRepository.getAppInfoForPackages(packageNames)
        val appInfoMap = appInfos.associateBy { it.packageName }

        val details = packageNames.map { packageName ->
            val appInfo = appInfoMap[packageName]
            val items = findBackedUpItems(snapshot, packageName)

            if (appInfo != null) {
                // App is installed or was found in cache
                BackupDetail(appInfo, items)
            } else {
                // App not installed and not in cache, create a placeholder
                val placeholderAppInfo = AppInfo(
                    name = packageName, // Use package name as the label
                    packageName = packageName,
                    icon = application.packageManager.defaultActivityIcon, // Use a generic icon
                    apkPath = "",
                    isSelected = false
                )
                BackupDetail(placeholderAppInfo, items)
            }
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


    fun onForgetSnapshot() {
        _showConfirmForgetDialog.value = true
    }

    fun confirmForgetSnapshot() {
        _showConfirmForgetDialog.value = false
        val snapshotToForget = _snapshot.value ?: return

        viewModelScope.launch {
            _isForgetting.value = true
            _error.value = null
            try {
                val repoPath = repositoriesRepository.selectedRepository.first()
                val password = repoPath?.let { repositoriesRepository.getRepositoryPassword(it) }

                if (repoPath != null && password != null) {
                    val result = resticRepository.forgetSnapshot(repoPath, password, snapshotToForget.id)
                    result.fold(
                        onSuccess = {
                            // The list will be refreshed on the home screen automatically.
                        },
                        onFailure = { _error.value = it.message }
                    )
                } else {
                    _error.value = "Repository or password not found"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isForgetting.value = false
            }
        }
    }

    fun cancelForgetSnapshot() {
        _showConfirmForgetDialog.value = false
    }
}
