package io.github.hddq.restoid.ui.screens

import android.Manifest
import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import io.github.hddq.restoid.R
import io.github.hddq.restoid.RestoidApplication
import io.github.hddq.restoid.model.BackupDetail
import io.github.hddq.restoid.model.CalendarBackupMetadata
import io.github.hddq.restoid.model.CalendarInfo
import io.github.hddq.restoid.model.CallLogsBackupMetadata
import io.github.hddq.restoid.model.ContactAccountInfo
import io.github.hddq.restoid.model.ContactsBackupMetadata
import io.github.hddq.restoid.ui.components.ExpressiveCardGroup
import io.github.hddq.restoid.ui.components.ExpressiveSectionHeader
import io.github.hddq.restoid.ui.components.ExpressiveSegmentedSurface
import io.github.hddq.restoid.ui.restore.RestoreTypes
import io.github.hddq.restoid.ui.restore.RestoreUiEvent
import io.github.hddq.restoid.ui.restore.RestoreViewModel
import io.github.hddq.restoid.ui.shared.CalendarOptionsBottomSheet
import io.github.hddq.restoid.ui.shared.ContactsOptionsBottomSheet
import io.github.hddq.restoid.ui.shared.TaskRow
import io.github.hddq.restoid.ui.shared.rememberPermissionsMap
import io.github.hddq.restoid.ui.shared.rememberPermissionsState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RestoreScreen(
    viewModel: RestoreViewModel,
    onNavigateToOperationProgress: () -> Unit,
    onNavigateToAppsConfig: () -> Unit,
    onNavigateToCustomDirectoriesConfig: () -> Unit,
    onNavigateToDeviceDataConfig: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val application = LocalContext.current.applicationContext as RestoidApplication
    val backupDetails by viewModel.backupDetails.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val allowDowngrade by viewModel.allowDowngrade.collectAsState()
    val operationBlocked by viewModel.operationBlocked.collectAsState()
    val customDirectories by viewModel.customDirectories.collectAsState()
    val contactsMetadata by viewModel.contactsMetadata.collectAsStateWithLifecycle()
    val callLogsMetadata by viewModel.callLogsMetadata.collectAsStateWithLifecycle()
    val calendarMetadata by viewModel.calendarMetadata.collectAsStateWithLifecycle()
    val selectAllContactAccounts by viewModel.selectAllContactAccounts.collectAsStateWithLifecycle()
    val selectedContactAccountKeys by viewModel.selectedContactAccountKeys.collectAsStateWithLifecycle()
    val availableContactAccounts by viewModel.availableContactAccounts.collectAsStateWithLifecycle()
    val selectAllCalendars by viewModel.selectAllCalendars.collectAsStateWithLifecycle()
    val selectedCalendarKeys by viewModel.selectedCalendarKeys.collectAsStateWithLifecycle()
    val availableCalendars by viewModel.availableCalendars.collectAsStateWithLifecycle()

    val restoreAppsEnabled by viewModel.restoreAppsEnabled.collectAsState()
    val restoreCustomDirectoriesEnabled by viewModel.restoreCustomDirectoriesEnabled.collectAsState()
    val restoreDeviceDataEnabled by viewModel.restoreDeviceDataEnabled.collectAsStateWithLifecycle()
    val restoreContactsEnabled by viewModel.restoreContactsEnabled.collectAsStateWithLifecycle()
    val restoreCallLogsEnabled by viewModel.restoreCallLogsEnabled.collectAsStateWithLifecycle()
    val restoreCalendarEnabled by viewModel.restoreCalendarEnabled.collectAsStateWithLifecycle()
    val restoreTypes by viewModel.restoreTypes.collectAsState()
    val appRestoreTypes by viewModel.appRestoreTypes.collectAsState()

    val context = LocalContext.current
    val contactsWritePermissionError = stringResource(R.string.error_contacts_write_permission_required)
    val callLogsWritePermissionError = stringResource(R.string.error_call_logs_write_permission_required)
    val calendarWritePermissionError = stringResource(R.string.error_calendar_write_permission_required)
    var pendingStartRestore by remember { mutableStateOf(false) }
    val (deviceDataPermissions, requestDeviceDataPermissions) = rememberPermissionsMap(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG,
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
        onResult = { statuses ->
            if (pendingStartRestore) {
                pendingStartRestore = false
                val needsContacts = restoreDeviceDataEnabled && contactsMetadata != null && restoreContactsEnabled
                val needsCallLogs = restoreDeviceDataEnabled && callLogsMetadata != null && restoreCallLogsEnabled
                val needsCalendar = restoreDeviceDataEnabled && calendarMetadata != null && restoreCalendarEnabled
                val contactsOk = !needsContacts || (
                    statuses[Manifest.permission.READ_CONTACTS] == true &&
                        statuses[Manifest.permission.WRITE_CONTACTS] == true
                    )
                val callLogsOk = !needsCallLogs || (
                    statuses[Manifest.permission.READ_CALL_LOG] == true &&
                        statuses[Manifest.permission.WRITE_CALL_LOG] == true
                    )
                val calendarOk = !needsCalendar || (
                    statuses[Manifest.permission.READ_CALENDAR] == true &&
                        statuses[Manifest.permission.WRITE_CALENDAR] == true
                    )
                when {
                    contactsOk && callLogsOk && calendarOk -> viewModel.startRestore()
                    !contactsOk -> Toast.makeText(context, contactsWritePermissionError, Toast.LENGTH_LONG).show()
                    !callLogsOk -> Toast.makeText(context, callLogsWritePermissionError, Toast.LENGTH_LONG).show()
                    else -> Toast.makeText(context, calendarWritePermissionError, Toast.LENGTH_LONG).show()
                }
            }
        }
    )
    val hasContactsPermissions =
        deviceDataPermissions[Manifest.permission.READ_CONTACTS] == true &&
            deviceDataPermissions[Manifest.permission.WRITE_CONTACTS] == true
    val hasCallLogsPermissions =
        deviceDataPermissions[Manifest.permission.READ_CALL_LOG] == true &&
            deviceDataPermissions[Manifest.permission.WRITE_CALL_LOG] == true
    val hasCalendarPermissions =
        deviceDataPermissions[Manifest.permission.READ_CALENDAR] == true &&
            deviceDataPermissions[Manifest.permission.WRITE_CALENDAR] == true

    LaunchedEffect(operationBlocked) {
        if (operationBlocked) {
            Toast.makeText(application, application.getString(R.string.error_operation_already_running), Toast.LENGTH_SHORT).show()
            viewModel.consumeOperationBlocked()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                RestoreUiEvent.NavigateToOperationProgress -> onNavigateToOperationProgress()
                is RestoreUiEvent.RequestDeviceDataPermissions -> {
                    pendingStartRestore = true
                    requestDeviceDataPermissions(event.permissions.toTypedArray())
                }
                is RestoreUiEvent.ShowError -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 88.dp + contentPadding.calculateBottomPadding(),
            top = 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            val hasRoot by viewModel.hasRoot.collectAsState()
            val selectableBackupDetails = backupDetails.filter { allowDowngrade || !it.isDowngrade }
            val customDirsList = customDirectories.map { io.github.hddq.restoid.model.CustomDirectory(it.key, it.value) }
            val hasContactsInSnapshot = contactsMetadata != null
            val hasCallLogsInSnapshot = callLogsMetadata != null
            val hasCalendarInSnapshot = calendarMetadata != null
            val hasDeviceDataInSnapshot = hasContactsInSnapshot || hasCallLogsInSnapshot || hasCalendarInSnapshot
            val contactsActive = hasContactsInSnapshot && restoreContactsEnabled
            val callLogsActive = hasCallLogsInSnapshot && restoreCallLogsEnabled
            val calendarActive = hasCalendarInSnapshot && restoreCalendarEnabled
            val missingContacts = contactsActive && !hasContactsPermissions
            val missingCallLogs = callLogsActive && !hasCallLogsPermissions
            val missingCalendar = calendarActive && !hasCalendarPermissions
            val missingPermissionsCount =
                (if (missingContacts) 1 else 0) +
                    (if (missingCallLogs) 1 else 0) +
                    (if (missingCalendar) 1 else 0)
            val deviceDataSubtitle = when {
                !hasDeviceDataInSnapshot -> stringResource(R.string.device_data_not_in_snapshot)
                !contactsActive && !callLogsActive && !calendarActive -> stringResource(R.string.device_data_none_selected)
                restoreDeviceDataEnabled && missingPermissionsCount > 1 -> stringResource(R.string.device_data_permissions_required)
                restoreDeviceDataEnabled && missingContacts -> stringResource(R.string.device_data_contacts_permission_required)
                restoreDeviceDataEnabled && missingCallLogs -> stringResource(R.string.device_data_call_logs_permission_required)
                restoreDeviceDataEnabled && missingCalendar -> stringResource(R.string.device_data_calendar_permission_required)
                else -> buildList {
                    if (contactsActive) {
                        val contactsSummary = formatRestoreContactsSubtitle(
                            context = context,
                            metadata = contactsMetadata!!,
                            selectAllContactAccounts = selectAllContactAccounts,
                            selectedContactAccountKeys = selectedContactAccountKeys,
                            availableContactAccounts = availableContactAccounts
                        )
                        add(
                            if (selectAllContactAccounts) {
                                contactsSummary
                            } else {
                                "${context.getString(R.string.device_data_contacts)}: $contactsSummary"
                            }
                        )
                    }
                    if (callLogsActive) add(formatCallLogsSummary(context, callLogsMetadata!!))
                    if (calendarActive) {
                        add(
                            formatRestoreCalendarSubtitle(
                                context = context,
                                metadata = calendarMetadata!!,
                                selectAllCalendars = selectAllCalendars,
                                selectedCalendarKeys = selectedCalendarKeys,
                                availableCalendars = availableCalendars
                            )
                        )
                    }
                }.joinToString(" • ")
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                ExpressiveSectionHeader(title = stringResource(R.string.operation_restore))
                ExpressiveCardGroup {
                    ExpressiveSegmentedSurface(index = 0, count = 3) {
                        TaskRow(
                            title = stringResource(R.string.run_tasks_applications),
                            subtitle = if (hasRoot) buildSelectedRestoreTypesSummary(selectableBackupDetails, appRestoreTypes, restoreTypes, context) else stringResource(R.string.root_access_denied),
                            icon = Icons.Outlined.Apps,
                            checked = if (hasRoot) restoreAppsEnabled else false,
                            onCheckedChange = viewModel::setRestoreAppsEnabled,
                            onNavigate = if (hasRoot) onNavigateToAppsConfig else null,
                            enabled = hasRoot
                        )
                    }
                    ExpressiveSegmentedSurface(index = 1, count = 3) {
                        TaskRow(
                            title = stringResource(R.string.run_tasks_custom_directories),
                            subtitle = io.github.hddq.restoid.ui.shared.buildCustomDirectoriesSubtitle(customDirsList, context),
                            icon = Icons.Outlined.Folder,
                            checked = restoreCustomDirectoriesEnabled,
                            onCheckedChange = viewModel::setRestoreCustomDirectoriesEnabled,
                            onNavigate = onNavigateToCustomDirectoriesConfig
                        )
                    }
                    ExpressiveSegmentedSurface(index = 2, count = 3) {
                        TaskRow(
                            title = stringResource(R.string.run_tasks_device_data),
                            subtitle = deviceDataSubtitle,
                            icon = Icons.Outlined.Smartphone,
                            checked = if (hasDeviceDataInSnapshot) restoreDeviceDataEnabled else false,
                            onCheckedChange = { enabled ->
                                viewModel.setRestoreDeviceDataEnabled(enabled)
                                if (enabled) {
                                    val missing = buildList {
                                        if (contactsActive && !hasContactsPermissions) {
                                            add(Manifest.permission.READ_CONTACTS)
                                            add(Manifest.permission.WRITE_CONTACTS)
                                        }
                                        if (callLogsActive && !hasCallLogsPermissions) {
                                            add(Manifest.permission.READ_CALL_LOG)
                                            add(Manifest.permission.WRITE_CALL_LOG)
                                        }
                                        if (calendarActive && !hasCalendarPermissions) {
                                            add(Manifest.permission.READ_CALENDAR)
                                            add(Manifest.permission.WRITE_CALENDAR)
                                        }
                                    }
                                    if (missing.isNotEmpty()) {
                                        requestDeviceDataPermissions(missing.toTypedArray())
                                    }
                                }
                            },
                            onNavigate = if (hasDeviceDataInSnapshot) onNavigateToDeviceDataConfig else null,
                            enabled = hasDeviceDataInSnapshot
                        )
                    }
                }
            }
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }
        }
    }
}

@Composable
fun RestoreAppsConfigScreen(
    viewModel: RestoreViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val backupDetails by viewModel.backupDetails.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val allowDowngrade by viewModel.allowDowngrade.collectAsState()
    val restoreTypes by viewModel.restoreTypes.collectAsState()
    val appRestoreTypes by viewModel.appRestoreTypes.collectAsState()
    val hasRoot by viewModel.hasRoot.collectAsState()

    if (!hasRoot) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.root_access_denied))
        }
        return
    }

    val selectableBackupDetails = backupDetails.filter { allowDowngrade || !it.isDowngrade }
    val isAllSelected = selectableBackupDetails.isNotEmpty() && selectableBackupDetails.all { it.appInfo.isSelected }
    var selectedAppPackageName by remember { mutableStateOf<String?>(null) }
    var showBulkRestoreTypesSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 88.dp + contentPadding.calculateBottomPadding(),
            top = 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item(key = "options_section") {
            Column(modifier = Modifier.padding(bottom = 14.dp)) {
                ExpressiveSectionHeader(title = stringResource(R.string.restore_options_title))
                ExpressiveSegmentedSurface(index = 0, count = 1) {
                    TaskRow(
                        title = stringResource(R.string.allow_downgrade),
                        icon = Icons.Outlined.History,
                        checked = allowDowngrade,
                        onCheckedChange = viewModel::setAllowDowngrade
                    )
                }
            }
        }

        if (backupDetails.isEmpty() && !isLoading) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.no_app_data_found_in_snapshot),
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            val totalAppRows = backupDetails.size + 1
            item(key = "apps_header") {
                ExpressiveSectionHeader(title = stringResource(R.string.apps_to_restore_title))
            }
            item(key = "apps_select_all") {
                ExpressiveSegmentedSurface(index = 0, count = totalAppRows) {
                    io.github.hddq.restoid.ui.shared.SelectAllListItem(
                        isChecked = isAllSelected,
                        subtitle = buildSelectedRestoreTypesSummary(selectableBackupDetails, appRestoreTypes, restoreTypes, context),
                        onClick = { showBulkRestoreTypesSheet = true },
                        onToggle = viewModel::toggleAllRestoreSelection
                    )
                }
            }
            itemsIndexed(backupDetails, key = { _, detail -> detail.appInfo.packageName }) { index, detail ->
                ExpressiveSegmentedSurface(index = index + 1, count = totalAppRows) {
                    RestoreAppListItem(
                        detail = detail,
                        allowDowngrade = allowDowngrade,
                        restoreTypes = appRestoreTypes[detail.appInfo.packageName] ?: restoreTypes,
                        onClick = { selectedAppPackageName = detail.appInfo.packageName },
                        onToggle = { viewModel.toggleRestoreAppSelection(detail.appInfo.packageName) }
                    )
                }
            }
        }
    }

    if (showBulkRestoreTypesSheet) {
        RestoreTypesBottomSheet(
            title = stringResource(R.string.toggle_all),
            restoreTypes = selectedBulkRestoreTypes(selectableBackupDetails, appRestoreTypes, restoreTypes),
            onRestoreTypesChange = viewModel::setSelectedAppsRestoreTypes,
            onDismissRequest = { showBulkRestoreTypesSheet = false }
        )
    }

    selectedAppPackageName?.let { packageName ->
        val detail = backupDetails.firstOrNull { it.appInfo.packageName == packageName }
        if (detail != null) {
            RestoreTypesBottomSheet(
                title = detail.appInfo.name,
                restoreTypes = appRestoreTypes[packageName] ?: restoreTypes,
                onRestoreTypesChange = { viewModel.setAppRestoreTypes(packageName, it) },
                onDismissRequest = { selectedAppPackageName = null }
            )
        }
    }
}

@Composable
fun RestoreCustomDirectoriesConfigScreen(
    viewModel: RestoreViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val customDirectories by viewModel.customDirectories.collectAsState()
    val customDirectoryMetadata by viewModel.customDirectoryMetadata.collectAsState()
    val customDirectoriesList = customDirectories.toList()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 88.dp + contentPadding.calculateBottomPadding(),
            top = 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (customDirectoriesList.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.run_tasks_custom_directories_subtitle_none),
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            item {
                val isAllCustomDirsSelected = customDirectoriesList.all { it.second }
                val selectedCount = customDirectoriesList.count { it.second }
                val totalRows = customDirectoriesList.size + 1

                ExpressiveCardGroup {
                    ExpressiveSegmentedSurface(index = 0, count = totalRows) {
                        io.github.hddq.restoid.ui.shared.SelectAllListItem(
                            isChecked = isAllCustomDirsSelected,
                            subtitle = if (selectedCount > 0) "$selectedCount selected" else "None selected",
                            onClick = null,
                            onToggle = viewModel::toggleAllCustomDirectoriesSelection,
                            iconSize = 24.dp,
                            iconPadding = 0.dp
                        )
                    }
                    customDirectoriesList.forEachIndexed { index, (path, isSelected) ->
                        ExpressiveSegmentedSurface(index = index + 1, count = totalRows) {
                            RestoreCustomDirListItem(
                                path = path,
                                savedIsDirectory = customDirectoryMetadata[path]?.isDirectory,
                                isSelected = isSelected,
                                onToggle = { viewModel.toggleCustomDirectory(path) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RestoreDeviceDataConfigScreen(
    viewModel: RestoreViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val contactsMetadata by viewModel.contactsMetadata.collectAsStateWithLifecycle()
    val callLogsMetadata by viewModel.callLogsMetadata.collectAsStateWithLifecycle()
    val calendarMetadata by viewModel.calendarMetadata.collectAsStateWithLifecycle()
    val restoreContactsEnabled by viewModel.restoreContactsEnabled.collectAsStateWithLifecycle()
    val restoreCallLogsEnabled by viewModel.restoreCallLogsEnabled.collectAsStateWithLifecycle()
    val restoreCalendarEnabled by viewModel.restoreCalendarEnabled.collectAsStateWithLifecycle()
    val selectAllContactAccounts by viewModel.selectAllContactAccounts.collectAsStateWithLifecycle()
    val selectedContactAccountKeys by viewModel.selectedContactAccountKeys.collectAsStateWithLifecycle()
    val availableContactAccounts by viewModel.availableContactAccounts.collectAsStateWithLifecycle()
    val isLoadingContactAccounts by viewModel.isLoadingContactAccounts.collectAsStateWithLifecycle()
    val contactAccountsError by viewModel.contactAccountsError.collectAsStateWithLifecycle()
    val selectAllCalendars by viewModel.selectAllCalendars.collectAsStateWithLifecycle()
    val selectedCalendarKeys by viewModel.selectedCalendarKeys.collectAsStateWithLifecycle()
    val availableCalendars by viewModel.availableCalendars.collectAsStateWithLifecycle()
    val isLoadingCalendars by viewModel.isLoadingCalendars.collectAsStateWithLifecycle()
    val calendarsError by viewModel.calendarsError.collectAsStateWithLifecycle()

    val (hasWriteContactsPermission, requestContactsPermissions) = rememberPermissionsState(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        onGranted = { viewModel.setRestoreContactsEnabled(true) }
    )
    val (hasWriteCallLogPermission, requestCallLogsPermissions) = rememberPermissionsState(
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG,
        onGranted = { viewModel.setRestoreCallLogsEnabled(true) }
    )
    val (hasWriteCalendarPermission, requestCalendarPermissions) = rememberPermissionsState(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
        onGranted = { viewModel.setRestoreCalendarEnabled(true) }
    )

    RestoreDeviceDataConfigContent(
        contactsMetadata = contactsMetadata,
        callLogsMetadata = callLogsMetadata,
        calendarMetadata = calendarMetadata,
        restoreContactsEnabled = restoreContactsEnabled,
        restoreCallLogsEnabled = restoreCallLogsEnabled,
        restoreCalendarEnabled = restoreCalendarEnabled,
        hasContactsPermission = hasWriteContactsPermission,
        hasCallLogsPermission = hasWriteCallLogPermission,
        hasCalendarPermission = hasWriteCalendarPermission,
        selectAllContactAccounts = selectAllContactAccounts,
        selectedContactAccountKeys = selectedContactAccountKeys,
        availableContactAccounts = availableContactAccounts,
        isLoadingContactAccounts = isLoadingContactAccounts,
        contactAccountsError = contactAccountsError,
        selectAllCalendars = selectAllCalendars,
        selectedCalendarKeys = selectedCalendarKeys,
        availableCalendars = availableCalendars,
        isLoadingCalendars = isLoadingCalendars,
        calendarsError = calendarsError,
        onContactsEnabledChange = viewModel::setRestoreContactsEnabled,
        onCallLogsEnabledChange = viewModel::setRestoreCallLogsEnabled,
        onCalendarEnabledChange = viewModel::setRestoreCalendarEnabled,
        onRequestContactsPermission = requestContactsPermissions,
        onRequestCallLogsPermission = requestCallLogsPermissions,
        onRequestCalendarPermission = requestCalendarPermissions,
        onLoadSnapshotContactAccounts = { viewModel.loadSnapshotContactAccounts() },
        onRetrySnapshotContactAccounts = { viewModel.loadSnapshotContactAccounts(force = true) },
        onToggleAllContactAccounts = viewModel::toggleAllContactAccounts,
        onToggleContactAccount = viewModel::toggleContactAccount,
        onLoadSnapshotCalendars = { viewModel.loadSnapshotCalendars() },
        onRetrySnapshotCalendars = { viewModel.loadSnapshotCalendars(force = true) },
        onToggleAllCalendars = viewModel::toggleAllCalendars,
        onToggleCalendar = viewModel::toggleCalendar,
        modifier = modifier,
        contentPadding = contentPadding
    )
}

@Composable
internal fun RestoreDeviceDataConfigContent(
    contactsMetadata: ContactsBackupMetadata?,
    callLogsMetadata: CallLogsBackupMetadata?,
    calendarMetadata: CalendarBackupMetadata?,
    restoreContactsEnabled: Boolean,
    restoreCallLogsEnabled: Boolean,
    restoreCalendarEnabled: Boolean,
    hasContactsPermission: Boolean = true,
    hasCallLogsPermission: Boolean = true,
    hasCalendarPermission: Boolean = true,
    selectAllContactAccounts: Boolean = true,
    selectedContactAccountKeys: Set<String> = emptySet(),
    availableContactAccounts: List<ContactAccountInfo> = emptyList(),
    isLoadingContactAccounts: Boolean = false,
    contactAccountsError: String? = null,
    selectAllCalendars: Boolean = true,
    selectedCalendarKeys: Set<String> = emptySet(),
    availableCalendars: List<CalendarInfo> = emptyList(),
    isLoadingCalendars: Boolean = false,
    calendarsError: String? = null,
    onContactsEnabledChange: (Boolean) -> Unit = {},
    onCallLogsEnabledChange: (Boolean) -> Unit = {},
    onCalendarEnabledChange: (Boolean) -> Unit = {},
    onRequestContactsPermission: () -> Unit = {},
    onRequestCallLogsPermission: () -> Unit = {},
    onRequestCalendarPermission: () -> Unit = {},
    onLoadSnapshotContactAccounts: () -> Unit = {},
    onRetrySnapshotContactAccounts: () -> Unit = {},
    onToggleAllContactAccounts: () -> Unit = {},
    onToggleContactAccount: (String) -> Unit = {},
    onLoadSnapshotCalendars: () -> Unit = {},
    onRetrySnapshotCalendars: () -> Unit = {},
    onToggleAllCalendars: () -> Unit = {},
    onToggleCalendar: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    var showContactsOptionsSheet by rememberSaveable { mutableStateOf(false) }
    var showCalendarOptionsSheet by rememberSaveable { mutableStateOf(false) }

    val latestLoadSnapshotContactAccounts by rememberUpdatedState(onLoadSnapshotContactAccounts)
    val latestLoadSnapshotCalendars by rememberUpdatedState(onLoadSnapshotCalendars)

    val contactsAvailable = contactsMetadata != null
    val contactsChecked = contactsAvailable && restoreContactsEnabled
    val contactsSubtitle = when {
        contactsMetadata == null -> stringResource(R.string.device_data_contacts_not_in_snapshot)
        restoreContactsEnabled && !hasContactsPermission -> stringResource(R.string.device_data_contacts_permission_required)
        else -> formatRestoreContactsSubtitle(
            context = context,
            metadata = contactsMetadata,
            selectAllContactAccounts = selectAllContactAccounts,
            selectedContactAccountKeys = selectedContactAccountKeys,
            availableContactAccounts = availableContactAccounts
        )
    }

    val callLogsAvailable = callLogsMetadata != null
    val callLogsChecked = callLogsAvailable && restoreCallLogsEnabled
    val callLogsSubtitle = when {
        callLogsMetadata == null -> stringResource(R.string.device_data_not_in_snapshot)
        restoreCallLogsEnabled && !hasCallLogsPermission -> stringResource(R.string.device_data_call_logs_permission_required)
        else -> formatCallLogsSummary(context, callLogsMetadata)
    }

    val calendarAvailable = calendarMetadata != null
    val calendarChecked = calendarAvailable && restoreCalendarEnabled
    val calendarSubtitle = when {
        calendarMetadata == null -> stringResource(R.string.device_data_not_in_snapshot)
        restoreCalendarEnabled && !hasCalendarPermission -> stringResource(R.string.device_data_calendar_permission_required)
        else -> formatRestoreCalendarSubtitle(
            context = context,
            metadata = calendarMetadata,
            selectAllCalendars = selectAllCalendars,
            selectedCalendarKeys = selectedCalendarKeys,
            availableCalendars = availableCalendars
        )
    }

    LaunchedEffect(showContactsOptionsSheet, contactsAvailable) {
        if (showContactsOptionsSheet && contactsAvailable) {
            latestLoadSnapshotContactAccounts()
        }
    }

    LaunchedEffect(showCalendarOptionsSheet, calendarAvailable) {
        if (showCalendarOptionsSheet && calendarAvailable) {
            latestLoadSnapshotCalendars()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 88.dp + contentPadding.calculateBottomPadding(),
            top = 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "restore_device_data_items") {
            ExpressiveCardGroup {
                ExpressiveSegmentedSurface(index = 0, count = 3) {
                    TaskRow(
                        title = stringResource(R.string.device_data_contacts),
                        subtitle = contactsSubtitle,
                        icon = Icons.Outlined.Contacts,
                        checked = contactsChecked,
                        onCheckedChange = { enabled ->
                            if (enabled && !hasContactsPermission) {
                                onRequestContactsPermission()
                            } else {
                                onContactsEnabledChange(enabled)
                            }
                        },
                        // Opening the snapshot account picker does not require device contacts permissions.
                        onNavigate = if (contactsAvailable) {
                            { showContactsOptionsSheet = true }
                        } else {
                            null
                        },
                        enabled = contactsAvailable
                    )
                }
                ExpressiveSegmentedSurface(index = 1, count = 3) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = callLogsAvailable) {
                                if (!restoreCallLogsEnabled && !hasCallLogsPermission) {
                                    onRequestCallLogsPermission()
                                } else {
                                    onCallLogsEnabledChange(!restoreCallLogsEnabled)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Call,
                            contentDescription = null,
                            tint = if (callLogsChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.device_data_call_logs),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (callLogsAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                            Text(
                                text = callLogsSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (callLogsAvailable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Switch(
                            checked = callLogsChecked,
                            enabled = callLogsAvailable,
                            onCheckedChange = { enabled ->
                                if (enabled && !hasCallLogsPermission) {
                                    onRequestCallLogsPermission()
                                } else {
                                    onCallLogsEnabledChange(enabled)
                                }
                            },
                            thumbContent = if (callLogsChecked) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            } else {
                                null
                            }
                        )
                    }
                }
                ExpressiveSegmentedSurface(index = 2, count = 3) {
                    TaskRow(
                        title = stringResource(R.string.device_data_calendar),
                        subtitle = calendarSubtitle,
                        icon = Icons.Outlined.CalendarMonth,
                        checked = calendarChecked,
                        onCheckedChange = { enabled ->
                            if (enabled && !hasCalendarPermission) {
                                onRequestCalendarPermission()
                            } else {
                                onCalendarEnabledChange(enabled)
                            }
                        },
                        // Opening the snapshot calendar picker does not require device calendar permissions.
                        onNavigate = if (calendarAvailable) {
                            { showCalendarOptionsSheet = true }
                        } else {
                            null
                        },
                        enabled = calendarAvailable
                    )
                }
            }
        }
    }

    if (showContactsOptionsSheet && contactsAvailable) {
        ContactsOptionsBottomSheet(
            selectAllContactAccounts = selectAllContactAccounts,
            selectedContactAccountKeys = selectedContactAccountKeys,
            availableContactAccounts = availableContactAccounts,
            isLoadingContactAccounts = isLoadingContactAccounts,
            hasReadContactsPermission = true,
            onRequestPermission = {},
            onToggleAllContactAccounts = onToggleAllContactAccounts,
            onToggleContactAccount = onToggleContactAccount,
            onDismissRequest = { showContactsOptionsSheet = false },
            errorMessage = contactAccountsError,
            onRetry = onRetrySnapshotContactAccounts,
            emptyMessage = stringResource(R.string.device_data_contacts_accounts_snapshot_empty)
        )
    }

    if (showCalendarOptionsSheet && calendarAvailable) {
        CalendarOptionsBottomSheet(
            selectAllCalendars = selectAllCalendars,
            selectedCalendarKeys = selectedCalendarKeys,
            availableCalendars = availableCalendars,
            isLoadingCalendars = isLoadingCalendars,
            hasReadCalendarPermission = true,
            onRequestPermission = {},
            onToggleAllCalendars = onToggleAllCalendars,
            onToggleCalendar = onToggleCalendar,
            onDismissRequest = { showCalendarOptionsSheet = false },
            errorMessage = calendarsError,
            onRetry = onRetrySnapshotCalendars,
            emptyMessage = stringResource(R.string.device_data_calendar_snapshot_empty)
        )
    }
}

private fun formatRestoreContactsSubtitle(
    context: Context,
    metadata: ContactsBackupMetadata,
    selectAllContactAccounts: Boolean,
    selectedContactAccountKeys: Set<String>,
    availableContactAccounts: List<ContactAccountInfo>
): String {
    if (selectAllContactAccounts) {
        return formatContactsSummary(context, metadata)
    }
    val selectedCount = if (availableContactAccounts.isNotEmpty()) {
        availableContactAccounts.count { it.selectionKey in selectedContactAccountKeys }
    } else {
        selectedContactAccountKeys.size
    }
    return if (selectedCount == 0) {
        context.getString(R.string.device_data_contacts_none_selected)
    } else {
        context.resources.getQuantityString(
            R.plurals.device_data_contacts_accounts_selected_count,
            selectedCount,
            selectedCount
        )
    }
}

private fun formatRestoreCalendarSubtitle(
    context: Context,
    metadata: CalendarBackupMetadata,
    selectAllCalendars: Boolean,
    selectedCalendarKeys: Set<String>,
    availableCalendars: List<CalendarInfo>
): String {
    if (selectAllCalendars) {
        return formatCalendarSummary(context, metadata)
    }
    val selectedCount = if (availableCalendars.isNotEmpty()) {
        availableCalendars.count { it.selectionKey in selectedCalendarKeys }
    } else {
        selectedCalendarKeys.size
    }
    return if (selectedCount == 0) {
        context.getString(R.string.device_data_calendar_none_selected)
    } else {
        context.resources.getQuantityString(
            R.plurals.device_data_calendars_selected_count,
            selectedCount,
            selectedCount
        )
    }
}

private fun formatContactsSummary(context: Context, metadata: ContactsBackupMetadata): String {
    val countText = context.resources.getQuantityString(
        R.plurals.device_data_contacts_count,
        metadata.count,
        metadata.count
    )
    val sizeText = Formatter.formatShortFileSize(context, metadata.size)
    return context.getString(R.string.device_data_contacts_count_size, countText, sizeText)
}

private fun formatCallLogsSummary(context: Context, metadata: CallLogsBackupMetadata): String {
    val countText = context.resources.getQuantityString(
        R.plurals.device_data_call_logs_count,
        metadata.count,
        metadata.count
    )
    val sizeText = Formatter.formatShortFileSize(context, metadata.size)
    return context.getString(R.string.device_data_call_logs_count_size, countText, sizeText)
}

private fun formatCalendarSummary(context: Context, metadata: CalendarBackupMetadata): String {
    val eventsText = context.resources.getQuantityString(
        R.plurals.device_data_calendar_events_count,
        metadata.count,
        metadata.count
    )
    val sizeText = Formatter.formatShortFileSize(context, metadata.size)
    val calCount = metadata.calendarCount ?: return context.getString(
        R.string.device_data_calendar_count_size,
        eventsText,
        sizeText
    )
    val calendarsText = context.resources.getQuantityString(
        R.plurals.device_data_calendar_calendars_count,
        calCount,
        calCount
    )
    return context.getString(
        R.string.device_data_calendar_calendars_events_size,
        calendarsText,
        eventsText,
        sizeText
    )
}

@Composable
fun RestoreTypeToggle(
    label: String,
    description: String,
    icon: ImageVector? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            thumbContent = if (checked) {
                {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            } else {
                null
            }
        )
    }
}

@Composable
private fun RestoreAppListItem(
    detail: BackupDetail,
    allowDowngrade: Boolean,
    restoreTypes: RestoreTypes,
    onClick: () -> Unit,
    onToggle: () -> Unit
) {
    val app = detail.appInfo
    val isEnabled = allowDowngrade || !detail.isDowngrade
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(enabled = isEnabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = rememberAsyncImagePainter(model = app.icon),
            contentDescription = app.name,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = app.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )

            val versionColor = if (detail.isDowngrade) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            val versionText = if (detail.isInstalled) {
                stringResource(
                    R.string.restore_backup_version_with_installed,
                    detail.versionName ?: stringResource(R.string.not_available),
                    app.versionName
                )
            } else {
                stringResource(
                    R.string.restore_backup_version_only,
                    detail.versionName ?: stringResource(R.string.not_available)
                )
            }

            Text(
                text = versionText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isEnabled) versionColor else versionColor.copy(alpha = 0.38f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildRestoreTypesSummary(restoreTypes, context),
                style = MaterialTheme.typography.bodySmall,
                color = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        )
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .fillMaxHeight(0.5f)
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = app.isSelected,
            onCheckedChange = { onToggle() },
            enabled = isEnabled,
            thumbContent = if (app.isSelected) {
                {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            } else {
                null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestoreTypesBottomSheet(
    title: String,
    restoreTypes: RestoreTypes,
    onRestoreTypesChange: (RestoreTypes) -> Unit,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            ExpressiveCardGroup {
                ExpressiveSegmentedSurface(index = 0, count = 9) {
                    RestoreTypeToggle(
                        label = stringResource(R.string.backup_type_apk),
                        description = stringResource(R.string.backup_type_apk_desc),
                        icon = Icons.Outlined.Android,
                        checked = restoreTypes.apk
                    ) {
                        onRestoreTypesChange(restoreTypes.copy(apk = it))
                    }
                }
                ExpressiveSegmentedSurface(index = 1, count = 9) {
                    RestoreTypeToggle(
                        label = stringResource(R.string.backup_type_data),
                        description = stringResource(R.string.backup_type_data_desc),
                        icon = Icons.Outlined.Storage,
                        checked = restoreTypes.data
                    ) {
                        onRestoreTypesChange(restoreTypes.copy(data = it))
                    }
                }
                ExpressiveSegmentedSurface(index = 2, count = 9) {
                    RestoreTypeToggle(
                        label = stringResource(R.string.backup_type_device_protected_data),
                        description = stringResource(R.string.backup_type_device_protected_data_desc),
                        icon = Icons.Outlined.Shield,
                        checked = restoreTypes.deviceProtectedData
                    ) {
                        onRestoreTypesChange(restoreTypes.copy(deviceProtectedData = it))
                    }
                }
                ExpressiveSegmentedSurface(index = 3, count = 9) {
                    RestoreTypeToggle(
                        label = stringResource(R.string.backup_type_external_data),
                        description = stringResource(R.string.backup_type_external_data_desc),
                        icon = Icons.Outlined.SdCard,
                        checked = restoreTypes.externalData
                    ) {
                        onRestoreTypesChange(restoreTypes.copy(externalData = it))
                    }
                }
                ExpressiveSegmentedSurface(index = 4, count = 9) {
                    RestoreTypeToggle(
                        label = stringResource(R.string.backup_type_obb_data),
                        description = stringResource(R.string.backup_type_obb_data_desc),
                        icon = Icons.Outlined.Inventory2,
                        checked = restoreTypes.obb
                    ) {
                        onRestoreTypesChange(restoreTypes.copy(obb = it))
                    }
                }
                ExpressiveSegmentedSurface(index = 5, count = 9) {
                    RestoreTypeToggle(
                        label = stringResource(R.string.backup_type_media_data),
                        description = stringResource(R.string.backup_type_media_data_desc),
                        icon = Icons.Outlined.PermMedia,
                        checked = restoreTypes.media
                    ) {
                        onRestoreTypesChange(restoreTypes.copy(media = it))
                    }
                }
                ExpressiveSegmentedSurface(index = 6, count = 9) {
                    RestoreTypeToggle(
                        label = stringResource(R.string.backup_type_permissions),
                        description = stringResource(R.string.backup_type_permissions_desc),
                        icon = Icons.Outlined.Key,
                        checked = restoreTypes.permissions
                    ) {
                        onRestoreTypesChange(restoreTypes.copy(permissions = it))
                    }
                }
                ExpressiveSegmentedSurface(index = 7, count = 9) {
                    RestoreTypeToggle(
                        label = stringResource(R.string.backup_type_special_access),
                        description = stringResource(R.string.backup_type_special_access_desc),
                        icon = Icons.Outlined.AdminPanelSettings,
                        checked = restoreTypes.specialAccess
                    ) {
                        onRestoreTypesChange(restoreTypes.copy(specialAccess = it))
                    }
                }
                ExpressiveSegmentedSurface(index = 8, count = 9) {
                    RestoreTypeToggle(
                        label = stringResource(R.string.backup_type_accessibility),
                        description = stringResource(R.string.backup_type_accessibility_desc),
                        icon = Icons.Outlined.AccessibilityNew,
                        checked = restoreTypes.accessibility
                    ) {
                        onRestoreTypesChange(restoreTypes.copy(accessibility = it))
                    }
                }
            }
        }
    }
}

private fun buildRestoreTypesSummary(restoreTypes: RestoreTypes, context: android.content.Context): String {
    val types = buildList {
        if (restoreTypes.apk) add(context.getString(R.string.backup_type_apk))
        if (restoreTypes.data) add(context.getString(R.string.backup_type_data))
        if (restoreTypes.deviceProtectedData) add(context.getString(R.string.backup_type_device_protected_data))
        if (restoreTypes.externalData) add(context.getString(R.string.backup_type_external_data))
        if (restoreTypes.obb) add(context.getString(R.string.backup_type_obb_data))
        if (restoreTypes.media) add(context.getString(R.string.backup_type_media_data))
        if (restoreTypes.permissions) add(context.getString(R.string.backup_type_permissions))
        if (restoreTypes.specialAccess) add(context.getString(R.string.backup_type_special_access))
        if (restoreTypes.accessibility) add(context.getString(R.string.backup_type_accessibility))
    }.joinToString(", ")

    return types.ifBlank { context.getString(R.string.backup_types_none) }
}

private fun buildSelectedRestoreTypesSummary(
    details: List<BackupDetail>,
    appRestoreTypes: Map<String, RestoreTypes>,
    defaultRestoreTypes: RestoreTypes,
    context: android.content.Context
): String {
    val selectedTypes = details
        .filter { it.appInfo.isSelected }
        .map { appRestoreTypes[it.appInfo.packageName] ?: defaultRestoreTypes }
        .distinct()

    return when (selectedTypes.size) {
        0 -> buildRestoreTypesSummary(defaultRestoreTypes, context)
        1 -> buildRestoreTypesSummary(selectedTypes.first(), context)
        else -> context.getString(R.string.backup_types_mixed)
    }
}

private fun selectedBulkRestoreTypes(
    details: List<BackupDetail>,
    appRestoreTypes: Map<String, RestoreTypes>,
    defaultRestoreTypes: RestoreTypes
): RestoreTypes {
    return details
        .firstOrNull { it.appInfo.isSelected }
        ?.let { appRestoreTypes[it.appInfo.packageName] ?: defaultRestoreTypes }
        ?: defaultRestoreTypes
}

@Composable
private fun RestoreCustomDirListItem(
    path: String,
    savedIsDirectory: Boolean?,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    savedIsDirectory?.let { isDirectory ->
        RestoreCustomDirListItemContent(path, isDirectory, isSelected, onToggle)
        return
    }
    val initialIsDir = io.github.hddq.restoid.util.StorageUtils.isDirectoryFast(path)
    val isDirectory by produceState(
        initialValue = initialIsDir ?: true,
        key1 = path
    ) {
        if (initialIsDir == null) {
            value = withContext(Dispatchers.IO) {
                io.github.hddq.restoid.util.StorageUtils.isDirectoryPath(path)
            }
        }
    }
    RestoreCustomDirListItemContent(path, isDirectory, isSelected, onToggle)
}

@Composable
private fun RestoreCustomDirListItemContent(
    path: String,
    isDirectory: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isDirectory) Icons.Outlined.Folder else Icons.Outlined.Description,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = path.substringAfterLast("/").ifEmpty { path },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            thumbContent = if (isSelected) {
                {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            } else null
        )
    }
}
