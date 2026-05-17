package io.github.hddq.restoid.ui.restore

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.AppInfoRepository
import io.github.hddq.restoid.data.MetadataRepository
import io.github.hddq.restoid.data.OperationType
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.RepositoryBackendType
import io.github.hddq.restoid.data.ResticBinaryManager
import io.github.hddq.restoid.data.ResticRepository
import io.github.hddq.restoid.data.ResticState
import io.github.hddq.restoid.data.SnapshotInfo
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.model.BackupDetail
import io.github.hddq.restoid.model.RestoidMetadata
import io.github.hddq.restoid.ui.shared.OperationProgress
import io.github.hddq.restoid.work.OperationWorkRepository
import io.github.hddq.restoid.work.RestoreAppSelection
import io.github.hddq.restoid.work.RestoreTypeSelection
import io.github.hddq.restoid.work.RestoreWorkRequest
import kotlinx.serialization.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Serializable
data class RestoreTypes(
    val apk: Boolean = true,
    val data: Boolean = true,
    val deviceProtectedData: Boolean = true,
    val externalData: Boolean = false,
    val obb: Boolean = false,
    val media: Boolean = false,
    val permissions: Boolean = true
) {
    fun toSelection(): RestoreTypeSelection {
        return RestoreTypeSelection(
            apk = apk,
            data = data,
            deviceProtectedData = deviceProtectedData,
            externalData = externalData,
            obb = obb,
            media = media,
            permissions = permissions
        )
    }

    fun anyEnabled(): Boolean {
        return apk || data || deviceProtectedData || externalData || obb || media || permissions
    }
}

fun RestoreTypeSelection.toUiModel(): RestoreTypes {
    return RestoreTypes(
        apk = apk,
        data = data,
        deviceProtectedData = deviceProtectedData,
        externalData = externalData,
        obb = obb,
        media = media,
        permissions = permissions
    )
}

sealed interface RestoreUiEvent {
    data object NavigateToOperationProgress : RestoreUiEvent
}

class RestoreViewModel(
    private val application: Application,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticBinaryManager: ResticBinaryManager,
    private val resticRepository: ResticRepository,
    private val appInfoRepository: AppInfoRepository,
    private val metadataRepository: MetadataRepository,
    private val preferencesRepository: io.github.hddq.restoid.data.PreferencesRepository,
    private val operationWorkRepository: OperationWorkRepository,
    val snapshotId: String
) : ViewModel() {

    private var snapshotMetadata: RestoidMetadata? = null

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

    private val _appRestoreTypes = MutableStateFlow<Map<String, RestoreTypes>>(emptyMap())
    val appRestoreTypes = _appRestoreTypes.asStateFlow()

    private val _allowDowngrade = MutableStateFlow(false)
    val allowDowngrade = _allowDowngrade.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring = _isRestoring.asStateFlow()

    private val _restoreProgress = MutableStateFlow(OperationProgress())
    val restoreProgress = _restoreProgress.asStateFlow()

    private val _operationBlocked = MutableStateFlow(false)
    val operationBlocked = _operationBlocked.asStateFlow()
    private val _uiEvents = MutableSharedFlow<RestoreUiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<RestoreUiEvent> = _uiEvents.asSharedFlow()

    init {
        _restoreTypes.value = preferencesRepository.loadRestoreTypes()
        _allowDowngrade.value = preferencesRepository.loadAllowDowngrade()
        observeOperationState()
        if (snapshotId.isNotBlank()) {
            loadSnapshotDetails(snapshotId)
        }
    }

    private fun observeOperationState() {
        viewModelScope.launch {
            operationWorkRepository.operationState.collect { state ->
                if (state.operationType == OperationType.RESTORE) {
                    _isRestoring.value = state.isRunning
                    _restoreProgress.value = state.progress
                } else {
                    _isRestoring.value = false
                    _restoreProgress.value = OperationProgress()
                }
            }
        }
    }

    private fun loadSnapshotDetails(snapshotId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _backupDetails.value = emptyList()
            try {
                val selectedRepoKey = repositoriesRepository.selectedRepository.first()
                val repo = selectedRepoKey?.let { repositoriesRepository.getRepositoryByKey(it) }
                val repoPath = repo?.path
                val password = selectedRepoKey?.let { repositoriesRepository.getRepositoryPassword(it) }
                val repoId = repo?.id

                if (repoPath != null && password != null && repoId != null) {
                    val executionEnvironment = repositoriesRepository.getExecutionEnvironmentVariables(selectedRepoKey)
                    val resticOptions = repositoriesRepository.getExecutionResticOptions(selectedRepoKey)

                    val result = resticRepository.getSnapshots(
                        repoPath,
                        password,
                        executionEnvironment,
                        resticOptions
                    )
                    result.fold(
                        onSuccess = { snapshots ->
                            val foundSnapshot = snapshots.find { it.id.startsWith(snapshotId) }
                            _snapshot.value = foundSnapshot
                            if (foundSnapshot != null) {
                                var metadata = metadataRepository.getMetadataForSnapshot(repoId, foundSnapshot.id)
                                
                                if (metadata == null) {
                                    // Attempt to recover metadata from the repository
                                    val lsResult = resticRepository.ls(repoPath, password, foundSnapshot.id, executionEnvironment, resticOptions)
                                    lsResult.fold(
                                        onSuccess = { paths ->
                                            val metadataPath = paths.find { it.endsWith("/restoid.json") }
                                            if (metadataPath != null) {
                                                val dumpResult = resticRepository.dump(repoPath, password, foundSnapshot.id, metadataPath, executionEnvironment, resticOptions)
                                                dumpResult.fold(
                                                    onSuccess = { content ->
                                                        try {
                                                            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                                                            val recoveredMetadata = json.decodeFromString<RestoidMetadata>(content)
                                                            metadataRepository.saveMetadataForSnapshot(repoId, foundSnapshot.id, recoveredMetadata)
                                                            metadata = recoveredMetadata
                                                        } catch (e: Exception) {
                                                            android.util.Log.e("RestoreViewModel", "Failed to parse recovered metadata", e)
                                                        }
                                                    },
                                                    onFailure = { android.util.Log.e("RestoreViewModel", "Failed to dump metadata: ${it.message}") }
                                                )
                                            }
                                        },
                                        onFailure = { android.util.Log.e("RestoreViewModel", "Failed to ls snapshot: ${it.message}") }
                                    )
                                }

                                snapshotMetadata = metadata
                                processSnapshot(foundSnapshot, metadata)
                            } else {
                                _error.value = application.getString(R.string.error_snapshot_not_found)
                            }
                        },
                        onFailure = { _error.value = it.message }
                    )
                } else {
                    _error.value = application.getString(R.string.error_repository_password_or_id_not_found)
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
        val packageNames = appMetadataMap.keys.toList().filter { it != application.packageName }

        if (packageNames.isEmpty()) {
            _backupDetails.value = emptyList()
            return
        }

        val appInfos = appInfoRepository.getAppInfoForPackages(packageNames)
        val appInfoMap = appInfos.associateBy { it.packageName }

        val details = appMetadataMap.filterKeys { it != application.packageName }.map { (packageName, appMeta) ->
            val appInfo = appInfoMap[packageName]
            val items = findBackedUpItems(snapshot, packageName, appMeta.grantedRuntimePermissions.isNotEmpty())

            val isInstalled = appInfo != null
            val isDowngrade = if (isInstalled) {
                appMeta.versionCode < appInfo.versionCode
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

        _backupDetails.value = details.sortedWith(
            compareByDescending<BackupDetail> { it.backupSize ?: 0L }
                .thenBy { it.appInfo.name.lowercase() }
        )
        _appRestoreTypes.value = details.associate { detail ->
            detail.appInfo.packageName to (_appRestoreTypes.value[detail.appInfo.packageName] ?: _restoreTypes.value)
        }
    }

    private fun findBackedUpItems(snapshot: SnapshotInfo, pkg: String, hasPermissionBackup: Boolean): List<String> {
        val items = mutableListOf<String>()
        snapshot.paths.forEach { path ->
            when {
                (path.startsWith("/data/app/") && path.contains("/${pkg}-")) -> if (!items.contains(application.getString(R.string.backup_type_apk))) items.add(application.getString(R.string.backup_type_apk))
                path == "/data/data/$pkg" || path.matches(Regex("^/data/user/\\d+/${Regex.escape(pkg)}$")) -> if (!items.contains(application.getString(R.string.backup_type_data))) items.add(application.getString(R.string.backup_type_data))
                path.matches(Regex("^/data/user_de/\\d+/${Regex.escape(pkg)}$")) -> if (!items.contains(application.getString(R.string.backup_type_device_protected_data))) items.add(application.getString(R.string.backup_type_device_protected_data))
                path.matches(Regex("^/storage/emulated/\\d+/Android/data/${Regex.escape(pkg)}$")) -> if (!items.contains(application.getString(R.string.backup_type_external_data))) items.add(application.getString(R.string.backup_type_external_data))
                path.matches(Regex("^/storage/emulated/\\d+/Android/obb/${Regex.escape(pkg)}$")) -> if (!items.contains(application.getString(R.string.backup_item_obb))) items.add(application.getString(R.string.backup_item_obb))
                path.matches(Regex("^/storage/emulated/\\d+/Android/media/${Regex.escape(pkg)}$")) -> if (!items.contains(application.getString(R.string.backup_item_media))) items.add(application.getString(R.string.backup_item_media))
            }
        }
        if (hasPermissionBackup && !items.contains(application.getString(R.string.backup_item_permissions))) {
            items.add(application.getString(R.string.backup_item_permissions))
        }
        return if (items.isNotEmpty()) items else listOf(application.getString(R.string.backup_item_unknown))
    }

    fun startRestore() {
        if (_isRestoring.value) return
        preferencesRepository.saveRestoreTypes(_restoreTypes.value)
        preferencesRepository.saveAllowDowngrade(_allowDowngrade.value)

        val resticState = resticBinaryManager.resticState.value
        val selectedRepoKey = repositoriesRepository.selectedRepository.value
        val selectedRepository = selectedRepoKey?.let { repositoriesRepository.getRepositoryByKey(it) }
        val selectedRepoPath = selectedRepository?.path
        val currentSnapshot = _snapshot.value
        val selectedApps = _backupDetails.value.filter { it.appInfo.isSelected && (_allowDowngrade.value || !it.isDowngrade) }

        if (resticState !is ResticState.Installed || selectedRepoPath == null || currentSnapshot == null) {
            _restoreProgress.value = OperationProgress(
                isFinished = true,
                error = application.getString(R.string.restore_error_preflight_failed),
                finalSummary = application.getString(R.string.restore_error_preflight_failed)
            )
            return
        }

        if (selectedApps.isEmpty()) {
            _restoreProgress.value = OperationProgress(
                isFinished = true,
                error = application.getString(R.string.restore_error_no_apps_selected),
                finalSummary = application.getString(R.string.restore_error_no_apps_selected)
            )
            return
        }

        if (selectedApps.none { effectiveRestoreTypes(it.appInfo.packageName).anyEnabled() }) {
            _restoreProgress.value = OperationProgress(
                isFinished = true,
                error = application.getString(R.string.restore_error_no_restore_types_selected),
                finalSummary = application.getString(R.string.restore_error_no_restore_types_selected)
            )
            return
        }

        if (selectedRepository.backendType == RepositoryBackendType.SFTP && !repositoriesRepository.hasSftpCredentials(selectedRepoKey)) {
            _restoreProgress.value = OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_sftp_password_not_found_for_repository),
                finalSummary = application.getString(R.string.summary_sftp_password_not_found)
            )
            return
        }

        if (
            selectedRepository.backendType == RepositoryBackendType.REST &&
            selectedRepository.restAuthRequired &&
            !repositoriesRepository.hasRestCredentials(selectedRepoKey)
        ) {
            _restoreProgress.value = OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_rest_credentials_not_found_for_repository),
                finalSummary = application.getString(R.string.summary_rest_credentials_not_found)
            )
            return
        }

        if (
            selectedRepository.backendType == RepositoryBackendType.S3 &&
            selectedRepository.s3AuthRequired &&
            !repositoriesRepository.hasS3Credentials(selectedRepoKey)
        ) {
            _restoreProgress.value = OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_s3_credentials_not_found_for_repository),
                finalSummary = application.getString(R.string.summary_s3_credentials_not_found)
            )
            return
        }

        val password = repositoriesRepository.getRepositoryPassword(selectedRepoKey)
        if (password == null) {
            _restoreProgress.value = OperationProgress(
                isFinished = true,
                error = application.getString(R.string.restore_error_password_not_found),
                finalSummary = application.getString(R.string.summary_password_not_found)
            )
            return
        }

        val request = RestoreWorkRequest(
            repositoryKey = selectedRepoKey,
            snapshotId = currentSnapshot.id,
            restoreTypes = _restoreTypes.value.toSelection(),
            allowDowngrade = _allowDowngrade.value,
            selectedApps = selectedApps.map {
                RestoreAppSelection(
                    packageName = it.appInfo.packageName,
                    appName = it.appInfo.name
                )
            },
            appRestoreTypes = selectedApps.associate { it.appInfo.packageName to effectiveRestoreTypes(it.appInfo.packageName).toSelection() }
        )

        viewModelScope.launch(Dispatchers.IO) {
            val enqueued = operationWorkRepository.enqueueRestore(request)
            if (enqueued) {
                _uiEvents.tryEmit(RestoreUiEvent.NavigateToOperationProgress)
            } else {
                _operationBlocked.value = true
                _restoreProgress.value = OperationProgress(
                    isFinished = true,
                    error = application.getString(R.string.error_operation_already_running),
                    finalSummary = application.getString(R.string.summary_operation_already_running)
                )
            }
        }
    }

    fun onDone() {
        operationWorkRepository.clearFinished(OperationType.RESTORE)
        _restoreProgress.value = OperationProgress()
    }

    fun consumeOperationBlocked() {
        _operationBlocked.value = false
    }

    fun toggleRestoreAppSelection(packageName: String) {
        _appRestoreTypes.update { it.ensurePackage(packageName, _restoreTypes.value) }
        _backupDetails.update { currentDetails ->
            currentDetails.map { detail ->
                if (detail.appInfo.packageName == packageName) {
                    if (_allowDowngrade.value || !detail.isDowngrade) {
                        detail.copy(appInfo = detail.appInfo.copy(isSelected = !detail.appInfo.isSelected))
                    } else {
                        detail
                    }
                } else {
                    detail
                }
            }
        }
    }

    fun toggleAllRestoreSelection() {
        _backupDetails.update { currentDetails ->
            val selectableDetails = currentDetails.filter { _allowDowngrade.value || !it.isDowngrade }
            val shouldSelectAll = selectableDetails.any { !it.appInfo.isSelected }
            _appRestoreTypes.update { currentTypes ->
                selectableDetails.fold(currentTypes) { acc, detail ->
                    acc.ensurePackage(detail.appInfo.packageName, _restoreTypes.value)
                }
            }
            currentDetails.map { detail ->
                val canBeSelected = _allowDowngrade.value || !detail.isDowngrade
                detail.copy(appInfo = detail.appInfo.copy(isSelected = shouldSelectAll && canBeSelected))
            }
        }
    }

    fun setAllowDowngrade(value: Boolean) {
        _allowDowngrade.value = value
        if (!value) {
            _backupDetails.update { currentDetails ->
                currentDetails.map { detail ->
                    if (detail.isDowngrade) detail.copy(appInfo = detail.appInfo.copy(isSelected = false)) else detail
                }
            }
        }
    }

    fun setRestoreApk(value: Boolean) = _restoreTypes.update { it.copy(apk = value) }
    fun setRestoreData(value: Boolean) = _restoreTypes.update { it.copy(data = value) }
    fun setRestoreDeviceProtectedData(value: Boolean) = _restoreTypes.update { it.copy(deviceProtectedData = value) }
    fun setRestoreExternalData(value: Boolean) = _restoreTypes.update { it.copy(externalData = value) }
    fun setRestoreObb(value: Boolean) = _restoreTypes.update { it.copy(obb = value) }
    fun setRestoreMedia(value: Boolean) = _restoreTypes.update { it.copy(media = value) }

    fun setAppRestoreTypes(packageName: String, restoreTypes: RestoreTypes) {
        _appRestoreTypes.update { it + (packageName to restoreTypes) }
    }

    fun setSelectedAppsRestoreTypes(restoreTypes: RestoreTypes) {
        val selectedPackageNames = _backupDetails.value
            .filter { it.appInfo.isSelected && (_allowDowngrade.value || !it.isDowngrade) }
            .map { it.appInfo.packageName }
        _restoreTypes.value = restoreTypes
        _appRestoreTypes.update { it + selectedPackageNames.associateWith { restoreTypes } }
    }

    private fun effectiveRestoreTypes(packageName: String): RestoreTypes {
        return _appRestoreTypes.value[packageName] ?: _restoreTypes.value
    }

    private fun Map<String, RestoreTypes>.ensurePackage(packageName: String, defaultTypes: RestoreTypes): Map<String, RestoreTypes> {
        return if (containsKey(packageName)) this else this + (packageName to defaultTypes)
    }
}
