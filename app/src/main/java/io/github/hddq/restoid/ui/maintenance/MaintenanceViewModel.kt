package io.github.hddq.restoid.ui.maintenance

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.OperationType
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.RepositoryBackendType
import io.github.hddq.restoid.data.ResticBinaryManager
import io.github.hddq.restoid.data.ResticState
import io.github.hddq.restoid.ui.shared.OperationProgress
import io.github.hddq.restoid.work.MaintenanceWorkRequest
import io.github.hddq.restoid.work.OperationWorkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MaintenanceUiState(
    val checkRepo: Boolean = true,
    val pruneRepo: Boolean = false,
    val unlockRepo: Boolean = false,
    val readData: Boolean = false,
    val forgetSnapshots: Boolean = false,
    val keepLast: Int = 5,
    val keepDaily: Int = 7,
    val keepWeekly: Int = 4,
    val keepMonthly: Int = 6,
    val isRunning: Boolean = false,
    val progress: OperationProgress = OperationProgress(),
)

sealed interface MaintenanceUiEvent {
    data object NavigateToOperationProgress : MaintenanceUiEvent
}

class MaintenanceViewModel(
    private val context: Context,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticBinaryManager: ResticBinaryManager,
    private val preferencesRepository: io.github.hddq.restoid.data.PreferencesRepository,
    private val operationWorkRepository: OperationWorkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MaintenanceUiState())
    val uiState = _uiState.asStateFlow()

    private val _operationBlocked = MutableStateFlow(false)
    val operationBlocked = _operationBlocked.asStateFlow()
    private val _uiEvents = MutableSharedFlow<MaintenanceUiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<MaintenanceUiEvent> = _uiEvents.asSharedFlow()

    init {
        _uiState.value = preferencesRepository.loadMaintenanceState()
        observeOperationState()
    }

    private fun observeOperationState() {
        viewModelScope.launch {
            operationWorkRepository.operationState.collect { state ->
                if (state.operationType == OperationType.MAINTENANCE) {
                    _uiState.update { it.copy(isRunning = state.isRunning, progress = state.progress) }
                } else {
                    _uiState.update { it.copy(isRunning = false, progress = OperationProgress()) }
                }
            }
        }
    }

    fun runTasks() {
        if (_uiState.value.isRunning) return
        preferencesRepository.saveMaintenanceState(_uiState.value)

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
                        error = context.getString(R.string.error_no_backup_repository_selected),
                        finalSummary = context.getString(R.string.summary_no_backup_repository_selected)
                    )
                )
            }
            return
        }

        val request = MaintenanceWorkRequest(
            repositoryKey = selectedRepoKey,
            checkRepo = _uiState.value.checkRepo,
            pruneRepo = _uiState.value.pruneRepo,
            unlockRepo = _uiState.value.unlockRepo,
            readData = _uiState.value.readData,
            forgetSnapshots = _uiState.value.forgetSnapshots,
            keepLast = _uiState.value.keepLast,
            keepDaily = _uiState.value.keepDaily,
            keepWeekly = _uiState.value.keepWeekly,
            keepMonthly = _uiState.value.keepMonthly
        )

        viewModelScope.launch(Dispatchers.IO) {
            val enqueued = operationWorkRepository.enqueueMaintenance(request)
            if (enqueued) {
                _uiEvents.tryEmit(MaintenanceUiEvent.NavigateToOperationProgress)
            } else {
                _operationBlocked.value = true
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        progress = OperationProgress(
                            isFinished = true,
                            error = context.getString(R.string.error_operation_already_running),
                            finalSummary = context.getString(R.string.summary_operation_already_running)
                        )
                    )
                }
            }
        }
    }

    private fun preflightChecks(): OperationProgress? {
        if (!_uiState.value.checkRepo && !_uiState.value.pruneRepo && !_uiState.value.unlockRepo && !_uiState.value.forgetSnapshots) {
            return OperationProgress(
                isFinished = true,
                error = context.getString(R.string.maintenance_error_no_tasks),
                finalSummary = context.getString(R.string.maintenance_summary_no_tasks)
            )
        }

        if (resticBinaryManager.resticState.value !is ResticState.Installed) {
            return OperationProgress(
                isFinished = true,
                error = context.getString(R.string.error_restic_not_installed),
                finalSummary = context.getString(R.string.summary_restic_binary_not_installed)
            )
        }

        val selectedRepoKey = repositoriesRepository.selectedRepository.value
        if (selectedRepoKey == null || repositoriesRepository.getRepositoryByKey(selectedRepoKey) == null) {
            return OperationProgress(
                isFinished = true,
                error = context.getString(R.string.error_no_backup_repository_selected),
                finalSummary = context.getString(R.string.summary_no_backup_repository_selected)
            )
        }

        if (repositoriesRepository.getRepositoryPassword(selectedRepoKey) == null) {
            return OperationProgress(
                isFinished = true,
                error = context.getString(R.string.error_password_not_found_for_repository),
                finalSummary = context.getString(R.string.summary_password_not_found)
            )
        }

        val repository = repositoriesRepository.getRepositoryByKey(selectedRepoKey)
        if (repository?.backendType == RepositoryBackendType.SFTP && !repositoriesRepository.hasSftpPassword(selectedRepoKey)) {
            return OperationProgress(
                isFinished = true,
                error = context.getString(R.string.error_sftp_password_not_found_for_repository),
                finalSummary = context.getString(R.string.summary_sftp_password_not_found)
            )
        }

        if (
            repository?.backendType == RepositoryBackendType.REST &&
            repository.restAuthRequired &&
            !repositoriesRepository.hasRestCredentials(selectedRepoKey)
        ) {
            return OperationProgress(
                isFinished = true,
                error = context.getString(R.string.error_rest_credentials_not_found_for_repository),
                finalSummary = context.getString(R.string.summary_rest_credentials_not_found)
            )
        }

        return null
    }

    fun onDone() {
        operationWorkRepository.clearFinished(OperationType.MAINTENANCE)
        _uiState.update { it.copy(progress = OperationProgress()) }
    }

    fun consumeOperationBlocked() {
        _operationBlocked.value = false
    }

    fun setCheckRepo(value: Boolean) = _uiState.update { it.copy(checkRepo = value) }
    fun setPruneRepo(value: Boolean) = _uiState.update { it.copy(pruneRepo = value) }
    fun setUnlockRepo(value: Boolean) = _uiState.update { it.copy(unlockRepo = value) }
    fun setReadData(value: Boolean) = _uiState.update { it.copy(readData = value) }
    fun setForgetSnapshots(value: Boolean) = _uiState.update { it.copy(forgetSnapshots = value) }
    fun setKeepLast(value: Int) = _uiState.update { it.copy(keepLast = value) }
    fun setKeepDaily(value: Int) = _uiState.update { it.copy(keepDaily = value) }
    fun setKeepWeekly(value: Int) = _uiState.update { it.copy(keepWeekly = value) }
    fun setKeepMonthly(value: Int) = _uiState.update { it.copy(keepMonthly = value) }
}
