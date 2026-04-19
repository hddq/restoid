package io.github.hddq.restoid.ui.maintenance

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.hddq.restoid.data.NotificationRepository
import io.github.hddq.restoid.data.OperationCoordinator
import io.github.hddq.restoid.data.OperationLockManager
import io.github.hddq.restoid.data.PreferencesRepository
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.ResticBinaryManager
import io.github.hddq.restoid.data.ResticRepository

class MaintenanceViewModelFactory(
    private val context: Context,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticBinaryManager: ResticBinaryManager, // Injected
    private val resticRepository: ResticRepository,
    private val notificationRepository: NotificationRepository,
    private val preferencesRepository: PreferencesRepository,
    private val operationCoordinator: OperationCoordinator,
    private val operationLockManager: OperationLockManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MaintenanceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MaintenanceViewModel(
                context,
                repositoriesRepository,
                resticBinaryManager,
                resticRepository,
                notificationRepository,
                preferencesRepository,
                operationCoordinator,
                operationLockManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
