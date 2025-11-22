package io.github.hddq.restoid.ui.maintenance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.hddq.restoid.data.NotificationRepository
import io.github.hddq.restoid.data.PreferencesRepository
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.ResticBinaryManager
import io.github.hddq.restoid.data.ResticRepository

class MaintenanceViewModelFactory(
    private val repositoriesRepository: RepositoriesRepository,
    private val resticBinaryManager: ResticBinaryManager, // Injected
    private val resticRepository: ResticRepository,
    private val notificationRepository: NotificationRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MaintenanceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MaintenanceViewModel(repositoriesRepository, resticBinaryManager, resticRepository, notificationRepository, preferencesRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}