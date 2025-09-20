package app.restoid.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restoid.data.AddRepositoryState
import app.restoid.data.NotificationRepository
import app.restoid.data.RepositoriesRepository
import app.restoid.data.ResticRepository
import app.restoid.data.ResticState
import app.restoid.data.RootRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// UI state for the "Add Repository" dialog
data class AddRepoUiState(
    val path: String = "",
    val password: String = "",
    val savePassword: Boolean = true,
    val showDialog: Boolean = false,
    val state: AddRepositoryState = AddRepositoryState.Idle
)

enum class ChangePasswordState {
    Idle,
    InProgress,
    Success,
    Error
}

class SettingsViewModel(
    private val rootRepository: RootRepository,
    private val resticRepository: ResticRepository,
    private val repositoriesRepository: RepositoriesRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    // Expose states from repositories
    val rootState = rootRepository.rootState
    val resticState = resticRepository.resticState
    val repositories = repositoriesRepository.repositories
    val selectedRepository = repositoriesRepository.selectedRepository
    val notificationPermissionState = notificationRepository.permissionState


    // Internal and exposed UI State for adding a new repository
    private val _addRepoUiState = MutableStateFlow(AddRepoUiState())
    val addRepoUiState = _addRepoUiState.asStateFlow()

    private val _changePasswordState = MutableStateFlow(ChangePasswordState.Idle)
    val changePasswordState = _changePasswordState.asStateFlow()

    fun requestRootAccess() {
        viewModelScope.launch {
            rootRepository.checkRootAccess()
        }
    }

    fun hasRepositoryPassword(path: String): Boolean {
        return repositoriesRepository.hasRepositoryPassword(path)
    }

    fun hasStoredRepositoryPassword(path: String): Boolean {
        return repositoriesRepository.hasStoredRepositoryPassword(path)
    }

    fun forgetPassword(path: String) {
        repositoriesRepository.forgetPassword(path)
    }

    fun savePassword(path: String, password: String) {
        repositoriesRepository.saveRepositoryPassword(path, password)
    }

    fun deleteRepository(path: String) {
        viewModelScope.launch {
            repositoriesRepository.deleteRepository(path)
        }
    }

    fun changePassword(path: String, oldPassword: String, newPassword: String) {
        viewModelScope.launch {
            _changePasswordState.value = ChangePasswordState.InProgress
            val result = resticRepository.changePassword(path, oldPassword, newPassword)
            if (result.isSuccess) {
                // If there was a stored password, update it.
                if (repositoriesRepository.hasStoredRepositoryPassword(path)) {
                    repositoriesRepository.saveRepositoryPassword(path, newPassword)
                }
                _changePasswordState.value = ChangePasswordState.Success
            } else {
                _changePasswordState.value = ChangePasswordState.Error
            }
        }
    }

    fun resetChangePasswordState() {
        _changePasswordState.value = ChangePasswordState.Idle
    }

    fun checkNotificationPermission() {
        notificationRepository.checkPermissionStatus()
    }

    fun downloadRestic() {
        viewModelScope.launch {
            resticRepository.downloadAndInstallRestic()
        }
    }

    fun selectRepository(path: String) {
        repositoriesRepository.selectRepository(path)
    }

    // UI event handlers for the Add Repository dialog
    fun onNewRepoPathChanged(path: String) {
        _addRepoUiState.update { it.copy(path = path) }
    }

    fun onNewRepoPasswordChanged(password: String) {
        _addRepoUiState.update { it.copy(password = password) }
    }

    fun onSavePasswordChanged(save: Boolean) {
        _addRepoUiState.update { it.copy(savePassword = save) }
    }

    fun onNewRepoDialogDismiss() {
        _addRepoUiState.value = AddRepoUiState() // Reset state on dismiss
    }

    fun onShowAddRepoDialog() {
        // Only show the dialog if restic is actually installed.
        if (resticState.value is ResticState.Installed) {
            _addRepoUiState.update { it.copy(showDialog = true) }
        }
    }

    // Logic to add a new repository
    fun addRepository() {
        val currentResticState = resticState.value
        if (currentResticState !is ResticState.Installed) {
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

            // If successful, close the dialog after a brief delay
            if (result is AddRepositoryState.Success) {
                delay(1000)
                onNewRepoDialogDismiss()
            }
        }
    }
}
