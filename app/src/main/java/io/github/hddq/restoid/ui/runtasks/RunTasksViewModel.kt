package io.github.hddq.restoid.ui.runtasks

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.AppInfoRepository
import io.github.hddq.restoid.data.OperationType
import io.github.hddq.restoid.data.PreferencesRepository
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.ResticBinaryManager
import io.github.hddq.restoid.data.ResticRepository
import io.github.hddq.restoid.data.RootRepository
import io.github.hddq.restoid.data.TaskPresetRepository
import io.github.hddq.restoid.model.CustomDirectory
import io.github.hddq.restoid.model.TaskPreset
import io.github.hddq.restoid.ui.shared.AppSortOrder
import io.github.hddq.restoid.ui.shared.BackupTypes
import io.github.hddq.restoid.ui.shared.OperationProgress
import io.github.hddq.restoid.ui.shared.TaskConfigEditorController
import io.github.hddq.restoid.ui.shared.TaskConfigEditorState
import io.github.hddq.restoid.ui.shared.normalizeForgetHosts
import io.github.hddq.restoid.ui.shared.toRunTasksConfig
import io.github.hddq.restoid.work.OperationWorkRepository
import io.github.hddq.restoid.work.toWorkRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RunTasksUiState(
    val editor: TaskConfigEditorState = TaskConfigEditorState(isLoadingApps = true),
    val availablePresets: List<TaskPreset> = emptyList(),
    val selectedPresetId: String? = null,
    val showSavePresetDialog: Boolean = false,
    val isRunning: Boolean = false,
    val progress: OperationProgress = OperationProgress()
)

sealed interface RunTasksUiEvent {
    data object NavigateToOperationProgress : RunTasksUiEvent
}

class RunTasksViewModel(
    private val application: Application,
    private val rootRepository: RootRepository,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticBinaryManager: ResticBinaryManager,
    private val resticRepository: ResticRepository,
    private val appInfoRepository: AppInfoRepository,
    private val preferencesRepository: PreferencesRepository,
    private val operationWorkRepository: OperationWorkRepository,
    private val taskPresetRepository: TaskPresetRepository
) : ViewModel() {

    val hasRoot = rootRepository.hasRoot

    private val _uiState = MutableStateFlow(
        RunTasksUiState(
            editor = buildInitialCustomEditorState()
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _operationBlocked = MutableStateFlow(false)
    val operationBlocked = _operationBlocked.asStateFlow()

    private val _uiEvents = MutableSharedFlow<RunTasksUiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<RunTasksUiEvent> = _uiEvents.asSharedFlow()

    private var appliedInitialPresetRepoId: String? = null
    private var persistCustomJob: Job? = null
    private var refreshPresetsJob: Job? = null

    private val editorController = TaskConfigEditorController(
        application = application,
        scope = viewModelScope,
        rootRepository = rootRepository,
        repositoriesRepository = repositoriesRepository,
        resticBinaryManager = resticBinaryManager,
        resticRepository = resticRepository,
        appInfoRepository = appInfoRepository,
        preferencesRepository = preferencesRepository,
        readState = { _uiState.value.editor },
        updateState = { transform ->
            _uiState.update { state -> state.copy(editor = transform(state.editor)) }
        },
        onConfigMutated = { mutatedEditor ->
            if (_uiState.value.selectedPresetId != null) {
                _uiState.update { it.copy(selectedPresetId = null) }
            }
            schedulePersistCustomEditor(mutatedEditor)
        }
    )

    init {
        loadInitialCustomApps()
        observeOperationState()
        observeSelectedRepository()
        editorController.triggerInitialExclusionResolutionIfNeeded()
    }

    private fun buildInitialCustomEditorState(): TaskConfigEditorState {
        val selectedCustomDirs = preferencesRepository.loadCustomDirectoriesSelected()
        val selectedExcludedDirs = preferencesRepository.loadExcludedCustomDirectoriesSelected()
        return TaskConfigEditorState(
            isLoadingApps = true,
            backupEnabled = preferencesRepository.loadRunTasksBackupEnabled(),
            backupTypes = preferencesRepository.loadBackupTypes(),
            maintenance = preferencesRepository.loadMaintenanceState(),
            customDirectoriesBackupEnabled = preferencesRepository.loadCustomDirectoriesBackupEnabled(),
            deviceDataBackupEnabled = preferencesRepository.loadDeviceDataBackupEnabled(),
            contactsBackupEnabled = preferencesRepository.loadDeviceDataContactsEnabled(),
            selectAllContactAccounts = preferencesRepository.loadDeviceDataSelectAllContactAccounts(),
            selectedContactAccountKeys = preferencesRepository.loadDeviceDataSelectedContactAccountKeys(),
            callLogsBackupEnabled = preferencesRepository.loadDeviceDataCallLogsEnabled(),
            calendarBackupEnabled = preferencesRepository.loadDeviceDataCalendarEnabled(),
            selectAllCalendars = preferencesRepository.loadDeviceDataSelectAllCalendars(),
            selectedCalendarKeys = preferencesRepository.loadDeviceDataSelectedCalendarKeys(),
            customDirectories = preferencesRepository.loadCustomDirectoriesAll().map { uri ->
                CustomDirectory(uri = uri, isSelected = selectedCustomDirs.contains(uri))
            },
            excludedCustomDirectories = preferencesRepository.loadExcludedCustomDirectoriesAll().map { uri ->
                CustomDirectory(uri = uri, isSelected = selectedExcludedDirs.contains(uri))
            }
        )
    }

    private fun loadInitialCustomApps() {
        val selectAll = preferencesRepository.loadRunTasksSelectAllApps()
        val selectedPackages = if (selectAll) null else preferencesRepository.loadRunTasksSelectedPackages().toList()
        val savedAppBackupTypes = preferencesRepository.loadRunTasksAppBackupTypes()
        editorController.loadInstalledApps(
            selectedPackageNames = selectedPackages,
            selectAllDefault = if (selectAll) true else null,
            savedAppBackupTypes = savedAppBackupTypes
        )
    }

    private fun observeSelectedRepository() {
        viewModelScope.launch {
            repositoriesRepository.selectedRepository.collect {
                refreshPresets()
            }
        }
    }

    fun refreshPresets() {
        refreshPresetsJob?.cancel()
        val repoKey = repositoriesRepository.selectedRepository.value
        val repoId = repoKey?.let { repositoriesRepository.getRepositoryByKey(it)?.id }
        if (repoId == null) {
            appliedInitialPresetRepoId = null
            _uiState.update { it.copy(availablePresets = emptyList(), selectedPresetId = null) }
            return
        }

        refreshPresetsJob = viewModelScope.launch {
            val presets = taskPresetRepository.getPresets(repoId)
            if (repositoriesRepository.selectedRepository.value != repoKey) return@launch
            val currentSelectedId = _uiState.value.selectedPresetId
            val shouldRestoreInitialPreset = appliedInitialPresetRepoId != repoId
            appliedInitialPresetRepoId = repoId

            val validSelectedId = currentSelectedId?.takeIf { id -> presets.any { it.id == id } }
            _uiState.update {
                it.copy(
                    availablePresets = presets,
                    selectedPresetId = validSelectedId
                )
            }

            if (shouldRestoreInitialPreset) {
                val lastPresetId = preferencesRepository.loadRunTasksLastPresetId(repoId)
                if (lastPresetId != null && presets.any { it.id == lastPresetId }) {
                    selectPreset(lastPresetId)
                }
            }
        }
    }

    fun selectPreset(presetId: String) {
        val preset = _uiState.value.availablePresets.find { it.id == presetId } ?: return
        if (_uiState.value.selectedPresetId == presetId) return

        _uiState.update { it.copy(selectedPresetId = presetId) }
        editorController.applyRunTasksConfig(
            config = preset.config,
            reloadApps = false
        )
    }

    fun openSaveAsPresetDialog() {
        _uiState.update { it.copy(showSavePresetDialog = true) }
    }

    fun dismissSaveAsPresetDialog() {
        _uiState.update { it.copy(showSavePresetDialog = false) }
    }

    fun saveCurrentAsPreset(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            Toast.makeText(
                application,
                application.getString(R.string.preset_error_name_empty),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val repoKey = repositoriesRepository.selectedRepository.value
        val repoId = repoKey?.let { repositoriesRepository.getRepositoryByKey(it)?.id }
        if (repoId == null) {
            Toast.makeText(
                application,
                application.getString(R.string.error_no_backup_repository_selected),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val currentEditor = _uiState.value.editor
        if (currentEditor.isLoadingApps) return
        if (
            currentEditor.maintenance.forgetSnapshots &&
            !currentEditor.maintenance.forgetAllHosts &&
            normalizeForgetHosts(currentEditor.maintenance.forgetHosts).isEmpty()
        ) {
            Toast.makeText(
                application,
                application.getString(R.string.error_no_forget_hosts_selected),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val newPreset = TaskPreset(
            name = trimmedName,
            config = currentEditor.toRunTasksConfig(hasRoot.value)
        )

        viewModelScope.launch {
            val saved = taskPresetRepository.savePreset(repoId, newPreset)
            if (saved == null) {
                Toast.makeText(
                    application,
                    application.getString(R.string.toast_preset_save_failed),
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            val presets = taskPresetRepository.getPresets(repoId)
            _uiState.update {
                it.copy(
                    availablePresets = presets,
                    selectedPresetId = saved.id,
                    showSavePresetDialog = false
                )
            }
            Toast.makeText(
                application,
                application.getString(R.string.toast_preset_saved),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun schedulePersistCustomEditor(editor: TaskConfigEditorState) {
        persistCustomJob?.cancel()
        persistCustomJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300L)
            persistCustomEditorToPreferences(editor)
        }
    }

    override fun onCleared() {
        if (persistCustomJob?.isActive == true && _uiState.value.selectedPresetId == null) {
            persistCustomJob?.cancel()
            persistCustomEditorToPreferences(_uiState.value.editor)
        }
        super.onCleared()
    }

    private fun persistCustomEditorToPreferences(editor: TaskConfigEditorState) {
        preferencesRepository.saveBackupTypes(editor.backupTypes)
        preferencesRepository.saveMaintenanceState(editor.maintenance)
        preferencesRepository.saveRunTasksBackupEnabled(editor.backupEnabled)

        if (editor.apps.isNotEmpty()) {
            preferencesRepository.saveRunTasksAppBackupTypes(editor.appBackupTypes)
            val allAppsSelected = editor.apps.all { it.isSelected }
            val selectedPackages = editor.apps.filter { it.isSelected }.map { it.packageName }.toSet()
            preferencesRepository.saveRunTasksSelectedApps(allAppsSelected, selectedPackages)
        }

        preferencesRepository.saveCustomDirectoriesBackupEnabled(editor.customDirectoriesBackupEnabled)
        preferencesRepository.saveDeviceDataBackupEnabled(editor.deviceDataBackupEnabled)
        preferencesRepository.saveDeviceDataContactsEnabled(editor.contactsBackupEnabled)
        preferencesRepository.saveDeviceDataContactAccountSelection(
            editor.selectAllContactAccounts,
            editor.selectedContactAccountKeys
        )
        preferencesRepository.saveDeviceDataCallLogsEnabled(editor.callLogsBackupEnabled)
        preferencesRepository.saveDeviceDataCalendarEnabled(editor.calendarBackupEnabled)
        preferencesRepository.saveDeviceDataCalendarSelection(
            editor.selectAllCalendars,
            editor.selectedCalendarKeys
        )

        val allCustomDirs = editor.customDirectories.map { it.uri }.toSet()
        val selectedCustomDirs = editor.customDirectories.filter { it.isSelected }.map { it.uri }.toSet()
        preferencesRepository.saveCustomDirectories(allCustomDirs, selectedCustomDirs)

        val allExcludedDirs = editor.excludedCustomDirectories.map { it.uri }.toSet()
        val selectedExcludedDirs = editor.excludedCustomDirectories.filter { it.isSelected }.map { it.uri }.toSet()
        preferencesRepository.saveExcludedCustomDirectories(allExcludedDirs, selectedExcludedDirs)
    }

    private fun observeOperationState() {
        viewModelScope.launch {
            operationWorkRepository.operationState.collect { state ->
                if (state.operationType == OperationType.RUN_TASKS) {
                    _uiState.update { it.copy(isRunning = state.isRunning, progress = state.progress) }
                } else {
                    _uiState.update { it.copy(isRunning = false, progress = OperationProgress()) }
                }
            }
        }
    }

    fun run() {
        val state = _uiState.value
        if (state.isRunning) return

        if (state.selectedPresetId == null) {
            persistCustomJob?.cancel()
            persistCustomEditorToPreferences(state.editor)
        }

        val config = state.editor.toRunTasksConfig(hasRoot.value)
        val errorState = editorController.validateForExecution(config, hasRoot.value)
        if (errorState != null) {
            errorState.error?.let { Toast.makeText(application, it, Toast.LENGTH_LONG).show() }
            _uiState.update { it.copy(isRunning = false, progress = errorState) }
            return
        }

        val selectedRepoKey = repositoriesRepository.selectedRepository.value ?: run {
            _uiState.update {
                it.copy(
                    isRunning = false,
                    progress = OperationProgress(
                        isFinished = true,
                        error = application.getString(R.string.error_no_backup_repository_selected),
                        finalSummary = application.getString(R.string.summary_no_backup_repository_selected)
                    )
                )
            }
            return
        }

        val selectedPreset = state.selectedPresetId?.let { id ->
            state.availablePresets.find { it.id == id }
        }

        val request = config.toWorkRequest(
            repositoryKey = selectedRepoKey,
            hasRoot = hasRoot.value,
            presetName = selectedPreset?.name
        )

        viewModelScope.launch(Dispatchers.IO) {
            val enqueued = operationWorkRepository.enqueueRunTasks(request)
            if (enqueued) {
                val repoId = repositoriesRepository.getRepositoryByKey(selectedRepoKey)?.id
                if (repoId != null) {
                    preferencesRepository.saveRunTasksLastPresetId(repoId, selectedPreset?.id)
                    if (selectedPreset != null) {
                        taskPresetRepository.updateLastRunTimestamp(repoId, selectedPreset.id)
                    }
                }
                _uiEvents.tryEmit(RunTasksUiEvent.NavigateToOperationProgress)
            } else {
                _operationBlocked.value = true
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        progress = OperationProgress(
                            isFinished = true,
                            error = application.getString(R.string.error_operation_already_running),
                            finalSummary = application.getString(R.string.summary_operation_already_running)
                        )
                    )
                }
            }
        }
    }

    fun consumeOperationBlocked() {
        _operationBlocked.value = false
    }

    fun refreshAppsList() = editorController.refreshAppsList()
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
    fun setAppSortOrder(order: AppSortOrder) = editorController.setAppSortOrder(order)
    fun toggleAppSelection(packageName: String) = editorController.toggleAppSelection(packageName)
    fun toggleAllApps() = editorController.toggleAllApps()
    fun setAppBackupTypes(packageName: String, backupTypes: BackupTypes) = editorController.setAppBackupTypes(packageName, backupTypes)
    fun setSelectedAppsBackupTypes(backupTypes: BackupTypes) = editorController.setSelectedAppsBackupTypes(backupTypes)
    fun setBackupApk(value: Boolean) = editorController.setBackupApk(value)
    fun setBackupData(value: Boolean) = editorController.setBackupData(value)
    fun setBackupDeviceProtectedData(value: Boolean) = editorController.setBackupDeviceProtectedData(value)
    fun setBackupExternalData(value: Boolean) = editorController.setBackupExternalData(value)
    fun setBackupObb(value: Boolean) = editorController.setBackupObb(value)
    fun setBackupMedia(value: Boolean) = editorController.setBackupMedia(value)
    fun setBackupPermissions(value: Boolean) = editorController.setBackupPermissions(value)
    fun setBackupSpecialAccess(value: Boolean) = editorController.setBackupSpecialAccess(value)
    fun setBackupAccessibility(value: Boolean) = editorController.setBackupAccessibility(value)
}
