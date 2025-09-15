package app.restoid.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restoid.data.AppInfoRepository
import app.restoid.data.RepositoriesRepository
import app.restoid.data.ResticRepository
import app.restoid.data.ResticState
import app.restoid.data.SnapshotInfo
import app.restoid.model.AppInfo
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val snapshots: List<SnapshotInfo> = emptyList(),
    val appInfoMap: Map<String, List<AppInfo>> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedRepo: String? = null,
    val resticState: ResticState = ResticState.Idle,
    val showPasswordDialogFor: String? = null
)

class HomeViewModel(
    private val repositoriesRepository: RepositoriesRepository,
    private val resticRepository: ResticRepository,
    private val appInfoRepository: AppInfoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        // Initial load of repositories and restic status
        viewModelScope.launch {
            repositoriesRepository.loadRepositories()
            resticRepository.checkResticStatus()
        }

        // React to changes in selected repository or restic status
        combine(
            repositoriesRepository.selectedRepository,
            resticRepository.resticState
        ) { repo, restic -> repo to restic }
            .onEach { (repo, restic) ->
                _uiState.update { it.copy(selectedRepo = repo, resticState = restic) }
                loadSnapshots(repo, restic)
            }.launchIn(viewModelScope)
    }

    fun loadSnapshots(repoPath: String?, resticState: ResticState) {
        if (repoPath == null || resticState !is ResticState.Installed) {
            _uiState.update { it.copy(snapshots = emptyList(), isLoading = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val password = repositoriesRepository.getRepositoryPassword(repoPath)
            if (password == null) {
                _uiState.update { it.copy(isLoading = false, showPasswordDialogFor = repoPath) }
                return@launch
            }

            handleSnapshotResult(resticRepository.getSnapshots(repoPath, password))
        }
    }

    private suspend fun handleSnapshotResult(result: Result<List<SnapshotInfo>>) {
        result.fold(
            onSuccess = { snapshots ->
                _uiState.update { it.copy(snapshots = snapshots, isLoading = false) }
                loadAppInfoForSnapshots(snapshots)
            },
            onFailure = { throwable ->
                _uiState.update { it.copy(error = throwable.message, isLoading = false) }
            }
        )
    }

    private fun loadAppInfoForSnapshots(snapshots: List<SnapshotInfo>) {
        viewModelScope.launch {
            val appInfoMap = mutableMapOf<String, List<AppInfo>>()
            snapshots.forEach { snapshot ->
                if (snapshot.tags.isNotEmpty()) {
                    val appInfos = appInfoRepository.getAppInfoForPackages(snapshot.tags)
                    appInfoMap[snapshot.id] = appInfos
                }
            }
            _uiState.update { it.copy(appInfoMap = appInfoMap) }
        }
    }

    fun onPasswordEntered(password: String) {
        val repoPath = _uiState.value.showPasswordDialogFor ?: return
        _uiState.update { it.copy(showPasswordDialogFor = null, isLoading = true, error = null) }
        viewModelScope.launch {
            handleSnapshotResult(resticRepository.getSnapshots(repoPath, password))
        }
    }

    fun onDismissPasswordDialog() {
        _uiState.update { it.copy(showPasswordDialogFor = null) }
    }
}
