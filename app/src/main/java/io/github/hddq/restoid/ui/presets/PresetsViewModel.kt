package io.github.hddq.restoid.ui.presets

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.AppInfoRepository
import io.github.hddq.restoid.data.PreferencesRepository
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.ResticBinaryManager
import io.github.hddq.restoid.data.ResticRepository
import io.github.hddq.restoid.data.RootRepository
import io.github.hddq.restoid.data.ScheduleRepository
import io.github.hddq.restoid.data.TagRepository
import io.github.hddq.restoid.data.TaskPresetRepository
import io.github.hddq.restoid.model.BackupTag
import io.github.hddq.restoid.model.Schedule
import io.github.hddq.restoid.model.TaskPreset
import io.github.hddq.restoid.ui.shared.AppSortOrder
import io.github.hddq.restoid.ui.shared.BackupTypes
import io.github.hddq.restoid.ui.shared.TaskConfigEditorController
import io.github.hddq.restoid.ui.shared.TaskConfigEditorState
import io.github.hddq.restoid.ui.shared.hasTaskConfigChanges
import io.github.hddq.restoid.ui.shared.normalizeForgetHosts
import io.github.hddq.restoid.ui.shared.toRunTasksConfig
import io.github.hddq.restoid.work.OperationWorkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class PresetsUiState(
    val presets: List<TaskPreset> = emptyList(),
    val schedulesByPresetId: Map<String, List<Schedule>> = emptyMap(),
    val selectedRepoKey: String? = null,
    val selectedRepoName: String? = null,
    val isLoading: Boolean = false,
    val isOperationRunning: Boolean = false
)

data class AddEditPresetUiState(
    val id: String? = null,
    val name: String = "",
    val lastRunTimestamp: Long? = null,
    val editor: TaskConfigEditorState = TaskConfigEditorState(),
    val linkedSchedules: List<Schedule> = emptyList(),
    val isSaving: Boolean = false,
    val showConfirmDeleteDialog: Boolean = false,
    val showDiscardChangesDialog: Boolean = false
)

sealed interface PresetsUiEvent {
    data class NavigateBack(val savedPresetId: String? = null) : PresetsUiEvent
    data object NavigateToOperationProgress : PresetsUiEvent
}

class PresetsViewModel(
    private val application: Application,
    private val taskPresetRepository: TaskPresetRepository,
    private val scheduleRepository: ScheduleRepository,
    private val repositoriesRepository: RepositoriesRepository,
    private val appInfoRepository: AppInfoRepository,
    private val rootRepository: RootRepository,
    private val resticBinaryManager: ResticBinaryManager,
    private val resticRepository: ResticRepository,
    private val preferencesRepository: PreferencesRepository,
    private val operationWorkRepository: OperationWorkRepository,
    private val tagRepository: TagRepository
) : ViewModel() {

    val hasRoot = rootRepository.hasRoot
    val allowExternalIntents = preferencesRepository.allowExternalIntents

    private val _uiState = MutableStateFlow(PresetsUiState())
    val uiState = _uiState.asStateFlow()

    private val _addEditState = MutableStateFlow(AddEditPresetUiState())
    val addEditState = _addEditState.asStateFlow()

    private var initialState: AddEditPresetUiState? = null
    private var addEditSessionId: Long = 0L
    private var loadPresetsJob: Job? = null

    private val _uiEvents = MutableSharedFlow<PresetsUiEvent>(extraBufferCapacity = 2)
    val uiEvents: SharedFlow<PresetsUiEvent> = _uiEvents.asSharedFlow()

    private val editorController = TaskConfigEditorController(
        application = application,
        scope = viewModelScope,
        rootRepository = rootRepository,
        repositoriesRepository = repositoriesRepository,
        resticBinaryManager = resticBinaryManager,
        resticRepository = resticRepository,
        appInfoRepository = appInfoRepository,
        preferencesRepository = preferencesRepository,
        readState = { _addEditState.value.editor },
        updateState = { transform ->
            _addEditState.update { state -> state.copy(editor = transform(state.editor)) }
        },
        selectedRepoKeyProvider = { _uiState.value.selectedRepoKey },
        tagRepository = tagRepository
    )

    init {
        viewModelScope.launch {
            repositoriesRepository.selectedRepository.collect { repoKey ->
                _uiState.update { it.copy(selectedRepoKey = repoKey) }
                loadPresets()
            }
        }
        viewModelScope.launch {
            operationWorkRepository.operationState.collect { opState ->
                _uiState.update { it.copy(isOperationRunning = opState.isRunning) }
            }
        }
    }

    fun loadPresets() {
        loadPresetsJob?.cancel()
        val repoKey = _uiState.value.selectedRepoKey
        if (repoKey == null) {
            editorController.loadAvailableTags(null)
            _uiState.update {
                it.copy(
                    presets = emptyList(),
                    schedulesByPresetId = emptyMap(),
                    selectedRepoName = null,
                    isLoading = false
                )
            }
            return
        }
        val repository = repositoriesRepository.getRepositoryByKey(repoKey) ?: return
        val repoId = repository.id ?: return
        editorController.loadAvailableTags(repoId)

        loadPresetsJob = viewModelScope.launch {
            _uiState.update { it.copy(selectedRepoName = repository.name, isLoading = true) }
            val presets = taskPresetRepository.getPresets(repoId)
            val schedules = scheduleRepository.getSchedules(repoId)
            if (_uiState.value.selectedRepoKey != repoKey) return@launch
            val grouped = schedules.groupBy { it.presetId }
            _uiState.update {
                it.copy(
                    presets = presets,
                    schedulesByPresetId = grouped,
                    isLoading = false
                )
            }
            val currentEditId = _addEditState.value.id
            if (currentEditId != null) {
                val updatedPreset = presets.find { it.id == currentEditId }
                _addEditState.update {
                    it.copy(
                        linkedSchedules = grouped[currentEditId].orEmpty(),
                        lastRunTimestamp = updatedPreset?.lastRunTimestamp ?: it.lastRunTimestamp
                    )
                }
            }
        }
    }

    fun refreshLinkedSchedules() {
        val presetId = _addEditState.value.id ?: return
        val repoKey = _uiState.value.selectedRepoKey ?: return
        val repoId = repositoriesRepository.getRepositoryByKey(repoKey)?.id ?: return
        viewModelScope.launch {
            val linked = scheduleRepository.getSchedulesForPreset(repoId, presetId)
            _addEditState.update { state ->
                if (state.id == presetId) state.copy(linkedSchedules = linked) else state
            }
        }
    }

    fun startAddPreset() {
        val sessionId = ++addEditSessionId
        _addEditState.value = AddEditPresetUiState()
        editorController.resetForNewConfig(
            onAppsLoaded = { loadedApps, loadedBackupTypes ->
                if (addEditSessionId != sessionId) return@resetForNewConfig
                val currentInitial = initialState
                if (currentInitial != null && currentInitial.editor.apps.isEmpty() && loadedApps.isNotEmpty()) {
                    val initialAppTypes = io.github.hddq.restoid.ui.shared.resolveEditorAppBackupTypes(
                        apps = loadedApps,
                        defaultBackupTypes = currentInitial.editor.backupTypes,
                        existingBackupTypes = currentInitial.editor.appBackupTypes,
                        selectAllApps = currentInitial.editor.selectAllApps,
                        excludedPackageNames = currentInitial.editor.excludedPackageNames
                    )
                    initialState = currentInitial.copy(
                        editor = editorController.recomputeConflicts(
                            currentInitial.editor.copy(
                                apps = loadedApps,
                                appBackupTypes = initialAppTypes,
                                isLoadingApps = false
                            )
                        )
                    )
                }
            }
        )
        initialState = _addEditState.value
        editorController.loadAvailableTags(currentSelectedRepoId())
    }

    fun startEditPreset(preset: TaskPreset) {
        val sessionId = ++addEditSessionId
        val linkedSchedules = _uiState.value.schedulesByPresetId[preset.id].orEmpty()
        _addEditState.value = AddEditPresetUiState(
            id = preset.id,
            name = preset.name,
            lastRunTimestamp = preset.lastRunTimestamp,
            linkedSchedules = linkedSchedules
        )
        editorController.applyRunTasksConfig(
            config = preset.config,
            reloadApps = true,
            onAppsLoaded = { loadedApps, _ ->
                if (addEditSessionId != sessionId) return@applyRunTasksConfig
                val currentInitial = initialState
                if (currentInitial != null && currentInitial.editor.apps.isEmpty() && loadedApps.isNotEmpty()) {
                    val initialAppTypes = io.github.hddq.restoid.ui.shared.resolveEditorAppBackupTypes(
                        apps = loadedApps,
                        defaultBackupTypes = currentInitial.editor.backupTypes,
                        existingBackupTypes = currentInitial.editor.appBackupTypes,
                        selectAllApps = currentInitial.editor.selectAllApps,
                        excludedPackageNames = currentInitial.editor.excludedPackageNames
                    )
                    initialState = currentInitial.copy(
                        editor = editorController.recomputeConflicts(
                            currentInitial.editor.copy(
                                apps = loadedApps,
                                appBackupTypes = initialAppTypes,
                                isLoadingApps = false
                            )
                        )
                    )
                }
            }
        )
        initialState = _addEditState.value
        editorController.loadAvailableTags(currentSelectedRepoId())
        refreshLinkedSchedules()
    }

    fun hasChanges(current: AddEditPresetUiState = _addEditState.value): Boolean {
        val initial = initialState ?: return false
        return hasPresetChanges(current, initial)
    }

    fun onBackPress() {
        if (hasChanges()) {
            _addEditState.update { it.copy(showDiscardChangesDialog = true) }
        } else {
            _uiEvents.tryEmit(PresetsUiEvent.NavigateBack(savedPresetId = null))
        }
    }

    fun onConfirmDiscard() {
        _addEditState.update { it.copy(showDiscardChangesDialog = false) }
        _uiEvents.tryEmit(PresetsUiEvent.NavigateBack(savedPresetId = null))
    }

    fun onDismissDiscard() {
        _addEditState.update { it.copy(showDiscardChangesDialog = false) }
    }

    fun setName(name: String) {
        _addEditState.update { it.copy(name = name) }
    }

    private fun buildPresetFromState(state: AddEditPresetUiState): TaskPreset {
        val name = state.name.trim().ifBlank { application.getString(R.string.preset_default_name) }
        return TaskPreset(
            id = state.id ?: UUID.randomUUID().toString(),
            name = name,
            config = state.editor.toRunTasksConfig(hasRoot = true),
            lastRunTimestamp = state.lastRunTimestamp
        )
    }

    fun savePreset() {
        val repoKey = _uiState.value.selectedRepoKey ?: return
        val repository = repositoriesRepository.getRepositoryByKey(repoKey) ?: return
        val repoId = repository.id ?: return

        val state = _addEditState.value
        if (state.isSaving || state.editor.isLoadingApps) return
        if (state.editor.maintenance.forgetSnapshots &&
            !state.editor.maintenance.forgetAllHosts &&
            normalizeForgetHosts(state.editor.maintenance.forgetHosts).isEmpty()
        ) {
            Toast.makeText(
                application,
                application.getString(R.string.error_no_forget_hosts_selected),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val preset = buildPresetFromState(state)
        viewModelScope.launch {
            _addEditState.update { it.copy(isSaving = true) }
            val saved = taskPresetRepository.savePreset(repoId, preset)
            if (saved == null) {
                _addEditState.update { it.copy(isSaving = false) }
                Toast.makeText(
                    application,
                    application.getString(R.string.toast_preset_save_failed),
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            loadPresets()
            _addEditState.update { it.copy(id = saved.id, isSaving = false) }
            initialState = state.copy(id = saved.id, isSaving = false)
            _uiEvents.emit(PresetsUiEvent.NavigateBack(savedPresetId = saved.id))
        }
    }

    fun duplicatePreset(preset: TaskPreset) {
        val repoKey = _uiState.value.selectedRepoKey ?: return
        val repoId = repositoriesRepository.getRepositoryByKey(repoKey)?.id ?: return
        val copyName = application.getString(R.string.preset_copy_name_format, preset.name)

        viewModelScope.launch {
            val duplicated = taskPresetRepository.duplicatePreset(repoId, preset.id, copyName)
            if (duplicated != null) {
                loadPresets()
                Toast.makeText(
                    application,
                    application.getString(R.string.toast_preset_duplicated),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    application,
                    application.getString(R.string.toast_preset_save_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun deletePreset(presetId: String) {
        val repoKey = _uiState.value.selectedRepoKey ?: return
        val repoId = repositoriesRepository.getRepositoryByKey(repoKey)?.id ?: return

        viewModelScope.launch {
            val deletedCount = taskPresetRepository.deletePreset(repoId, presetId)
            if (deletedCount != null) {
                loadPresets()
            } else {
                Toast.makeText(
                    application,
                    application.getString(R.string.toast_preset_delete_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun onDeletePresetClick() = _addEditState.update { it.copy(showConfirmDeleteDialog = true) }
    fun onCancelDeletePreset() = _addEditState.update { it.copy(showConfirmDeleteDialog = false) }

    fun confirmDeletePreset() {
        val presetId = _addEditState.value.id ?: return
        _addEditState.update { it.copy(showConfirmDeleteDialog = false) }
        val repoKey = _uiState.value.selectedRepoKey ?: return
        val repoId = repositoriesRepository.getRepositoryByKey(repoKey)?.id ?: return

        viewModelScope.launch {
            val deletedCount = taskPresetRepository.deletePreset(repoId, presetId)
            if (deletedCount == null) {
                Toast.makeText(
                    application,
                    application.getString(R.string.toast_preset_delete_failed),
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            loadPresets()
            _uiEvents.emit(PresetsUiEvent.NavigateBack(savedPresetId = null))
        }
    }

    fun toggleLinkedScheduleEnabled(schedule: Schedule) {
        val repoKey = _uiState.value.selectedRepoKey ?: return
        val repoId = repositoriesRepository.getRepositoryByKey(repoKey)?.id ?: return

        viewModelScope.launch {
            scheduleRepository.toggleSchedule(repoKey, repoId, schedule.id, !schedule.isEnabled)
            refreshLinkedSchedules()
            loadPresets()
        }
    }

    fun runPresetNow(preset: TaskPreset) {
        val repoKey = _uiState.value.selectedRepoKey ?: return
        val repository = repositoriesRepository.getRepositoryByKey(repoKey) ?: return
        val repoId = repository.id ?: return

        if (operationWorkRepository.operationState.value.isRunning) {
            Toast.makeText(
                application,
                application.getString(R.string.error_operation_already_running),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val error = editorController.validateForExecution(
            config = preset.config,
            hasRoot = hasRoot.value,
            repoKeyOverride = repoKey
        )
        if (error != null) {
            error.error?.let { Toast.makeText(application, it, Toast.LENGTH_LONG).show() }
            return
        }

        viewModelScope.launch {
            val enqueued = withContext(Dispatchers.IO) {
                taskPresetRepository.runPresetNow(
                    repoKey = repoKey,
                    repoId = repoId,
                    preset = preset,
                    hasRoot = hasRoot.value
                )
            }
            if (enqueued) {
                loadPresets()
                _uiEvents.emit(PresetsUiEvent.NavigateToOperationProgress)
            } else {
                Toast.makeText(
                    application,
                    application.getString(R.string.error_operation_already_running),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun runCurrentPresetNow() {
        val repoKey = _uiState.value.selectedRepoKey ?: return
        val repository = repositoriesRepository.getRepositoryByKey(repoKey) ?: return
        val repoId = repository.id ?: return

        if (operationWorkRepository.operationState.value.isRunning) {
            Toast.makeText(
                application,
                application.getString(R.string.error_operation_already_running),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val state = _addEditState.value
        if (state.isSaving || state.editor.isLoadingApps) return
        val preset = buildPresetFromState(state)
        val error = editorController.validateForExecution(
            config = preset.config,
            hasRoot = hasRoot.value,
            repoKeyOverride = repoKey
        )
        if (error != null) {
            error.error?.let { Toast.makeText(application, it, Toast.LENGTH_LONG).show() }
            return
        }

        val sessionId = addEditSessionId
        viewModelScope.launch {
            val presetToRun = if (state.id == null || hasChanges(state)) {
                _addEditState.update { current ->
                    if (addEditSessionId == sessionId) current.copy(isSaving = true) else current
                }
                val saved = taskPresetRepository.savePreset(repoId, preset)
                if (saved == null) {
                    if (addEditSessionId == sessionId) {
                        _addEditState.update { current ->
                            if (addEditSessionId == sessionId) current.copy(isSaving = false) else current
                        }
                    }
                    Toast.makeText(
                        application,
                        application.getString(R.string.toast_preset_save_failed),
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }
                if (addEditSessionId == sessionId) {
                    _addEditState.update { current ->
                        if (addEditSessionId == sessionId) current.copy(id = saved.id, isSaving = false) else current
                    }
                    initialState = state.copy(id = saved.id, isSaving = false)
                }
                saved
            } else {
                preset
            }

            val enqueued = withContext(Dispatchers.IO) {
                taskPresetRepository.runPresetNow(
                    repoKey = repoKey,
                    repoId = repoId,
                    preset = presetToRun,
                    hasRoot = hasRoot.value
                )
            }
            if (enqueued) {
                loadPresets()
                _uiEvents.emit(PresetsUiEvent.NavigateToOperationProgress)
            } else {
                Toast.makeText(
                    application,
                    application.getString(R.string.error_operation_already_running),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Delegated TaskConfigEditorController mutators
    fun setBackupEnabled(enabled: Boolean) = editorController.setBackupEnabled(enabled)
    fun setCustomDirectoriesBackupEnabled(enabled: Boolean) = editorController.setCustomDirectoriesBackupEnabled(enabled)
    fun setDeviceDataBackupEnabled(enabled: Boolean) = editorController.setDeviceDataBackupEnabled(enabled)
    fun setContactsBackupEnabled(enabled: Boolean) = editorController.setContactsBackupEnabled(enabled)
    fun refreshContactAccounts() = editorController.refreshContactAccounts()
    fun toggleAllContactAccounts() = editorController.toggleAllContactAccounts()
    fun toggleContactAccount(selectionKey: String) = editorController.toggleContactAccount(selectionKey)
    fun setCallLogsBackupEnabled(enabled: Boolean) = editorController.setCallLogsBackupEnabled(enabled)
    fun setCalendarBackupEnabled(enabled: Boolean) = editorController.setCalendarBackupEnabled(enabled)
    fun refreshCalendars() = editorController.refreshCalendars()
    fun toggleAllCalendars() = editorController.toggleAllCalendars()
    fun toggleCalendar(selectionKey: String) = editorController.toggleCalendar(selectionKey)
    fun addCustomDirectory(uriString: String) = editorController.addCustomDirectory(uriString)
    fun toggleCustomDirectory(uriString: String) = editorController.toggleCustomDirectory(uriString)
    fun removeCustomDirectory(uriString: String) = editorController.removeCustomDirectory(uriString)
    fun addExcludedCustomDirectory(uriString: String) = editorController.addExcludedCustomDirectory(uriString)
    fun toggleExcludedCustomDirectory(uriString: String) = editorController.toggleExcludedCustomDirectory(uriString)
    fun removeExcludedCustomDirectory(uriString: String) = editorController.removeExcludedCustomDirectory(uriString)
    fun setUnlockRepo(value: Boolean) = editorController.setUnlockRepo(value)
    fun setForgetSnapshots(value: Boolean) = editorController.setForgetSnapshots(value)
    fun setPruneRepo(value: Boolean) = editorController.setPruneRepo(value)
    fun setCheckRepo(value: Boolean) = editorController.setCheckRepo(value)
    fun setReadData(value: Boolean) = editorController.setReadData(value)
    fun setKeepLast(value: Int) = editorController.setKeepLast(value)
    fun setKeepDaily(value: Int) = editorController.setKeepDaily(value)
    fun setKeepWeekly(value: Int) = editorController.setKeepWeekly(value)
    fun setKeepMonthly(value: Int) = editorController.setKeepMonthly(value)
    fun toggleAllForgetHosts() = editorController.toggleAllForgetHosts()
    fun toggleForgetHost(hostname: String) = editorController.toggleForgetHost(hostname)
    fun addCustomForgetHost(hostname: String) = editorController.addCustomForgetHost(hostname)
    fun removeCustomForgetHost(hostname: String) = editorController.removeCustomForgetHost(hostname)
    fun refreshForgetHosts(userInitiated: Boolean = true) = editorController.refreshForgetHosts(userInitiated)
    fun runForgetDryRun() = editorController.runForgetDryRun()
    fun clearForgetDryRun() = editorController.clearForgetDryRun()
    fun setAppSortOrder(order: AppSortOrder) = editorController.setAppSortOrder(order)
    fun setSelectAllApps(enabled: Boolean) = editorController.setSelectAllApps(enabled)
    fun toggleAppSelection(packageName: String) = editorController.toggleAppSelection(packageName)
    fun toggleAllApps() = editorController.toggleAllApps()
    fun setAppBackupTypes(packageName: String, backupTypes: BackupTypes) = editorController.setAppBackupTypes(packageName, backupTypes)
    fun setSelectedAppsBackupTypes(backupTypes: BackupTypes) = editorController.setSelectedAppsBackupTypes(backupTypes)
    fun refreshAppsList() = editorController.refreshAppsList()
    fun toggleBackupTag(tagName: String) = editorController.toggleBackupTag(tagName)
    fun toggleForgetTag(tagName: String) = editorController.toggleForgetTag(tagName)

    suspend fun createTag(name: String, colorArgb: Int): Result<BackupTag> {
        val repoId = currentSelectedRepoId()
            ?: return Result.failure(IllegalStateException("No repository selected"))
        return editorController.createTag(repoId, name, colorArgb)
    }

    suspend fun createForgetTag(name: String, colorArgb: Int): Result<BackupTag> {
        val repoId = currentSelectedRepoId()
            ?: return Result.failure(IllegalStateException("No repository selected"))
        return editorController.createTag(repoId, name, colorArgb, selectInBackup = false, selectInForget = true)
    }

    suspend fun updateTagColor(name: String, colorArgb: Int): Result<BackupTag> {
        val repoId = currentSelectedRepoId()
            ?: return Result.failure(IllegalStateException("No repository selected"))
        return editorController.updateTagColor(repoId, name, colorArgb)
    }

    suspend fun deleteTag(name: String): Result<Unit> {
        val repoId = currentSelectedRepoId()
            ?: return Result.failure(IllegalStateException("No repository selected"))
        val result = editorController.deleteTag(repoId, name)
        if (result.isSuccess) {
            loadPresets()
        }
        return result
    }

    private fun currentSelectedRepoId(): String? {
        val repoKey = _uiState.value.selectedRepoKey ?: repositoriesRepository.selectedRepository.value ?: return null
        return repositoriesRepository.getRepositoryByKey(repoKey)?.id
    }
}

internal fun hasPresetChanges(
    current: AddEditPresetUiState,
    initial: AddEditPresetUiState
): Boolean {
    return current.name != initial.name || hasTaskConfigChanges(current.editor, initial.editor)
}
