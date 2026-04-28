package io.github.hddq.restoid.ui.settings

import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hddq.restoid.data.*
import io.github.hddq.restoid.R
import io.github.hddq.restoid.util.isValidEnvironmentVariableName
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AddRepoUiState(
    val backendType: RepositoryBackendType = RepositoryBackendType.LOCAL,
    val path: String = "",
    val password: String = "",
    val sftpPassword: String = "",
    val s3AccessKeyId: String = "",
    val s3SecretAccessKey: String = "",
    val restUsername: String = "",
    val restPassword: String = "",
    val sftpServerTrustInfo: SftpServerTrustInfo? = null,
    val environmentVariablesRaw: String = "",
    val savePassword: Boolean = true,
    val showDialog: Boolean = false,
    val state: AddRepositoryState = AddRepositoryState.Idle
)

enum class ChangePasswordState { Idle, InProgress, Success, Error }

private data class AddRepositoryInput(
    val path: String,
    val backendType: RepositoryBackendType,
    val password: String,
    val sftpPassword: String,
    val s3AccessKeyId: String,
    val s3SecretAccessKey: String,
    val restUsername: String,
    val restPassword: String,
    val environmentVariables: Map<String, String>,
    val savePassword: Boolean
)

private data class PendingSftpTrustRequest(
    val input: AddRepositoryInput,
    val knownHostEntries: List<String>
)

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
    val resticState = resticBinaryManager.resticState

    val repositories = repositoriesRepository.repositories
    val selectedRepository = repositoriesRepository.selectedRepository
    val notificationPermissionState = notificationRepository.permissionState

    private val _addRepoUiState = MutableStateFlow(AddRepoUiState())
    val addRepoUiState = _addRepoUiState.asStateFlow()

    private val _changePasswordState = MutableStateFlow(ChangePasswordState.Idle)
    val changePasswordState = _changePasswordState.asStateFlow()

    private val _requireAppUnlock = MutableStateFlow(preferencesRepository.loadRequireAppUnlock())
    val requireAppUnlock = _requireAppUnlock.asStateFlow()

    private val _isIgnoringBatteryOptimizations = MutableStateFlow(false)
    val isIgnoringBatteryOptimizations = _isIgnoringBatteryOptimizations.asStateFlow()

    private var pendingSftpTrustRequest: PendingSftpTrustRequest? = null

    init {
        viewModelScope.launch {
            refreshSystemState()
        }
    }

    fun requestRootAccess() {
        viewModelScope.launch {
            refreshRootDependentState()
        }
    }

    fun refreshSystemState() {
        viewModelScope.launch {
            refreshRootDependentState()
            refreshBatteryOptimizationStatus()
            checkNotificationPermission()
        }
    }

    private suspend fun refreshRootDependentState() {
        rootRepository.checkRootAccess()
        withContext(Dispatchers.IO) {
            resticBinaryManager.checkResticStatus()
            repositoriesRepository.loadRepositories()
        }
    }

    fun repositoryKey(repository: LocalRepository): String = repositoriesRepository.repositoryKey(repository)

    fun hasRepositoryPassword(key: String) = repositoriesRepository.hasRepositoryPassword(key)
    fun hasStoredRepositoryPassword(key: String) = repositoriesRepository.hasStoredRepositoryPassword(key)
    fun forgetPassword(key: String) = repositoriesRepository.forgetPassword(key)
    fun savePassword(key: String, password: String) = repositoriesRepository.saveRepositoryPassword(key, password)
    fun hasSftpPassword(key: String) = repositoriesRepository.hasSftpPassword(key)
    fun hasStoredSftpPassword(key: String) = repositoriesRepository.hasStoredSftpPassword(key)
    fun forgetSftpPassword(key: String) = repositoriesRepository.forgetSftpPassword(key)
    fun saveSftpPassword(key: String, password: String) = repositoriesRepository.saveSftpPassword(key, password)
    fun hasRestCredentials(key: String) = repositoriesRepository.hasRestCredentials(key)
    fun hasStoredRestCredentials(key: String) = repositoriesRepository.hasStoredRestCredentials(key)
    fun forgetRestCredentials(key: String) = repositoriesRepository.forgetRestCredentials(key)
    fun saveRestCredentials(key: String, username: String, password: String) {
        repositoriesRepository.saveRestCredentials(key, username, password)
    }
    fun hasS3Credentials(key: String) = repositoriesRepository.hasS3Credentials(key)
    fun hasStoredS3Credentials(key: String) = repositoriesRepository.hasStoredS3Credentials(key)
    fun forgetS3Credentials(key: String) = repositoriesRepository.forgetS3Credentials(key)
    fun saveS3Credentials(key: String, accessKeyId: String, secretAccessKey: String) {
        repositoriesRepository.saveS3Credentials(key, accessKeyId, secretAccessKey)
    }

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

    fun refreshBatteryOptimizationStatus() {
        val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        _isIgnoringBatteryOptimizations.value = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun selectRepository(key: String) = repositoriesRepository.selectRepository(key)

    // UI handlers
    fun onNewRepoBackendTypeChanged(backendType: RepositoryBackendType) {
        pendingSftpTrustRequest = null
        _addRepoUiState.update {
            if (it.backendType == backendType) it else it.copy(
                backendType = backendType,
                path = "",
                sftpPassword = "",
                s3AccessKeyId = "",
                s3SecretAccessKey = "",
                restUsername = "",
                restPassword = "",
                sftpServerTrustInfo = null,
                environmentVariablesRaw = "",
                state = AddRepositoryState.Idle
            )
        }
    }

    fun onNewRepoPathChanged(path: String) {
        pendingSftpTrustRequest = null
        _addRepoUiState.update { it.copy(path = path, sftpServerTrustInfo = null) }
    }

    fun onNewRepoPasswordChanged(password: String) {
        pendingSftpTrustRequest = null
        _addRepoUiState.update { it.copy(password = password, sftpServerTrustInfo = null) }
    }

    fun onNewRepoSftpPasswordChanged(password: String) {
        pendingSftpTrustRequest = null
        _addRepoUiState.update { it.copy(sftpPassword = password, sftpServerTrustInfo = null) }
    }

    fun onNewRepoS3AccessKeyIdChanged(accessKeyId: String) {
        pendingSftpTrustRequest = null
        _addRepoUiState.update { it.copy(s3AccessKeyId = accessKeyId, sftpServerTrustInfo = null) }
    }

    fun onNewRepoS3SecretAccessKeyChanged(secretAccessKey: String) {
        pendingSftpTrustRequest = null
        _addRepoUiState.update { it.copy(s3SecretAccessKey = secretAccessKey, sftpServerTrustInfo = null) }
    }

    fun onNewRepoRestUsernameChanged(username: String) {
        pendingSftpTrustRequest = null
        _addRepoUiState.update { it.copy(restUsername = username, sftpServerTrustInfo = null) }
    }

    fun onNewRepoRestPasswordChanged(password: String) {
        pendingSftpTrustRequest = null
        _addRepoUiState.update { it.copy(restPassword = password, sftpServerTrustInfo = null) }
    }

    fun onNewRepoEnvironmentVariablesChanged(raw: String) {
        pendingSftpTrustRequest = null
        _addRepoUiState.update { it.copy(environmentVariablesRaw = raw, sftpServerTrustInfo = null) }
    }

    fun onSavePasswordChanged(save: Boolean) = _addRepoUiState.update { it.copy(savePassword = save) }
    fun onNewRepoDialogDismiss() {
        pendingSftpTrustRequest = null
        _addRepoUiState.value = AddRepoUiState()
    }

    fun onSftpTrustDialogDismiss() {
        pendingSftpTrustRequest = null
        _addRepoUiState.update { it.copy(sftpServerTrustInfo = null, state = AddRepositoryState.Idle) }
    }

    fun onSftpTrustDialogConfirm() {
        val pendingRequest = pendingSftpTrustRequest ?: run {
            onSftpTrustDialogDismiss()
            return
        }

        viewModelScope.launch {
            _addRepoUiState.update { it.copy(state = AddRepositoryState.Initializing) }

            val trustResult = repositoriesRepository.trustSftpServer(pendingRequest.knownHostEntries)
            if (trustResult.isFailure) {
                val message = trustResult.exceptionOrNull()?.message
                    ?: context.getString(R.string.repo_error_sftp_known_hosts_setup_failed)
                pendingSftpTrustRequest = null
                _addRepoUiState.update {
                    it.copy(
                        sftpServerTrustInfo = null,
                        state = AddRepositoryState.Error(message)
                    )
                }
                return@launch
            }

            pendingSftpTrustRequest = null
            _addRepoUiState.update { it.copy(sftpServerTrustInfo = null) }
            executeAddRepository(pendingRequest.input)
        }
    }

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
        val s3AccessKeyId = addRepoUiState.value.s3AccessKeyId
        val s3SecretAccessKey = addRepoUiState.value.s3SecretAccessKey
        val restUsername = addRepoUiState.value.restUsername
        val restPassword = addRepoUiState.value.restPassword
        val savePassword = addRepoUiState.value.savePassword
        val backendType = addRepoUiState.value.backendType
        val envParseResult = parseEnvironmentVariables(addRepoUiState.value.environmentVariablesRaw)

        if (path.isBlank() || password.isBlank()) {
            _addRepoUiState.update { it.copy(state = AddRepositoryState.Error(context.getString(R.string.settings_error_path_password_empty))) }
            return
        }

        if (backendType == RepositoryBackendType.REST) {
            val hasRestUsername = restUsername.isNotBlank()
            val hasRestPassword = restPassword.isNotBlank()
            if (hasRestUsername != hasRestPassword) {
                _addRepoUiState.update {
                    it.copy(state = AddRepositoryState.Error(context.getString(R.string.settings_error_rest_credentials_incomplete)))
                }
                return
            }
        }

        if (backendType == RepositoryBackendType.S3) {
            val hasS3AccessKeyId = s3AccessKeyId.isNotBlank()
            val hasS3SecretAccessKey = s3SecretAccessKey.isNotBlank()
            if (hasS3AccessKeyId != hasS3SecretAccessKey) {
                _addRepoUiState.update {
                    it.copy(state = AddRepositoryState.Error(context.getString(R.string.settings_error_s3_credentials_incomplete)))
                }
                return
            }
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
        val input = AddRepositoryInput(
            path = path,
            backendType = backendType,
            password = password,
            sftpPassword = sftpPassword,
            s3AccessKeyId = s3AccessKeyId,
            s3SecretAccessKey = s3SecretAccessKey,
            restUsername = restUsername,
            restPassword = restPassword,
            environmentVariables = environmentVariables,
            savePassword = savePassword
        )

        viewModelScope.launch {
            if (input.backendType == RepositoryBackendType.SFTP) {
                _addRepoUiState.update { it.copy(state = AddRepositoryState.Initializing, sftpServerTrustInfo = null) }

                val trustInfoResult = repositoriesRepository.probeSftpServerTrust(
                    path = input.path,
                    password = input.password,
                    environmentVariables = input.environmentVariables,
                    resticOptions = emptyMap(),
                    sftpPassword = input.sftpPassword
                )

                if (trustInfoResult.isFailure) {
                    _addRepoUiState.update {
                        it.copy(
                            state = AddRepositoryState.Error(
                                trustInfoResult.exceptionOrNull()?.message
                                    ?: context.getString(R.string.repo_error_sftp_fingerprint_probe_failed)
                            )
                        )
                    }
                    return@launch
                }

                val trustInfo = trustInfoResult.getOrThrow()
                pendingSftpTrustRequest = PendingSftpTrustRequest(
                    input = input,
                    knownHostEntries = trustInfo.knownHostEntries
                )

                _addRepoUiState.update {
                    it.copy(
                        state = AddRepositoryState.Idle,
                        sftpServerTrustInfo = trustInfo
                    )
                }
                return@launch
            }

            executeAddRepository(input)
        }
    }

    private suspend fun executeAddRepository(input: AddRepositoryInput) {
        _addRepoUiState.update { it.copy(state = AddRepositoryState.Initializing) }

        val result = repositoriesRepository.addRepository(
            path = input.path,
            backendType = input.backendType,
            password = input.password,
            environmentVariables = input.environmentVariables,
            resticOptions = emptyMap(),
            sftpPassword = input.sftpPassword,
            s3AccessKeyId = input.s3AccessKeyId,
            s3SecretAccessKey = input.s3SecretAccessKey,
            restUsername = input.restUsername,
            restPassword = input.restPassword,
            resticRepository = resticRepository,
            savePassword = input.savePassword
        )

        _addRepoUiState.update { it.copy(state = result, sftpServerTrustInfo = null) }

        if (result is AddRepositoryState.Success) {
            delay(1000)
            onNewRepoDialogDismiss()
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
