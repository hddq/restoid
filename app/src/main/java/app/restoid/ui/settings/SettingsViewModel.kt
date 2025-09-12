package app.restoid.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restoid.data.ResticRepository
import app.restoid.data.RootRepository
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val rootRepository: RootRepository,
    private val resticRepository: ResticRepository // Add restic repository
) : ViewModel() {

    // Expose root state from its repository
    val rootState = rootRepository.rootState

    // Expose restic state from its repository
    val resticState = resticRepository.resticState

    // Function to request root access
    fun requestRootAccess() {
        viewModelScope.launch {
            rootRepository.checkRootAccess()
        }
    }

    // Function to download the restic binary
    fun downloadRestic() {
        viewModelScope.launch {
            resticRepository.downloadAndInstallRestic()
        }
    }
}
