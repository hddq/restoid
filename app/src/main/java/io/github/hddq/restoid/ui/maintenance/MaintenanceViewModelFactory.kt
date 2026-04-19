package io.github.hddq.restoid.ui.maintenance

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.hddq.restoid.data.PreferencesRepository
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.ResticBinaryManager
import io.github.hddq.restoid.work.OperationWorkRepository

class MaintenanceViewModelFactory(
    private val context: Context,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticBinaryManager: ResticBinaryManager,
    private val preferencesRepository: PreferencesRepository,
    private val operationWorkRepository: OperationWorkRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MaintenanceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MaintenanceViewModel(
                context,
                repositoriesRepository,
                resticBinaryManager,
                preferencesRepository,
                operationWorkRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
