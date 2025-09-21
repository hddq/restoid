package io.github.hddq.restoid.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.hddq.restoid.data.NotificationRepository
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.ResticRepository
import io.github.hddq.restoid.data.RootRepository

class SettingsViewModelFactory(
    private val rootRepository: RootRepository,
    private val resticRepository: ResticRepository,
    private val repositoriesRepository: RepositoriesRepository,
    private val notificationRepository: NotificationRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // Pass all required repositories to the ViewModel
            return SettingsViewModel(rootRepository, resticRepository, repositoriesRepository, notificationRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
