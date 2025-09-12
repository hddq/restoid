package app.restoid.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.restoid.data.ResticRepository
import app.restoid.data.RootRepository

class SettingsViewModelFactory(
    private val rootRepository: RootRepository,
    private val resticRepository: ResticRepository // Add restic repository dependency
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // Pass both repositories to the ViewModel
            return SettingsViewModel(rootRepository, resticRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
