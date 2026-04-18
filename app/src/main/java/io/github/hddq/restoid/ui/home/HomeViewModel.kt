package io.github.hddq.restoid.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hddq.restoid.data.*
import io.github.hddq.restoid.R
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
    val showSftpPasswordDialogFor: String? = null,
    val hasPasswordForSelectedRepo: Boolean = false
)

class HomeViewModel(
    private val context: android.content.Context,
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
        ) { repoKey, restic, snapshots, repos, _ ->

            val selectedRepository = repoKey?.let { repositoriesRepository.getRepositoryByKey(it) }
            val repoPath = selectedRepository?.path
            val env = repoKey?.let { repositoriesRepository.getExecutionEnvironmentVariables(it) }.orEmpty()
            val resticOptions = repoKey?.let { repositoriesRepository.getExecutionResticOptions(it) }.orEmpty()

            val hasRepositoryPassword = repoKey?.let { repositoriesRepository.hasRepositoryPassword(it) } ?: false
            val hasSftpPassword = if (selectedRepository?.backendType == RepositoryBackendType.SFTP) {
                repositoriesRepository.hasSftpPassword(repositoriesRepository.repositoryKey(selectedRepository))
            } else {
                true
            }
            _uiState.update {
                it.copy(
                    selectedRepo = repoKey,
                    resticState = restic,
                    hasPasswordForSelectedRepo = hasRepositoryPassword && hasSftpPassword
                )
            }

            if (repoPath == null || restic !is ResticState.Installed) {
                resticRepository.clearSnapshots()
                _uiState.update { it.copy(snapshotsWithMetadata = emptyList(), isLoading = false, error = null) }
                return@combine
            }

            if (snapshots == null) {
                if (!_uiState.value.isRefreshing) {
                    _uiState.update { it.copy(isLoading = true, error = null) }
                }
                loadSnapshots(repoPath, env, resticOptions, repoKey, restic)
                return@combine
            }

            _uiState.update { it.copy(isLoading = false) }

            val repo = repos.find { repositoriesRepository.repositoryKey(it) == repoKey }
            if (repo?.id == null) {
                // repoPath is guaranteed not null here because of the check above
                val errorMsg = context.getString(R.string.home_error_repository_id_not_found)
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

    private fun loadSnapshots(
        repoPath: String?,
        environmentVariables: Map<String, String>,
        resticOptions: Map<String, String>,
        repoKey: String?,
        resticState: ResticState
    ) {
        if (repoPath == null || repoKey == null || resticState !is ResticState.Installed) {
            _uiState.update { it.copy(snapshotsWithMetadata = emptyList(), isLoading = false) }
            return
        }

        val repository = repositoriesRepository.getRepositoryByKey(repoKey)
            ?: run {
                _uiState.update { it.copy(snapshotsWithMetadata = emptyList(), isLoading = false) }
                return
            }

        viewModelScope.launch {
            if (!repositoriesRepository.hasRepositoryPassword(repoKey)) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        showPasswordDialogFor = repoKey,
                        showSftpPasswordDialogFor = null
                    )
                }
                return@launch
            }

            if (repository.backendType == RepositoryBackendType.SFTP && !repositoriesRepository.hasSftpPassword(repoKey)) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        showPasswordDialogFor = null,
                        showSftpPasswordDialogFor = repoKey
                    )
                }
                return@launch
            }

            val password = repositoriesRepository.getRepositoryPassword(repoKey)!!
            val result = resticRepository.getSnapshots(repoPath, password, environmentVariables, resticOptions)
            if (result.isFailure) {
                _uiState.update {
                    it.copy(
                        error = result.exceptionOrNull()?.message,
                        isLoading = false,
                        showPasswordDialogFor = null,
                        showSftpPasswordDialogFor = null
                    )
                }
            }
        }
    }

    fun refreshSnapshots() {
        val repoKey = _uiState.value.selectedRepo
        val resticState = _uiState.value.resticState
        if (repoKey == null || resticState !is ResticState.Installed) return

        val repository = repositoriesRepository.getRepositoryByKey(repoKey)
        val repoPath = repository?.path
        val environmentVariables = repositoriesRepository.getExecutionEnvironmentVariables(repoKey)
        val resticOptions = repositoriesRepository.getExecutionResticOptions(repoKey)

        if (repoPath == null) return
        if (!repositoriesRepository.hasRepositoryPassword(repoKey)) {
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    error = null,
                    showPasswordDialogFor = repoKey,
                    showSftpPasswordDialogFor = null
                )
            }
            return
        }

        if (repository.backendType == RepositoryBackendType.SFTP && !repositoriesRepository.hasSftpPassword(repoKey)) {
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    error = null,
                    showPasswordDialogFor = null,
                    showSftpPasswordDialogFor = repoKey
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                val password = repositoriesRepository.getRepositoryPassword(repoKey)!!
                val result = resticRepository.getSnapshots(repoPath, password, environmentVariables, resticOptions)
                if (result.isFailure) {
                    _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
                } else {
                    refreshTrigger.update { it + 1 }
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
        val repoKey = _uiState.value.showPasswordDialogFor ?: return
        val repository = repositoriesRepository.getRepositoryByKey(repoKey) ?: return
        _uiState.update {
            it.copy(
                showPasswordDialogFor = null,
                showSftpPasswordDialogFor = null,
                isLoading = true,
                error = null
            )
        }

        if (save) repositoriesRepository.saveRepositoryPassword(repoKey, password)
        else repositoriesRepository.saveRepositoryPasswordTemporary(repoKey, password)

        loadSnapshots(
            repository.path,
            repositoriesRepository.getExecutionEnvironmentVariables(repoKey),
            repositoriesRepository.getExecutionResticOptions(repoKey),
            repoKey,
            uiState.value.resticState
        )
    }

    fun onDismissPasswordDialog() {
        _uiState.update {
            it.copy(
                showPasswordDialogFor = null,
                showSftpPasswordDialogFor = null,
                isLoading = false
            )
        }
    }

    fun onSftpPasswordEntered(password: String, save: Boolean) {
        val repoKey = _uiState.value.showSftpPasswordDialogFor ?: return
        val repository = repositoriesRepository.getRepositoryByKey(repoKey) ?: return
        _uiState.update {
            it.copy(
                showSftpPasswordDialogFor = null,
                showPasswordDialogFor = null,
                isLoading = true,
                error = null
            )
        }

        if (save) repositoriesRepository.saveSftpPassword(repoKey, password)
        else repositoriesRepository.saveSftpPasswordTemporary(repoKey, password)

        loadSnapshots(
            repository.path,
            repositoriesRepository.getExecutionEnvironmentVariables(repoKey),
            repositoriesRepository.getExecutionResticOptions(repoKey),
            repoKey,
            uiState.value.resticState
        )
    }

    fun onDismissSftpPasswordDialog() {
        _uiState.update {
            it.copy(
                showSftpPasswordDialogFor = null,
                showPasswordDialogFor = null,
                isLoading = false
            )
        }
    }
}
