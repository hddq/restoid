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

enum class HomeAuthFailure {
    REPOSITORY_PASSWORD,
    SFTP_PASSWORD,
    REST_CREDENTIALS
}

enum class HomeCredentialPrompt {
    REPOSITORY_PASSWORD,
    SFTP_PASSWORD,
    REST_CREDENTIALS
}

data class HomeUiState(
    val snapshotsWithMetadata: List<SnapshotWithMetadata> = emptyList(),
    val appInfoMap: Map<String, List<AppInfo>> = emptyMap(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val authFailure: HomeAuthFailure? = null,
    val openPrompt: HomeCredentialPrompt? = null,
    val selectedRepo: String? = null,
    val resticState: ResticState = ResticState.Idle,
    val showPasswordDialogFor: String? = null,
    val showSftpPasswordDialogFor: String? = null,
    val showRestCredentialsDialogFor: String? = null,
    val isRepoReady: Boolean = false
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
    private var lastObservedRepoKey: String? = null

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
            val isRepoSwitch = repoKey != lastObservedRepoKey

            val hasRepositoryPassword = repoKey?.let { repositoriesRepository.hasRepositoryPassword(it) } ?: false
            val hasSftpPassword = if (selectedRepository?.backendType == RepositoryBackendType.SFTP) {
                repositoriesRepository.hasSftpPassword(repositoriesRepository.repositoryKey(selectedRepository))
            } else {
                true
            }
            val hasRestCredentials = if (
                selectedRepository?.backendType == RepositoryBackendType.REST &&
                selectedRepository.restAuthRequired
            ) {
                repositoriesRepository.hasRestCredentials(repositoriesRepository.repositoryKey(selectedRepository))
            } else {
                true
            }

            val isRepoReady =
                repoKey != null &&
                    restic is ResticState.Installed &&
                    hasRepositoryPassword &&
                    hasSftpPassword &&
                    hasRestCredentials

            _uiState.update {
                it.copy(
                    selectedRepo = repoKey,
                    resticState = restic,
                    isRepoReady = isRepoReady
                )
            }

            if (isRepoSwitch) {
                lastObservedRepoKey = repoKey
                val canLoadSelectedRepo = repoPath != null && restic is ResticState.Installed

                if (snapshots != null) {
                    resticRepository.clearSnapshots()
                }

                _uiState.update {
                    it.copy(
                        snapshotsWithMetadata = emptyList(),
                        appInfoMap = emptyMap(),
                        isLoading = canLoadSelectedRepo,
                        isRefreshing = false,
                        error = null,
                        authFailure = null,
                        openPrompt = null,
                        showPasswordDialogFor = null,
                        showSftpPasswordDialogFor = null,
                        showRestCredentialsDialogFor = null
                    )
                }

                if (canLoadSelectedRepo && snapshots == null) {
                    loadSnapshots(repoPath, env, resticOptions, repoKey, restic)
                }

                return@combine
            }

            if (repoPath == null || restic !is ResticState.Installed) {
                resticRepository.clearSnapshots()
                _uiState.update {
                    it.copy(
                        snapshotsWithMetadata = emptyList(),
                        appInfoMap = emptyMap(),
                        isLoading = false,
                        error = null,
                        authFailure = null,
                        openPrompt = null,
                        showPasswordDialogFor = null,
                        showSftpPasswordDialogFor = null,
                        showRestCredentialsDialogFor = null
                    )
                }
                return@combine
            }

            if (snapshots == null) {
                if (!_uiState.value.isRefreshing) {
                    _uiState.update { it.copy(isLoading = true, error = null, openPrompt = null) }
                }
                loadSnapshots(repoPath, env, resticOptions, repoKey, restic)
                return@combine
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = null,
                    authFailure = null,
                    openPrompt = null,
                    showPasswordDialogFor = null,
                    showSftpPasswordDialogFor = null,
                    showRestCredentialsDialogFor = null
                )
            }

            val repo = repos.find { repositoriesRepository.repositoryKey(it) == repoKey }
            if (repo?.id == null) {
                // repoPath is guaranteed not null here because of the check above
                val errorMsg = context.getString(R.string.home_error_repository_id_not_found)
                _uiState.update {
                    it.copy(
                        snapshotsWithMetadata = emptyList(),
                        appInfoMap = emptyMap(),
                        error = errorMsg,
                        authFailure = null,
                        openPrompt = null
                    )
                }
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
            _uiState.update {
                it.copy(
                    snapshotsWithMetadata = emptyList(),
                    appInfoMap = emptyMap(),
                    isLoading = false,
                    authFailure = null,
                    openPrompt = null
                )
            }
            return
        }

        val repository = repositoriesRepository.getRepositoryByKey(repoKey)
            ?: run {
                _uiState.update {
                    it.copy(
                        snapshotsWithMetadata = emptyList(),
                        appInfoMap = emptyMap(),
                        isLoading = false,
                        authFailure = null,
                        openPrompt = null
                    )
                }
                return
            }

        viewModelScope.launch {
            if (repository.backendType == RepositoryBackendType.SFTP && !repositoriesRepository.hasSftpPassword(repoKey)) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        authFailure = null,
                        openPrompt = null,
                        showPasswordDialogFor = null,
                        showSftpPasswordDialogFor = repoKey,
                        showRestCredentialsDialogFor = null
                    )
                }
                return@launch
            }

            if (
                repository.backendType == RepositoryBackendType.REST &&
                repository.restAuthRequired &&
                !repositoriesRepository.hasRestCredentials(repoKey)
            ) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        authFailure = null,
                        openPrompt = null,
                        showPasswordDialogFor = null,
                        showSftpPasswordDialogFor = null,
                        showRestCredentialsDialogFor = repoKey
                    )
                }
                return@launch
            }

            if (!repositoriesRepository.hasRepositoryPassword(repoKey)) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        authFailure = null,
                        openPrompt = null,
                        showPasswordDialogFor = repoKey,
                        showSftpPasswordDialogFor = null,
                        showRestCredentialsDialogFor = null
                    )
                }
                return@launch
            }

            val password = repositoriesRepository.getRepositoryPassword(repoKey)!!
            val result = resticRepository.getSnapshots(repoPath, password, environmentVariables, resticOptions)
            if (result.isFailure) {
                val rawMessage = result.exceptionOrNull()?.message ?: context.getString(R.string.error_unknown)
                val authFailure = classifyAuthenticationFailure(repository, rawMessage)
                _uiState.update {
                    it.copy(
                        error = when (authFailure) {
                            HomeAuthFailure.REPOSITORY_PASSWORD -> context.getString(R.string.home_error_repository_password_incorrect)
                            HomeAuthFailure.SFTP_PASSWORD -> context.getString(R.string.home_error_sftp_password_incorrect)
                            HomeAuthFailure.REST_CREDENTIALS -> context.getString(R.string.home_error_rest_credentials_incorrect)
                            null -> rawMessage
                        },
                        authFailure = authFailure,
                        openPrompt = null,
                        isLoading = false,
                        showPasswordDialogFor = null,
                        showSftpPasswordDialogFor = null,
                        showRestCredentialsDialogFor = null
                    )
                }
            }
        }
    }

    fun refreshSnapshots() {
        val repoKey = _uiState.value.selectedRepo
        val resticState = _uiState.value.resticState
        if (repoKey == null || resticState !is ResticState.Installed) return

        val repository = repositoriesRepository.getRepositoryByKey(repoKey) ?: return
        val repoPath = repository.path
        val environmentVariables = repositoriesRepository.getExecutionEnvironmentVariables(repoKey)
        val resticOptions = repositoriesRepository.getExecutionResticOptions(repoKey)

        if (repository.backendType == RepositoryBackendType.SFTP && !repositoriesRepository.hasSftpPassword(repoKey)) {
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    error = null,
                    authFailure = null,
                    openPrompt = null,
                    showPasswordDialogFor = null,
                    showSftpPasswordDialogFor = repoKey,
                    showRestCredentialsDialogFor = null
                )
            }
            return
        }

        if (
            repository.backendType == RepositoryBackendType.REST &&
            repository.restAuthRequired &&
            !repositoriesRepository.hasRestCredentials(repoKey)
        ) {
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    error = null,
                    authFailure = null,
                    openPrompt = null,
                    showPasswordDialogFor = null,
                    showSftpPasswordDialogFor = null,
                    showRestCredentialsDialogFor = repoKey
                )
            }
            return
        }

        if (!repositoriesRepository.hasRepositoryPassword(repoKey)) {
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    error = null,
                    authFailure = null,
                    openPrompt = null,
                    showPasswordDialogFor = repoKey,
                    showSftpPasswordDialogFor = null,
                    showRestCredentialsDialogFor = null
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null, authFailure = null, openPrompt = null) }
            try {
                val password = repositoriesRepository.getRepositoryPassword(repoKey)!!
                val result = resticRepository.getSnapshots(repoPath, password, environmentVariables, resticOptions)
                if (result.isFailure) {
                    val rawMessage = result.exceptionOrNull()?.message ?: context.getString(R.string.error_unknown)
                    val authFailure = classifyAuthenticationFailure(repository, rawMessage)
                    _uiState.update {
                        it.copy(
                            error = when (authFailure) {
                                HomeAuthFailure.REPOSITORY_PASSWORD -> context.getString(R.string.home_error_repository_password_incorrect)
                                HomeAuthFailure.SFTP_PASSWORD -> context.getString(R.string.home_error_sftp_password_incorrect)
                                HomeAuthFailure.REST_CREDENTIALS -> context.getString(R.string.home_error_rest_credentials_incorrect)
                                null -> rawMessage
                            },
                            authFailure = authFailure,
                            openPrompt = null
                        )
                    }
                } else {
                    refreshTrigger.update { it + 1 }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, authFailure = null, openPrompt = null) }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private fun classifyAuthenticationFailure(
        repository: LocalRepository,
        message: String
    ): HomeAuthFailure? {
        val normalized = message.lowercase()
        val isRepositoryPasswordError =
            normalized.contains("wrong password or no key found") ||
                normalized.contains("wrong password") ||
                normalized.contains("no key found")

        if (isRepositoryPasswordError) {
            return HomeAuthFailure.REPOSITORY_PASSWORD
        }

        if (repository.backendType == RepositoryBackendType.SFTP) {
            val isSftpAuthError =
                normalized.contains("permission denied (") ||
                    normalized.contains("authentication failed") ||
                    normalized.contains("unable to authenticate") ||
                    normalized.contains("too many authentication failures") ||
                    normalized.contains("ssh: handshake failed") ||
                    normalized.contains("publickey,password") ||
                    normalized.contains("keyboard-interactive")

            if (isSftpAuthError) {
                return HomeAuthFailure.SFTP_PASSWORD
            }
        }

        if (repository.backendType == RepositoryBackendType.REST && repository.restAuthRequired) {
            val isRestAuthError =
                normalized.contains("401") ||
                    normalized.contains("403") ||
                    normalized.contains("unauthorized") ||
                    normalized.contains("forbidden") ||
                    normalized.contains("authentication required") ||
                    normalized.contains("authentication failed") ||
                    normalized.contains("authorization failed") ||
                    normalized.contains("access denied")

            if (isRestAuthError) {
                return HomeAuthFailure.REST_CREDENTIALS
            }
        }

        return null
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
                showRestCredentialsDialogFor = null,
                isLoading = true,
                error = null,
                authFailure = null,
                openPrompt = null
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
                showRestCredentialsDialogFor = null,
                snapshotsWithMetadata = emptyList(),
                appInfoMap = emptyMap(),
                isLoading = false,
                error = null,
                authFailure = null,
                openPrompt = HomeCredentialPrompt.REPOSITORY_PASSWORD
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
                showRestCredentialsDialogFor = null,
                isLoading = true,
                error = null,
                authFailure = null,
                openPrompt = null
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
                showRestCredentialsDialogFor = null,
                snapshotsWithMetadata = emptyList(),
                appInfoMap = emptyMap(),
                isLoading = false,
                error = null,
                authFailure = null,
                openPrompt = HomeCredentialPrompt.SFTP_PASSWORD
            )
        }
    }

    fun onRestCredentialsEntered(username: String, password: String, save: Boolean) {
        val repoKey = _uiState.value.showRestCredentialsDialogFor ?: return
        val repository = repositoriesRepository.getRepositoryByKey(repoKey) ?: return
        _uiState.update {
            it.copy(
                showRestCredentialsDialogFor = null,
                showSftpPasswordDialogFor = null,
                showPasswordDialogFor = null,
                isLoading = true,
                error = null,
                authFailure = null,
                openPrompt = null
            )
        }

        if (save) repositoriesRepository.saveRestCredentials(repoKey, username, password)
        else repositoriesRepository.saveRestCredentialsTemporary(repoKey, username, password)

        loadSnapshots(
            repository.path,
            repositoriesRepository.getExecutionEnvironmentVariables(repoKey),
            repositoriesRepository.getExecutionResticOptions(repoKey),
            repoKey,
            uiState.value.resticState
        )
    }

    fun onDismissRestCredentialsDialog() {
        _uiState.update {
            it.copy(
                showRestCredentialsDialogFor = null,
                showSftpPasswordDialogFor = null,
                showPasswordDialogFor = null,
                snapshotsWithMetadata = emptyList(),
                appInfoMap = emptyMap(),
                isLoading = false,
                error = null,
                authFailure = null,
                openPrompt = HomeCredentialPrompt.REST_CREDENTIALS
            )
        }
    }

    fun onRetryRepositoryPasswordEntry() {
        val repoKey = _uiState.value.selectedRepo ?: return
        _uiState.update {
            it.copy(
                showPasswordDialogFor = repoKey,
                showSftpPasswordDialogFor = null,
                showRestCredentialsDialogFor = null,
                isLoading = false,
                error = null,
                authFailure = null,
                openPrompt = null
            )
        }
    }

    fun onRetrySftpPasswordEntry() {
        val repoKey = _uiState.value.selectedRepo ?: return
        val repository = repositoriesRepository.getRepositoryByKey(repoKey) ?: return
        if (repository.backendType != RepositoryBackendType.SFTP) return

        _uiState.update {
            it.copy(
                showSftpPasswordDialogFor = repoKey,
                showPasswordDialogFor = null,
                showRestCredentialsDialogFor = null,
                isLoading = false,
                error = null,
                authFailure = null,
                openPrompt = null
            )
        }
    }

    fun onRetryRestCredentialsEntry() {
        val repoKey = _uiState.value.selectedRepo ?: return
        val repository = repositoriesRepository.getRepositoryByKey(repoKey) ?: return
        if (repository.backendType != RepositoryBackendType.REST || !repository.restAuthRequired) return

        _uiState.update {
            it.copy(
                showRestCredentialsDialogFor = repoKey,
                showSftpPasswordDialogFor = null,
                showPasswordDialogFor = null,
                isLoading = false,
                error = null,
                authFailure = null,
                openPrompt = null
            )
        }
    }
}
