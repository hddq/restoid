package app.restoid.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.restoid.data.RepositoriesRepository
import app.restoid.data.ResticRepository
import app.restoid.data.RootRepository

class SettingsViewModelFactory(
    private val rootRepository: RootRepository,
    private val resticRepository: ResticRepository,
    private val repositoriesRepository: RepositoriesRepository // Add repositories repository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // Pass all required repositories to the ViewModel
            return SettingsViewModel(rootRepository, resticRepository, repositoriesRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

