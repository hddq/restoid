package io.github.hddq.restoid.ui.backup

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.AppInfoRepository
import io.github.hddq.restoid.data.OperationType
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.RepositoryBackendType
import io.github.hddq.restoid.data.ResticBinaryManager
import io.github.hddq.restoid.data.ResticState
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.ui.shared.OperationProgress
import io.github.hddq.restoid.work.BackupTypeSelection
import io.github.hddq.restoid.work.BackupWorkRequest
import io.github.hddq.restoid.work.OperationWorkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupTypes(
    val apk: Boolean = true,
    val data: Boolean = true,
    val deviceProtectedData: Boolean = true,
    val externalData: Boolean = false,
    val obb: Boolean = false,
    val media: Boolean = false
)

sealed interface BackupUiEvent {
    data object NavigateToOperationProgress : BackupUiEvent
}

class BackupViewModel(
    private val application: Application,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticBinaryManager: ResticBinaryManager,
    private val appInfoRepository: AppInfoRepository,
    private val preferencesRepository: io.github.hddq.restoid.data.PreferencesRepository,
    private val operationWorkRepository: OperationWorkRepository
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

    private val _operationBlocked = MutableStateFlow(false)
    val operationBlocked = _operationBlocked.asStateFlow()
    private val _uiEvents = MutableSharedFlow<BackupUiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<BackupUiEvent> = _uiEvents.asSharedFlow()

    init {
        _backupTypes.value = preferencesRepository.loadBackupTypes()
        loadInstalledApps()
        observeOperationState()
    }

    fun refreshAppsList() {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            _apps.value = appInfoRepository.getInstalledUserApps()
            _isLoadingApps.value = false
        }
    }

    private fun observeOperationState() {
        viewModelScope.launch {
            operationWorkRepository.operationState.collect { state ->
                if (state.operationType == OperationType.BACKUP) {
                    _isBackingUp.value = state.isRunning
                    _backupProgress.value = state.progress
                } else {
                    _isBackingUp.value = false
                    _backupProgress.value = OperationProgress()
                }
            }
        }
    }

    fun startBackup() {
        if (_isBackingUp.value) return
        preferencesRepository.saveBackupTypes(_backupTypes.value)

        val errorState = preflightChecks()
        if (errorState != null) {
            _backupProgress.value = errorState
            return
        }

        val selectedRepoKey = repositoriesRepository.selectedRepository.value ?: run {
            _backupProgress.value = OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_no_backup_repository_selected),
                finalSummary = application.getString(R.string.summary_no_backup_repository_selected)
            )
            return
        }

        val request = BackupWorkRequest(
            repositoryKey = selectedRepoKey,
            backupTypes = BackupTypeSelection(
                apk = _backupTypes.value.apk,
                data = _backupTypes.value.data,
                deviceProtectedData = _backupTypes.value.deviceProtectedData,
                externalData = _backupTypes.value.externalData,
                obb = _backupTypes.value.obb,
                media = _backupTypes.value.media
            ),
            selectedPackageNames = _apps.value.filter { it.isSelected }.map { it.packageName }
        )

        viewModelScope.launch(Dispatchers.IO) {
            val enqueued = operationWorkRepository.enqueueBackup(request)
            if (enqueued) {
                _uiEvents.tryEmit(BackupUiEvent.NavigateToOperationProgress)
            } else {
                _operationBlocked.value = true
                _backupProgress.value = OperationProgress(
                    isFinished = true,
                    error = application.getString(R.string.error_operation_already_running),
                    finalSummary = application.getString(R.string.summary_operation_already_running)
                )
            }
        }
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

        if (
            repository?.backendType == RepositoryBackendType.REST &&
            repository.restAuthRequired &&
            !repositoriesRepository.hasRestCredentials(selectedRepoKey)
        ) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_rest_credentials_not_found_for_repository),
                finalSummary = application.getString(R.string.summary_rest_credentials_not_found)
            )
        }

        if (
            repository?.backendType == RepositoryBackendType.S3 &&
            repository.s3AuthRequired &&
            !repositoriesRepository.hasS3Credentials(selectedRepoKey)
        ) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_s3_credentials_not_found_for_repository),
                finalSummary = application.getString(R.string.summary_s3_credentials_not_found)
            )
        }

        return null
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

    fun onDone() {
        operationWorkRepository.clearFinished(OperationType.BACKUP)
        _backupProgress.value = OperationProgress()
    }

    fun consumeOperationBlocked() {
        _operationBlocked.value = false
    }

    fun setBackupApk(value: Boolean) = _backupTypes.update { it.copy(apk = value) }
    fun setBackupData(value: Boolean) = _backupTypes.update { it.copy(data = value) }
    fun setBackupDeviceProtectedData(value: Boolean) = _backupTypes.update { it.copy(deviceProtectedData = value) }
    fun setBackupExternalData(value: Boolean) = _backupTypes.update { it.copy(externalData = value) }
    fun setBackupObb(value: Boolean) = _backupTypes.update { it.copy(obb = value) }
    fun setBackupMedia(value: Boolean) = _backupTypes.update { it.copy(media = value) }
}
