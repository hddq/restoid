package io.github.hddq.restoid.ui.schedules

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.RootRepository
import io.github.hddq.restoid.data.ScheduleRepository
import io.github.hddq.restoid.data.TaskPresetRepository

class SchedulesViewModelFactory(
    private val application: Application,
    private val scheduleRepository: ScheduleRepository,
    private val taskPresetRepository: TaskPresetRepository,
    private val repositoriesRepository: RepositoriesRepository,
    private val rootRepository: RootRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SchedulesViewModel::class.java)) {
            return SchedulesViewModel(
                application = application,
                scheduleRepository = scheduleRepository,
                taskPresetRepository = taskPresetRepository,
                repositoriesRepository = repositoriesRepository,
                rootRepository = rootRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
