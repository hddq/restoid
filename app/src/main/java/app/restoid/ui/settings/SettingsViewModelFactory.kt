// file: app/restoid/ui/settings/SettingsViewModelFactory.kt
package app.restoid.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.restoid.data.RootRepository

class SettingsViewModelFactory(
    private val rootRepository: RootRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(rootRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}