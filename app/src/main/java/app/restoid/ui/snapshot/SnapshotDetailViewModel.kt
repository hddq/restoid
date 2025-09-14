package app.restoid.ui.snapshot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restoid.data.RepositoriesRepository
import app.restoid.data.ResticRepository
import app.restoid.data.SnapshotInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SnapshotDetailsViewModel(
    private val repositoriesRepository: RepositoriesRepository,
    private val resticRepository: ResticRepository
) : ViewModel() {

    private val _snapshot = MutableStateFlow<SnapshotInfo?>(null)
    val snapshot = _snapshot.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _showConfirmForgetDialog = MutableStateFlow(false)
    val showConfirmForgetDialog = _showConfirmForgetDialog.asStateFlow()

    private val _isForgetting = MutableStateFlow(false)
    val isForgetting = _isForgetting.asStateFlow()


    fun loadSnapshotDetails(snapshotId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val repoPath = repositoriesRepository.selectedRepository.first()
                val password = repoPath?.let { repositoriesRepository.getRepositoryPassword(it) }

                if (repoPath != null && password != null) {
                    val result = resticRepository.getSnapshots(repoPath, password)
                    result.fold(
                        onSuccess = { snapshots ->
                            _snapshot.value = snapshots.find { it.id.startsWith(snapshotId) }
                        },
                        onFailure = { _error.value = it.message }
                    )
                } else {
                    _error.value = "Repository or password not found"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onForgetSnapshot() {
        _showConfirmForgetDialog.value = true
    }

    fun confirmForgetSnapshot() {
        _showConfirmForgetDialog.value = false
        val snapshotToForget = _snapshot.value ?: return

        viewModelScope.launch {
            _isForgetting.value = true
            _error.value = null
            try {
                val repoPath = repositoriesRepository.selectedRepository.first()
                val password = repoPath?.let { repositoriesRepository.getRepositoryPassword(it) }

                if (repoPath != null && password != null) {
                    val result = resticRepository.forgetSnapshot(repoPath, password, snapshotToForget.id)
                    result.fold(
                        onSuccess = {
                            // In a real app you might want to navigate back or show a success message
                        },
                        onFailure = { _error.value = it.message }
                    )
                } else {
                    _error.value = "Repository or password not found"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isForgetting.value = false
            }
        }
    }

    fun cancelForgetSnapshot() {
        _showConfirmForgetDialog.value = false
    }
}

