package io.github.hddq.restoid.ui.runtasks

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.hddq.restoid.data.AppInfoRepository
import io.github.hddq.restoid.data.PreferencesRepository
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.ResticBinaryManager
import io.github.hddq.restoid.work.OperationWorkRepository

class RunTasksViewModelFactory(
    private val application: Application,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticBinaryManager: ResticBinaryManager,
    private val appInfoRepository: AppInfoRepository,
    private val preferencesRepository: PreferencesRepository,
    private val operationWorkRepository: OperationWorkRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RunTasksViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RunTasksViewModel(
                application = application,
                repositoriesRepository = repositoriesRepository,
                resticBinaryManager = resticBinaryManager,
                appInfoRepository = appInfoRepository,
                preferencesRepository = preferencesRepository,
                operationWorkRepository = operationWorkRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
