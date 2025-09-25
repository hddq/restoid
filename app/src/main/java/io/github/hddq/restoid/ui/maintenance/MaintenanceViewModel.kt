package io.github.hddq.restoid.ui.maintenance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hddq.restoid.data.NotificationRepository
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.ResticRepository
import io.github.hddq.restoid.data.ResticState
import io.github.hddq.restoid.ui.shared.OperationProgress
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
    val readData: Boolean = false, // Add this
    val isRunning: Boolean = false,
    val progress: OperationProgress = OperationProgress(),
)

class MaintenanceViewModel(
    private val repositoriesRepository: RepositoriesRepository,
    private val resticRepository: ResticRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MaintenanceUiState())
    val uiState = _uiState.asStateFlow()

    private var maintenanceJob: Job? = null

    fun runTasks() {
        if (_uiState.value.isRunning) return

        maintenanceJob = viewModelScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            _uiState.update { it.copy(isRunning = true, progress = OperationProgress(stageTitle = "Initializing...")) }

            var finalSummary = StringBuilder()
            var overallSuccess = true

            try {
                // --- Pre-flight checks ---
                val errorState = preflightChecks()
                if (errorState != null) {
                    _uiState.update { it.copy(progress = errorState, isRunning = false) }
                    return@launch
                }

                val selectedRepoPath = repositoriesRepository.selectedRepository.value!!
                val password = repositoriesRepository.getRepositoryPassword(selectedRepoPath)!!

                val tasksToRun = mutableListOf<suspend () -> Pair<String, Result<String>>>()
                if (_uiState.value.unlockRepo) {
                    tasksToRun.add { "Unlock" to resticRepository.unlock(selectedRepoPath, password) }
                }
                if (_uiState.value.pruneRepo) {
                    tasksToRun.add { "Prune" to resticRepository.prune(selectedRepoPath, password) }
                }
                if (_uiState.value.checkRepo) {
                    tasksToRun.add { "Check" to resticRepository.check(selectedRepoPath, password, _uiState.value.readData) }
                }

                if (tasksToRun.isEmpty()) {
                    throw IllegalStateException("No maintenance tasks selected.")
                }

                tasksToRun.forEachIndexed { index, task ->
                    val (taskName, result) = task()
                    val stageTitle = "[${index + 1}/${tasksToRun.size}] Running '$taskName'..."
                    _uiState.update {
                        it.copy(progress = it.progress.copy(
                            stageTitle = stageTitle,
                            overallPercentage = (index + 1f) / tasksToRun.size
                        ))
                    }
                    notificationRepository.showOperationProgressNotification("Maintenance", _uiState.value.progress)


                    result.fold(
                        onSuccess = { output ->
                            finalSummary.append("$taskName successful:\n$output\n\n")
                        },
                        onFailure = { exception ->
                            overallSuccess = false
                            finalSummary.append("$taskName failed:\n${exception.message}\n\n")
                        }
                    )
                }

            } catch (e: Exception) {
                overallSuccess = false
                finalSummary.append("A fatal error occurred: ${e.message}")
            } finally {
                val finalProgress = _uiState.value.progress.copy(
                    isFinished = true,
                    finalSummary = finalSummary.toString().trim(),
                    error = if (!overallSuccess) "One or more tasks failed." else null,
                    elapsedTime = (System.currentTimeMillis() - startTime) / 1000
                )
                _uiState.update { it.copy(isRunning = false, progress = finalProgress) }
                notificationRepository.showOperationFinishedNotification("Maintenance", overallSuccess, finalProgress.finalSummary)

                // Refresh snapshots if prune was successful
                if (_uiState.value.pruneRepo && overallSuccess) {
                    val repoPath = repositoriesRepository.selectedRepository.value
                    val password = repoPath?.let { repositoriesRepository.getRepositoryPassword(it) }
                    if (repoPath != null && password != null) {
                        resticRepository.refreshSnapshots(repoPath, password)
                    }
                }
            }
        }
    }

    private fun preflightChecks(): OperationProgress? {
        if (!_uiState.value.checkRepo && !_uiState.value.pruneRepo && !_uiState.value.unlockRepo) {
            return OperationProgress(isFinished = true, error = "No tasks selected.", finalSummary = "No maintenance tasks were selected.")
        }
        if (resticRepository.resticState.value !is ResticState.Installed) {
            return OperationProgress(isFinished = true, error = "Restic is not installed.", finalSummary = "Restic binary is not installed.")
        }
        val selectedRepoPath = repositoriesRepository.selectedRepository.value
        if (selectedRepoPath == null) {
            return OperationProgress(isFinished = true, error = "No backup repository selected.", finalSummary = "No backup repository is selected.")
        }
        if (repositoriesRepository.getRepositoryPassword(selectedRepoPath) == null) {
            return OperationProgress(isFinished = true, error = "Password for repository not found.", finalSummary = "Could not find the password for the repository.")
        }
        return null
    }

    fun onDone() {
        _uiState.update { it.copy(progress = OperationProgress()) }
    }


    fun setCheckRepo(value: Boolean) {
        _uiState.update { it.copy(checkRepo = value) }
    }

    fun setPruneRepo(value: Boolean) {
        _uiState.update { it.copy(pruneRepo = value) }
    }

    fun setUnlockRepo(value: Boolean) {
        _uiState.update { it.copy(unlockRepo = value) }
    }

    fun setReadData(value: Boolean) {
        _uiState.update { it.copy(readData = value) }
    }
}
