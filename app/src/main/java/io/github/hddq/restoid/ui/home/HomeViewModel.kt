package io.github.hddq.restoid.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hddq.restoid.data.*
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.model.RestoidMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SnapshotWithMetadata(
    val snapshotInfo: SnapshotInfo,
    val metadata: RestoidMetadata?
)

data class HomeUiState(
    val snapshotsWithMetadata: List<SnapshotWithMetadata> = emptyList(),
    val appInfoMap: Map<String, List<AppInfo>> = emptyMap(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val selectedRepo: String? = null,
    val resticState: ResticState = ResticState.Idle,
    val showPasswordDialogFor: String? = null,
    val hasPasswordForSelectedRepo: Boolean = false
)

class HomeViewModel(
    private val repositoriesRepository: RepositoriesRepository,
    private val resticBinaryManager: ResticBinaryManager, // Use Manager for state
    private val resticRepository: ResticRepository,
    private val appInfoRepository: AppInfoRepository,
    private val metadataRepository: MetadataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()
    private val refreshTrigger = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            repositoriesRepository.loadRepositories()
            resticBinaryManager.checkResticStatus()
        }

        combine(
            repositoriesRepository.selectedRepository,
            resticBinaryManager.resticState, // Observe state from Manager
            resticRepository.snapshots,
            repositoriesRepository.repositories,
            refreshTrigger
        ) { repoPath, restic, snapshots, repos, _ ->

            val hasPassword = repoPath?.let { repositoriesRepository.hasRepositoryPassword(it) } ?: false
            _uiState.update { it.copy(selectedRepo = repoPath, resticState = restic, hasPasswordForSelectedRepo = hasPassword) }

            if (repoPath == null || restic !is ResticState.Installed) {
                resticRepository.clearSnapshots()
                _uiState.update { it.copy(snapshotsWithMetadata = emptyList(), isLoading = false, error = null) }
                return@combine
            }

            if (snapshots == null) {
                if (!_uiState.value.isRefreshing) {
                    _uiState.update { it.copy(isLoading = true, error = null) }
                }
                loadSnapshots(repoPath, restic)
                return@combine
            }

            _uiState.update { it.copy(isLoading = false) }

            val repo = repos.find { it.path == repoPath }
            if (repo?.id == null) {
                // repoPath is guaranteed not null here because of the check above
                val errorMsg = "Repository ID not found"
                _uiState.update { it.copy(snapshotsWithMetadata = emptyList(), error = errorMsg) }
                return@combine
            }

            val filteredSnapshots = snapshots.filter { it.tags.contains("restoid") && it.tags.contains("backup") }

            val snapshotsWithMetadata = filteredSnapshots.map { snapshot ->
                val metadata = metadataRepository.getMetadataForSnapshot(repo.id, snapshot.id)
                SnapshotWithMetadata(snapshot, metadata)
            }

            _uiState.update { it.copy(snapshotsWithMetadata = snapshotsWithMetadata) }
            loadAppInfoForSnapshots(snapshotsWithMetadata)

        }.launchIn(viewModelScope)
    }

    private fun loadSnapshots(repoPath: String?, resticState: ResticState) {
        if (repoPath == null || resticState !is ResticState.Installed) {
            _uiState.update { it.copy(snapshotsWithMetadata = emptyList(), isLoading = false) }
            return
        }

        viewModelScope.launch {
            if (repositoriesRepository.hasRepositoryPassword(repoPath)) {
                val password = repositoriesRepository.getRepositoryPassword(repoPath)!!
                val result = resticRepository.getSnapshots(repoPath, password)
                if (result.isFailure) {
                    _uiState.update { it.copy(error = result.exceptionOrNull()?.message, isLoading = false) }
                }
            } else {
                _uiState.update { it.copy(isLoading = false, showPasswordDialogFor = repoPath) }
            }
        }
    }

    fun refreshSnapshots() {
        val repoPath = _uiState.value.selectedRepo
        val resticState = _uiState.value.resticState

        if (repoPath == null || resticState !is ResticState.Installed) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                if (repositoriesRepository.hasRepositoryPassword(repoPath)) {
                    val password = repositoriesRepository.getRepositoryPassword(repoPath)!!
                    val result = resticRepository.getSnapshots(repoPath, password)
                    if (result.isFailure) {
                        _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
                    } else {
                        refreshTrigger.update { it + 1 }
                    }
                } else {
                    _uiState.update { it.copy(showPasswordDialogFor = repoPath) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private fun loadAppInfoForSnapshots(snapshotsWithMetadata: List<SnapshotWithMetadata>) {
        viewModelScope.launch {
            val appInfoMap = withContext(Dispatchers.Default) {
                val map = mutableMapOf<String, List<AppInfo>>()
                snapshotsWithMetadata.forEach { item ->
                    val appsMetadata = item.metadata?.apps
                    val packageNames = appsMetadata?.keys?.toList() ?: emptyList()
                    if (packageNames.isNotEmpty()) {
                        val appInfos = appInfoRepository.getAppInfoForPackages(packageNames)
                        val sortedApps = appInfos.sortedByDescending { app ->
                            appsMetadata?.get(app.packageName)?.size ?: 0L
                        }
                        map[item.snapshotInfo.id] = sortedApps
                    }
                }
                map
            }
            _uiState.update { it.copy(appInfoMap = appInfoMap) }
        }
    }

    fun onPasswordEntered(password: String, save: Boolean) {
        val repoPath = _uiState.value.showPasswordDialogFor ?: return
        _uiState.update { it.copy(showPasswordDialogFor = null, isLoading = true, error = null) }

        if (save) repositoriesRepository.saveRepositoryPassword(repoPath, password)
        else repositoriesRepository.saveRepositoryPasswordTemporary(repoPath, password)

        loadSnapshots(repoPath, uiState.value.resticState)
    }

    fun onDismissPasswordDialog() {
        _uiState.update { it.copy(showPasswordDialogFor = null, isLoading = false) }
    }
}