package io.github.hddq.restoid.ui.schedules

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hddq.restoid.data.AppInfoRepository
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.ScheduleRepository
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.model.Schedule
import io.github.hddq.restoid.ui.runtasks.RunTasksMaintenanceConfig
import io.github.hddq.restoid.ui.shared.BackupTypes
import io.github.hddq.restoid.ui.shared.toUiModel
import io.github.hddq.restoid.work.RunTasksConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class SchedulesUiState(
    val schedules: List<Schedule> = emptyList(),
    val selectedRepoKey: String? = null,
    val isLoading: Boolean = false
)

data class AddEditScheduleUiState(
    val id: String? = null,
    val name: String = "",
    val intervalHours: Int = 24,
    val isEnabled: Boolean = true,
    val backupEnabled: Boolean = true,
    val backupTypes: BackupTypes = BackupTypes(),
    val apps: List<AppInfo> = emptyList(),
    val maintenance: RunTasksMaintenanceConfig = RunTasksMaintenanceConfig(),
    val isLoadingApps: Boolean = false,
    val isSaving: Boolean = false
)

sealed interface SchedulesUiEvent {
    data object NavigateBack : SchedulesUiEvent
}

class SchedulesViewModel(
    private val application: Application,
    private val scheduleRepository: ScheduleRepository,
    private val repositoriesRepository: RepositoriesRepository,
    private val appInfoRepository: AppInfoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SchedulesUiState())
    val uiState = _uiState.asStateFlow()

    private val _addEditState = MutableStateFlow(AddEditScheduleUiState())
    val addEditState = _addEditState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<SchedulesUiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<SchedulesUiEvent> = _uiEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            repositoriesRepository.selectedRepository.collect { repoKey ->
                _uiState.update { it.copy(selectedRepoKey = repoKey) }
                loadSchedules()
            }
        }
    }

    fun loadSchedules() {
        val repoKey = _uiState.value.selectedRepoKey ?: return
        val repository = repositoriesRepository.getRepositoryByKey(repoKey) ?: return
        val repoId = repository.id ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val schedules = scheduleRepository.getSchedules(repoId)
            _uiState.update { it.copy(schedules = schedules, isLoading = false) }
        }
    }

    fun deleteSchedule(scheduleId: String) {
        val repoKey = _uiState.value.selectedRepoKey ?: return
        val repository = repositoriesRepository.getRepositoryByKey(repoKey) ?: return
        val repoId = repository.id ?: return

        viewModelScope.launch {
            scheduleRepository.deleteSchedule(repoKey, repoId, scheduleId)
            loadSchedules()
        }
    }

    fun toggleScheduleEnabled(schedule: Schedule) {
        val repoKey = _uiState.value.selectedRepoKey ?: return
        val repository = repositoriesRepository.getRepositoryByKey(repoKey) ?: return
        val repoId = repository.id ?: return

        viewModelScope.launch {
            scheduleRepository.toggleSchedule(repoKey, repoId, schedule.id, !schedule.isEnabled)
            loadSchedules()
        }
    }

    fun runNow(schedule: Schedule) {
        val repoKey = _uiState.value.selectedRepoKey ?: return
        scheduleRepository.runNow(repoKey, schedule)
    }

    // Add/Edit Screen methods
    fun startAddSchedule() {
        _addEditState.value = AddEditScheduleUiState()
        loadAppsForAddEdit()
    }

    fun startEditSchedule(schedule: Schedule) {
        _addEditState.value = AddEditScheduleUiState(
            id = schedule.id,
            name = schedule.name,
            intervalHours = schedule.intervalHours,
            isEnabled = schedule.isEnabled,
            backupEnabled = schedule.config.backupEnabled,
            backupTypes = schedule.config.backupTypes.toUiModel(),
            maintenance = RunTasksMaintenanceConfig(
                unlockRepo = schedule.config.unlockRepo,
                forgetSnapshots = schedule.config.forgetSnapshots,
                pruneRepo = schedule.config.pruneRepo,
                checkRepo = schedule.config.checkRepo,
                readData = schedule.config.readData,
                keepLast = schedule.config.keepLast,
                keepDaily = schedule.config.keepDaily,
                keepWeekly = schedule.config.keepWeekly,
                keepMonthly = schedule.config.keepMonthly
            )
        )
        loadAppsForAddEdit(schedule.config.selectedPackageNames)
    }

    private fun loadAppsForAddEdit(selectedPackageNames: List<String>? = null) {
        viewModelScope.launch {
            _addEditState.update { it.copy(isLoadingApps = true) }
            val allApps = appInfoRepository.getInstalledUserApps()
            val apps = if (selectedPackageNames != null) {
                allApps.map { it.copy(isSelected = selectedPackageNames.contains(it.packageName)) }
            } else {
                allApps // Default selection from AppInfo might be true, we might want to default to true for new schedules too
            }
            _addEditState.update { it.copy(apps = apps, isLoadingApps = false) }
        }
    }

    fun saveSchedule() {
        val repoKey = _uiState.value.selectedRepoKey ?: return
        val repository = repositoriesRepository.getRepositoryByKey(repoKey) ?: return
        val repoId = repository.id ?: return

        val state = _addEditState.value
        val schedule = Schedule(
            id = state.id ?: UUID.randomUUID().toString(),
            name = state.name.ifBlank { "Schedule" },
            intervalHours = state.intervalHours,
            isEnabled = state.isEnabled,
            config = RunTasksConfig(
                backupEnabled = state.backupEnabled,
                backupTypes = state.backupTypes.toSelection(),
                selectedPackageNames = state.apps.filter { it.isSelected }.map { it.packageName },
                unlockRepo = state.maintenance.unlockRepo,
                forgetSnapshots = state.maintenance.forgetSnapshots,
                pruneRepo = state.maintenance.pruneRepo,
                checkRepo = state.maintenance.checkRepo,
                readData = state.maintenance.readData,
                keepLast = state.maintenance.keepLast,
                keepDaily = state.maintenance.keepDaily,
                keepWeekly = state.maintenance.keepWeekly,
                keepMonthly = state.maintenance.keepMonthly
            )
        )

        viewModelScope.launch {
            _addEditState.update { it.copy(isSaving = true) }
            scheduleRepository.saveSchedule(repoKey, repoId, schedule)
            _addEditState.update { it.copy(isSaving = false) }
            loadSchedules()
            _uiEvents.emit(SchedulesUiEvent.NavigateBack)
        }
    }

    fun setName(name: String) = _addEditState.update { it.copy(name = name) }
    fun setIntervalHours(hours: Int) = _addEditState.update { it.copy(intervalHours = hours) }
    fun setBackupEnabled(enabled: Boolean) = _addEditState.update { it.copy(backupEnabled = enabled) }
    fun setBackupApk(value: Boolean) = _addEditState.update { it.copy(backupTypes = it.backupTypes.copy(apk = value)) }
    fun setBackupData(value: Boolean) = _addEditState.update { it.copy(backupTypes = it.backupTypes.copy(data = value)) }
    fun setBackupDeviceProtectedData(value: Boolean) = _addEditState.update { it.copy(backupTypes = it.backupTypes.copy(deviceProtectedData = value)) }
    fun setBackupExternalData(value: Boolean) = _addEditState.update { it.copy(backupTypes = it.backupTypes.copy(externalData = value)) }
    fun setBackupObb(value: Boolean) = _addEditState.update { it.copy(backupTypes = it.backupTypes.copy(obb = value)) }
    fun setBackupMedia(value: Boolean) = _addEditState.update { it.copy(backupTypes = it.backupTypes.copy(media = value)) }

    fun toggleAppSelection(packageName: String) {
        _addEditState.update { state ->
            state.copy(apps = state.apps.map {
                if (it.packageName == packageName) it.copy(isSelected = !it.isSelected) else it
            })
        }
    }

    fun toggleAllApps() {
        _addEditState.update { state ->
            val shouldSelectAll = state.apps.any { !it.isSelected }
            state.copy(apps = state.apps.map { it.copy(isSelected = shouldSelectAll) })
        }
    }

    fun setUnlockRepo(value: Boolean) = _addEditState.update { it.copy(maintenance = it.maintenance.copy(unlockRepo = value)) }
    fun setForgetSnapshots(value: Boolean) = _addEditState.update { it.copy(maintenance = it.maintenance.copy(forgetSnapshots = value)) }
    fun setPruneRepo(value: Boolean) = _addEditState.update { it.copy(maintenance = it.maintenance.copy(pruneRepo = value)) }
    fun setCheckRepo(value: Boolean) = _addEditState.update { it.copy(maintenance = it.maintenance.copy(checkRepo = value)) }
    fun setReadData(value: Boolean) = _addEditState.update { it.copy(maintenance = it.maintenance.copy(readData = value)) }
    fun setKeepLast(value: Int) = _addEditState.update { it.copy(maintenance = it.maintenance.copy(keepLast = value)) }
    fun setKeepDaily(value: Int) = _addEditState.update { it.copy(maintenance = it.maintenance.copy(keepDaily = value)) }
    fun setKeepWeekly(value: Int) = _addEditState.update { it.copy(maintenance = it.maintenance.copy(keepWeekly = value)) }
    fun setKeepMonthly(value: Int) = _addEditState.update { it.copy(maintenance = it.maintenance.copy(keepMonthly = value)) }

    fun refreshAppsList() {
        loadAppsForAddEdit(_addEditState.value.apps.filter { it.isSelected }.map { it.packageName })
    }
}
