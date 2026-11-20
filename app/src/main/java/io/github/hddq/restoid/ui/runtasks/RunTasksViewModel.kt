package io.github.hddq.restoid.ui.runtasks

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.CalendarBackupRepository
import io.github.hddq.restoid.data.OperationType
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.RepositoryBackendType
import io.github.hddq.restoid.data.ResticBinaryManager
import io.github.hddq.restoid.data.ResticRepository
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.model.CalendarInfo
import io.github.hddq.restoid.ui.shared.BackupTypes
import io.github.hddq.restoid.ui.shared.ForgetHostOption
import io.github.hddq.restoid.ui.shared.ForgetHostsController
import io.github.hddq.restoid.ui.shared.ForgetHostsSlice
import io.github.hddq.restoid.ui.shared.OperationProgress
import io.github.hddq.restoid.ui.shared.normalizeForgetHosts
import io.github.hddq.restoid.util.StorageUtils
import io.github.hddq.restoid.work.BackupWorkRequest
import io.github.hddq.restoid.work.DeviceDataBackupSelection
import io.github.hddq.restoid.work.ExclusionConflict
import io.github.hddq.restoid.work.OperationWorkRepository
import io.github.hddq.restoid.work.RunTasksWorkRequest
import io.github.hddq.restoid.work.findConflictingExclusions
import io.github.hddq.restoid.work.resolveExclusionPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import io.github.hddq.restoid.model.MaintenanceConfig

import io.github.hddq.restoid.model.CustomDirectory

data class RunTasksUiState(
    val apps: List<AppInfo> = emptyList(),
    val isLoadingApps: Boolean = true,
    val backupEnabled: Boolean = true,
    val backupTypes: BackupTypes = BackupTypes(),
    val appBackupTypes: Map<String, BackupTypes> = emptyMap(),
    val maintenance: MaintenanceConfig = MaintenanceConfig(),
    val isRunning: Boolean = false,
    val progress: OperationProgress = OperationProgress(),
    val customDirectoriesBackupEnabled: Boolean = false,
    val deviceDataBackupEnabled: Boolean = false,
    val contactsBackupEnabled: Boolean = true,
    val includeReadOnlyContacts: Boolean = false,
    val callLogsBackupEnabled: Boolean = false,
    val calendarBackupEnabled: Boolean = false,
    val selectAllCalendars: Boolean = true,
    val selectedCalendarKeys: Set<String> = emptySet(),
    val availableCalendars: List<CalendarInfo> = emptyList(),
    val isLoadingCalendars: Boolean = false,
    val customDirectories: List<CustomDirectory> = emptyList(),
    val excludedCustomDirectories: List<CustomDirectory> = emptyList(),
    val exclusionConflicts: List<ExclusionConflict> = emptyList(),
    val availableForgetHosts: List<ForgetHostOption> = emptyList(),
    val areRepoSnapshotsLoaded: Boolean = false,
    val isLoadingForgetHosts: Boolean = false,
    val forgetHostsError: String? = null
)

sealed interface RunTasksUiEvent {
    data object NavigateToOperationProgress : RunTasksUiEvent
}

class RunTasksViewModel(
    private val application: Application,
    private val rootRepository: io.github.hddq.restoid.data.RootRepository,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticBinaryManager: ResticBinaryManager,
    private val resticRepository: ResticRepository,
    private val appInfoRepository: io.github.hddq.restoid.data.AppInfoRepository,
    private val preferencesRepository: io.github.hddq.restoid.data.PreferencesRepository,
    private val operationWorkRepository: OperationWorkRepository
) : ViewModel() {

    private val calendarBackupRepository = CalendarBackupRepository(application)

    val rootState = rootRepository.rootState

    private val _uiState = MutableStateFlow(
        RunTasksUiState(
            backupEnabled = preferencesRepository.loadRunTasksBackupEnabled(),
            backupTypes = preferencesRepository.loadBackupTypes(),
            maintenance = preferencesRepository.loadMaintenanceState(),
            customDirectoriesBackupEnabled = preferencesRepository.loadCustomDirectoriesBackupEnabled(),
            deviceDataBackupEnabled = preferencesRepository.loadDeviceDataBackupEnabled(),
            contactsBackupEnabled = preferencesRepository.loadDeviceDataContactsEnabled(),
            includeReadOnlyContacts = preferencesRepository.loadIncludeReadOnlyContacts(),
            callLogsBackupEnabled = preferencesRepository.loadDeviceDataCallLogsEnabled(),
            calendarBackupEnabled = preferencesRepository.loadDeviceDataCalendarEnabled(),
            selectAllCalendars = preferencesRepository.loadDeviceDataSelectAllCalendars(),
            selectedCalendarKeys = preferencesRepository.loadDeviceDataSelectedCalendarKeys(),
            customDirectories = preferencesRepository.loadCustomDirectoriesAll().map { uri ->
                CustomDirectory(
                    uri = uri,
                    isSelected = preferencesRepository.loadCustomDirectoriesSelected().contains(uri)
                )
            },
            excludedCustomDirectories = preferencesRepository.loadExcludedCustomDirectoriesAll().map { uri ->
                CustomDirectory(
                    uri = uri,
                    isSelected = preferencesRepository.loadExcludedCustomDirectoriesSelected().contains(uri)
                )
            }
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _operationBlocked = MutableStateFlow(false)
    val operationBlocked = _operationBlocked.asStateFlow()

    private val _uiEvents = MutableSharedFlow<RunTasksUiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<RunTasksUiEvent> = _uiEvents.asSharedFlow()

    private var cachedRawExclusions: List<String> = emptyList()
    private var cachedResolvedExclusions: List<String> = emptyList()
    private var conflictResolutionJob: Job? = null

    private val forgetHostsController = ForgetHostsController(
        context = application,
        scope = viewModelScope,
        repositoriesRepository = repositoriesRepository,
        resticBinaryManager = resticBinaryManager,
        resticRepository = resticRepository,
        preferencesRepository = preferencesRepository,
        initialManualHosts = _uiState.value.maintenance.forgetHosts,
        readSlice = {
            val state = _uiState.value
            ForgetHostsSlice(
                maintenance = state.maintenance,
                availableForgetHosts = state.availableForgetHosts,
                areRepoSnapshotsLoaded = state.areRepoSnapshotsLoaded,
                isLoadingForgetHosts = state.isLoadingForgetHosts,
                forgetHostsError = state.forgetHostsError
            )
        },
        updateSlice = { transform ->
            _uiState.update { state ->
                val updated = transform(
                    ForgetHostsSlice(
                        maintenance = state.maintenance,
                        availableForgetHosts = state.availableForgetHosts,
                        areRepoSnapshotsLoaded = state.areRepoSnapshotsLoaded,
                        isLoadingForgetHosts = state.isLoadingForgetHosts,
                        forgetHostsError = state.forgetHostsError
                    )
                )
                state.copy(
                    maintenance = updated.maintenance,
                    availableForgetHosts = updated.availableForgetHosts,
                    areRepoSnapshotsLoaded = updated.areRepoSnapshotsLoaded,
                    isLoadingForgetHosts = updated.isLoadingForgetHosts,
                    forgetHostsError = updated.forgetHostsError
                )
            }
        }
    )

    init {
        loadInstalledApps()
        observeOperationState()
        observeRootState()
        val initialExcluded = if (_uiState.value.customDirectoriesBackupEnabled) {
            _uiState.value.excludedCustomDirectories.filter { it.isSelected }.map { it.uri }
        } else {
            emptyList()
        }
        if (initialExcluded.isNotEmpty()) {
            triggerBackgroundExclusionResolution(initialExcluded)
        }
    }

    private fun observeRootState() {
        viewModelScope.launch {
            rootRepository.rootState.collect {
                _uiState.update { recomputeConflicts(it) }
            }
        }
    }

    fun refreshAppsList() {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            val selectAll = preferencesRepository.loadRunTasksSelectAllApps()
            val selectedPackages = preferencesRepository.loadRunTasksSelectedPackages()
            val savedAppBackupTypes = preferencesRepository.loadRunTasksAppBackupTypes()
            val currentSelection = _uiState.value.apps.associate { it.packageName to it.isSelected }
            val currentBackupTypes = _uiState.value.appBackupTypes
            val defaultBackupTypes = _uiState.value.backupTypes
            val apps = appInfoRepository.getInstalledUserApps().map { app ->
                val selected = if (_uiState.value.apps.isEmpty()) {
                    // Initial load: restore from preferences
                    if (selectAll) true else selectedPackages.contains(app.packageName)
                } else {
                    // Refresh: keep current selection, fallback to default (true)
                    currentSelection[app.packageName] ?: app.isSelected
                }
                app.copy(isSelected = selected)
            }
            val appBackupTypes = apps.associate { app ->
                app.packageName to (
                        currentBackupTypes[app.packageName]
                            ?: savedAppBackupTypes[app.packageName]
                            ?: defaultBackupTypes
                        )
            }
            _uiState.update { recomputeConflicts(it.copy(apps = apps, appBackupTypes = appBackupTypes, isLoadingApps = false)) }
        }
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
        if (_uiState.value.isRunning) return

        preferencesRepository.saveBackupTypes(_uiState.value.backupTypes)
        preferencesRepository.saveRunTasksAppBackupTypes(_uiState.value.appBackupTypes)
        preferencesRepository.saveMaintenanceState(_uiState.value.maintenance)
        preferencesRepository.saveRunTasksBackupEnabled(_uiState.value.backupEnabled)
        
        val allAppsSelected = _uiState.value.apps.isNotEmpty() && _uiState.value.apps.all { it.isSelected }
        val selectedPackages = _uiState.value.apps.filter { it.isSelected }.map { it.packageName }.toSet()
        preferencesRepository.saveRunTasksSelectedApps(allAppsSelected, selectedPackages)

        preferencesRepository.saveCustomDirectoriesBackupEnabled(_uiState.value.customDirectoriesBackupEnabled)
        preferencesRepository.saveDeviceDataBackupEnabled(_uiState.value.deviceDataBackupEnabled)
        preferencesRepository.saveDeviceDataContactsEnabled(_uiState.value.contactsBackupEnabled)
        preferencesRepository.saveIncludeReadOnlyContacts(_uiState.value.includeReadOnlyContacts)
        preferencesRepository.saveDeviceDataCallLogsEnabled(_uiState.value.callLogsBackupEnabled)
        preferencesRepository.saveDeviceDataCalendarEnabled(_uiState.value.calendarBackupEnabled)
        preferencesRepository.saveDeviceDataCalendarSelection(
            _uiState.value.selectAllCalendars,
            _uiState.value.selectedCalendarKeys
        )
        val allCustomDirs = _uiState.value.customDirectories.map { it.uri }.toSet()
        val selectedCustomDirs = _uiState.value.customDirectories.filter { it.isSelected }.map { it.uri }.toSet()
        preferencesRepository.saveCustomDirectories(allCustomDirs, selectedCustomDirs)
        val selectedExcludedCustomDirs = _uiState.value.excludedCustomDirectories.filter { it.isSelected }.map { it.uri }.toSet()
        persistExcludedCustomDirectories()

        val errorState = preflightChecks()
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

        val maintenance = _uiState.value.maintenance
        val hasCustomDirs = _uiState.value.customDirectoriesBackupEnabled && selectedCustomDirs.isNotEmpty()
        val hasContactsBackup = _uiState.value.deviceDataBackupEnabled && _uiState.value.contactsBackupEnabled
        val hasCallLogsBackup = _uiState.value.deviceDataBackupEnabled && _uiState.value.callLogsBackupEnabled
        val hasCalendarBackup = _uiState.value.deviceDataBackupEnabled && _uiState.value.calendarBackupEnabled
        val hasDeviceData = hasContactsBackup || hasCallLogsBackup || hasCalendarBackup
        val hasRoot = rootState.value == io.github.hddq.restoid.data.RootState.Granted
        val backupAppsEnabled = _uiState.value.backupEnabled && hasRoot
        
        val request = RunTasksWorkRequest(
            repositoryKey = selectedRepoKey,
            backupEnabled = backupAppsEnabled || hasCustomDirs || hasDeviceData,
            backupTypes = _uiState.value.backupTypes.toSelection(),
            selectedPackageNames = if (backupAppsEnabled) _uiState.value.apps.filter { it.isSelected }.map { it.packageName } else emptyList(),
            appBackupTypes = if (backupAppsEnabled) selectedAppBackupTypes().mapValues { it.value.toSelection() } else emptyMap(),
            customDirectories = if (hasCustomDirs) selectedCustomDirs.toList() else emptyList(),
            excludedCustomDirectories = if (_uiState.value.customDirectoriesBackupEnabled) selectedExcludedCustomDirs.toList() else emptyList(),
            deviceData = DeviceDataBackupSelection(
                contacts = hasContactsBackup,
                includeReadOnlyContacts = _uiState.value.includeReadOnlyContacts,
                callLogs = hasCallLogsBackup,
                calendar = hasCalendarBackup,
                selectAllCalendars = _uiState.value.selectAllCalendars,
                selectedCalendarKeys = _uiState.value.selectedCalendarKeys
            ),
            unlockRepo = maintenance.unlockRepo,
            forgetSnapshots = maintenance.forgetSnapshots,
            pruneRepo = maintenance.pruneRepo,
            checkRepo = maintenance.checkRepo,
            readData = maintenance.readData,
            keepLast = maintenance.keepLast,
            keepDaily = maintenance.keepDaily,
            keepWeekly = maintenance.keepWeekly,
            keepMonthly = maintenance.keepMonthly,
            forgetAllHosts = maintenance.forgetAllHosts,
            forgetHosts = if (maintenance.forgetAllHosts) emptyList() else normalizeForgetHosts(maintenance.forgetHosts)
        )

        viewModelScope.launch(Dispatchers.IO) {
            val enqueued = operationWorkRepository.enqueueRunTasks(request)
            if (enqueued) {
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

    private fun preflightChecks(): OperationProgress? {
        val state = _uiState.value
        val maintenance = state.maintenance
        val hasMaintenanceTask = maintenance.unlockRepo || maintenance.forgetSnapshots || maintenance.pruneRepo || maintenance.checkRepo
        val hasCustomDirs = state.customDirectoriesBackupEnabled && state.customDirectories.any { it.isSelected }
        val hasContactsBackup = state.deviceDataBackupEnabled && state.contactsBackupEnabled
        val hasCallLogsBackup = state.deviceDataBackupEnabled && state.callLogsBackupEnabled
        val hasCalendarBackup = state.deviceDataBackupEnabled && state.calendarBackupEnabled
        val hasDeviceData = hasContactsBackup || hasCallLogsBackup || hasCalendarBackup
        val hasRoot = rootState.value == io.github.hddq.restoid.data.RootState.Granted
        val backupAppsEnabled = state.backupEnabled && hasRoot
        
        if (!backupAppsEnabled && !hasCustomDirs && !hasDeviceData && !hasMaintenanceTask) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.maintenance_error_no_tasks),
                finalSummary = application.getString(R.string.maintenance_summary_no_tasks)
            )
        }

        if (hasContactsBackup && !io.github.hddq.restoid.data.ContactsBackupRepository(application).hasReadContactsPermission()) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_contacts_read_permission_required),
                finalSummary = application.getString(R.string.summary_contacts_read_permission_required)
            )
        }

        if (hasCallLogsBackup && !io.github.hddq.restoid.data.CallLogsBackupRepository(application).hasReadCallLogPermission()) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_call_logs_read_permission_required),
                finalSummary = application.getString(R.string.summary_call_logs_read_permission_required)
            )
        }

        if (hasCalendarBackup && !calendarBackupRepository.hasReadCalendarPermission()) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_calendar_read_permission_required),
                finalSummary = application.getString(R.string.summary_calendar_read_permission_required)
            )
        }

        if (hasCalendarBackup && !state.selectAllCalendars && state.selectedCalendarKeys.isEmpty()) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_calendar_none_selected),
                finalSummary = application.getString(R.string.summary_calendar_none_selected)
            )
        }

        if (maintenance.forgetSnapshots && !maintenance.forgetAllHosts && normalizeForgetHosts(maintenance.forgetHosts).isEmpty()) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_no_forget_hosts_selected),
                finalSummary = application.getString(R.string.summary_no_forget_hosts_selected)
            )
        }

        if (backupAppsEnabled) {
            if (state.apps.none { it.isSelected } && !hasCustomDirs && !hasDeviceData) {
                return OperationProgress(
                    isFinished = true,
                    error = application.getString(R.string.error_no_items_selected),
                    finalSummary = application.getString(R.string.summary_no_items_selected)
                )
            }
            val hasSelectedBackupType = state.apps
                .filter { it.isSelected }
                .any { app -> effectiveBackupTypes(state, app.packageName).anyEnabled() } || hasCustomDirs || hasDeviceData
            if (!hasSelectedBackupType) {
                return OperationProgress(
                    isFinished = true,
                    error = application.getString(R.string.error_no_backup_types_selected),
                    finalSummary = application.getString(R.string.summary_no_backup_types_selected)
                )
            }
        }

        if (!resticBinaryManager.isInstalled) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_restic_not_installed),
                finalSummary = application.getString(R.string.summary_restic_binary_not_installed)
            )
        }

        val selectedRepoKey = repositoriesRepository.selectedRepository.value
        val repository = selectedRepoKey?.let { repositoriesRepository.getRepositoryByKey(it) }
        if (selectedRepoKey == null || repository == null) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_no_backup_repository_selected),
                finalSummary = application.getString(R.string.summary_no_backup_repository_selected)
            )
        }

        if (repositoriesRepository.getRepositoryPassword(selectedRepoKey) == null) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_password_not_found_for_repository),
                finalSummary = application.getString(R.string.summary_password_not_found)
            )
        }

        if (repository.backendType == RepositoryBackendType.SFTP && !repositoriesRepository.hasSftpCredentials(selectedRepoKey)) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_sftp_password_not_found_for_repository),
                finalSummary = application.getString(R.string.summary_sftp_password_not_found)
            )
        }

        if (
            repository.backendType == RepositoryBackendType.REST &&
            repository.restAuthRequired &&
            !repositoriesRepository.hasRestCredentials(selectedRepoKey)
        ) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_rest_credentials_not_found_for_repository),
                finalSummary = application.getString(R.string.summary_rest_credentials_not_found)
            )
        }

        if (
            repository.backendType == RepositoryBackendType.S3 &&
            repository.s3AuthRequired &&
            !repositoriesRepository.hasS3Credentials(selectedRepoKey)
        ) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_s3_credentials_not_found_for_repository),
                finalSummary = application.getString(R.string.summary_s3_credentials_not_found)
            )
        }

        return null
    }

    fun consumeOperationBlocked() {
        _operationBlocked.value = false
    }

    fun setBackupEnabled(enabled: Boolean) {
        _uiState.update { recomputeConflicts(it.copy(backupEnabled = enabled)) }
    }

    fun setCustomDirectoriesBackupEnabled(enabled: Boolean) {
        _uiState.update { recomputeConflicts(it.copy(customDirectoriesBackupEnabled = enabled)) }
        preferencesRepository.saveCustomDirectoriesBackupEnabled(enabled)
    }

    fun setDeviceDataBackupEnabled(enabled: Boolean) {
        _uiState.update { it.copy(deviceDataBackupEnabled = enabled) }
        preferencesRepository.saveDeviceDataBackupEnabled(enabled)
    }

    fun setContactsBackupEnabled(enabled: Boolean) {
        _uiState.update { it.copy(contactsBackupEnabled = enabled) }
        preferencesRepository.saveDeviceDataContactsEnabled(enabled)
    }

    fun setIncludeReadOnlyContacts(enabled: Boolean) {
        _uiState.update { it.copy(includeReadOnlyContacts = enabled) }
        preferencesRepository.saveIncludeReadOnlyContacts(enabled)
    }

    fun setCallLogsBackupEnabled(enabled: Boolean) {
        _uiState.update { it.copy(callLogsBackupEnabled = enabled) }
        preferencesRepository.saveDeviceDataCallLogsEnabled(enabled)
    }

    fun setCalendarBackupEnabled(enabled: Boolean) {
        _uiState.update { it.copy(calendarBackupEnabled = enabled) }
        preferencesRepository.saveDeviceDataCalendarEnabled(enabled)
        if (enabled) {
            refreshCalendars()
        }
    }

    fun refreshCalendars() {
        if (!calendarBackupRepository.hasReadCalendarPermission()) {
            _uiState.update { it.copy(availableCalendars = emptyList(), isLoadingCalendars = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCalendars = true) }
            try {
                val calendars = calendarBackupRepository.queryDeviceCalendars()
                _uiState.update { it.copy(availableCalendars = calendars) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("RunTasksViewModel", "Failed to load device calendars", e)
                _uiState.update { it.copy(availableCalendars = emptyList()) }
            } finally {
                _uiState.update { it.copy(isLoadingCalendars = false) }
            }
        }
    }

    fun toggleAllCalendars() {
        _uiState.update { state ->
            val allSelected = state.selectAllCalendars ||
                (state.availableCalendars.isNotEmpty() && state.availableCalendars.all { it.selectionKey in state.selectedCalendarKeys })
            val newSelectAll = !allSelected
            val newKeys = emptySet<String>()
            preferencesRepository.saveDeviceDataCalendarSelection(newSelectAll, newKeys)
            state.copy(selectAllCalendars = newSelectAll, selectedCalendarKeys = newKeys)
        }
    }

    fun toggleCalendar(selectionKey: String) {
        _uiState.update { state ->
            val allKeys = state.availableCalendars.map { it.selectionKey }.toSet()
            val currentKeys = if (state.selectAllCalendars) allKeys else state.selectedCalendarKeys
            val updatedKeys = if (selectionKey in currentKeys) currentKeys - selectionKey else currentKeys + selectionKey
            val newSelectAll = allKeys.isNotEmpty() && allKeys.all { it in updatedKeys }
            val persistedKeys = if (newSelectAll) emptySet() else updatedKeys
            preferencesRepository.saveDeviceDataCalendarSelection(newSelectAll, persistedKeys)
            state.copy(selectAllCalendars = newSelectAll, selectedCalendarKeys = persistedKeys)
        }
    }

    fun addCustomDirectory(uriString: String) {
        val newKey = StorageUtils.resolveCustomDirectoryKey(uriString)
        if (_uiState.value.customDirectories.any { StorageUtils.resolveCustomDirectoryKey(it.uri) == newKey }) {
            Toast.makeText(
                application,
                application.getString(R.string.custom_directories_already_added),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val repoKey = repositoriesRepository.selectedRepository.value
        val repository = repoKey?.let { repositoriesRepository.getRepositoryByKey(it) }

        if (repository != null && repository.backendType == RepositoryBackendType.LOCAL) {
            val repoCanonicalKey = StorageUtils.resolveCustomDirectoryKey(repository.path)
            if (repoCanonicalKey == newKey) {
                Toast.makeText(
                    application,
                    application.getString(R.string.error_cannot_backup_repo_to_itself),
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        _uiState.update { state ->
            state.copy(customDirectories = state.customDirectories + CustomDirectory(uriString))
        }
        persistCustomDirectories()
    }

    fun toggleCustomDirectory(uriString: String) {
        _uiState.update { state ->
            state.copy(
                customDirectories = state.customDirectories.map {
                    if (it.uri == uriString) it.copy(isSelected = !it.isSelected) else it
                }
            )
        }
        persistCustomDirectories()
    }

    fun removeCustomDirectory(uriString: String) {
        _uiState.update { state ->
            state.copy(customDirectories = state.customDirectories.filter { it.uri != uriString })
        }
        persistCustomDirectories()
    }

    private fun persistCustomDirectories() {
        val current = _uiState.value.customDirectories
        val allCustomDirs = current.map { it.uri }.toSet()
        val selectedCustomDirs = current.filter { it.isSelected }.map { it.uri }.toSet()
        preferencesRepository.saveCustomDirectories(allCustomDirs, selectedCustomDirs)
    }

    fun addExcludedCustomDirectory(uriString: String) {
        val newKey = StorageUtils.resolveCustomDirectoryKey(uriString)
        if (_uiState.value.excludedCustomDirectories.any { StorageUtils.resolveCustomDirectoryKey(it.uri) == newKey }) {
            Toast.makeText(
                application,
                application.getString(R.string.custom_directories_already_added),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        _uiState.update { state ->
            recomputeConflicts(state.copy(excludedCustomDirectories = state.excludedCustomDirectories + CustomDirectory(uriString)))
        }
        persistExcludedCustomDirectories()
    }

    fun toggleExcludedCustomDirectory(uriString: String) {
        _uiState.update { state ->
            recomputeConflicts(
                state.copy(
                    excludedCustomDirectories = state.excludedCustomDirectories.map {
                        if (it.uri == uriString) it.copy(isSelected = !it.isSelected) else it
                    }
                )
            )
        }
        persistExcludedCustomDirectories()
    }

    fun removeExcludedCustomDirectory(uriString: String) {
        _uiState.update { state ->
            recomputeConflicts(state.copy(excludedCustomDirectories = state.excludedCustomDirectories.filter { it.uri != uriString }))
        }
        persistExcludedCustomDirectories()
    }

    private fun persistExcludedCustomDirectories() {
        val current = _uiState.value.excludedCustomDirectories
        val allDirs = current.map { it.uri }.toSet()
        val selectedDirs = current.filter { it.isSelected }.map { it.uri }.toSet()
        preferencesRepository.saveExcludedCustomDirectories(allDirs, selectedDirs)
    }

    fun setUnlockRepo(value: Boolean) = _uiState.update { it.copy(maintenance = it.maintenance.copy(unlockRepo = value)) }
    fun setForgetSnapshots(value: Boolean) = _uiState.update { it.copy(maintenance = it.maintenance.copy(forgetSnapshots = value)) }
    fun setPruneRepo(value: Boolean) = _uiState.update { it.copy(maintenance = it.maintenance.copy(pruneRepo = value)) }
    fun setCheckRepo(value: Boolean) = _uiState.update { it.copy(maintenance = it.maintenance.copy(checkRepo = value)) }
    fun setReadData(value: Boolean) = _uiState.update { it.copy(maintenance = it.maintenance.copy(readData = value)) }
    fun setKeepLast(value: Int) = _uiState.update { it.copy(maintenance = it.maintenance.copy(keepLast = value)) }
    fun setKeepDaily(value: Int) = _uiState.update { it.copy(maintenance = it.maintenance.copy(keepDaily = value)) }
    fun setKeepWeekly(value: Int) = _uiState.update { it.copy(maintenance = it.maintenance.copy(keepWeekly = value)) }
    fun setKeepMonthly(value: Int) = _uiState.update { it.copy(maintenance = it.maintenance.copy(keepMonthly = value)) }

    fun toggleAllForgetHosts() = forgetHostsController.toggleAll()
    fun toggleForgetHost(hostname: String) = forgetHostsController.toggleHost(hostname)
    fun addCustomForgetHost(hostname: String) = forgetHostsController.addCustomHost(hostname)
    fun removeCustomForgetHost(hostname: String) = forgetHostsController.removeCustomHost(hostname)
    fun refreshForgetHosts(userInitiated: Boolean = true) = forgetHostsController.refreshHosts(userInitiated = userInitiated)

    fun toggleAppSelection(packageName: String) {
        _uiState.update { state ->
            recomputeConflicts(
                state.copy(
                    apps = state.apps.map { app ->
                        if (app.packageName == packageName) app.copy(isSelected = !app.isSelected) else app
                    },
                    appBackupTypes = state.appBackupTypes.ensurePackage(packageName, state.backupTypes)
                )
            )
        }
    }

    fun toggleAllApps() {
        _uiState.update { state ->
            val shouldSelectAll = state.apps.any { !it.isSelected }
            val apps = state.apps.map { it.copy(isSelected = shouldSelectAll) }
            recomputeConflicts(
                state.copy(
                    apps = apps,
                    appBackupTypes = apps.fold(state.appBackupTypes) { acc, app ->
                        acc.ensurePackage(app.packageName, state.backupTypes)
                    }
                )
            )
        }
    }

    fun setAppBackupTypes(packageName: String, backupTypes: BackupTypes) {
        _uiState.update { state ->
            recomputeConflicts(
                state.copy(appBackupTypes = state.appBackupTypes + (packageName to backupTypes))
            )
        }
    }

    fun setSelectedAppsBackupTypes(backupTypes: BackupTypes) {
        _uiState.update { state ->
            val selectedPackageNames = state.apps.filter { it.isSelected }.map { it.packageName }
            recomputeConflicts(
                state.copy(
                    backupTypes = backupTypes,
                    appBackupTypes = state.appBackupTypes + selectedPackageNames.associateWith { backupTypes }
                )
            )
        }
    }

    fun setBackupApk(value: Boolean) = _uiState.update { recomputeConflicts(it.copy(backupTypes = it.backupTypes.copy(apk = value))) }
    fun setBackupData(value: Boolean) = _uiState.update { recomputeConflicts(it.copy(backupTypes = it.backupTypes.copy(data = value))) }
    fun setBackupDeviceProtectedData(value: Boolean) = _uiState.update { recomputeConflicts(it.copy(backupTypes = it.backupTypes.copy(deviceProtectedData = value))) }
    fun setBackupExternalData(value: Boolean) = _uiState.update { recomputeConflicts(it.copy(backupTypes = it.backupTypes.copy(externalData = value))) }
    fun setBackupObb(value: Boolean) = _uiState.update { recomputeConflicts(it.copy(backupTypes = it.backupTypes.copy(obb = value))) }
    fun setBackupMedia(value: Boolean) = _uiState.update { recomputeConflicts(it.copy(backupTypes = it.backupTypes.copy(media = value))) }
    fun setBackupPermissions(value: Boolean) = _uiState.update { recomputeConflicts(it.copy(backupTypes = it.backupTypes.copy(permissions = value))) }
    fun setBackupSpecialAccess(value: Boolean) = _uiState.update { recomputeConflicts(it.copy(backupTypes = it.backupTypes.copy(specialAccess = value))) }
    fun setBackupAccessibility(value: Boolean) = _uiState.update { recomputeConflicts(it.copy(backupTypes = it.backupTypes.copy(accessibility = value))) }

    private fun recomputeConflicts(state: RunTasksUiState): RunTasksUiState {
        val hasRoot = rootState.value == io.github.hddq.restoid.data.RootState.Granted
        val selectedApps = if (state.backupEnabled && hasRoot) state.apps.filter { it.isSelected } else emptyList()
        val selectedExcluded = if (state.customDirectoriesBackupEnabled) {
            state.excludedCustomDirectories.filter { it.isSelected }.map { it.uri }
        } else {
            emptyList()
        }

        if (selectedApps.isEmpty() || selectedExcluded.isEmpty()) {
            return if (state.exclusionConflicts.isEmpty()) state else state.copy(exclusionConflicts = emptyList())
        }

        if (selectedExcluded != cachedRawExclusions) {
            triggerBackgroundExclusionResolution(selectedExcluded)
        }

        val effectiveBackupTypes = state.backupTypes.toSelection()
        val appBackupTypes = state.appBackupTypes.mapValues { it.value.toSelection() }
        val currentUserId = runCatching {
            android.os.UserHandle::class.java.getMethod("myUserId").invoke(null) as Int
        }.getOrElse { android.os.Process.myUid() / 100000 }

        val dummyRequest = BackupWorkRequest(
            repositoryKey = "",
            backupTypes = effectiveBackupTypes,
            selectedPackageNames = selectedApps.map { it.packageName },
            appBackupTypes = appBackupTypes,
            excludedCustomDirectories = selectedExcluded
        )
        val resolved = if (selectedExcluded == cachedRawExclusions) {
            cachedResolvedExclusions
        } else {
            selectedExcluded.mapNotNull { uri ->
                (StorageUtils.resolveCustomDirectoryPath(uri) ?: uri).trimEnd('/')
            }
        }
        val conflicts = findConflictingExclusions(selectedApps, dummyRequest, currentUserId, resolved)
        return state.copy(exclusionConflicts = conflicts)
    }

    private fun triggerBackgroundExclusionResolution(selectedExcluded: List<String>) {
        conflictResolutionJob?.cancel()
        conflictResolutionJob = viewModelScope.launch {
            val resolved = resolveExclusionPaths(selectedExcluded)
            cachedRawExclusions = selectedExcluded
            cachedResolvedExclusions = resolved
            _uiState.update { recomputeConflicts(it) }
        }
    }

    private fun selectedAppBackupTypes(): Map<String, BackupTypes> {
        val state = _uiState.value
        return state.apps
            .filter { it.isSelected }
            .associate { app -> app.packageName to effectiveBackupTypes(state, app.packageName) }
    }

    private fun effectiveBackupTypes(state: RunTasksUiState, packageName: String): BackupTypes {
        return state.appBackupTypes[packageName] ?: state.backupTypes
    }

    private fun Map<String, BackupTypes>.ensurePackage(packageName: String, defaultTypes: BackupTypes): Map<String, BackupTypes> {
        return if (containsKey(packageName)) this else this + (packageName to defaultTypes)
    }
}
