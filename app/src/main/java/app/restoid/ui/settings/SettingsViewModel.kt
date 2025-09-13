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

    fun requestRootAccess() {
        viewModelScope.launch {
            rootRepository.checkRootAccess()
        }
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
                resticBinaryPath = currentResticState.path,
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
