package app.restoid.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restoid.data.RootRepository
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val rootRepository: RootRepository
) : ViewModel() {

    val rootState = rootRepository.rootState

    fun requestRootAccess() {
        viewModelScope.launch {
            // It now simply calls the one and only check function.
            rootRepository.checkRootAccess()
        }
    }
}