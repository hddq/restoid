package io.github.hddq.restoid.ui.maintenance

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MaintenanceUiState(
    val checkRepo: Boolean = true,
    val pruneRepo: Boolean = false
)

class MaintenanceViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MaintenanceUiState())
    val uiState = _uiState.asStateFlow()

    fun setCheckRepo(value: Boolean) {
        _uiState.update { it.copy(checkRepo = value) }
    }

    fun setPruneRepo(value: Boolean) {
        _uiState.update { it.copy(pruneRepo = value) }
    }
}
