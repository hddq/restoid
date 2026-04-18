package io.github.hddq.restoid.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hddq.restoid.data.*
import io.github.hddq.restoid.R
import io.github.hddq.restoid.util.isValidEnvironmentVariableName
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddRepoUiState(
    val backendType: RepositoryBackendType = RepositoryBackendType.LOCAL,
    val path: String = "",
    val password: String = "",
    val sftpPassword: String = "",
    val trustSftpServer: Boolean = false,
    val environmentVariablesRaw: String = "",
    val savePassword: Boolean = true,
    val showDialog: Boolean = false,
    val state: AddRepositoryState = AddRepositoryState.Idle
)

enum class ChangePasswordState { Idle, InProgress, Success, Error }

class SettingsViewModel(
    private val context: android.content.Context,
    private val rootRepository: RootRepository,
    private val resticBinaryManager: ResticBinaryManager, // Injected Manager
    private val resticRepository: ResticRepository,
    private val repositoriesRepository: RepositoriesRepository,
    private val notificationRepository: NotificationRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val rootState = rootRepository.rootState
    // Use Manager for binary state
    val resticState = resticBinaryManager.resticState
    val latestResticVersion = resticBinaryManager.latestResticVersion
    val stableResticVersion: String get() = resticBinaryManager.stableResticVersion

    val repositories = repositoriesRepository.repositories
    val selectedRepository = repositoriesRepository.selectedRepository
    val notificationPermissionState = notificationRepository.permissionState

    private val _addRepoUiState = MutableStateFlow(AddRepoUiState())
    val addRepoUiState = _addRepoUiState.asStateFlow()

    private val _changePasswordState = MutableStateFlow(ChangePasswordState.Idle)
    val changePasswordState = _changePasswordState.asStateFlow()

    private val _requireAppUnlock = MutableStateFlow(preferencesRepository.loadRequireAppUnlock())
    val requireAppUnlock = _requireAppUnlock.asStateFlow()

    init {
        viewModelScope.launch {
            resticBinaryManager.fetchLatestResticVersion()
        }
    }

    fun requestRootAccess() {
        viewModelScope.launch { rootRepository.checkRootAccess() }
    }

    fun repositoryKey(repository: LocalRepository): String = repositoriesRepository.repositoryKey(repository)

    fun hasRepositoryPassword(key: String) = repositoriesRepository.hasRepositoryPassword(key)
    fun hasStoredRepositoryPassword(key: String) = repositoriesRepository.hasStoredRepositoryPassword(key)
    fun forgetPassword(key: String) = repositoriesRepository.forgetPassword(key)
    fun savePassword(key: String, password: String) = repositoriesRepository.saveRepositoryPassword(key, password)
    fun hasSftpPassword(key: String) = repositoriesRepository.hasSftpPassword(key)
    fun forgetSftpPassword(key: String) = repositoriesRepository.forgetSftpPassword(key)

    fun deleteRepository(key: String) {
        viewModelScope.launch { repositoriesRepository.deleteRepository(key) }
    }

    fun changePassword(key: String, oldPassword: String, newPassword: String) {
        viewModelScope.launch {
            _changePasswordState.value = ChangePasswordState.InProgress
            val repository = repositoriesRepository.getRepositoryByKey(key)
            if (repository == null) {
                _changePasswordState.value = ChangePasswordState.Error
                return@launch
            }

            val result = resticRepository.changePassword(
                repository.path,
                oldPassword,
                newPassword,
                repositoriesRepository.getExecutionEnvironmentVariables(key),
                repositoriesRepository.getExecutionResticOptions(key)
            )
            if (result.isSuccess) {
                if (repositoriesRepository.hasStoredRepositoryPassword(key)) {
                    repositoriesRepository.saveRepositoryPassword(key, newPassword)
                }
                _changePasswordState.value = ChangePasswordState.Success
            } else {
                _changePasswordState.value = ChangePasswordState.Error
            }
        }
    }

    fun resetChangePasswordState() { _changePasswordState.value = ChangePasswordState.Idle }
    fun checkNotificationPermission() = notificationRepository.checkPermissionStatus()

    fun onRequireAppUnlockChanged(required: Boolean) {
        _requireAppUnlock.value = required
        preferencesRepository.saveRequireAppUnlock(required)
    }

    fun downloadRestic() {
        viewModelScope.launch { resticBinaryManager.downloadAndInstallRestic() }
    }

    fun downloadLatestRestic() {
        viewModelScope.launch { resticBinaryManager.downloadAndInstallLatestRestic() }
    }

    fun selectRepository(key: String) = repositoriesRepository.selectRepository(key)

    // UI handlers
    fun onNewRepoBackendTypeChanged(backendType: RepositoryBackendType) {
        _addRepoUiState.update {
            if (it.backendType == backendType) it else it.copy(
                backendType = backendType,
                path = "",
                sftpPassword = "",
                trustSftpServer = false,
                environmentVariablesRaw = "",
                state = AddRepositoryState.Idle
            )
        }
    }

    fun onNewRepoPathChanged(path: String) = _addRepoUiState.update { it.copy(path = path) }
    fun onNewRepoPasswordChanged(password: String) = _addRepoUiState.update { it.copy(password = password) }
    fun onNewRepoSftpPasswordChanged(password: String) = _addRepoUiState.update { it.copy(sftpPassword = password) }
    fun onTrustSftpServerChanged(trust: Boolean) = _addRepoUiState.update { it.copy(trustSftpServer = trust) }
    fun onNewRepoEnvironmentVariablesChanged(raw: String) = _addRepoUiState.update { it.copy(environmentVariablesRaw = raw) }
    fun onSavePasswordChanged(save: Boolean) = _addRepoUiState.update { it.copy(savePassword = save) }
    fun onNewRepoDialogDismiss() { _addRepoUiState.value = AddRepoUiState() }

    fun onShowAddRepoDialog() {
        if (resticState.value is ResticState.Installed) {
            _addRepoUiState.update { it.copy(showDialog = true) }
        }
    }

    fun addRepository() {
        if (resticState.value !is ResticState.Installed) {
            _addRepoUiState.update { it.copy(state = AddRepositoryState.Error(context.getString(R.string.error_restic_not_installed))) }
            return
        }

        val path = addRepoUiState.value.path.trim()
        val password = addRepoUiState.value.password
        val sftpPassword = addRepoUiState.value.sftpPassword
        val trustSftpServer = addRepoUiState.value.trustSftpServer
        val savePassword = addRepoUiState.value.savePassword
        val backendType = addRepoUiState.value.backendType
        val envParseResult = parseEnvironmentVariables(addRepoUiState.value.environmentVariablesRaw)

        if (path.isBlank() || password.isBlank()) {
            _addRepoUiState.update { it.copy(state = AddRepositoryState.Error(context.getString(R.string.settings_error_path_password_empty))) }
            return
        }

        if (backendType == RepositoryBackendType.SFTP && !trustSftpServer) {
            _addRepoUiState.update {
                it.copy(state = AddRepositoryState.Error(context.getString(R.string.repo_error_sftp_server_trust_required)))
            }
            return
        }

        if (envParseResult.isFailure) {
            _addRepoUiState.update {
                it.copy(
                    state = AddRepositoryState.Error(
                        envParseResult.exceptionOrNull()?.message
                            ?: context.getString(R.string.settings_error_invalid_environment_variables)
                    )
                )
            }
            return
        }

        val environmentVariables = envParseResult.getOrNull().orEmpty()

        viewModelScope.launch {
            _addRepoUiState.update { it.copy(state = AddRepositoryState.Initializing) }
            val result = repositoriesRepository.addRepository(
                path = path,
                backendType = backendType,
                password = password,
                environmentVariables = environmentVariables,
                resticOptions = emptyMap(),
                sftpPassword = sftpPassword,
                trustSftpServer = trustSftpServer,
                resticRepository = resticRepository,
                savePassword = savePassword
            )
            _addRepoUiState.update { it.copy(state = result) }

            if (result is AddRepositoryState.Success) {
                delay(1000)
                onNewRepoDialogDismiss()
            }
        }
    }

    private fun parseEnvironmentVariables(raw: String): Result<Map<String, String>> {
        if (raw.isBlank()) return Result.success(emptyMap())

        val parsed = linkedMapOf<String, String>()
        val lines = raw.lines()

        lines.forEachIndexed { index, originalLine ->
            val line = originalLine.trim()
            if (line.isEmpty()) return@forEachIndexed

            val normalized = if (line.startsWith("export ")) line.removePrefix("export ").trim() else line
            val separatorIndex = normalized.indexOf('=')
            if (separatorIndex <= 0) {
                val message = context.getString(R.string.settings_error_invalid_environment_line, index + 1)
                return Result.failure(IllegalArgumentException(message))
            }

            val key = normalized.substring(0, separatorIndex).trim()
            var value = normalized.substring(separatorIndex + 1).trim()

            if (!isValidEnvironmentVariableName(key)) {
                val message = context.getString(R.string.settings_error_invalid_environment_name, key)
                return Result.failure(IllegalArgumentException(message))
            }

            if (value.length >= 2 &&
                ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith('\'') && value.endsWith('\'')))
            ) {
                value = value.substring(1, value.length - 1)
            }

            parsed[key] = value
        }

        return Result.success(parsed)
    }
}
