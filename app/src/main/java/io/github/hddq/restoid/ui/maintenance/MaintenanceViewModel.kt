package io.github.hddq.restoid.ui.maintenance

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hddq.restoid.data.*
import io.github.hddq.restoid.R
import io.github.hddq.restoid.ui.shared.OperationProgress
import io.github.hddq.restoid.util.MaintenanceOutputParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
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

class MaintenanceViewModel(
    private val context: Context,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticBinaryManager: ResticBinaryManager, // Inject Manager
    private val resticRepository: ResticRepository,
    private val notificationRepository: NotificationRepository,
    private val preferencesRepository: PreferencesRepository,
    private val operationCoordinator: OperationCoordinator,
    private val operationLockManager: OperationLockManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MaintenanceUiState())
    val uiState = _uiState.asStateFlow()
    private var maintenanceJob: Job? = null

    private val _operationBlocked = MutableStateFlow(false)
    val operationBlocked = _operationBlocked.asStateFlow()

    init {
        _uiState.value = preferencesRepository.loadMaintenanceState()
    }

    fun runTasks() {
        if (_uiState.value.isRunning) return
        preferencesRepository.saveMaintenanceState(_uiState.value)

        maintenanceJob = viewModelScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            _uiState.update { it.copy(isRunning = true, progress = OperationProgress(stageTitle = context.getString(R.string.progress_initializing))) }
            var operationStarted = false

            var finalSummary = StringBuilder()
            var overallSuccess = true

            try {
                val errorState = preflightChecks()
                if (errorState != null) {
                    _uiState.update { it.copy(progress = errorState, isRunning = false) }
                    return@launch
                }

                val selectedRepoKey = repositoriesRepository.selectedRepository.value!!
                val repository = repositoriesRepository.getRepositoryByKey(selectedRepoKey)
                    ?: throw IllegalStateException(context.getString(R.string.summary_no_backup_repository_selected))

                if (!operationCoordinator.tryStart(OperationType.MAINTENANCE, repository.backendType)) {
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
                    return@launch
                }
                operationStarted = true

                operationLockManager.acquire(repository.backendType)

                val selectedRepoPath = repository.path
                val repositoryEnvironment = repositoriesRepository.getExecutionEnvironmentVariables(selectedRepoKey)
                val repositoryResticOptions = repositoriesRepository.getExecutionResticOptions(selectedRepoKey)
                val password = repositoriesRepository.getRepositoryPassword(selectedRepoKey)!!

                val tasksToRun = mutableListOf<Pair<String, suspend () -> Result<String>>>()
                if (_uiState.value.unlockRepo) {
                    tasksToRun.add(
                        context.getString(R.string.maintenance_task_unlock) to {
                            resticRepository.unlock(selectedRepoPath, password, repositoryEnvironment, repositoryResticOptions)
                        }
                    )
                }
                if (_uiState.value.forgetSnapshots) {
                    val state = _uiState.value
                    tasksToRun.add(context.getString(R.string.maintenance_task_forget) to {
                        resticRepository.forget(
                            selectedRepoPath,
                            password,
                            state.keepLast,
                            state.keepDaily,
                            state.keepWeekly,
                            state.keepMonthly,
                            repositoryEnvironment,
                            repositoryResticOptions
                        )
                    })
                }
                if (_uiState.value.pruneRepo) {
                    tasksToRun.add(
                        context.getString(R.string.maintenance_task_prune) to {
                            resticRepository.prune(selectedRepoPath, password, repositoryEnvironment, repositoryResticOptions)
                        }
                    )
                }
                if (_uiState.value.checkRepo) {
                    tasksToRun.add(
                        context.getString(R.string.maintenance_task_check) to {
                            resticRepository.check(
                                selectedRepoPath,
                                password,
                                _uiState.value.readData,
                                repositoryEnvironment,
                                repositoryResticOptions
                            )
                        }
                    )
                }

                if (tasksToRun.isEmpty()) throw IllegalStateException(context.getString(R.string.maintenance_error_no_tasks_selected))

                tasksToRun.forEachIndexed { index, (taskName, taskAction) ->
                    val stageTitle = context.getString(R.string.maintenance_stage_running_task, index + 1, tasksToRun.size, taskName)
                    _uiState.update {
                        it.copy(progress = it.progress.copy(stageTitle = stageTitle, overallPercentage = index.toFloat() / tasksToRun.size))
                    }
                    notificationRepository.showOperationProgressNotification(context.getString(R.string.operation_maintenance), _uiState.value.progress)

                    val result = taskAction()

                    _uiState.update {
                        it.copy(progress = it.progress.copy(overallPercentage = (index + 1f) / tasksToRun.size))
                    }

                    result.fold(
                        onSuccess = { output ->
                            val summary = MaintenanceOutputParser.parse(taskName, output, context)
                            finalSummary.append(context.getString(R.string.maintenance_task_successful, taskName, summary))
                        },
                        onFailure = { exception ->
                            overallSuccess = false
                            finalSummary.append(context.getString(R.string.maintenance_task_failed, taskName, exception.message ?: ""))
                        }
                    )
                }

            } catch (e: Exception) {
                overallSuccess = false
                finalSummary.append(context.getString(R.string.error_fatal_with_message, e.message ?: ""))
            } finally {
                if (operationStarted) {
                    operationLockManager.release()
                    operationCoordinator.finish(OperationType.MAINTENANCE)
                }
                val finalProgress = _uiState.value.progress.copy(
                    isFinished = true,
                    finalSummary = finalSummary.toString().trim(),
                    error = if (!overallSuccess) context.getString(R.string.maintenance_error_one_or_more_failed) else null,
                    elapsedTime = (System.currentTimeMillis() - startTime) / 1000
                )
                _uiState.update { it.copy(isRunning = false, progress = finalProgress) }
                notificationRepository.showOperationFinishedNotification(context.getString(R.string.operation_maintenance), overallSuccess, finalProgress.finalSummary)

                if ((_uiState.value.pruneRepo || _uiState.value.forgetSnapshots) && overallSuccess) {
                    val repoKey = repositoriesRepository.selectedRepository.value
                    val repository = repoKey?.let { repositoriesRepository.getRepositoryByKey(it) }
                    val password = repoKey?.let { repositoriesRepository.getRepositoryPassword(it) }
                    if (repository != null && password != null) {
                        resticRepository.refreshSnapshots(
                            repository.path,
                            password,
                            repositoriesRepository.getExecutionEnvironmentVariables(repoKey),
                            repositoriesRepository.getExecutionResticOptions(repoKey)
                        )
                    }
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
        // Use Manager for check
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

        return null
    }

    fun onDone() { _uiState.update { it.copy(progress = OperationProgress()) } }
    fun consumeOperationBlocked() { _operationBlocked.value = false }
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
