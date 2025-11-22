package io.github.hddq.restoid.ui.snapshot

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hddq.restoid.data.AppInfoRepository
import io.github.hddq.restoid.data.MetadataRepository
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.ResticRepository
import io.github.hddq.restoid.data.SnapshotInfo
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.model.BackupDetail
import io.github.hddq.restoid.model.RestoidMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class ForgetResult {
    object Idle : ForgetResult()
    object Success : ForgetResult()
    data class Error(val message: String) : ForgetResult()
}

// Data class to hold the state of restore types
data class RestoreTypes(
    val apk: Boolean = true,
    val data: Boolean = true,
    val deviceProtectedData: Boolean = true,
    val externalData: Boolean = false,
    val obb: Boolean = false,
    val media: Boolean = false
)

class SnapshotDetailsViewModel(
    private val application: Application,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticRepository: ResticRepository,
    private val appInfoRepository: AppInfoRepository,
    private val metadataRepository: MetadataRepository
) : ViewModel() {

    private val _snapshot = MutableStateFlow<SnapshotInfo?>(null)
    val snapshot = _snapshot.asStateFlow()

    private val _metadata = MutableStateFlow<RestoidMetadata?>(null)
    val metadata = _metadata.asStateFlow()

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

    private val _forgetResult = MutableStateFlow<ForgetResult>(ForgetResult.Idle)
    val forgetResult = _forgetResult.asStateFlow()

    private val _restoreTypes = MutableStateFlow(RestoreTypes())
    val restoreTypes = _restoreTypes.asStateFlow()


    fun loadSnapshotDetails(snapshotId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _backupDetails.value = emptyList() // Clear previous details
            try {
                val repoPath = repositoriesRepository.selectedRepository.first()
                val password = repoPath?.let { repositoriesRepository.getRepositoryPassword(it) }
                val repo = repositoriesRepository.repositories.value.find { it.path == repoPath }


                if (repoPath != null && password != null && repo?.id != null) {
                    val loadedMetadata = metadataRepository.getMetadataForSnapshot(repo.id, snapshotId)
                    _metadata.value = loadedMetadata

                    val result = resticRepository.getSnapshots(repoPath, password)
                    result.fold(
                        onSuccess = { snapshots ->
                            val foundSnapshot = snapshots.find { it.id.startsWith(snapshotId) }
                            _snapshot.value = foundSnapshot
                            foundSnapshot?.let { processSnapshot(it, loadedMetadata) }
                        },
                        onFailure = { _error.value = it.message }
                    )
                } else {
                    _error.value = "Repository, password, or repo ID not found"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun processSnapshot(snapshot: SnapshotInfo, metadata: RestoidMetadata?) {
        val appMetadataMap = metadata?.apps ?: emptyMap()
        val packageNames = appMetadataMap.keys.toList()

        if (packageNames.isEmpty()) {
            _backupDetails.value = emptyList()
            return
        }

        val appInfos = appInfoRepository.getAppInfoForPackages(packageNames)
        val appInfoMap = appInfos.associateBy { it.packageName }

        val details = appMetadataMap.map { (packageName, appMeta) ->
            val appInfo = appInfoMap[packageName]
            val items = findBackedUpItems(snapshot, packageName)

            val isInstalled = appInfo != null
            val isDowngrade = if (isInstalled) {
                appMeta.versionCode < appInfo!!.versionCode
            } else {
                false
            }

            val finalAppInfo = appInfo ?: AppInfo(
                name = packageName,
                packageName = packageName,
                versionName = appMeta.versionName,
                versionCode = appMeta.versionCode,
                icon = application.packageManager.defaultActivityIcon,
                apkPaths = emptyList(),
                isSelected = true
            )

            BackupDetail(finalAppInfo, items, appMeta.versionName, appMeta.versionCode, appMeta.size, isDowngrade, isInstalled)
        }

        // SORTING: Size (High -> Low), then Name (A -> Z)
        _backupDetails.value = details.sortedWith(
            compareByDescending<BackupDetail> { it.backupSize ?: 0L }
                .thenBy { it.appInfo.name.lowercase() }
        )
    }


    private fun findBackedUpItems(snapshot: SnapshotInfo, pkg: String): List<String> {
        val items = mutableListOf<String>()
        snapshot.paths.forEach { path ->
            when {
                (path.startsWith("/data/app/") && path.contains("/${pkg}-")) -> if (!items.contains("APK")) items.add("APK")
                path == "/data/data/$pkg" -> if (!items.contains("Data")) items.add("Data")
                path == "/data/user_de/0/$pkg" -> if (!items.contains("Device Protected Data")) items.add("Device Protected Data")
                path == "/storage/emulated/0/Android/data/$pkg" -> if (!items.contains("External Data")) items.add("External Data")
                path == "/storage/emulated/0/Android/obb/$pkg" -> if (!items.contains("OBB")) items.add("OBB")
                path == "/storage/emulated/0/Android/media/$pkg" -> if (!items.contains("Media")) items.add("Media")
            }
        }
        return if (items.isNotEmpty()) items else listOf("Unknown items")
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
            // If any app is not selected, select all. Otherwise, deselect all.
            val shouldSelectAll = currentDetails.any { !it.appInfo.isSelected }
            currentDetails.map { detail ->
                detail.copy(appInfo = detail.appInfo.copy(isSelected = shouldSelectAll))
            }
        }
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
                val repo = repositoriesRepository.repositories.value.find { it.path == repoPath }

                if (repoPath != null && password != null && repo?.id != null) {
                    val result = resticRepository.forgetSnapshot(repoPath, password, snapshotToForget.id)
                    result.fold(
                        onSuccess = {
                            // Also delete the metadata file
                            val deleted = metadataRepository.deleteMetadataForSnapshot(repo.id, snapshotToForget.id)
                            if (deleted) {
                                // After deleting, back up the changes to the metadata folder
                                resticRepository.backupMetadata(repo.id, repoPath, password)
                            }
                            _forgetResult.value = ForgetResult.Success
                        },
                        onFailure = {
                            _error.value = it.message
                            _forgetResult.value = ForgetResult.Error(it.message ?: "Unknown error")
                        }
                    )
                } else {
                    _error.value = "Repository, password, or repo ID not found"
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

    // --- Restore Type Toggles ---
    fun setRestoreApk(value: Boolean) = _restoreTypes.update { it.copy(apk = value) }
    fun setRestoreData(value: Boolean) = _restoreTypes.update { it.copy(data = value) }
    fun setRestoreDeviceProtectedData(value: Boolean) = _restoreTypes.update { it.copy(deviceProtectedData = value) }
    fun setRestoreExternalData(value: Boolean) = _restoreTypes.update { it.copy(externalData = value) }
    fun setRestoreObb(value: Boolean) = _restoreTypes.update { it.copy(obb = value) }
    fun setRestoreMedia(value: Boolean) = _restoreTypes.update { it.copy(media = value) }
}