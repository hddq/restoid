package io.github.hddq.restoid.ui.runtasks

import android.Manifest
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hddq.restoid.LocalAppBarActions
import io.github.hddq.restoid.R
import io.github.hddq.restoid.ui.components.ExpressiveCardGroup
import io.github.hddq.restoid.ui.components.ExpressiveSectionHeader
import io.github.hddq.restoid.ui.components.ExpressiveSegmentedSurface
import io.github.hddq.restoid.ui.components.TasksGuideBottomSheet
import io.github.hddq.restoid.ui.shared.ExclusionConflictWarningCard
import io.github.hddq.restoid.ui.shared.TaskRow
import io.github.hddq.restoid.ui.shared.buildBackupSubtitle
import io.github.hddq.restoid.ui.shared.buildCheckSubtitle
import io.github.hddq.restoid.ui.shared.buildCustomDirectoriesSubtitle
import io.github.hddq.restoid.ui.shared.buildDeviceDataSubtitle
import io.github.hddq.restoid.ui.shared.buildForgetSubtitle
import io.github.hddq.restoid.ui.shared.rememberPermissionsMap

@Composable
fun RunTasksScreen(
    viewModel: RunTasksViewModel,
    onNavigateToOperationProgress: () -> Unit,
    onNavigateToBackupConfig: () -> Unit,
    onNavigateToCustomDirectoriesConfig: () -> Unit,
    onNavigateToDeviceDataConfig: () -> Unit,
    onNavigateToForgetConfig: () -> Unit,
    onNavigateToCheckConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val operationBlocked by viewModel.operationBlocked.collectAsStateWithLifecycle()
    val rootState by viewModel.rootState.collectAsStateWithLifecycle()
    val appBarActions = LocalAppBarActions.current
    var showGuideSheet by remember { mutableStateOf(false) }

    val (deviceDataPermissions, requestDeviceDataPermissions) = rememberPermissionsMap(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CALENDAR
    )
    val hasReadContactsPermission = deviceDataPermissions[Manifest.permission.READ_CONTACTS] == true
    val hasReadCallLogPermission = deviceDataPermissions[Manifest.permission.READ_CALL_LOG] == true
    val hasReadCalendarPermission = deviceDataPermissions[Manifest.permission.READ_CALENDAR] == true

    DisposableEffect(Unit) {
        appBarActions.value = {
            IconButton(onClick = { showGuideSheet = true }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = stringResource(R.string.cd_help)
                )
            }
        }
        onDispose {
            appBarActions.value = null
        }
    }

    LaunchedEffect(operationBlocked) {
        if (operationBlocked) {
            Toast.makeText(context, context.getString(R.string.error_operation_already_running), Toast.LENGTH_SHORT).show()
            viewModel.consumeOperationBlocked()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                RunTasksUiEvent.NavigateToOperationProgress -> onNavigateToOperationProgress()
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            val hasRoot = rootState == io.github.hddq.restoid.data.RootState.Granted

            Column(modifier = Modifier.fillMaxWidth()) {
                ExclusionConflictWarningCard(conflicts = uiState.exclusionConflicts)
                ExpressiveSectionHeader(title = context.getString(R.string.operation_backup))
                ExpressiveCardGroup {
                    ExpressiveSegmentedSurface(index = 0, count = 3) {
                        TaskRow(
                            title = context.getString(R.string.run_tasks_applications),
                            subtitle = if (hasRoot) buildBackupSubtitle(uiState.apps, uiState.appBackupTypes, uiState.backupTypes, context) else context.getString(R.string.root_access_denied),
                            icon = Icons.Outlined.Apps,
                            checked = if (hasRoot) uiState.backupEnabled else false,
                            onCheckedChange = viewModel::setBackupEnabled,
                            onNavigate = if (hasRoot) onNavigateToBackupConfig else null,
                            enabled = hasRoot
                        )
                    }
                    ExpressiveSegmentedSurface(index = 1, count = 3) {
                        TaskRow(
                            title = context.getString(R.string.run_tasks_custom_directories),
                            subtitle = buildCustomDirectoriesSubtitle(uiState.customDirectories, uiState.excludedCustomDirectories, context),
                            icon = Icons.Outlined.Folder,
                            checked = uiState.customDirectoriesBackupEnabled,
                            onCheckedChange = viewModel::setCustomDirectoriesBackupEnabled,
                            onNavigate = onNavigateToCustomDirectoriesConfig
                        )
                    }
                    ExpressiveSegmentedSurface(index = 2, count = 3) {
                        TaskRow(
                            title = stringResource(R.string.run_tasks_device_data),
                            subtitle = buildDeviceDataSubtitle(
                                contactsEnabled = uiState.contactsBackupEnabled,
                                callLogsEnabled = uiState.callLogsBackupEnabled,
                                calendarEnabled = uiState.calendarBackupEnabled,
                                deviceDataBackupEnabled = uiState.deviceDataBackupEnabled,
                                hasReadContactsPermission = hasReadContactsPermission,
                                hasReadCallLogPermission = hasReadCallLogPermission,
                                hasReadCalendarPermission = hasReadCalendarPermission,
                                context = context
                            ),
                            icon = Icons.Outlined.Smartphone,
                            checked = uiState.deviceDataBackupEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setDeviceDataBackupEnabled(enabled)
                                if (enabled) {
                                    val missing = buildList {
                                        if (uiState.contactsBackupEnabled && !hasReadContactsPermission) {
                                            add(Manifest.permission.READ_CONTACTS)
                                        }
                                        if (uiState.callLogsBackupEnabled && !hasReadCallLogPermission) {
                                            add(Manifest.permission.READ_CALL_LOG)
                                        }
                                        if (uiState.calendarBackupEnabled && !hasReadCalendarPermission) {
                                            add(Manifest.permission.READ_CALENDAR)
                                        }
                                    }
                                    if (missing.isNotEmpty()) {
                                        requestDeviceDataPermissions(missing.toTypedArray())
                                    }
                                }
                            },
                            onNavigate = onNavigateToDeviceDataConfig
                        )
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                ExpressiveSectionHeader(title = context.getString(R.string.operation_maintenance))
                ExpressiveCardGroup {
                    ExpressiveSegmentedSurface(index = 0, count = 4) {
                        TaskRow(
                            title = context.getString(R.string.maintenance_task_unlock_repository),
                            icon = Icons.Outlined.LockOpen,
                            checked = uiState.maintenance.unlockRepo,
                            onCheckedChange = viewModel::setUnlockRepo
                        )
                    }
                    ExpressiveSegmentedSurface(index = 1, count = 4) {
                        TaskRow(
                            title = context.getString(R.string.run_tasks_forget_snapshots),
                            subtitle = buildForgetSubtitle(uiState.maintenance, context),
                            icon = Icons.Outlined.AutoDelete,
                            checked = uiState.maintenance.forgetSnapshots,
                            onCheckedChange = viewModel::setForgetSnapshots,
                            onNavigate = onNavigateToForgetConfig
                        )
                    }
                    ExpressiveSegmentedSurface(index = 2, count = 4) {
                        TaskRow(
                            title = context.getString(R.string.maintenance_task_prune_repository),
                            icon = Icons.Outlined.CleaningServices,
                            checked = uiState.maintenance.pruneRepo,
                            onCheckedChange = viewModel::setPruneRepo
                        )
                    }
                    ExpressiveSegmentedSurface(index = 3, count = 4) {
                        TaskRow(
                            title = context.getString(R.string.run_tasks_check_integrity),
                            subtitle = buildCheckSubtitle(uiState.maintenance, context),
                            icon = Icons.Outlined.FactCheck,
                            checked = uiState.maintenance.checkRepo,
                            onCheckedChange = viewModel::setCheckRepo,
                            onNavigate = onNavigateToCheckConfig
                        )
                    }
                }
            }
        }
    }

    if (showGuideSheet) {
        TasksGuideBottomSheet(onDismissRequest = { showGuideSheet = false })
    }
}

@Composable
fun BackupConfigScreen(
    viewModel: RunTasksViewModel,
    modifier: Modifier = Modifier
) {
    val rootState by viewModel.rootState.collectAsStateWithLifecycle()
    if (rootState != io.github.hddq.restoid.data.RootState.Granted) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.root_access_denied))
        }
        return
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    io.github.hddq.restoid.ui.shared.BackupConfigScreen(
        isLoadingApps = uiState.isLoadingApps,
        apps = uiState.apps,
        appBackupTypes = uiState.appBackupTypes,
        backupTypes = uiState.backupTypes,
        onRefreshApps = viewModel::refreshAppsList,
        onToggleAllApps = viewModel::toggleAllApps,
        onToggleAppSelection = viewModel::toggleAppSelection,
        onSetSelectedAppsBackupTypes = viewModel::setSelectedAppsBackupTypes,
        onSetAppBackupTypes = viewModel::setAppBackupTypes,
        modifier = modifier
    )
}

@Composable
fun ForgetConfigScreen(
    viewModel: RunTasksViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    io.github.hddq.restoid.ui.shared.ForgetConfigScreen(
        maintenance = uiState.maintenance,
        onKeepLastChange = viewModel::setKeepLast,
        onKeepDailyChange = viewModel::setKeepDaily,
        onKeepWeeklyChange = viewModel::setKeepWeekly,
        onKeepMonthlyChange = viewModel::setKeepMonthly,
        availableHosts = uiState.availableForgetHosts,
        areRepoSnapshotsLoaded = uiState.areRepoSnapshotsLoaded,
        isLoadingHosts = uiState.isLoadingForgetHosts,
        hostsError = uiState.forgetHostsError,
        onToggleAllHosts = viewModel::toggleAllForgetHosts,
        onToggleHost = viewModel::toggleForgetHost,
        onAddHost = viewModel::addCustomForgetHost,
        onRemoveHost = viewModel::removeCustomForgetHost,
        onRefreshHosts = { viewModel.refreshForgetHosts(userInitiated = true) },
        onAutoLoadHosts = { viewModel.refreshForgetHosts(userInitiated = false) },
        modifier = modifier
    )
}

@Composable
fun CheckConfigScreen(
    viewModel: RunTasksViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    io.github.hddq.restoid.ui.shared.CheckConfigScreen(
        maintenance = uiState.maintenance,
        onReadDataChange = viewModel::setReadData,
        modifier = modifier
    )
}

@Composable
fun CustomDirectoriesConfigScreen(
    viewModel: RunTasksViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    io.github.hddq.restoid.ui.shared.CustomDirectoriesConfigScreen(
        customDirectories = uiState.customDirectories,
        onAddDirectory = viewModel::addCustomDirectory,
        onToggleDirectory = viewModel::toggleCustomDirectory,
        onRemoveDirectory = viewModel::removeCustomDirectory,
        excludedDirectories = uiState.excludedCustomDirectories,
        onAddExcludedDirectory = viewModel::addExcludedCustomDirectory,
        onToggleExcludedDirectory = viewModel::toggleExcludedCustomDirectory,
        onRemoveExcludedDirectory = viewModel::removeExcludedCustomDirectory,
        modifier = modifier
    )
}

@Composable
fun DeviceDataConfigScreen(
    viewModel: RunTasksViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    io.github.hddq.restoid.ui.shared.DeviceDataConfigScreen(
        contactsEnabled = uiState.contactsBackupEnabled,
        selectAllContactAccounts = uiState.selectAllContactAccounts,
        selectedContactAccountKeys = uiState.selectedContactAccountKeys,
        availableContactAccounts = uiState.availableContactAccounts,
        isLoadingContactAccounts = uiState.isLoadingContactAccounts,
        callLogsEnabled = uiState.callLogsBackupEnabled,
        calendarEnabled = uiState.calendarBackupEnabled,
        selectAllCalendars = uiState.selectAllCalendars,
        selectedCalendarKeys = uiState.selectedCalendarKeys,
        availableCalendars = uiState.availableCalendars,
        isLoadingCalendars = uiState.isLoadingCalendars,
        onContactsEnabledChange = viewModel::setContactsBackupEnabled,
        onToggleAllContactAccounts = viewModel::toggleAllContactAccounts,
        onToggleContactAccount = viewModel::toggleContactAccount,
        onRefreshContactAccounts = viewModel::refreshContactAccounts,
        onCallLogsEnabledChange = viewModel::setCallLogsBackupEnabled,
        onCalendarEnabledChange = viewModel::setCalendarBackupEnabled,
        onToggleAllCalendars = viewModel::toggleAllCalendars,
        onToggleCalendar = viewModel::toggleCalendar,
        onRefreshCalendars = viewModel::refreshCalendars,
        modifier = modifier
    )
}
