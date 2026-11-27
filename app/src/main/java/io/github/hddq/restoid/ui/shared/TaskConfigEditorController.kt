package io.github.hddq.restoid.ui.shared

import android.app.Application
import android.content.Context
import android.widget.Toast
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.AppInfoRepository
import io.github.hddq.restoid.data.CalendarBackupRepository
import io.github.hddq.restoid.data.CallLogsBackupRepository
import io.github.hddq.restoid.data.ContactsBackupRepository
import io.github.hddq.restoid.data.PreferencesRepository
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.RepositoryBackendType
import io.github.hddq.restoid.data.ResticBinaryManager
import io.github.hddq.restoid.data.ResticRepository
import io.github.hddq.restoid.data.RootRepository
import io.github.hddq.restoid.data.TagRepository
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.model.BackupTag
import io.github.hddq.restoid.model.BackupTagRules
import io.github.hddq.restoid.model.CalendarInfo
import io.github.hddq.restoid.model.ContactAccountInfo
import io.github.hddq.restoid.model.CustomDirectory
import io.github.hddq.restoid.model.MaintenanceConfig
import io.github.hddq.restoid.util.StorageUtils
import io.github.hddq.restoid.work.BackupWorkRequest
import io.github.hddq.restoid.work.DeviceDataBackupSelection
import io.github.hddq.restoid.work.ExclusionConflict
import io.github.hddq.restoid.work.RunTasksConfig
import io.github.hddq.restoid.work.findConflictingExclusions
import io.github.hddq.restoid.work.resolveExclusionPaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class TaskConfigEditorState(
    val apps: List<AppInfo> = emptyList(),
    val isLoadingApps: Boolean = false,
    val appSortOrder: AppSortOrder = AppSortOrder.NAME_ASCENDING,
    val backupEnabled: Boolean = true,
    val backupTypes: BackupTypes = BackupTypes(),
    val appBackupTypes: Map<String, BackupTypes> = emptyMap(),
    val maintenance: MaintenanceConfig = MaintenanceConfig(),
    val customDirectoriesBackupEnabled: Boolean = false,
    val deviceDataBackupEnabled: Boolean = false,
    val contactsBackupEnabled: Boolean = true,
    val selectAllContactAccounts: Boolean = true,
    val selectedContactAccountKeys: Set<String> = emptySet(),
    val availableContactAccounts: List<ContactAccountInfo> = emptyList(),
    val isLoadingContactAccounts: Boolean = false,
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
    val forgetHostsError: String? = null,
    val backupTags: List<String> = emptyList(),
    val availableTags: List<BackupTag> = emptyList()
)

fun TaskConfigEditorState.toRunTasksConfig(hasRoot: Boolean): RunTasksConfig {
    val actualBackupEnabled = backupEnabled && hasRoot
    val selectedApps = apps.filter { it.isSelected }
    val selectedAppTypes = selectedApps.associate { app ->
        app.packageName to (appBackupTypes[app.packageName] ?: backupTypes).toSelection()
    }
    return RunTasksConfig(
        backupEnabled = actualBackupEnabled,
        backupTypes = backupTypes.toSelection(),
        selectedPackageNames = if (actualBackupEnabled) selectedApps.map { it.packageName } else emptyList(),
        appBackupTypes = if (actualBackupEnabled) selectedAppTypes else emptyMap(),
        customDirectories = if (customDirectoriesBackupEnabled) customDirectories.filter { it.isSelected }.map { it.uri } else emptyList(),
        excludedCustomDirectories = if (customDirectoriesBackupEnabled) excludedCustomDirectories.filter { it.isSelected }.map { it.uri } else emptyList(),
        deviceData = DeviceDataBackupSelection(
            contacts = deviceDataBackupEnabled && contactsBackupEnabled,
            selectAllContactAccounts = selectAllContactAccounts,
            selectedContactAccountKeys = selectedContactAccountKeys,
            callLogs = deviceDataBackupEnabled && callLogsBackupEnabled,
            calendar = deviceDataBackupEnabled && calendarBackupEnabled,
            selectAllCalendars = selectAllCalendars,
            selectedCalendarKeys = selectedCalendarKeys
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
        forgetHosts = if (maintenance.forgetAllHosts) emptyList() else normalizeForgetHosts(maintenance.forgetHosts),
        forgetTags = BackupTagRules.sanitizeBackupTags(maintenance.forgetTags),
        backupTags = BackupTagRules.sanitizeBackupTags(backupTags)
    )
}

fun TaskConfigEditorState.withAppliedRunTasksConfig(
    config: RunTasksConfig,
    existingApps: List<AppInfo> = apps
): TaskConfigEditorState {
    val normalizedHosts = normalizeForgetHosts(config.forgetHosts)
    val defaultTypes = config.backupTypes.toUiModel()
    val configAppTypes = config.appBackupTypes.mapValues { it.value.toUiModel() }
    val selectedPkgSet = config.selectedPackageNames.toSet()
    val updatedApps = if (existingApps.isNotEmpty()) {
        sortApps(
            existingApps.map { app -> app.copy(isSelected = app.packageName in selectedPkgSet) },
            appSortOrder
        )
    } else {
        emptyList()
    }
    val updatedAppBackupTypes = if (updatedApps.isNotEmpty()) {
        updatedApps.associate { app ->
            app.packageName to (configAppTypes[app.packageName] ?: defaultTypes)
        }
    } else {
        configAppTypes
    }
    return copy(
        apps = updatedApps,
        backupEnabled = config.backupEnabled,
        backupTypes = defaultTypes,
        appBackupTypes = updatedAppBackupTypes,
        maintenance = MaintenanceConfig(
            unlockRepo = config.unlockRepo,
            forgetSnapshots = config.forgetSnapshots,
            pruneRepo = config.pruneRepo,
            checkRepo = config.checkRepo,
            readData = config.readData,
            keepLast = config.keepLast,
            keepDaily = config.keepDaily,
            keepWeekly = config.keepWeekly,
            keepMonthly = config.keepMonthly,
            forgetAllHosts = config.forgetAllHosts,
            forgetHosts = normalizedHosts,
            forgetTags = BackupTagRules.sanitizeBackupTags(config.forgetTags)
        ),
        customDirectoriesBackupEnabled = config.customDirectories.isNotEmpty() || config.excludedCustomDirectories.isNotEmpty(),
        deviceDataBackupEnabled = config.deviceData.anyEnabled(),
        contactsBackupEnabled = if (config.deviceData.anyEnabled()) config.deviceData.contacts else true,
        selectAllContactAccounts = config.deviceData.selectAllContactAccounts,
        selectedContactAccountKeys = config.deviceData.selectedContactAccountKeys,
        callLogsBackupEnabled = config.deviceData.callLogs,
        calendarBackupEnabled = config.deviceData.calendar,
        selectAllCalendars = config.deviceData.selectAllCalendars,
        selectedCalendarKeys = config.deviceData.selectedCalendarKeys,
        customDirectories = config.customDirectories.map { CustomDirectory(it, true) },
        excludedCustomDirectories = config.excludedCustomDirectories.map { CustomDirectory(it, true) },
        backupTags = BackupTagRules.sanitizeBackupTags(config.backupTags)
    )
}

fun hasTaskConfigChanges(
    current: TaskConfigEditorState,
    initial: TaskConfigEditorState
): Boolean {
    return current.backupEnabled != initial.backupEnabled ||
        current.backupTypes != initial.backupTypes ||
        current.appBackupTypes != initial.appBackupTypes ||
        current.apps.associate { it.packageName to it.isSelected } != initial.apps.associate { it.packageName to it.isSelected } ||
        current.maintenance != initial.maintenance ||
        current.customDirectoriesBackupEnabled != initial.customDirectoriesBackupEnabled ||
        current.deviceDataBackupEnabled != initial.deviceDataBackupEnabled ||
        current.contactsBackupEnabled != initial.contactsBackupEnabled ||
        current.selectAllContactAccounts != initial.selectAllContactAccounts ||
        current.selectedContactAccountKeys != initial.selectedContactAccountKeys ||
        current.callLogsBackupEnabled != initial.callLogsBackupEnabled ||
        current.calendarBackupEnabled != initial.calendarBackupEnabled ||
        current.selectAllCalendars != initial.selectAllCalendars ||
        current.selectedCalendarKeys != initial.selectedCalendarKeys ||
        current.customDirectories != initial.customDirectories ||
        current.excludedCustomDirectories != initial.excludedCustomDirectories ||
        current.backupTags != initial.backupTags
}

fun summarizeTaskConfig(
    context: Context,
    config: RunTasksConfig,
    hasRoot: Boolean
): String {
    val parts = mutableListOf<String>()
    if (config.backupEnabled && hasRoot) {
        val appCount = config.selectedPackageNames.size
        parts.add(context.resources.getQuantityString(R.plurals.preset_summary_apps_count, appCount, appCount))
    }
    if (config.customDirectories.isNotEmpty()) {
        parts.add(context.getString(R.string.preset_summary_custom_folders))
    }
    if (config.deviceData.anyEnabled()) {
        parts.add(context.getString(R.string.preset_summary_device_data))
    }
    if (config.unlockRepo) {
        parts.add(context.getString(R.string.task_unlock))
    }
    if (config.forgetSnapshots) {
        parts.add(context.getString(R.string.task_forget))
    }
    if (config.pruneRepo) {
        parts.add(context.getString(R.string.task_prune))
    }
    if (config.checkRepo) {
        parts.add(context.getString(R.string.task_check))
    }
    return if (parts.isEmpty()) {
        context.getString(R.string.preset_summary_no_tasks)
    } else {
        parts.joinToString(" · ")
    }
}

class TaskConfigEditorController(
    private val application: Application,
    private val scope: CoroutineScope,
    private val rootRepository: RootRepository,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticBinaryManager: ResticBinaryManager,
    private val resticRepository: ResticRepository,
    private val appInfoRepository: AppInfoRepository,
    private val preferencesRepository: PreferencesRepository,
    private val readState: () -> TaskConfigEditorState,
    private val updateState: ((TaskConfigEditorState) -> TaskConfigEditorState) -> Unit,
    private val onConfigMutated: (TaskConfigEditorState) -> Unit = {},
    private val selectedRepoKeyProvider: () -> String? = { repositoriesRepository.selectedRepository.value },
    private val tagRepository: TagRepository? = null
) {
    private val contactsBackupRepository = ContactsBackupRepository(application)
    private val calendarBackupRepository = CalendarBackupRepository(application)

    private var cachedRawExclusions: List<String> = emptyList()
    private var cachedResolvedExclusions: List<String> = emptyList()
    private var conflictResolutionJob: Job? = null
    private var appsLoadSessionId: Long = 0L
    private var contactAccountsRefreshRequestId: Long = 0L
    private var calendarRefreshRequestId: Long = 0L

    private val forgetHostsController = ForgetHostsController(
        context = application,
        scope = scope,
        repositoriesRepository = repositoriesRepository,
        resticBinaryManager = resticBinaryManager,
        resticRepository = resticRepository,
        preferencesRepository = preferencesRepository,
        initialManualHosts = readState().maintenance.forgetHosts,
        readSlice = {
            val state = readState()
            ForgetHostsSlice(
                maintenance = state.maintenance,
                availableForgetHosts = state.availableForgetHosts,
                areRepoSnapshotsLoaded = state.areRepoSnapshotsLoaded,
                isLoadingForgetHosts = state.isLoadingForgetHosts,
                forgetHostsError = state.forgetHostsError
            )
        },
        updateSlice = { transform ->
            updateState { state ->
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
        scope.launch {
            rootRepository.hasRoot.collect {
                updateState { recomputeConflicts(it) }
            }
        }
    }

    fun triggerInitialExclusionResolutionIfNeeded() {
        val state = readState()
        val initialExcluded = if (state.customDirectoriesBackupEnabled) {
            state.excludedCustomDirectories.filter { it.isSelected }.map { it.uri }
        } else {
            emptyList()
        }
        if (initialExcluded.isNotEmpty()) {
            triggerBackgroundExclusionResolution(initialExcluded)
        }
    }

    fun invalidatePendingRefreshes() {
        ++appsLoadSessionId
        ++contactAccountsRefreshRequestId
        ++calendarRefreshRequestId
    }

    fun resetForNewConfig(
        onAppsLoaded: ((List<AppInfo>, Map<String, BackupTypes>) -> Unit)? = null
    ) {
        val sessionId = ++appsLoadSessionId
        ++contactAccountsRefreshRequestId
        ++calendarRefreshRequestId
        val defaultHostname = preferencesRepository.getDeviceHostname()
        val newState = TaskConfigEditorState(
            maintenance = MaintenanceConfig(
                forgetAllHosts = false,
                forgetHosts = listOf(defaultHostname)
            )
        )
        updateState { newState }
        forgetHostsController.setManualHosts(listOf(defaultHostname))
        loadInstalledApps(
            selectedPackageNames = null,
            sessionId = sessionId,
            onAppsLoaded = onAppsLoaded
        )
    }

    fun applyRunTasksConfig(
        config: RunTasksConfig,
        reloadApps: Boolean = false,
        onAppsLoaded: ((List<AppInfo>, Map<String, BackupTypes>) -> Unit)? = null
    ) {
        val sessionId = ++appsLoadSessionId
        ++contactAccountsRefreshRequestId
        ++calendarRefreshRequestId
        val normalizedHosts = normalizeForgetHosts(config.forgetHosts)
        val existingApps = if (reloadApps) emptyList() else readState().apps
        updateState { state ->
            recomputeConflicts(
                state.withAppliedRunTasksConfig(config, existingApps = existingApps).copy(
                    isLoadingContactAccounts = false,
                    isLoadingCalendars = false
                )
            )
        }
        forgetHostsController.setManualHosts(normalizedHosts)
        if (existingApps.isEmpty()) {
            loadInstalledApps(
                selectedPackageNames = config.selectedPackageNames,
                sessionId = sessionId,
                onAppsLoaded = onAppsLoaded
            )
        } else {
            val current = readState()
            onAppsLoaded?.invoke(current.apps, current.appBackupTypes)
        }
    }

    fun setManualForgetHosts(hosts: List<String>) {
        forgetHostsController.setManualHosts(hosts)
    }

    fun refreshAppsList() {
        val state = readState()
        if (state.isLoadingApps && state.apps.isEmpty()) return
        loadInstalledApps(selectedPackageNames = null)
    }

    fun loadInstalledApps(
        selectedPackageNames: List<String>? = null,
        selectAllDefault: Boolean? = null,
        savedAppBackupTypes: Map<String, BackupTypes> = emptyMap(),
        sessionId: Long = ++appsLoadSessionId,
        onAppsLoaded: ((List<AppInfo>, Map<String, BackupTypes>) -> Unit)? = null
    ) {
        scope.launch {
            updateState { it.copy(isLoadingApps = true) }
            val installedApps = appInfoRepository.getInstalledUserApps()
            if (appsLoadSessionId != sessionId) return@launch

            val stateSnapshot = readState()
            val currentSelection = stateSnapshot.apps.associate { it.packageName to it.isSelected }
            val currentBackupTypes = stateSnapshot.appBackupTypes
            val defaultBackupTypes = stateSnapshot.backupTypes

            val mappedApps = installedApps.map { app ->
                val selected = when {
                    selectedPackageNames != null -> selectedPackageNames.contains(app.packageName)
                    selectAllDefault != null -> selectAllDefault
                    stateSnapshot.apps.isNotEmpty() -> currentSelection[app.packageName] ?: app.isSelected
                    else -> app.isSelected
                }
                app.copy(isSelected = selected)
            }
            val sortedApps = sortApps(mappedApps, stateSnapshot.appSortOrder)
            val appBackupTypes = sortedApps.associate { app ->
                app.packageName to (
                    currentBackupTypes[app.packageName]
                        ?: savedAppBackupTypes[app.packageName]
                        ?: defaultBackupTypes
                    )
            }
            var delivered = false
            updateState { state ->
                if (appsLoadSessionId != sessionId) return@updateState state
                delivered = true
                recomputeConflicts(
                    state.copy(
                        apps = sortedApps,
                        appBackupTypes = appBackupTypes,
                        isLoadingApps = false
                    )
                )
            }
            if (delivered) {
                onAppsLoaded?.invoke(sortedApps, appBackupTypes)
            }
        }
    }

    private inline fun mutateConfig(crossinline transform: (TaskConfigEditorState) -> TaskConfigEditorState) {
        updateState { state -> transform(state) }
        onConfigMutated(readState())
    }

    fun setBackupEnabled(enabled: Boolean) {
        mutateConfig { recomputeConflicts(it.copy(backupEnabled = enabled)) }
    }

    fun setCustomDirectoriesBackupEnabled(enabled: Boolean) {
        mutateConfig { recomputeConflicts(it.copy(customDirectoriesBackupEnabled = enabled)) }
    }

    fun setDeviceDataBackupEnabled(enabled: Boolean) {
        mutateConfig { it.copy(deviceDataBackupEnabled = enabled) }
    }

    fun setContactsBackupEnabled(enabled: Boolean) {
        mutateConfig { it.copy(contactsBackupEnabled = enabled) }
        if (enabled) {
            refreshContactAccounts()
        }
    }

    fun refreshContactAccounts() {
        val requestId = ++contactAccountsRefreshRequestId
        if (!contactsBackupRepository.hasReadContactsPermission()) {
            updateState { it.copy(availableContactAccounts = emptyList(), isLoadingContactAccounts = false) }
            return
        }
        scope.launch {
            updateState { it.copy(isLoadingContactAccounts = true) }
            try {
                val accounts = contactsBackupRepository.queryDeviceContactAccounts()
                if (contactAccountsRefreshRequestId == requestId) {
                    updateState { it.copy(availableContactAccounts = accounts) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("TaskConfigEditor", "Failed to load device contact accounts", e)
                if (contactAccountsRefreshRequestId == requestId) {
                    updateState { it.copy(availableContactAccounts = emptyList()) }
                }
            } finally {
                if (contactAccountsRefreshRequestId == requestId) {
                    updateState { it.copy(isLoadingContactAccounts = false) }
                }
            }
        }
    }

    fun toggleAllContactAccounts() {
        mutateConfig { state ->
            val allSelected = state.selectAllContactAccounts ||
                (state.availableContactAccounts.isNotEmpty() && state.availableContactAccounts.all { it.selectionKey in state.selectedContactAccountKeys })
            val newSelectAll = !allSelected
            state.copy(selectAllContactAccounts = newSelectAll, selectedContactAccountKeys = emptySet())
        }
    }

    fun toggleContactAccount(selectionKey: String) {
        mutateConfig { state ->
            val allKeys = state.availableContactAccounts.map { it.selectionKey }.toSet()
            val currentKeys = if (state.selectAllContactAccounts) allKeys else state.selectedContactAccountKeys
            val updatedKeys = if (selectionKey in currentKeys) currentKeys - selectionKey else currentKeys + selectionKey
            val newSelectAll = allKeys.isNotEmpty() && allKeys.all { it in updatedKeys }
            val persistedKeys = if (newSelectAll) emptySet() else updatedKeys
            state.copy(selectAllContactAccounts = newSelectAll, selectedContactAccountKeys = persistedKeys)
        }
    }

    fun setCallLogsBackupEnabled(enabled: Boolean) {
        mutateConfig { it.copy(callLogsBackupEnabled = enabled) }
    }

    fun setCalendarBackupEnabled(enabled: Boolean) {
        mutateConfig { it.copy(calendarBackupEnabled = enabled) }
        if (enabled) {
            refreshCalendars()
        }
    }

    fun refreshCalendars() {
        val requestId = ++calendarRefreshRequestId
        if (!calendarBackupRepository.hasReadCalendarPermission()) {
            updateState { it.copy(availableCalendars = emptyList(), isLoadingCalendars = false) }
            return
        }
        scope.launch {
            updateState { it.copy(isLoadingCalendars = true) }
            try {
                val calendars = calendarBackupRepository.queryDeviceCalendars()
                if (calendarRefreshRequestId == requestId) {
                    updateState { it.copy(availableCalendars = calendars) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("TaskConfigEditor", "Failed to load device calendars", e)
                if (calendarRefreshRequestId == requestId) {
                    updateState { it.copy(availableCalendars = emptyList()) }
                }
            } finally {
                if (calendarRefreshRequestId == requestId) {
                    updateState { it.copy(isLoadingCalendars = false) }
                }
            }
        }
    }

    fun toggleAllCalendars() {
        mutateConfig { state ->
            val allSelected = state.selectAllCalendars ||
                (state.availableCalendars.isNotEmpty() && state.availableCalendars.all { it.selectionKey in state.selectedCalendarKeys })
            val newSelectAll = !allSelected
            state.copy(selectAllCalendars = newSelectAll, selectedCalendarKeys = emptySet())
        }
    }

    fun toggleCalendar(selectionKey: String) {
        mutateConfig { state ->
            val allKeys = state.availableCalendars.map { it.selectionKey }.toSet()
            val currentKeys = if (state.selectAllCalendars) allKeys else state.selectedCalendarKeys
            val updatedKeys = if (selectionKey in currentKeys) currentKeys - selectionKey else currentKeys + selectionKey
            val newSelectAll = allKeys.isNotEmpty() && allKeys.all { it in updatedKeys }
            val persistedKeys = if (newSelectAll) emptySet() else updatedKeys
            state.copy(selectAllCalendars = newSelectAll, selectedCalendarKeys = persistedKeys)
        }
    }

    fun addCustomDirectory(uriString: String) {
        val newKey = StorageUtils.resolveCustomDirectoryKey(uriString)
        if (readState().customDirectories.any { StorageUtils.resolveCustomDirectoryKey(it.uri) == newKey }) {
            Toast.makeText(
                application,
                application.getString(R.string.custom_directories_already_added),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val repoKey = selectedRepoKeyProvider()
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

        mutateConfig { state ->
            state.copy(customDirectories = state.customDirectories + CustomDirectory(uriString, true))
        }
    }

    fun toggleCustomDirectory(uriString: String) {
        mutateConfig { state ->
            state.copy(
                customDirectories = state.customDirectories.map {
                    if (it.uri == uriString) it.copy(isSelected = !it.isSelected) else it
                }
            )
        }
    }

    fun removeCustomDirectory(uriString: String) {
        mutateConfig { state ->
            state.copy(customDirectories = state.customDirectories.filter { it.uri != uriString })
        }
    }

    fun addExcludedCustomDirectory(uriString: String) {
        val newKey = StorageUtils.resolveCustomDirectoryKey(uriString)
        if (readState().excludedCustomDirectories.any { StorageUtils.resolveCustomDirectoryKey(it.uri) == newKey }) {
            Toast.makeText(
                application,
                application.getString(R.string.custom_directories_already_added),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        mutateConfig { state ->
            recomputeConflicts(
                state.copy(
                    excludedCustomDirectories = state.excludedCustomDirectories + CustomDirectory(uriString, true)
                )
            )
        }
    }

    fun toggleExcludedCustomDirectory(uriString: String) {
        mutateConfig { state ->
            recomputeConflicts(
                state.copy(
                    excludedCustomDirectories = state.excludedCustomDirectories.map {
                        if (it.uri == uriString) it.copy(isSelected = !it.isSelected) else it
                    }
                )
            )
        }
    }

    fun removeExcludedCustomDirectory(uriString: String) {
        mutateConfig { state ->
            recomputeConflicts(
                state.copy(excludedCustomDirectories = state.excludedCustomDirectories.filter { it.uri != uriString })
            )
        }
    }

    fun setUnlockRepo(value: Boolean) = mutateConfig { it.copy(maintenance = it.maintenance.copy(unlockRepo = value)) }
    fun setForgetSnapshots(value: Boolean) = mutateConfig { it.copy(maintenance = it.maintenance.copy(forgetSnapshots = value)) }
    fun setPruneRepo(value: Boolean) = mutateConfig { it.copy(maintenance = it.maintenance.copy(pruneRepo = value)) }
    fun setCheckRepo(value: Boolean) = mutateConfig { it.copy(maintenance = it.maintenance.copy(checkRepo = value)) }
    fun setReadData(value: Boolean) = mutateConfig { it.copy(maintenance = it.maintenance.copy(readData = value)) }
    fun setKeepLast(value: Int) = mutateConfig { it.copy(maintenance = it.maintenance.copy(keepLast = value)) }
    fun setKeepDaily(value: Int) = mutateConfig { it.copy(maintenance = it.maintenance.copy(keepDaily = value)) }
    fun setKeepWeekly(value: Int) = mutateConfig { it.copy(maintenance = it.maintenance.copy(keepWeekly = value)) }
    fun setKeepMonthly(value: Int) = mutateConfig { it.copy(maintenance = it.maintenance.copy(keepMonthly = value)) }

    fun toggleAllForgetHosts() {
        forgetHostsController.toggleAll()
        onConfigMutated(readState())
    }

    fun toggleForgetHost(hostname: String) {
        forgetHostsController.toggleHost(hostname)
        onConfigMutated(readState())
    }

    fun addCustomForgetHost(hostname: String) {
        forgetHostsController.addCustomHost(hostname)
        onConfigMutated(readState())
    }

    fun removeCustomForgetHost(hostname: String) {
        forgetHostsController.removeCustomHost(hostname)
        onConfigMutated(readState())
    }

    fun refreshForgetHosts(userInitiated: Boolean = true) {
        forgetHostsController.refreshHosts(
            selectedRepoKeyOverride = selectedRepoKeyProvider(),
            userInitiated = userInitiated
        )
    }

    fun setAppSortOrder(order: AppSortOrder) {
        if (readState().appSortOrder == order) return
        updateState { state ->
            state.copy(
                appSortOrder = order,
                apps = sortApps(state.apps, order)
            )
        }
    }

    fun toggleAppSelection(packageName: String) {
        mutateConfig { state ->
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
        mutateConfig { state ->
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
        mutateConfig { state ->
            recomputeConflicts(
                state.copy(appBackupTypes = state.appBackupTypes + (packageName to backupTypes))
            )
        }
    }

    fun setSelectedAppsBackupTypes(backupTypes: BackupTypes) {
        mutateConfig { state ->
            val selectedPackageNames = state.apps.filter { it.isSelected }.map { it.packageName }
            recomputeConflicts(
                state.copy(
                    backupTypes = backupTypes,
                    appBackupTypes = state.appBackupTypes + selectedPackageNames.associateWith { backupTypes }
                )
            )
        }
    }

    fun setBackupApk(value: Boolean) = mutateConfig { recomputeConflicts(it.copy(backupTypes = it.backupTypes.copy(apk = value))) }
    fun setBackupData(value: Boolean) = mutateConfig { recomputeConflicts(it.copy(backupTypes = it.backupTypes.copy(data = value))) }
    fun setBackupDeviceProtectedData(value: Boolean) = mutateConfig { recomputeConflicts(it.copy(backupTypes = it.backupTypes.copy(deviceProtectedData = value))) }
    fun setBackupExternalData(value: Boolean) = mutateConfig { recomputeConflicts(it.copy(backupTypes = it.backupTypes.copy(externalData = value))) }
    fun setBackupObb(value: Boolean) = mutateConfig { recomputeConflicts(it.copy(backupTypes = it.backupTypes.copy(obb = value))) }
    fun setBackupMedia(value: Boolean) = mutateConfig { recomputeConflicts(it.copy(backupTypes = it.backupTypes.copy(media = value))) }
    fun setBackupPermissions(value: Boolean) = mutateConfig { recomputeConflicts(it.copy(backupTypes = it.backupTypes.copy(permissions = value))) }
    fun setBackupSpecialAccess(value: Boolean) = mutateConfig { recomputeConflicts(it.copy(backupTypes = it.backupTypes.copy(specialAccess = value))) }
    fun setBackupAccessibility(value: Boolean) = mutateConfig { recomputeConflicts(it.copy(backupTypes = it.backupTypes.copy(accessibility = value))) }

    private var loadTagsJob: Job? = null

    fun loadAvailableTags(repoId: String?) {
        loadTagsJob?.cancel()
        val repo = tagRepository
        if (repoId == null || repo == null) {
            updateState { it.copy(availableTags = emptyList()) }
            return
        }
        loadTagsJob = scope.launch {
            val tags = repo.getTags(repoId)
            updateState { it.copy(availableTags = tags) }
        }
    }

    fun toggleBackupTag(tagName: String) {
        val normalized = BackupTagRules.normalizeTagName(tagName)
        if (normalized.isEmpty()) return
        mutateConfig { state ->
            val exists = state.backupTags.any { it.equals(normalized, ignoreCase = true) }
            val updated = if (exists) {
                state.backupTags.filterNot { it.equals(normalized, ignoreCase = true) }
            } else {
                state.backupTags + normalized
            }
            state.copy(backupTags = updated)
        }
    }

    fun toggleForgetTag(tagName: String) {
        val normalized = BackupTagRules.normalizeTagName(tagName)
        if (normalized.isEmpty()) return
        mutateConfig { state ->
            val current = state.maintenance.forgetTags
            val exists = current.any { it.equals(normalized, ignoreCase = true) }
            val updated = if (exists) {
                current.filterNot { it.equals(normalized, ignoreCase = true) }
            } else {
                current + normalized
            }
            state.copy(maintenance = state.maintenance.copy(forgetTags = updated))
        }
    }

    suspend fun createTag(
        repoId: String,
        name: String,
        colorArgb: Int,
        selectInBackup: Boolean = true,
        selectInForget: Boolean = false
    ): Result<BackupTag> {
        val repo = tagRepository ?: return Result.failure(IllegalStateException("TagRepository not configured"))
        loadTagsJob?.cancel()
        val result = repo.createTag(repoId, name, colorArgb)
        result.onSuccess { created ->
            val tags = repo.getTags(repoId)
            mutateConfig { state ->
                val updatedBackupTags = if (selectInBackup && state.backupTags.none { it.equals(created.name, ignoreCase = true) }) {
                    state.backupTags + created.name
                } else {
                    state.backupTags
                }
                val updatedForgetTags = if (selectInForget && state.maintenance.forgetTags.none { it.equals(created.name, ignoreCase = true) }) {
                    state.maintenance.forgetTags + created.name
                } else {
                    state.maintenance.forgetTags
                }
                state.copy(
                    availableTags = tags,
                    backupTags = updatedBackupTags,
                    maintenance = state.maintenance.copy(forgetTags = updatedForgetTags)
                )
            }
        }
        return result
    }

    suspend fun updateTagColor(repoId: String, name: String, colorArgb: Int): Result<BackupTag> {
        val repo = tagRepository ?: return Result.failure(IllegalStateException("TagRepository not configured"))
        loadTagsJob?.cancel()
        val result = repo.updateTagColor(repoId, name, colorArgb)
        result.onSuccess {
            val tags = repo.getTags(repoId)
            updateState { it.copy(availableTags = tags) }
        }
        return result
    }

    suspend fun deleteTag(repoId: String, name: String): Result<Unit> {
        val repo = tagRepository ?: return Result.failure(IllegalStateException("TagRepository not configured"))
        loadTagsJob?.cancel()
        val result = repo.deleteTag(repoId, name)
        result.onSuccess {
            val tags = repo.getTags(repoId)
            val normalized = BackupTagRules.normalizeTagName(name)
            val currentState = readState()
            val hadBackupTag = currentState.backupTags.any { it.equals(normalized, ignoreCase = true) }
            val hadForgetTag = currentState.maintenance.forgetTags.any { it.equals(normalized, ignoreCase = true) }
            if (hadBackupTag || hadForgetTag) {
                mutateConfig { state ->
                    state.copy(
                        availableTags = tags,
                        backupTags = state.backupTags.filterNot { it.equals(normalized, ignoreCase = true) },
                        maintenance = state.maintenance.copy(
                            forgetTags = state.maintenance.forgetTags.filterNot { it.equals(normalized, ignoreCase = true) }
                        )
                    )
                }
            } else {
                updateState { it.copy(availableTags = tags) }
            }
        }
        return result
    }

    fun validateForExecution(
        config: RunTasksConfig,
        hasRoot: Boolean = rootRepository.hasRoot.value,
        repoKeyOverride: String? = null
    ): OperationProgress? {
        val hasMaintenanceTask = config.unlockRepo || config.forgetSnapshots || config.pruneRepo || config.checkRepo
        val hasCustomDirs = config.customDirectories.isNotEmpty()
        val hasContactsBackup = config.deviceData.contacts
        val hasCallLogsBackup = config.deviceData.callLogs
        val hasCalendarBackup = config.deviceData.calendar
        val hasDeviceData = config.deviceData.anyEnabled()
        val backupAppsEnabled = config.backupEnabled && hasRoot

        if (!backupAppsEnabled && !hasCustomDirs && !hasDeviceData && !hasMaintenanceTask) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.maintenance_error_no_tasks),
                finalSummary = application.getString(R.string.maintenance_summary_no_tasks)
            )
        }

        if (hasContactsBackup && !contactsBackupRepository.hasReadContactsPermission()) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_contacts_read_permission_required),
                finalSummary = application.getString(R.string.summary_contacts_read_permission_required)
            )
        }

        if (hasContactsBackup && !config.deviceData.selectAllContactAccounts && config.deviceData.selectedContactAccountKeys.isEmpty()) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_contacts_none_selected),
                finalSummary = application.getString(R.string.summary_contacts_none_selected)
            )
        }

        if (hasCallLogsBackup && !CallLogsBackupRepository(application).hasReadCallLogPermission()) {
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

        if (hasCalendarBackup && !config.deviceData.selectAllCalendars && config.deviceData.selectedCalendarKeys.isEmpty()) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_calendar_none_selected),
                finalSummary = application.getString(R.string.summary_calendar_none_selected)
            )
        }

        if (config.forgetSnapshots && !config.forgetAllHosts && normalizeForgetHosts(config.forgetHosts).isEmpty()) {
            return OperationProgress(
                isFinished = true,
                error = application.getString(R.string.error_no_forget_hosts_selected),
                finalSummary = application.getString(R.string.summary_no_forget_hosts_selected)
            )
        }

        if (backupAppsEnabled) {
            if (config.selectedPackageNames.isEmpty() && !hasCustomDirs && !hasDeviceData) {
                return OperationProgress(
                    isFinished = true,
                    error = application.getString(R.string.error_no_items_selected),
                    finalSummary = application.getString(R.string.summary_no_items_selected)
                )
            }
            val hasSelectedBackupType = config.selectedPackageNames.any { pkg ->
                (config.appBackupTypes[pkg] ?: config.backupTypes).toUiModel().anyEnabled()
            } || hasCustomDirs || hasDeviceData
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

        val selectedRepoKey = repoKeyOverride ?: selectedRepoKeyProvider()
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

    fun recomputeConflicts(state: TaskConfigEditorState): TaskConfigEditorState {
        val selectedApps = if (state.backupEnabled && rootRepository.hasRoot.value) {
            state.apps.filter { it.isSelected }
        } else {
            emptyList()
        }
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
        conflictResolutionJob = scope.launch {
            val resolved = resolveExclusionPaths(selectedExcluded)
            cachedRawExclusions = selectedExcluded
            cachedResolvedExclusions = resolved
            updateState { recomputeConflicts(it) }
        }
    }

    private fun Map<String, BackupTypes>.ensurePackage(
        packageName: String,
        defaultTypes: BackupTypes
    ): Map<String, BackupTypes> {
        return if (containsKey(packageName)) this else this + (packageName to defaultTypes)
    }
}
