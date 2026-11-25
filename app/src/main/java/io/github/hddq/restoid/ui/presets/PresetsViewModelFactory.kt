package io.github.hddq.restoid.ui.presets

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.hddq.restoid.data.AppInfoRepository
import io.github.hddq.restoid.data.PreferencesRepository
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.ResticBinaryManager
import io.github.hddq.restoid.data.ResticRepository
import io.github.hddq.restoid.data.RootRepository
import io.github.hddq.restoid.data.TaskPresetRepository
import io.github.hddq.restoid.work.OperationWorkRepository

class PresetsViewModelFactory(
    private val application: Application,
    private val taskPresetRepository: TaskPresetRepository,
    private val repositoriesRepository: RepositoriesRepository,
    private val appInfoRepository: AppInfoRepository,
    private val rootRepository: RootRepository,
    private val resticBinaryManager: ResticBinaryManager,
    private val resticRepository: ResticRepository,
    private val preferencesRepository: PreferencesRepository,
    private val operationWorkRepository: OperationWorkRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PresetsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PresetsViewModel(
                application = application,
                taskPresetRepository = taskPresetRepository,
                repositoriesRepository = repositoriesRepository,
                appInfoRepository = appInfoRepository,
                rootRepository = rootRepository,
                resticBinaryManager = resticBinaryManager,
                resticRepository = resticRepository,
                preferencesRepository = preferencesRepository,
                operationWorkRepository = operationWorkRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
