package io.github.hddq.restoid.ui.runtasks

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.OperationType
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.RepositoryBackendType
import io.github.hddq.restoid.data.ResticBinaryManager
import io.github.hddq.restoid.data.ResticState
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.ui.backup.BackupTypes
import io.github.hddq.restoid.ui.shared.OperationProgress
import io.github.hddq.restoid.work.BackupTypeSelection
import io.github.hddq.restoid.work.OperationWorkRepository
import io.github.hddq.restoid.work.RunTasksWorkRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RunTasksMaintenanceConfig(
    val unlockRepo: Boolean = false,
    val forgetSnapshots: Boolean = false,
    val pruneRepo: Boolean = false,
    val checkRepo: Boolean = true,
    val readData: Boolean = false,
    val keepLast: Int = 5,
    val keepDaily: Int = 7,
    val keepWeekly: Int = 4,
    val keepMonthly: Int = 6
)

data class RunTasksUiState(
    val apps: List<AppInfo> = emptyList(),
    val isLoadingApps: Boolean = true,
    val backupEnabled: Boolean = true,
    val backupTypes: BackupTypes = BackupTypes(),
    val maintenance: RunTasksMaintenanceConfig = RunTasksMaintenanceConfig(),
    val isRunning: Boolean = false,
    val progress: OperationProgress = OperationProgress()
)

sealed interface RunTasksUiEvent {
    data object NavigateToOperationProgress : RunTasksUiEvent
}

class RunTasksViewModel(
    private val application: Application,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticBinaryManager: ResticBinaryManager,
    private val appInfoRepository: io.github.hddq.restoid.data.AppInfoRepository,
    private val preferencesRepository: io.github.hddq.restoid.data.PreferencesRepository,
    private val operationWorkRepository: OperationWorkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        RunTasksUiState(
            backupTypes = preferencesRepository.loadBackupTypes(),
            maintenance = preferencesRepository.loadMaintenanceState()
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _operationBlocked = MutableStateFlow(false)
    val operationBlocked = _operationBlocked.asStateFlow()

    private val _uiEvents = MutableSharedFlow<RunTasksUiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<RunTasksUiEvent> = _uiEvents.asSharedFlow()

    init {
        loadInstalledApps()
        observeOperationState()
    }

    fun refreshAppsList() {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            val currentSelection = _uiState.value.apps.associate { it.packageName to it.isSelected }
            val apps = appInfoRepository.getInstalledUserApps().map { app ->
                val selected = currentSelection[app.packageName] ?: app.isSelected
                app.copy(isSelected = selected)
            }
            _uiState.update { it.copy(apps = apps, isLoadingApps = false) }
        }
    }

    private fun observeOperationState() {
        viewModelScope.launch {
            operationWorkRepository.operationState.collect { state ->
                if (state.operationType == OperationType.RUN_TASKS) {
                    _uiState.update { it.copy(isRunning = state.isRunning, progress = state.progress) }
                } else {
                    _uiState.update { it.copy(isRunning = false, progress = OperationProgress()) }
                }
            }
        }
    }

    fun run() {
        if (_uiState.value.isRunning) return

        preferencesRepository.saveBackupTypes(_uiState.value.backupTypes)
        preferencesRepository.saveMaintenanceState(_uiState.value.maintenance)

        val errorState = preflightChecks()
        if (errorState != null) {
            _uiState.update { it.copy(isRunning = false, progress = errorState) }
            return
        }

        val selectedRepoKey = repositoriesRepository.selectedRepository.value ?: run {
            _uiState.update {
                it.copy(
                    isRunning = false,
                    progress = OperationProgress(
                        isFinished = true,
                        error = application.getString(R.string.error_no_backup_repository_selected),
                        finalSummary = application.getString(R.string.summary_no_backup_repository_selected)
                    )
                )
            }
            return
        }

        val maintenance = _uiState.value.maintenance
        val request = RunTasksWorkRequest(
            repositoryKey = selectedRepoKey,
            backupEnabled = _uiState.value.backupEnabled,
            backupTypes = _uiState.value.backupTypes.toSelection(),
            selectedPackageNames = _uiState.value.apps.filter { it.isSelected }.map { it.packageName },
            unlockRepo = maintenance.unlockRepo,
            forgetSnapshots = maintenance.forgetSnapshots,
            pruneRepo = maintenance.pruneRepo,
            checkRepo = maintenance.checkRepo,
            readData = maintenance.readData,
            keepLast = maintenance.keepLast,
            keepDaily = maintenance.keepDaily,
            keepWeekly = maintenance.keepWeekly,
            keepMonthly = maintenance.keepMonthly
        )

        viewModelScope.launch(Dispatchers.IO) {
            val enqueued = operationWorkRepository.enqueueRunTasks(request)
            if (enqueued) {
                _uiEvents.tryEmit(RunTasksUiEvent.NavigateToOperationProgress)
            } else {
                _operationBlocked.value = true
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        progress = OperationProgress(
                            isFinished = true,
                            error = application.getString(R.string.error_operation_already_running),
                            finalSummary = application.getString(R.string.summary_operation_already_running)
                        )
                    )
                }
            }
        }
    }

    private fun preflightChecks(): OperationProgress? {
        val state = _uiState.value
        val maintenance = state.maintenance
        val hasMaintenanceTask = maintenance.unlockRepo || maintenance.forgetSnapshots || maintenance.pruneRepo || maintenance.checkRepo
        if (!state.backupEnabled && !hasMaintenanceTask) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.maintenance_error_no_tasks),
                finalSummary = application.getString(R.string.maintenance_summary_no_tasks)
            )
        }

        if (state.backupEnabled) {
            if (state.apps.none { it.isSelected }) {
                return OperationProgress(
                    isFinished = true,
                    error = application.getString(R.string.error_no_apps_selected),
                    finalSummary = application.getString(R.string.summary_no_apps_selected)
                )
            }
            if (!state.backupTypes.anyEnabled()) {
                return OperationProgress(
                    isFinished = true,
                    error = application.getString(R.string.error_no_backup_types_selected),
                    finalSummary = application.getString(R.string.summary_no_backup_types_selected)
                )
            }
        }

        if (resticBinaryManager.resticState.value !is ResticState.Installed) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_restic_not_installed),
                finalSummary = application.getString(R.string.summary_restic_binary_not_installed)
            )
        }

        val selectedRepoKey = repositoriesRepository.selectedRepository.value
        val repository = selectedRepoKey?.let { repositoriesRepository.getRepositoryByKey(it) }
        if (selectedRepoKey == null || repository == null) {
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

        if (repository.backendType == RepositoryBackendType.SFTP && !repositoriesRepository.hasSftpPassword(selectedRepoKey)) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_sftp_password_not_found_for_repository),
                finalSummary = application.getString(R.string.summary_sftp_password_not_found)
            )
        }

        if (
            repository.backendType == RepositoryBackendType.REST &&
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
            repository.backendType == RepositoryBackendType.S3 &&
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

    fun consumeOperationBlocked() {
        _operationBlocked.value = false
    }

    fun setBackupEnabled(value: Boolean) = _uiState.update { it.copy(backupEnabled = value) }
    fun setUnlockRepo(value: Boolean) = _uiState.update { it.copy(maintenance = it.maintenance.copy(unlockRepo = value)) }
    fun setForgetSnapshots(value: Boolean) = _uiState.update { it.copy(maintenance = it.maintenance.copy(forgetSnapshots = value)) }
    fun setPruneRepo(value: Boolean) = _uiState.update { it.copy(maintenance = it.maintenance.copy(pruneRepo = value)) }
    fun setCheckRepo(value: Boolean) = _uiState.update { it.copy(maintenance = it.maintenance.copy(checkRepo = value)) }
    fun setReadData(value: Boolean) = _uiState.update { it.copy(maintenance = it.maintenance.copy(readData = value)) }
    fun setKeepLast(value: Int) = _uiState.update { it.copy(maintenance = it.maintenance.copy(keepLast = value)) }
    fun setKeepDaily(value: Int) = _uiState.update { it.copy(maintenance = it.maintenance.copy(keepDaily = value)) }
    fun setKeepWeekly(value: Int) = _uiState.update { it.copy(maintenance = it.maintenance.copy(keepWeekly = value)) }
    fun setKeepMonthly(value: Int) = _uiState.update { it.copy(maintenance = it.maintenance.copy(keepMonthly = value)) }

    fun toggleAppSelection(packageName: String) {
        _uiState.update { state ->
            state.copy(
                apps = state.apps.map { app ->
                    if (app.packageName == packageName) app.copy(isSelected = !app.isSelected) else app
                }
            )
        }
    }

    fun toggleAllApps() {
        _uiState.update { state ->
            val shouldSelectAll = state.apps.any { !it.isSelected }
            state.copy(apps = state.apps.map { it.copy(isSelected = shouldSelectAll) })
        }
    }

    fun setBackupApk(value: Boolean) = _uiState.update { it.copy(backupTypes = it.backupTypes.copy(apk = value)) }
    fun setBackupData(value: Boolean) = _uiState.update { it.copy(backupTypes = it.backupTypes.copy(data = value)) }
    fun setBackupDeviceProtectedData(value: Boolean) = _uiState.update { it.copy(backupTypes = it.backupTypes.copy(deviceProtectedData = value)) }
    fun setBackupExternalData(value: Boolean) = _uiState.update { it.copy(backupTypes = it.backupTypes.copy(externalData = value)) }
    fun setBackupObb(value: Boolean) = _uiState.update { it.copy(backupTypes = it.backupTypes.copy(obb = value)) }
    fun setBackupMedia(value: Boolean) = _uiState.update { it.copy(backupTypes = it.backupTypes.copy(media = value)) }
}

private fun BackupTypes.toSelection(): BackupTypeSelection {
    return BackupTypeSelection(
        apk = apk,
        data = data,
        deviceProtectedData = deviceProtectedData,
        externalData = externalData,
        obb = obb,
        media = media
    )
}

private fun BackupTypes.anyEnabled(): Boolean {
    return apk || data || deviceProtectedData || externalData || obb || media
}
