package io.github.hddq.restoid.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hddq.restoid.data.AppInfoRepository
import io.github.hddq.restoid.data.MetadataRepository
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.ResticRepository
import io.github.hddq.restoid.data.ResticState
import io.github.hddq.restoid.data.SnapshotInfo
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.model.RestoidMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// New data class to combine snapshot with its metadata
data class SnapshotWithMetadata(
    val snapshotInfo: SnapshotInfo,
    val metadata: RestoidMetadata?
)

data class HomeUiState(
    val snapshotsWithMetadata: List<SnapshotWithMetadata> = emptyList(),
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
    private val appInfoRepository: AppInfoRepository,
    private val metadataRepository: MetadataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repositoriesRepository.loadRepositories()
            resticRepository.checkResticStatus()
        }

        // React to changes in repositories, restic status, or snapshots
        combine(
            repositoriesRepository.selectedRepository,
            resticRepository.resticState,
            resticRepository.snapshots,
            repositoriesRepository.repositories
        ) { repoPath, restic, snapshots, repos ->

            _uiState.update { it.copy(selectedRepo = repoPath, resticState = restic) }

            if (repoPath == null || restic !is ResticState.Installed) {
                resticRepository.clearSnapshots()
                _uiState.update { it.copy(snapshotsWithMetadata = emptyList(), isLoading = false, error = null) }
                return@combine
            }

            if (snapshots == null) {
                // State is "not loaded yet". Trigger the load and show spinner.
                _uiState.update { it.copy(isLoading = true, error = null) }
                loadSnapshots(repoPath, restic)
                return@combine // Wait for flow to be updated by loadSnapshots
            }

            // If we're here, snapshots are loaded (could be an empty list). Stop the spinner.
            _uiState.update { it.copy(isLoading = false) }

            val repo = repos.find { it.path == repoPath }
            if (repo?.id == null) {
                val errorMsg = if (repoPath != null) "Repository ID not found" else null
                _uiState.update { it.copy(snapshotsWithMetadata = emptyList(), error = errorMsg) }
                return@combine
            }

            val filteredSnapshots = snapshots.filter {
                it.tags.contains("restoid") && it.tags.contains("backup")
            }

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
                // On success, the `snapshots` flow is updated, which the `combine` block will handle.
            } else {
                _uiState.update { it.copy(isLoading = false, showPasswordDialogFor = repoPath) }
            }
        }
    }

    private fun loadAppInfoForSnapshots(snapshotsWithMetadata: List<SnapshotWithMetadata>) {
        viewModelScope.launch {
            val appInfoMap = mutableMapOf<String, List<AppInfo>>()
            snapshotsWithMetadata.forEach { item ->
                val packageNames = item.metadata?.apps?.keys?.toList() ?: emptyList()
                if (packageNames.isNotEmpty()) {
                    val appInfos = appInfoRepository.getAppInfoForPackages(packageNames)
                    appInfoMap[item.snapshotInfo.id] = appInfos
                }
            }
            _uiState.update { it.copy(appInfoMap = appInfoMap) }
        }
    }

    fun onPasswordEntered(password: String, save: Boolean) {
        val repoPath = _uiState.value.showPasswordDialogFor ?: return
        _uiState.update { it.copy(showPasswordDialogFor = null, isLoading = true, error = null) }

        if (save) {
            repositoriesRepository.saveRepositoryPassword(repoPath, password)
        } else {
            repositoriesRepository.saveRepositoryPasswordTemporary(repoPath, password)
        }

        // The original comment was incorrect, the combine block isn't triggered by saving a password.
        // We need to explicitly call loadSnapshots again now that we have a password.
        loadSnapshots(repoPath, uiState.value.resticState)
    }

    fun onDismissPasswordDialog() {
        _uiState.update { it.copy(showPasswordDialogFor = null, isLoading = false) }
    }
}
