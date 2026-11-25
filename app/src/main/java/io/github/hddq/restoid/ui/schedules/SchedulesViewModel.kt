package io.github.hddq.restoid.ui.schedules

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.RootRepository
import io.github.hddq.restoid.data.ScheduleConditionMonitor
import io.github.hddq.restoid.data.ScheduleRepository
import io.github.hddq.restoid.data.TaskPresetRepository
import io.github.hddq.restoid.data.TriggerConditionStatusSnapshot
import io.github.hddq.restoid.model.Schedule
import io.github.hddq.restoid.model.TaskPreset
import io.github.hddq.restoid.model.TriggerConditions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class SchedulesUiState(
    val schedules: List<Schedule> = emptyList(),
    val presetsById: Map<String, TaskPreset> = emptyMap(),
    val selectedRepoKey: String? = null,
    val isLoading: Boolean = false
)

data class AddEditScheduleUiState(
    val id: String? = null,
    val presetId: String? = null,
    val name: String = "",
    val intervalHours: Int = 24,
    val isEnabled: Boolean = true,
    val triggerConditions: TriggerConditions = TriggerConditions(),
    val lastRunTimestamp: Long? = null,
    val availablePresets: List<TaskPreset> = emptyList(),
    val isSaving: Boolean = false,
    val showConfirmDeleteDialog: Boolean = false,
    val showDiscardChangesDialog: Boolean = false
)

sealed interface SchedulesUiEvent {
    data object NavigateBack : SchedulesUiEvent
}

class SchedulesViewModel(
    private val application: Application,
    private val scheduleRepository: ScheduleRepository,
    private val taskPresetRepository: TaskPresetRepository,
    private val repositoriesRepository: RepositoriesRepository,
    private val rootRepository: RootRepository
) : ViewModel() {

    private val conditionMonitor = ScheduleConditionMonitor(application)

    val hasRoot = rootRepository.hasRoot

    val conditionStatus: StateFlow<TriggerConditionStatusSnapshot> = conditionMonitor.observeStatus()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = conditionMonitor.readCurrentStatus()
        )

    private val _uiState = MutableStateFlow(SchedulesUiState())
    val uiState = _uiState.asStateFlow()

    private val _addEditState = MutableStateFlow(AddEditScheduleUiState())
    val addEditState = _addEditState.asStateFlow()

    private var initialState: AddEditScheduleUiState? = null
    private var addEditSessionId: Long = 0L

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
        val repoKey = _uiState.value.selectedRepoKey ?: repositoriesRepository.selectedRepository.value
        val repository = repoKey?.let { repositoriesRepository.getRepositoryByKey(it) }
        val repoId = repository?.id
        if (repoId == null) {
            _uiState.update {
                it.copy(
                    schedules = emptyList(),
                    presetsById = emptyMap(),
                    isLoading = false
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val schedules = scheduleRepository.getSchedules(repoId)
            val presets = taskPresetRepository.getPresets(repoId)
            _uiState.update {
                it.copy(
                    schedules = schedules,
                    presetsById = presets.associateBy { preset -> preset.id },
                    isLoading = false
                )
            }
            _addEditState.update { it.copy(availablePresets = presets) }
        }
    }

    fun refreshPresets(selectPresetId: String? = null) {
        val repoKey = _uiState.value.selectedRepoKey ?: repositoriesRepository.selectedRepository.value ?: return
        val repository = repositoriesRepository.getRepositoryByKey(repoKey) ?: return
        val repoId = repository.id ?: return
        val sessionId = addEditSessionId

        viewModelScope.launch {
            val presets = taskPresetRepository.getPresets(repoId)
            val schedules = scheduleRepository.getSchedules(repoId)
            _uiState.update {
                it.copy(
                    schedules = schedules,
                    presetsById = presets.associateBy { preset -> preset.id }
                )
            }
            if (addEditSessionId != sessionId) return@launch

            val currentEditId = _addEditState.value.id
            if (currentEditId != null && schedules.none { it.id == currentEditId }) {
                _uiEvents.tryEmit(SchedulesUiEvent.NavigateBack)
                return@launch
            }

            _addEditState.update { current ->
                val resolvedPresetId = when {
                    selectPresetId != null && presets.any { it.id == selectPresetId } -> selectPresetId
                    current.presetId != null && presets.any { it.id == current.presetId } -> current.presetId
                    else -> presets.firstOrNull()?.id
                }
                val updated = current.copy(
                    availablePresets = presets,
                    presetId = resolvedPresetId
                )
                val currentInitial = initialState
                if (selectPresetId == null && currentInitial != null && currentInitial.presetId == null && resolvedPresetId != null && !hasScheduleChanges(current, currentInitial)) {
                    initialState = currentInitial.copy(
                        availablePresets = presets,
                        presetId = resolvedPresetId
                    )
                }
                updated
            }
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
        Toast.makeText(
            application,
            application.getString(R.string.toast_schedule_triggered),
            Toast.LENGTH_SHORT
        ).show()
    }

    fun startAddSchedule(preselectedPresetId: String? = null) {
        val sessionId = ++addEditSessionId
        val cachedPresets = _uiState.value.presetsById.values.toList()
        val initialPresetId = preselectedPresetId ?: cachedPresets.firstOrNull()?.id
        val newState = AddEditScheduleUiState(
            presetId = initialPresetId,
            availablePresets = cachedPresets
        )
        _addEditState.value = newState
        initialState = newState
        refreshPresetsForSession(sessionId = sessionId, preferredPresetId = preselectedPresetId)
    }

    fun startEditSchedule(schedule: Schedule) {
        val sessionId = ++addEditSessionId
        val cachedPresets = _uiState.value.presetsById.values.toList()
        val editState = AddEditScheduleUiState(
            id = schedule.id,
            presetId = schedule.presetId,
            name = schedule.name.orEmpty(),
            intervalHours = schedule.intervalHours,
            isEnabled = schedule.isEnabled,
            triggerConditions = schedule.triggerConditions,
            lastRunTimestamp = schedule.lastRunTimestamp,
            availablePresets = cachedPresets
        )
        _addEditState.value = editState
        initialState = editState
        refreshPresetsForSession(sessionId = sessionId, preferredPresetId = schedule.presetId)
    }

    private fun refreshPresetsForSession(sessionId: Long, preferredPresetId: String?) {
        val repoKey = _uiState.value.selectedRepoKey ?: repositoriesRepository.selectedRepository.value ?: return
        val repository = repositoriesRepository.getRepositoryByKey(repoKey) ?: return
        val repoId = repository.id ?: return

        viewModelScope.launch {
            val presets = taskPresetRepository.getPresets(repoId)
            _uiState.update { it.copy(presetsById = presets.associateBy { preset -> preset.id }) }
            if (addEditSessionId != sessionId) return@launch

            _addEditState.update { current ->
                val userChangedPreset = initialState?.let { current.presetId != it.presetId } == true
                val resolvedPresetId = when {
                    !userChangedPreset && preferredPresetId != null && presets.any { it.id == preferredPresetId } -> preferredPresetId
                    current.presetId != null && presets.any { it.id == current.presetId } -> current.presetId
                    else -> presets.firstOrNull()?.id
                }
                val updated = current.copy(
                    availablePresets = presets,
                    presetId = resolvedPresetId
                )
                val currentInitial = initialState
                if (currentInitial != null && !hasScheduleChanges(current, currentInitial)) {
                    initialState = currentInitial.copy(
                        availablePresets = presets,
                        presetId = resolvedPresetId
                    )
                }
                updated
            }
        }
    }

    fun hasChanges(current: AddEditScheduleUiState = _addEditState.value): Boolean {
        val initial = initialState ?: return false
        return hasScheduleChanges(current, initial)
    }

    fun onBackPress() {
        if (hasChanges()) {
            _addEditState.update { it.copy(showDiscardChangesDialog = true) }
        } else {
            _uiEvents.tryEmit(SchedulesUiEvent.NavigateBack)
        }
    }

    fun onConfirmDiscard() {
        _addEditState.update { it.copy(showDiscardChangesDialog = false) }
        _uiEvents.tryEmit(SchedulesUiEvent.NavigateBack)
    }

    fun onDismissDiscard() {
        _addEditState.update { it.copy(showDiscardChangesDialog = false) }
    }

    private fun buildScheduleFromState(state: AddEditScheduleUiState): Schedule? {
        val selectedPresetId = state.presetId?.takeIf { id ->
            id.isNotBlank() && state.availablePresets.any { it.id == id }
        } ?: return null

        return Schedule(
            id = state.id ?: UUID.randomUUID().toString(),
            presetId = selectedPresetId,
            name = state.name.trim().ifBlank { null },
            intervalHours = state.intervalHours.coerceIn(1, 720),
            triggerConditions = state.triggerConditions,
            lastRunTimestamp = state.lastRunTimestamp,
            isEnabled = state.isEnabled
        )
    }

    fun runCurrentScheduleNow() {
        val repoKey = _uiState.value.selectedRepoKey ?: repositoriesRepository.selectedRepository.value ?: return
        val repository = repositoriesRepository.getRepositoryByKey(repoKey) ?: return
        val repoId = repository.id ?: return
        val sessionId = addEditSessionId
        val state = _addEditState.value
        val schedule = buildScheduleFromState(state)
        if (schedule == null) {
            Toast.makeText(
                application,
                application.getString(R.string.schedule_error_preset_required),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        viewModelScope.launch {
            if (hasChanges(state) || state.id == null) {
                _addEditState.update { current ->
                    if (addEditSessionId == sessionId) {
                        current.copy(id = schedule.id, isSaving = true)
                    } else {
                        current
                    }
                }
                scheduleRepository.saveSchedule(repoKey, repoId, schedule)
                if (addEditSessionId == sessionId) {
                    val synced = _addEditState.value.copy(id = schedule.id, isSaving = false)
                    _addEditState.value = synced
                    initialState = synced
                }
                loadSchedules()
            }
            scheduleRepository.runNow(repoKey, schedule)
            Toast.makeText(
                application,
                application.getString(R.string.toast_schedule_triggered),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun saveSchedule() {
        val repoKey = _uiState.value.selectedRepoKey ?: repositoriesRepository.selectedRepository.value ?: return
        val repository = repositoriesRepository.getRepositoryByKey(repoKey) ?: return
        val repoId = repository.id ?: return

        val state = _addEditState.value
        val schedule = buildScheduleFromState(state)
        if (schedule == null) {
            Toast.makeText(
                application,
                application.getString(R.string.schedule_error_preset_required),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        viewModelScope.launch {
            _addEditState.update { it.copy(isSaving = true) }
            scheduleRepository.saveSchedule(repoKey, repoId, schedule)
            _addEditState.update { it.copy(isSaving = false) }
            loadSchedules()
            _uiEvents.emit(SchedulesUiEvent.NavigateBack)
        }
    }

    fun setPresetId(presetId: String) = _addEditState.update { it.copy(presetId = presetId) }
    fun setName(name: String) = _addEditState.update { it.copy(name = name) }
    fun setIntervalHours(hours: Int) = _addEditState.update { it.copy(intervalHours = hours) }
    fun setRequireBatteryNotLow(value: Boolean) =
        _addEditState.update { it.copy(triggerConditions = it.triggerConditions.copy(requireBatteryNotLow = value)) }
    fun setRequireCharging(value: Boolean) =
        _addEditState.update { it.copy(triggerConditions = it.triggerConditions.copy(requireCharging = value)) }
    fun setRequireUnmeteredNetwork(value: Boolean) =
        _addEditState.update { it.copy(triggerConditions = it.triggerConditions.copy(requireUnmeteredNetwork = value)) }

    fun onDeleteScheduleClick() = _addEditState.update { it.copy(showConfirmDeleteDialog = true) }
    fun onCancelDeleteSchedule() = _addEditState.update { it.copy(showConfirmDeleteDialog = false) }

    fun confirmDeleteSchedule() {
        val scheduleId = _addEditState.value.id ?: return
        _addEditState.update { it.copy(showConfirmDeleteDialog = false) }

        val repoKey = _uiState.value.selectedRepoKey ?: repositoriesRepository.selectedRepository.value ?: return
        val repository = repositoriesRepository.getRepositoryByKey(repoKey) ?: return
        val repoId = repository.id ?: return

        viewModelScope.launch {
            scheduleRepository.deleteSchedule(repoKey, repoId, scheduleId)
            loadSchedules()
            _uiEvents.emit(SchedulesUiEvent.NavigateBack)
        }
    }
}

internal fun hasScheduleChanges(
    current: AddEditScheduleUiState,
    initial: AddEditScheduleUiState
): Boolean {
    return current.presetId != initial.presetId ||
        current.name != initial.name ||
        current.intervalHours != initial.intervalHours ||
        current.isEnabled != initial.isEnabled ||
        current.triggerConditions != initial.triggerConditions
}
