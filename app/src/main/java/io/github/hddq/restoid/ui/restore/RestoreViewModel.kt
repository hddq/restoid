package io.github.hddq.restoid.ui.restore

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.AppInfoRepository
import io.github.hddq.restoid.data.CalendarBackupRepository
import io.github.hddq.restoid.data.ContactsBackupRepository
import io.github.hddq.restoid.data.MetadataRepository
import io.github.hddq.restoid.data.OperationType
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.RepositoryBackendType
import io.github.hddq.restoid.data.ResticBinaryManager
import io.github.hddq.restoid.data.ResticRepository
import io.github.hddq.restoid.data.SnapshotInfo
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.model.BackupDetail
import io.github.hddq.restoid.model.CalendarBackupMetadata
import io.github.hddq.restoid.model.CalendarInfo
import io.github.hddq.restoid.model.CallLogsBackupMetadata
import io.github.hddq.restoid.model.ContactAccountInfo
import io.github.hddq.restoid.model.ContactsBackupMetadata
import io.github.hddq.restoid.model.CustomDirectoryMetadata
import io.github.hddq.restoid.model.RestoidMetadata
import io.github.hddq.restoid.model.toCalendarInfo
import io.github.hddq.restoid.model.toContactAccountInfo
import io.github.hddq.restoid.ui.shared.AppSortOrder
import io.github.hddq.restoid.ui.shared.OperationProgress
import io.github.hddq.restoid.ui.shared.sortBackupDetails
import io.github.hddq.restoid.work.DeviceDataRestoreSelection
import io.github.hddq.restoid.work.OperationWorkRepository
import io.github.hddq.restoid.work.RestoreAppSelection
import io.github.hddq.restoid.work.RestoreTypeSelection
import io.github.hddq.restoid.work.RestoreWorkRequest
import kotlinx.serialization.Serializable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Serializable
data class RestoreTypes(
    val apk: Boolean = true,
    val data: Boolean = true,
    val deviceProtectedData: Boolean = true,
    val externalData: Boolean = false,
    val obb: Boolean = false,
    val media: Boolean = false,
    val permissions: Boolean = true,
    val specialAccess: Boolean = true,
    val accessibility: Boolean = true
) {
    fun toSelection(): RestoreTypeSelection {
        return RestoreTypeSelection(
            apk = apk,
            data = data,
            deviceProtectedData = deviceProtectedData,
            externalData = externalData,
            obb = obb,
            media = media,
            permissions = permissions,
            specialAccess = specialAccess,
            accessibility = accessibility
        )
    }

    fun anyEnabled(): Boolean {
        return apk || data || deviceProtectedData || externalData || obb || media ||
            permissions || specialAccess || accessibility
    }
}

fun RestoreTypeSelection.toUiModel(): RestoreTypes {
    return RestoreTypes(
        apk = apk,
        data = data,
        deviceProtectedData = deviceProtectedData,
        externalData = externalData,
        obb = obb,
        media = media,
        permissions = permissions,
        specialAccess = specialAccess,
        accessibility = accessibility
    )
}

sealed interface RestoreUiEvent {
    data object NavigateToOperationProgress : RestoreUiEvent
    data class RequestDeviceDataPermissions(val permissions: List<String>) : RestoreUiEvent
    data class ShowError(val message: String) : RestoreUiEvent
}

class RestoreViewModel(
    private val application: Application,
    private val repositoriesRepository: RepositoriesRepository,
    private val rootRepository: io.github.hddq.restoid.data.RootRepository,
    private val resticBinaryManager: ResticBinaryManager,
    private val resticRepository: ResticRepository,
    private val appInfoRepository: AppInfoRepository,
    private val metadataRepository: MetadataRepository,
    private val preferencesRepository: io.github.hddq.restoid.data.PreferencesRepository,
    private val operationWorkRepository: OperationWorkRepository,
    val snapshotId: String
) : ViewModel() {

    private var snapshotMetadata: RestoidMetadata? = null
    private val metadataUpdateMutex = Mutex()
    private val contactsBackupRepository = ContactsBackupRepository(application)
    private val calendarBackupRepository = CalendarBackupRepository(application)
    private var contactAccountsLoadRequestId = 0L
    private var calendarsLoadRequestId = 0L
    private var contactAccountsLoaded = false
    private var calendarsLoaded = false

    private val _snapshot = MutableStateFlow<SnapshotInfo?>(null)
    val snapshot = _snapshot.asStateFlow()

    private val _backupDetails = MutableStateFlow<List<BackupDetail>>(emptyList())
    val backupDetails = _backupDetails.asStateFlow()

    private val _sortOrder = MutableStateFlow(AppSortOrder.SIZE_DESCENDING)
    val sortOrder = _sortOrder.asStateFlow()

    private val _customDirectories = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val customDirectories = _customDirectories.asStateFlow()

    private val _customDirectoryMetadata = MutableStateFlow<Map<String, CustomDirectoryMetadata>>(emptyMap())
    val customDirectoryMetadata = _customDirectoryMetadata.asStateFlow()

    private val _contactsMetadata = MutableStateFlow<ContactsBackupMetadata?>(null)
    val contactsMetadata = _contactsMetadata.asStateFlow()

    private val _callLogsMetadata = MutableStateFlow<CallLogsBackupMetadata?>(null)
    val callLogsMetadata = _callLogsMetadata.asStateFlow()

    private val _calendarMetadata = MutableStateFlow<CalendarBackupMetadata?>(null)
    val calendarMetadata = _calendarMetadata.asStateFlow()

    private val _selectAllContactAccounts = MutableStateFlow(true)
    val selectAllContactAccounts = _selectAllContactAccounts.asStateFlow()

    private val _selectedContactAccountKeys = MutableStateFlow<Set<String>>(emptySet())
    val selectedContactAccountKeys = _selectedContactAccountKeys.asStateFlow()

    private val _availableContactAccounts = MutableStateFlow<List<ContactAccountInfo>>(emptyList())
    val availableContactAccounts = _availableContactAccounts.asStateFlow()

    private val _isLoadingContactAccounts = MutableStateFlow(false)
    val isLoadingContactAccounts = _isLoadingContactAccounts.asStateFlow()

    private val _contactAccountsError = MutableStateFlow<String?>(null)
    val contactAccountsError = _contactAccountsError.asStateFlow()

    private val _selectAllCalendars = MutableStateFlow(true)
    val selectAllCalendars = _selectAllCalendars.asStateFlow()

    private val _selectedCalendarKeys = MutableStateFlow<Set<String>>(emptySet())
    val selectedCalendarKeys = _selectedCalendarKeys.asStateFlow()

    private val _availableCalendars = MutableStateFlow<List<CalendarInfo>>(emptyList())
    val availableCalendars = _availableCalendars.asStateFlow()

    private val _isLoadingCalendars = MutableStateFlow(false)
    val isLoadingCalendars = _isLoadingCalendars.asStateFlow()

    private val _calendarsError = MutableStateFlow<String?>(null)
    val calendarsError = _calendarsError.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _restoreTypes = MutableStateFlow(RestoreTypes())
    val restoreTypes = _restoreTypes.asStateFlow()

    private val _appRestoreTypes = MutableStateFlow<Map<String, RestoreTypes>>(emptyMap())
    val appRestoreTypes = _appRestoreTypes.asStateFlow()

    private val _allowDowngrade = MutableStateFlow(false)
    val allowDowngrade = _allowDowngrade.asStateFlow()
    
    private val _restoreAppsEnabled = MutableStateFlow(true)
    val restoreAppsEnabled = _restoreAppsEnabled.asStateFlow()

    private val _restoreCustomDirectoriesEnabled = MutableStateFlow(false)
    val restoreCustomDirectoriesEnabled = _restoreCustomDirectoriesEnabled.asStateFlow()

    private val _restoreDeviceDataEnabled = MutableStateFlow(false)
    val restoreDeviceDataEnabled = _restoreDeviceDataEnabled.asStateFlow()

    private val _restoreContactsEnabled = MutableStateFlow(false)
    val restoreContactsEnabled = _restoreContactsEnabled.asStateFlow()

    private val _restoreCallLogsEnabled = MutableStateFlow(false)
    val restoreCallLogsEnabled = _restoreCallLogsEnabled.asStateFlow()

    private val _restoreCalendarEnabled = MutableStateFlow(false)
    val restoreCalendarEnabled = _restoreCalendarEnabled.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring = _isRestoring.asStateFlow()

    private val _restoreProgress = MutableStateFlow(OperationProgress())
    val restoreProgress = _restoreProgress.asStateFlow()

    private val _operationBlocked = MutableStateFlow(false)
    val operationBlocked = _operationBlocked.asStateFlow()
    private val _uiEvents = MutableSharedFlow<RestoreUiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<RestoreUiEvent> = _uiEvents.asSharedFlow()

    private var restoreJob: Job? = null

    val hasRoot = rootRepository.hasRoot

    init {
        _restoreTypes.value = preferencesRepository.loadRestoreTypes()
        _allowDowngrade.value = preferencesRepository.loadAllowDowngrade()
        viewModelScope.launch {
            rootRepository.checkRootAccess()
        }
        observeOperationState()
        if (snapshotId.isNotBlank()) {
            loadSnapshotDetails(snapshotId)
        }
    }

    private fun observeOperationState() {
        viewModelScope.launch {
            operationWorkRepository.operationState.collect { state ->
                if (state.operationType == OperationType.RESTORE) {
                    _isRestoring.value = state.isRunning
                    _restoreProgress.value = state.progress
                } else {
                    _isRestoring.value = false
                    _restoreProgress.value = OperationProgress()
                }
            }
        }
    }

    private fun loadSnapshotDetails(snapshotId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _backupDetails.value = emptyList()
            try {
                val selectedRepoKey = repositoriesRepository.selectedRepository.first()
                val repo = selectedRepoKey?.let { repositoriesRepository.getRepositoryByKey(it) }
                val repoPath = repo?.path
                val password = selectedRepoKey?.let { repositoriesRepository.getRepositoryPassword(it) }
                val repoId = repo?.id

                if (repoPath != null && password != null && repoId != null) {
                    val executionEnvironment = repositoriesRepository.getExecutionEnvironmentVariables(selectedRepoKey)
                    val resticOptions = repositoriesRepository.getExecutionResticOptions(selectedRepoKey)

                    val result = resticRepository.getSnapshots(
                        repoPath,
                        password,
                        executionEnvironment,
                        resticOptions
                    )
                    result.fold(
                        onSuccess = { snapshots ->
                            val foundSnapshot = snapshots.find { it.id.startsWith(snapshotId) }
                            _snapshot.value = foundSnapshot
                            if (foundSnapshot != null) {
                                var metadata = metadataRepository.getMetadataForSnapshot(repoId, foundSnapshot.id)
                                
                                if (metadata == null) {
                                    // Attempt to recover metadata from the repository
                                    val lsResult = resticRepository.ls(repoPath, password, foundSnapshot.id, executionEnvironment, resticOptions)
                                    lsResult.fold(
                                        onSuccess = { paths ->
                                            val metadataPath = paths.find { it.endsWith("/restoid.json") }
                                            if (metadataPath != null) {
                                                val dumpResult = resticRepository.dump(repoPath, password, foundSnapshot.id, metadataPath, executionEnvironment, resticOptions)
                                                dumpResult.fold(
                                                    onSuccess = { content ->
                                                        try {
                                                            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                                                            val recoveredMetadata = json.decodeFromString<RestoidMetadata>(content)
                                                            metadataRepository.saveMetadataForSnapshot(repoId, foundSnapshot.id, recoveredMetadata)
                                                            metadata = recoveredMetadata
                                                        } catch (e: Exception) {
                                                            android.util.Log.e("RestoreViewModel", "Failed to parse recovered metadata", e)
                                                        }
                                                    },
                                                    onFailure = { android.util.Log.e("RestoreViewModel", "Failed to dump metadata: ${it.message}") }
                                                )
                                            }
                                        },
                                        onFailure = { android.util.Log.e("RestoreViewModel", "Failed to ls snapshot: ${it.message}") }
                                    )
                                }

                                snapshotMetadata = metadata
                                processSnapshot(foundSnapshot, metadata)
                            } else {
                                _error.value = application.getString(R.string.error_snapshot_not_found)
                            }
                        },
                        onFailure = { _error.value = it.message }
                    )
                } else {
                    _error.value = application.getString(R.string.error_repository_password_or_id_not_found)
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun processSnapshot(snapshot: SnapshotInfo, metadata: RestoidMetadata?) {
        val appMetadataMap = metadata?.apps ?: emptyMap()
        val packageNames = appMetadataMap.keys.toList().filter { it != application.packageName }

        if (packageNames.isNotEmpty()) {
            val appInfos = appInfoRepository.getAppInfoForPackages(packageNames)
            val appInfoMap = appInfos.associateBy { it.packageName }

            val details = appMetadataMap.filterKeys { it != application.packageName }.map { (packageName, appMeta) ->
                val appInfo = appInfoMap[packageName]
                val items = findBackedUpItems(
                    snapshot = snapshot,
                    pkg = packageName,
                    hasPermissionBackup = appMeta.grantedRuntimePermissions.isNotEmpty(),
                    hasSpecialAccessBackup = appMeta.hasSpecialAccessBackup(),
                    hasAccessibilityBackup = appMeta.hasAccessibilityBackup()
                )

                val isInstalled = appInfo != null
                val isDowngrade = if (isInstalled) {
                    appMeta.versionCode < appInfo.versionCode
                } else {
                    false
                }

                val finalAppInfo = appInfo ?: AppInfo(
                    name = appMeta.appName ?: packageName,
                    packageName = packageName,
                    versionName = appMeta.versionName,
                    versionCode = appMeta.versionCode,
                    icon = application.packageManager.defaultActivityIcon,
                    apkPaths = emptyList(),
                    isSelected = true,
                    firstInstallTime = 0L
                )

                BackupDetail(finalAppInfo, items, appMeta.versionName, appMeta.versionCode, appMeta.size, isDowngrade, isInstalled)
            }

            _backupDetails.value = sortBackupDetails(details, _sortOrder.value)
            _appRestoreTypes.value = details.associate { detail ->
                detail.appInfo.packageName to (_appRestoreTypes.value[detail.appInfo.packageName] ?: _restoreTypes.value)
            }
        } else {
            _backupDetails.value = emptyList()
            _appRestoreTypes.value = emptyMap()
        }

        _customDirectoryMetadata.value = metadata?.customDirectories ?: emptyMap()
        _customDirectories.value = _customDirectoryMetadata.value.mapValues { true }
        if (_customDirectories.value.isNotEmpty()) {
            _restoreCustomDirectoriesEnabled.value = true
        }

        val contactsMeta = metadata?.deviceData?.contacts
        val callLogsMeta = metadata?.deviceData?.callLogs
        val calendarMeta = metadata?.deviceData?.calendar
        _contactsMetadata.value = contactsMeta
        _callLogsMetadata.value = callLogsMeta
        _calendarMetadata.value = calendarMeta
        _restoreContactsEnabled.value = contactsMeta != null
        _restoreCallLogsEnabled.value = callLogsMeta != null
        _restoreCalendarEnabled.value = calendarMeta != null
        _restoreDeviceDataEnabled.value = contactsMeta != null || callLogsMeta != null || calendarMeta != null

        contactAccountsLoadRequestId++
        calendarsLoadRequestId++
        val localDisplayName = application.getString(R.string.device_data_contacts_local_account)

        _selectAllContactAccounts.value = true
        _selectedContactAccountKeys.value = emptySet()
        _isLoadingContactAccounts.value = false
        _contactAccountsError.value = null
        val savedContactAccounts = contactsMeta?.accounts
        if (contactsMeta != null && savedContactAccounts != null) {
            _availableContactAccounts.value = savedContactAccounts.map { it.toContactAccountInfo(localDisplayName) }
            contactAccountsLoaded = true
        } else {
            _availableContactAccounts.value = emptyList()
            contactAccountsLoaded = false
        }

        _selectAllCalendars.value = true
        _selectedCalendarKeys.value = emptySet()
        _isLoadingCalendars.value = false
        _calendarsError.value = null
        val savedCalendars = calendarMeta?.calendars
        if (calendarMeta != null && savedCalendars != null) {
            _availableCalendars.value = savedCalendars.map {
                it.toCalendarInfo(CalendarBackupRepository.DEFAULT_CALENDAR_COLOR)
            }
            calendarsLoaded = true
        } else {
            _availableCalendars.value = emptyList()
            calendarsLoaded = false
        }
    }

    fun setSortOrder(order: AppSortOrder) {
        if (_sortOrder.value == order) return

        _sortOrder.value = order
        _backupDetails.update { details -> sortBackupDetails(details, order) }
    }

    fun setRestoreAppsEnabled(enabled: Boolean) {
        _restoreAppsEnabled.value = enabled
    }

    fun setRestoreCustomDirectoriesEnabled(enabled: Boolean) {
        _restoreCustomDirectoriesEnabled.value = enabled
    }

    fun setRestoreDeviceDataEnabled(enabled: Boolean) {
        _restoreDeviceDataEnabled.value = enabled
    }

    fun setRestoreContactsEnabled(enabled: Boolean) {
        _restoreContactsEnabled.value = enabled
    }

    fun setRestoreCallLogsEnabled(enabled: Boolean) {
        _restoreCallLogsEnabled.value = enabled
    }

    fun setRestoreCalendarEnabled(enabled: Boolean) {
        _restoreCalendarEnabled.value = enabled
    }

    fun loadSnapshotContactAccounts(force: Boolean = false) {
        val meta = _contactsMetadata.value
        if (meta == null) {
            _availableContactAccounts.value = emptyList()
            _isLoadingContactAccounts.value = false
            _contactAccountsError.value = null
            contactAccountsLoaded = false
            return
        }

        val localDisplayName = application.getString(R.string.device_data_contacts_local_account)
        val cachedSummaries = meta.accounts
        if (!force && cachedSummaries != null) {
            if (!contactAccountsLoaded || _availableContactAccounts.value.isEmpty()) {
                _availableContactAccounts.value = cachedSummaries.map { it.toContactAccountInfo(localDisplayName) }
                contactAccountsLoaded = true
            }
            _contactAccountsError.value = null
            return
        }

        if (!force && (_isLoadingContactAccounts.value || contactAccountsLoaded)) return

        val currentSnapshot = _snapshot.value
        if (currentSnapshot == null) {
            _contactAccountsError.value = application.getString(R.string.restore_device_data_load_failed)
            return
        }

        val requestId = ++contactAccountsLoadRequestId
        _isLoadingContactAccounts.value = true
        _contactAccountsError.value = null

        viewModelScope.launch {
            try {
                // Backwards compatibility: legacy snapshots do not record contact account summaries in restoid.json,
                // so inspect contacts.json via `restic dump --no-lock` on demand and cache summaries in MetadataRepository.
                val selectedRepoKey = repositoriesRepository.selectedRepository.first()
                val repo = selectedRepoKey?.let { repositoriesRepository.getRepositoryByKey(it) }
                val repoPath = repo?.path
                val repoId = repo?.id
                val password = selectedRepoKey?.let { repositoriesRepository.getRepositoryPassword(it) }
                if (repoPath == null || repoId == null || password == null) {
                    throw IllegalStateException(application.getString(R.string.error_repository_password_or_id_not_found))
                }
                val directPath = "${meta.path.trimEnd('/')}/${ContactsBackupRepository.CONTACTS_FILE_NAME}"
                val (content, resolvedDirPath) = withContext(Dispatchers.IO) {
                    val executionEnvironment = repositoriesRepository.getExecutionEnvironmentVariables(selectedRepoKey)
                    val resticOptions = repositoriesRepository.getExecutionResticOptions(selectedRepoKey)
                    val directDump = resticRepository.dump(
                        repoPath = repoPath,
                        password = password,
                        snapshotId = currentSnapshot.id,
                        filePath = directPath,
                        environmentVariables = executionEnvironment,
                        resticOptions = resticOptions,
                        noLock = true
                    )
                    directDump.fold(
                        onSuccess = { dumped -> dumped to null },
                        onFailure = { directError ->
                            val paths = resticRepository.ls(
                                repoPath = repoPath,
                                password = password,
                                snapshotId = currentSnapshot.id,
                                environmentVariables = executionEnvironment,
                                resticOptions = resticOptions,
                                noLock = true
                            ).getOrElse { throw directError }
                            val fallbackPath = paths.find {
                                it.endsWith("/${ContactsBackupRepository.CONTACTS_RELATIVE_STAGING_PATH}/${ContactsBackupRepository.CONTACTS_FILE_NAME}")
                            } ?: paths.find {
                                it.endsWith("/${ContactsBackupRepository.CONTACTS_FILE_NAME}")
                            } ?: throw directError
                            val dumped = resticRepository.dump(
                                repoPath = repoPath,
                                password = password,
                                snapshotId = currentSnapshot.id,
                                filePath = fallbackPath,
                                environmentVariables = executionEnvironment,
                                resticOptions = resticOptions,
                                noLock = true
                            ).getOrThrow()
                            dumped to parentDirectoryPath(fallbackPath)
                        }
                    )
                }

                val summaries = withContext(Dispatchers.Default) {
                    contactsBackupRepository.parseContactAccountsFromArchiveJson(content)
                }
                if (contactAccountsLoadRequestId != requestId) return@launch

                metadataUpdateMutex.withLock {
                    if (contactAccountsLoadRequestId != requestId) return@withLock
                    val currentMeta = snapshotMetadata
                    if (currentMeta?.deviceData?.contacts != null) {
                        val updatedContactsMeta = currentMeta.deviceData.contacts.copy(
                            path = resolvedDirPath ?: currentMeta.deviceData.contacts.path,
                            accounts = summaries
                        )
                        val updatedMetadata = currentMeta.copy(
                            deviceData = currentMeta.deviceData.copy(contacts = updatedContactsMeta)
                        )
                        snapshotMetadata = updatedMetadata
                        _contactsMetadata.value = updatedContactsMeta
                        metadataRepository.saveMetadataForSnapshot(repoId, currentSnapshot.id, updatedMetadata)
                    } else {
                        _contactsMetadata.update { current ->
                            current?.copy(
                                path = resolvedDirPath ?: current.path,
                                accounts = summaries
                            )
                        }
                    }
                }

                if (contactAccountsLoadRequestId == requestId) {
                    _availableContactAccounts.value = summaries.map { it.toContactAccountInfo(localDisplayName) }
                    contactAccountsLoaded = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("RestoreViewModel", "Failed to load contact accounts from snapshot", e)
                if (contactAccountsLoadRequestId == requestId) {
                    _contactAccountsError.value = e.message?.takeIf { it.isNotBlank() }
                        ?: application.getString(R.string.restore_device_data_load_failed)
                }
            } finally {
                if (contactAccountsLoadRequestId == requestId) {
                    _isLoadingContactAccounts.value = false
                }
            }
        }
    }

    fun loadSnapshotCalendars(force: Boolean = false) {
        val meta = _calendarMetadata.value
        if (meta == null) {
            _availableCalendars.value = emptyList()
            _isLoadingCalendars.value = false
            _calendarsError.value = null
            calendarsLoaded = false
            return
        }

        val cachedSummaries = meta.calendars
        if (!force && cachedSummaries != null) {
            if (!calendarsLoaded || _availableCalendars.value.isEmpty()) {
                _availableCalendars.value = cachedSummaries.map {
                    it.toCalendarInfo(CalendarBackupRepository.DEFAULT_CALENDAR_COLOR)
                }
                calendarsLoaded = true
            }
            _calendarsError.value = null
            return
        }

        if (!force && (_isLoadingCalendars.value || calendarsLoaded)) return

        val currentSnapshot = _snapshot.value
        if (currentSnapshot == null) {
            _calendarsError.value = application.getString(R.string.restore_device_data_load_failed)
            return
        }

        val requestId = ++calendarsLoadRequestId
        _isLoadingCalendars.value = true
        _calendarsError.value = null

        viewModelScope.launch {
            try {
                // Backwards compatibility: legacy snapshots do not record calendar summaries in restoid.json,
                // so inspect calendar.json via `restic dump --no-lock` on demand and cache summaries in MetadataRepository.
                val selectedRepoKey = repositoriesRepository.selectedRepository.first()
                val repo = selectedRepoKey?.let { repositoriesRepository.getRepositoryByKey(it) }
                val repoPath = repo?.path
                val repoId = repo?.id
                val password = selectedRepoKey?.let { repositoriesRepository.getRepositoryPassword(it) }
                if (repoPath == null || repoId == null || password == null) {
                    throw IllegalStateException(application.getString(R.string.error_repository_password_or_id_not_found))
                }

                val directPath = "${meta.path.trimEnd('/')}/${CalendarBackupRepository.CALENDAR_FILE_NAME}"
                val (content, resolvedDirPath) = withContext(Dispatchers.IO) {
                    val executionEnvironment = repositoriesRepository.getExecutionEnvironmentVariables(selectedRepoKey)
                    val resticOptions = repositoriesRepository.getExecutionResticOptions(selectedRepoKey)
                    val directDump = resticRepository.dump(
                        repoPath = repoPath,
                        password = password,
                        snapshotId = currentSnapshot.id,
                        filePath = directPath,
                        environmentVariables = executionEnvironment,
                        resticOptions = resticOptions,
                        noLock = true
                    )
                    directDump.fold(
                        onSuccess = { dumped -> dumped to null },
                        onFailure = { directError ->
                            val paths = resticRepository.ls(
                                repoPath = repoPath,
                                password = password,
                                snapshotId = currentSnapshot.id,
                                environmentVariables = executionEnvironment,
                                resticOptions = resticOptions,
                                noLock = true
                            ).getOrElse { throw directError }
                            val fallbackPath = paths.find {
                                it.endsWith("/${CalendarBackupRepository.CALENDAR_RELATIVE_STAGING_PATH}/${CalendarBackupRepository.CALENDAR_FILE_NAME}")
                            } ?: paths.find {
                                it.endsWith("/${CalendarBackupRepository.CALENDAR_FILE_NAME}")
                            } ?: throw directError
                            val dumped = resticRepository.dump(
                                repoPath = repoPath,
                                password = password,
                                snapshotId = currentSnapshot.id,
                                filePath = fallbackPath,
                                environmentVariables = executionEnvironment,
                                resticOptions = resticOptions,
                                noLock = true
                            ).getOrThrow()
                            dumped to parentDirectoryPath(fallbackPath)
                        }
                    )
                }

                val summaries = withContext(Dispatchers.Default) {
                    calendarBackupRepository.parseCalendarsFromArchiveJson(content)
                }
                if (calendarsLoadRequestId != requestId) return@launch

                metadataUpdateMutex.withLock {
                    if (calendarsLoadRequestId != requestId) return@withLock
                    val currentMeta = snapshotMetadata
                    if (currentMeta?.deviceData?.calendar != null) {
                        val updatedCalendarMeta = currentMeta.deviceData.calendar.copy(
                            path = resolvedDirPath ?: currentMeta.deviceData.calendar.path,
                            calendars = summaries
                        )
                        val updatedMetadata = currentMeta.copy(
                            deviceData = currentMeta.deviceData.copy(calendar = updatedCalendarMeta)
                        )
                        snapshotMetadata = updatedMetadata
                        _calendarMetadata.value = updatedCalendarMeta
                        metadataRepository.saveMetadataForSnapshot(repoId, currentSnapshot.id, updatedMetadata)
                    } else {
                        _calendarMetadata.update { current ->
                            current?.copy(
                                path = resolvedDirPath ?: current.path,
                                calendars = summaries
                            )
                        }
                    }
                }

                if (calendarsLoadRequestId == requestId) {
                    _availableCalendars.value = summaries.map {
                        it.toCalendarInfo(CalendarBackupRepository.DEFAULT_CALENDAR_COLOR)
                    }
                    calendarsLoaded = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("RestoreViewModel", "Failed to load calendars from snapshot", e)
                if (calendarsLoadRequestId == requestId) {
                    _calendarsError.value = e.message?.takeIf { it.isNotBlank() }
                        ?: application.getString(R.string.restore_device_data_load_failed)
                }
            } finally {
                if (calendarsLoadRequestId == requestId) {
                    _isLoadingCalendars.value = false
                }
            }
        }
    }

    private fun parentDirectoryPath(filePath: String): String? {
        val slashIndex = filePath.lastIndexOf('/')
        return when {
            slashIndex < 0 -> null
            slashIndex == 0 -> "/"
            else -> filePath.substring(0, slashIndex)
        }
    }

    fun toggleAllContactAccounts() {
        val available = _availableContactAccounts.value
        val allSelected = _selectAllContactAccounts.value ||
            (available.isNotEmpty() && available.all { it.selectionKey in _selectedContactAccountKeys.value })
        _selectAllContactAccounts.value = !allSelected
        _selectedContactAccountKeys.value = emptySet()
    }

    fun toggleContactAccount(selectionKey: String) {
        val allKeys = _availableContactAccounts.value.map { it.selectionKey }.toSet()
        val currentKeys = if (_selectAllContactAccounts.value) allKeys else _selectedContactAccountKeys.value
        val updatedKeys = if (selectionKey in currentKeys) currentKeys - selectionKey else currentKeys + selectionKey
        val newSelectAll = allKeys.isNotEmpty() && allKeys.all { it in updatedKeys }
        _selectAllContactAccounts.value = newSelectAll
        _selectedContactAccountKeys.value = if (newSelectAll) emptySet() else updatedKeys
    }

    fun toggleAllCalendars() {
        val available = _availableCalendars.value
        val allSelected = _selectAllCalendars.value ||
            (available.isNotEmpty() && available.all { it.selectionKey in _selectedCalendarKeys.value })
        _selectAllCalendars.value = !allSelected
        _selectedCalendarKeys.value = emptySet()
    }

    fun toggleCalendar(selectionKey: String) {
        val allKeys = _availableCalendars.value.map { it.selectionKey }.toSet()
        val currentKeys = if (_selectAllCalendars.value) allKeys else _selectedCalendarKeys.value
        val updatedKeys = if (selectionKey in currentKeys) currentKeys - selectionKey else currentKeys + selectionKey
        val newSelectAll = allKeys.isNotEmpty() && allKeys.all { it in updatedKeys }
        _selectAllCalendars.value = newSelectAll
        _selectedCalendarKeys.value = if (newSelectAll) emptySet() else updatedKeys
    }

    private fun findBackedUpItems(
        snapshot: SnapshotInfo,
        pkg: String,
        hasPermissionBackup: Boolean,
        hasSpecialAccessBackup: Boolean,
        hasAccessibilityBackup: Boolean
    ): List<String> {
        val items = mutableListOf<String>()
        snapshot.paths.forEach { path ->
            when {
                (path.startsWith("/data/app/") && path.contains("/${pkg}-")) -> if (!items.contains(application.getString(R.string.backup_type_apk))) items.add(application.getString(R.string.backup_type_apk))
                path == "/data/data/$pkg" || path.matches(Regex("^/data/user/\\d+/${Regex.escape(pkg)}$")) -> if (!items.contains(application.getString(R.string.backup_type_data))) items.add(application.getString(R.string.backup_type_data))
                path.matches(Regex("^/data/user_de/\\d+/${Regex.escape(pkg)}$")) -> if (!items.contains(application.getString(R.string.backup_type_device_protected_data))) items.add(application.getString(R.string.backup_type_device_protected_data))
                path.matches(Regex("^/storage/emulated/\\d+/Android/data/${Regex.escape(pkg)}$")) -> if (!items.contains(application.getString(R.string.backup_type_external_data))) items.add(application.getString(R.string.backup_type_external_data))
                path.matches(Regex("^/storage/emulated/\\d+/Android/obb/${Regex.escape(pkg)}$")) -> if (!items.contains(application.getString(R.string.backup_item_obb))) items.add(application.getString(R.string.backup_item_obb))
                path.matches(Regex("^/storage/emulated/\\d+/Android/media/${Regex.escape(pkg)}$")) -> if (!items.contains(application.getString(R.string.backup_item_media))) items.add(application.getString(R.string.backup_item_media))
            }
        }
        if (hasPermissionBackup && !items.contains(application.getString(R.string.backup_item_permissions))) {
            items.add(application.getString(R.string.backup_item_permissions))
        }
        if (hasSpecialAccessBackup && !items.contains(application.getString(R.string.backup_item_special_access))) {
            items.add(application.getString(R.string.backup_item_special_access))
        }
        if (hasAccessibilityBackup && !items.contains(application.getString(R.string.backup_item_accessibility))) {
            items.add(application.getString(R.string.backup_item_accessibility))
        }
        return if (items.isNotEmpty()) items else listOf(application.getString(R.string.backup_item_unknown))
    }

    fun startRestore() {
        if (_isRestoring.value || restoreJob?.isActive == true) return

        restoreJob = viewModelScope.launch {
            val resolvedHasRoot = rootRepository.checkRootAccess()
            preferencesRepository.saveRestoreTypes(_restoreTypes.value)
            preferencesRepository.saveAllowDowngrade(_allowDowngrade.value)

            val selectedRepoKey = repositoriesRepository.selectedRepository.value
            val selectedRepository = selectedRepoKey?.let { repositoriesRepository.getRepositoryByKey(it) }
            val selectedRepoPath = selectedRepository?.path
            val currentSnapshot = _snapshot.value

            val selectedApps = if (_restoreAppsEnabled.value && resolvedHasRoot) {
                _backupDetails.value.filter { it.appInfo.isSelected && (_allowDowngrade.value || !it.isDowngrade) }
            } else {
                emptyList()
            }

            val selectedCustomDirectories = if (_restoreCustomDirectoriesEnabled.value) {
                _customDirectories.value.filter { it.value }.keys.toList()
            } else {
                emptyList()
            }

            val restoreContacts = _restoreDeviceDataEnabled.value && _contactsMetadata.value != null && _restoreContactsEnabled.value
            val restoreCallLogs = _restoreDeviceDataEnabled.value && _callLogsMetadata.value != null && _restoreCallLogsEnabled.value
            val restoreCalendar = _restoreDeviceDataEnabled.value && _calendarMetadata.value != null && _restoreCalendarEnabled.value

            fun failPreflight(error: String, summary: String = error) {
                _restoreProgress.value = OperationProgress(
                    isFinished = true,
                    error = error,
                    finalSummary = summary
                )
                _uiEvents.tryEmit(RestoreUiEvent.ShowError(error))
            }

            if (!resticBinaryManager.isInstalled || selectedRepoPath == null || currentSnapshot == null) {
                failPreflight(application.getString(R.string.restore_error_preflight_failed))
                return@launch
            }

            if (selectedApps.isEmpty() && selectedCustomDirectories.isEmpty() && !restoreContacts && !restoreCallLogs && !restoreCalendar) {
                failPreflight(application.getString(R.string.restore_error_no_items_selected))
                return@launch
            }

            if (restoreContacts && !_selectAllContactAccounts.value && _selectedContactAccountKeys.value.isEmpty()) {
                failPreflight(
                    application.getString(R.string.restore_error_contacts_none_selected),
                    application.getString(R.string.restore_summary_contacts_none_selected)
                )
                return@launch
            }

            if (restoreCalendar && !_selectAllCalendars.value && _selectedCalendarKeys.value.isEmpty()) {
                failPreflight(
                    application.getString(R.string.restore_error_calendar_none_selected),
                    application.getString(R.string.restore_summary_calendar_none_selected)
                )
                return@launch
            }

            val missingPermissions = buildList {
                if (restoreContacts) {
                    val contactsRepo = io.github.hddq.restoid.data.ContactsBackupRepository(application)
                    if (!contactsRepo.hasReadContactsPermission()) add(android.Manifest.permission.READ_CONTACTS)
                    if (!contactsRepo.hasWriteContactsPermission()) add(android.Manifest.permission.WRITE_CONTACTS)
                }
                if (restoreCallLogs) {
                    val callLogsRepo = io.github.hddq.restoid.data.CallLogsBackupRepository(application)
                    if (!callLogsRepo.hasReadCallLogPermission()) add(android.Manifest.permission.READ_CALL_LOG)
                    if (!callLogsRepo.hasWriteCallLogPermission()) add(android.Manifest.permission.WRITE_CALL_LOG)
                }
                if (restoreCalendar) {
                    val calendarRepo = io.github.hddq.restoid.data.CalendarBackupRepository(application)
                    if (!calendarRepo.hasReadCalendarPermission()) add(android.Manifest.permission.READ_CALENDAR)
                    if (!calendarRepo.hasWriteCalendarPermission()) add(android.Manifest.permission.WRITE_CALENDAR)
                }
            }
            if (missingPermissions.isNotEmpty()) {
                _uiEvents.tryEmit(RestoreUiEvent.RequestDeviceDataPermissions(missingPermissions))
                return@launch
            }

            if (selectedApps.isNotEmpty() && selectedApps.none { effectiveRestoreTypes(it.appInfo.packageName).anyEnabled() }) {
                failPreflight(application.getString(R.string.restore_error_no_restore_types_selected))
                return@launch
            }

            if (selectedRepository.backendType == RepositoryBackendType.SFTP && !repositoriesRepository.hasSftpCredentials(selectedRepoKey)) {
                failPreflight(
                    application.getString(R.string.error_sftp_password_not_found_for_repository),
                    application.getString(R.string.summary_sftp_password_not_found)
                )
                return@launch
            }

            if (
                selectedRepository.backendType == RepositoryBackendType.REST &&
                selectedRepository.restAuthRequired &&
                !repositoriesRepository.hasRestCredentials(selectedRepoKey)
            ) {
                failPreflight(
                    application.getString(R.string.error_rest_credentials_not_found_for_repository),
                    application.getString(R.string.summary_rest_credentials_not_found)
                )
                return@launch
            }

            if (
                selectedRepository.backendType == RepositoryBackendType.S3 &&
                selectedRepository.s3AuthRequired &&
                !repositoriesRepository.hasS3Credentials(selectedRepoKey)
            ) {
                failPreflight(
                    application.getString(R.string.error_s3_credentials_not_found_for_repository),
                    application.getString(R.string.summary_s3_credentials_not_found)
                )
                return@launch
            }

            val password = repositoriesRepository.getRepositoryPassword(selectedRepoKey)
            if (password == null) {
                failPreflight(
                    application.getString(R.string.restore_error_password_not_found),
                    application.getString(R.string.summary_password_not_found)
                )
                return@launch
            }

            val request = RestoreWorkRequest(
                repositoryKey = selectedRepoKey,
                snapshotId = currentSnapshot.id,
                restoreTypes = _restoreTypes.value.toSelection(),
                allowDowngrade = _allowDowngrade.value,
                selectedApps = selectedApps.map {
                    RestoreAppSelection(
                        packageName = it.appInfo.packageName,
                        appName = it.appInfo.name
                    )
                },
                appRestoreTypes = selectedApps.associate { it.appInfo.packageName to effectiveRestoreTypes(it.appInfo.packageName).toSelection() },
                selectedCustomDirectories = selectedCustomDirectories,
                deviceData = DeviceDataRestoreSelection(
                    contacts = restoreContacts,
                    selectAllContactAccounts = _selectAllContactAccounts.value,
                    selectedContactAccountKeys = if (_selectAllContactAccounts.value) emptySet() else _selectedContactAccountKeys.value,
                    callLogs = restoreCallLogs,
                    calendar = restoreCalendar,
                    selectAllCalendars = _selectAllCalendars.value,
                    selectedCalendarKeys = if (_selectAllCalendars.value) emptySet() else _selectedCalendarKeys.value
                )
            )

            val enqueued = withContext(Dispatchers.IO) {
                operationWorkRepository.enqueueRestore(request)
            }
            if (enqueued) {
                _uiEvents.tryEmit(RestoreUiEvent.NavigateToOperationProgress)
            } else {
                _operationBlocked.value = true
                _restoreProgress.value = OperationProgress(
                    isFinished = true,
                    error = application.getString(R.string.error_operation_already_running),
                    finalSummary = application.getString(R.string.summary_operation_already_running)
                )
            }
        }
    }

    fun onDone() {
        operationWorkRepository.clearFinished(OperationType.RESTORE)
        _restoreProgress.value = OperationProgress()
    }

    fun consumeOperationBlocked() {
        _operationBlocked.value = false
    }

    fun toggleRestoreAppSelection(packageName: String) {
        _appRestoreTypes.update { it.ensurePackage(packageName, _restoreTypes.value) }
        _backupDetails.update { currentDetails ->
            currentDetails.map { detail ->
                if (detail.appInfo.packageName == packageName) {
                    if (_allowDowngrade.value || !detail.isDowngrade) {
                        detail.copy(appInfo = detail.appInfo.copy(isSelected = !detail.appInfo.isSelected))
                    } else {
                        detail
                    }
                } else {
                    detail
                }
            }
        }
    }

    fun toggleAllRestoreSelection() {
        _backupDetails.update { currentDetails ->
            val selectableDetails = currentDetails.filter { _allowDowngrade.value || !it.isDowngrade }
            val shouldSelectAll = selectableDetails.any { !it.appInfo.isSelected }
            _appRestoreTypes.update { currentTypes ->
                selectableDetails.fold(currentTypes) { acc, detail ->
                    acc.ensurePackage(detail.appInfo.packageName, _restoreTypes.value)
                }
            }
            currentDetails.map { detail ->
                val canBeSelected = _allowDowngrade.value || !detail.isDowngrade
                detail.copy(appInfo = detail.appInfo.copy(isSelected = shouldSelectAll && canBeSelected))
            }
        }
    }

    fun toggleCustomDirectory(path: String) {
        _customDirectories.update { current ->
            current.toMutableMap().apply {
                this[path] = !(this[path] ?: true)
            }
        }
    }

    fun toggleAllCustomDirectoriesSelection() {
        _customDirectories.update { current ->
            val shouldSelectAll = current.values.any { !it }
            current.mapValues { shouldSelectAll }
        }
    }

    fun setAllowDowngrade(value: Boolean) {
        _allowDowngrade.value = value
        if (!value) {
            _backupDetails.update { currentDetails ->
                currentDetails.map { detail ->
                    if (detail.isDowngrade) detail.copy(appInfo = detail.appInfo.copy(isSelected = false)) else detail
                }
            }
        }
    }

    fun setRestoreApk(value: Boolean) = _restoreTypes.update { it.copy(apk = value) }
    fun setRestoreData(value: Boolean) = _restoreTypes.update { it.copy(data = value) }
    fun setRestoreDeviceProtectedData(value: Boolean) = _restoreTypes.update { it.copy(deviceProtectedData = value) }
    fun setRestoreExternalData(value: Boolean) = _restoreTypes.update { it.copy(externalData = value) }
    fun setRestoreObb(value: Boolean) = _restoreTypes.update { it.copy(obb = value) }
    fun setRestoreMedia(value: Boolean) = _restoreTypes.update { it.copy(media = value) }

    fun setAppRestoreTypes(packageName: String, restoreTypes: RestoreTypes) {
        _appRestoreTypes.update { it + (packageName to restoreTypes) }
    }

    fun setSelectedAppsRestoreTypes(restoreTypes: RestoreTypes) {
        val selectedPackageNames = _backupDetails.value
            .filter { it.appInfo.isSelected && (_allowDowngrade.value || !it.isDowngrade) }
            .map { it.appInfo.packageName }
        _restoreTypes.value = restoreTypes
        _appRestoreTypes.update { it + selectedPackageNames.associateWith { restoreTypes } }
    }

    private fun effectiveRestoreTypes(packageName: String): RestoreTypes {
        return _appRestoreTypes.value[packageName] ?: _restoreTypes.value
    }

    private fun Map<String, RestoreTypes>.ensurePackage(packageName: String, defaultTypes: RestoreTypes): Map<String, RestoreTypes> {
        return if (containsKey(packageName)) this else this + (packageName to defaultTypes)
    }
}
