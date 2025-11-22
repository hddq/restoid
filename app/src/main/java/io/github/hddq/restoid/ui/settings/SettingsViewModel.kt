package io.github.hddq.restoid.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hddq.restoid.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddRepoUiState(
    val path: String = "",
    val password: String = "",
    val savePassword: Boolean = true,
    val showDialog: Boolean = false,
    val state: AddRepositoryState = AddRepositoryState.Idle
)

enum class ChangePasswordState { Idle, InProgress, Success, Error }

class SettingsViewModel(
    private val rootRepository: RootRepository,
    private val resticBinaryManager: ResticBinaryManager, // Injected Manager
    private val resticRepository: ResticRepository,
    private val repositoriesRepository: RepositoriesRepository,
    private val notificationRepository: NotificationRepository
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

    init {
        viewModelScope.launch {
            resticBinaryManager.fetchLatestResticVersion()
        }
    }

    fun requestRootAccess() {
        viewModelScope.launch { rootRepository.checkRootAccess() }
    }

    fun hasRepositoryPassword(path: String) = repositoriesRepository.hasRepositoryPassword(path)
    fun hasStoredRepositoryPassword(path: String) = repositoriesRepository.hasStoredRepositoryPassword(path)
    fun forgetPassword(path: String) = repositoriesRepository.forgetPassword(path)
    fun savePassword(path: String, password: String) = repositoriesRepository.saveRepositoryPassword(path, password)

    fun deleteRepository(path: String) {
        viewModelScope.launch { repositoriesRepository.deleteRepository(path) }
    }

    fun changePassword(path: String, oldPassword: String, newPassword: String) {
        viewModelScope.launch {
            _changePasswordState.value = ChangePasswordState.InProgress
            val result = resticRepository.changePassword(path, oldPassword, newPassword)
            if (result.isSuccess) {
                if (repositoriesRepository.hasStoredRepositoryPassword(path)) {
                    repositoriesRepository.saveRepositoryPassword(path, newPassword)
                }
                _changePasswordState.value = ChangePasswordState.Success
            } else {
                _changePasswordState.value = ChangePasswordState.Error
            }
        }
    }

    fun resetChangePasswordState() { _changePasswordState.value = ChangePasswordState.Idle }
    fun checkNotificationPermission() = notificationRepository.checkPermissionStatus()

    fun downloadRestic() {
        viewModelScope.launch { resticBinaryManager.downloadAndInstallRestic() }
    }

    fun downloadLatestRestic() {
        viewModelScope.launch { resticBinaryManager.downloadAndInstallLatestRestic() }
    }

    fun selectRepository(path: String) = repositoriesRepository.selectRepository(path)

    // UI handlers
    fun onNewRepoPathChanged(path: String) = _addRepoUiState.update { it.copy(path = path) }
    fun onNewRepoPasswordChanged(password: String) = _addRepoUiState.update { it.copy(password = password) }
    fun onSavePasswordChanged(save: Boolean) = _addRepoUiState.update { it.copy(savePassword = save) }
    fun onNewRepoDialogDismiss() { _addRepoUiState.value = AddRepoUiState() }

    fun onShowAddRepoDialog() {
        if (resticState.value is ResticState.Installed) {
            _addRepoUiState.update { it.copy(showDialog = true) }
        }
    }

    fun addRepository() {
        if (resticState.value !is ResticState.Installed) {
            _addRepoUiState.update { it.copy(state = AddRepositoryState.Error("Restic is not installed.")) }
            return
        }

        val path = addRepoUiState.value.path.trim()
        val password = addRepoUiState.value.password
        val savePassword = addRepoUiState.value.savePassword

        if (path.isBlank() || password.isBlank()) {
            _addRepoUiState.update { it.copy(state = AddRepositoryState.Error("Path and password cannot be empty.")) }
            return
        }

        viewModelScope.launch {
            _addRepoUiState.update { it.copy(state = AddRepositoryState.Initializing) }
            val result = repositoriesRepository.addRepository(
                path = path,
                password = password,
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
}