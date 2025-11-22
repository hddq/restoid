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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Data class to combine snapshot with its metadata
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
    private val resticRepository: ResticRepository,
    private val appInfoRepository: AppInfoRepository,
    private val metadataRepository: MetadataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    // Added a trigger to force metadata re-reads even if snapshots list hasn't changed
    private val refreshTrigger = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            repositoriesRepository.loadRepositories()
            resticRepository.checkResticStatus()
        }

        // React to changes in repositories, restic status, snapshots, OR the manual refresh trigger
        combine(
            repositoriesRepository.selectedRepository,
            resticRepository.resticState,
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
                // State is "not loaded yet". Trigger the load and show spinner.
                // Only show main loader if we aren't already refreshing
                if (!_uiState.value.isRefreshing) {
                    _uiState.update { it.copy(isLoading = true, error = null) }
                }
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
                // access metadataRepository here, which reads from disk.
                // Triggering this block via refreshTrigger ensures we re-read files.
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

    // Explicit refresh function for pull-to-refresh
    fun refreshSnapshots() {
        val repoPath = _uiState.value.selectedRepo
        val resticState = _uiState.value.resticState

        if (repoPath == null || resticState !is ResticState.Installed) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }

            try {
                if (repositoriesRepository.hasRepositoryPassword(repoPath)) {
                    val password = repositoriesRepository.getRepositoryPassword(repoPath)!!
                    // We await this call to ensure the refresh indicator stays until done
                    val result = resticRepository.getSnapshots(repoPath, password)
                    if (result.isFailure) {
                        _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
                    } else {
                        // Force the combine block to run again to reload metadata from disk
                        refreshTrigger.update { it + 1 }
                    }
                } else {
                    _uiState.update { it.copy(showPasswordDialogFor = repoPath) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                // Always turn off the refreshing state, preventing infinite loader
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private fun loadAppInfoForSnapshots(snapshotsWithMetadata: List<SnapshotWithMetadata>) {
        viewModelScope.launch {
            // We switch to Default dispatcher to ensure sorting doesn't affect UI thread,
            // although it's very fast.
            val appInfoMap = withContext(Dispatchers.Default) {
                val map = mutableMapOf<String, List<AppInfo>>()
                snapshotsWithMetadata.forEach { item ->
                    val appsMetadata = item.metadata?.apps
                    val packageNames = appsMetadata?.keys?.toList() ?: emptyList()

                    if (packageNames.isNotEmpty()) {
                        val appInfos = appInfoRepository.getAppInfoForPackages(packageNames)

                        // SORTING LOGIC: Sort by size (descending)
                        // If size is missing (0L), those apps go to the end.
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

        if (save) {
            repositoriesRepository.saveRepositoryPassword(repoPath, password)
        } else {
            repositoriesRepository.saveRepositoryPasswordTemporary(repoPath, password)
        }

        // We need to explicitly call loadSnapshots again now that we have a password.
        loadSnapshots(repoPath, uiState.value.resticState)
    }

    fun onDismissPasswordDialog() {
        _uiState.update { it.copy(showPasswordDialogFor = null, isLoading = false) }
    }
}