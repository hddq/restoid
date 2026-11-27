package io.github.hddq.restoid.ui.runtasks

import android.Manifest
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hddq.restoid.LocalAppBarActions
import io.github.hddq.restoid.R
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.model.BackupTag
import io.github.hddq.restoid.ui.components.BackupTagPickerSheet
import io.github.hddq.restoid.ui.components.BackupTagsRow
import io.github.hddq.restoid.ui.components.ExpressiveCardGroup
import io.github.hddq.restoid.ui.components.ExpressiveSectionHeader
import io.github.hddq.restoid.ui.components.ExpressiveSegmentedSurface
import io.github.hddq.restoid.ui.components.TasksGuideBottomSheet
import io.github.hddq.restoid.ui.home.SnapshotWithMetadata
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
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val operationBlocked by viewModel.operationBlocked.collectAsStateWithLifecycle()
    val hasRoot by viewModel.hasRoot.collectAsStateWithLifecycle()
    val appBarActions = LocalAppBarActions.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showGuideSheet by remember { mutableStateOf(false) }
    var showTagPickerSheet by remember { mutableStateOf(false) }

    val (deviceDataPermissions, requestDeviceDataPermissions) = rememberPermissionsMap(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CALENDAR
    )
    val hasReadContactsPermission = deviceDataPermissions[Manifest.permission.READ_CONTACTS] == true
    val hasReadCallLogPermission = deviceDataPermissions[Manifest.permission.READ_CALL_LOG] == true
    val hasReadCalendarPermission = deviceDataPermissions[Manifest.permission.READ_CALENDAR] == true

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPresets()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        appBarActions.value = {
            IconButton(onClick = { viewModel.openSaveAsPresetDialog() }) {
                Icon(
                    imageVector = Icons.Outlined.BookmarkAdd,
                    contentDescription = stringResource(R.string.action_save_as_preset)
                )
            }
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

    RunTasksContent(
        uiState = uiState,
        hasRoot = hasRoot,
        hasReadContactsPermission = hasReadContactsPermission,
        hasReadCallLogPermission = hasReadCallLogPermission,
        hasReadCalendarPermission = hasReadCalendarPermission,
        onSelectPreset = viewModel::selectPreset,
        onSetBackupEnabled = viewModel::setBackupEnabled,
        onNavigateToBackupConfig = onNavigateToBackupConfig,
        onSetCustomDirectoriesBackupEnabled = viewModel::setCustomDirectoriesBackupEnabled,
        onNavigateToCustomDirectoriesConfig = onNavigateToCustomDirectoriesConfig,
        onSetDeviceDataBackupEnabled = { enabled ->
            viewModel.setDeviceDataBackupEnabled(enabled)
            if (enabled) {
                val missing = buildList {
                    if (uiState.editor.contactsBackupEnabled && !hasReadContactsPermission) {
                        add(Manifest.permission.READ_CONTACTS)
                    }
                    if (uiState.editor.callLogsBackupEnabled && !hasReadCallLogPermission) {
                        add(Manifest.permission.READ_CALL_LOG)
                    }
                    if (uiState.editor.calendarBackupEnabled && !hasReadCalendarPermission) {
                        add(Manifest.permission.READ_CALENDAR)
                    }
                }
                if (missing.isNotEmpty()) {
                    requestDeviceDataPermissions(missing.toTypedArray())
                }
            }
        },
        onNavigateToDeviceDataConfig = onNavigateToDeviceDataConfig,
        onOpenTagPicker = { showTagPickerSheet = true },
        onSetUnlockRepo = viewModel::setUnlockRepo,
        onSetForgetSnapshots = viewModel::setForgetSnapshots,
        onNavigateToForgetConfig = onNavigateToForgetConfig,
        onSetPruneRepo = viewModel::setPruneRepo,
        onSetCheckRepo = viewModel::setCheckRepo,
        onNavigateToCheckConfig = onNavigateToCheckConfig,
        modifier = modifier,
        contentPadding = contentPadding
    )

    val editor = uiState.editor

    if (uiState.showSavePresetDialog) {
        val selectedPreset = remember(uiState.selectedPresetId, uiState.availablePresets) {
            uiState.availablePresets.find { it.id == uiState.selectedPresetId }
        }
        val suggestedName = if (selectedPreset != null) {
            stringResource(R.string.preset_copy_name_format, selectedPreset.name)
        } else {
            ""
        }
        var presetName by remember(uiState.showSavePresetDialog, suggestedName) {
            mutableStateOf(suggestedName)
        }
        AlertDialog(
            onDismissRequest = { viewModel.dismissSaveAsPresetDialog() },
            title = { Text(stringResource(R.string.dialog_save_as_preset_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.dialog_save_as_preset_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text(stringResource(R.string.preset_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.saveCurrentAsPreset(presetName) },
                    enabled = presetName.isNotBlank()
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSaveAsPresetDialog() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showGuideSheet) {
        TasksGuideBottomSheet(onDismissRequest = { showGuideSheet = false })
    }

    if (showTagPickerSheet) {
        BackupTagPickerSheet(
            availableTags = editor.availableTags,
            selectedTags = editor.backupTags,
            onToggleTag = viewModel::toggleBackupTag,
            onCreateTag = viewModel::createTag,
            onUpdateTagColor = viewModel::updateTagColor,
            onDeleteTag = viewModel::deleteTag,
            onDismissRequest = { showTagPickerSheet = false }
        )
    }
}

@Composable
internal fun RunTasksContent(
    uiState: RunTasksUiState,
    hasRoot: Boolean,
    hasReadContactsPermission: Boolean = true,
    hasReadCallLogPermission: Boolean = true,
    hasReadCalendarPermission: Boolean = true,
    onSelectPreset: (String) -> Unit = {},
    onSetBackupEnabled: (Boolean) -> Unit = {},
    onNavigateToBackupConfig: () -> Unit = {},
    onSetCustomDirectoriesBackupEnabled: (Boolean) -> Unit = {},
    onNavigateToCustomDirectoriesConfig: () -> Unit = {},
    onSetDeviceDataBackupEnabled: (Boolean) -> Unit = {},
    onNavigateToDeviceDataConfig: () -> Unit = {},
    onOpenTagPicker: () -> Unit = {},
    onSetUnlockRepo: (Boolean) -> Unit = {},
    onSetForgetSnapshots: (Boolean) -> Unit = {},
    onNavigateToForgetConfig: () -> Unit = {},
    onSetPruneRepo: (Boolean) -> Unit = {},
    onSetCheckRepo: (Boolean) -> Unit = {},
    onNavigateToCheckConfig: () -> Unit = {},
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val editor = uiState.editor

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
        if (uiState.availablePresets.isNotEmpty()) {
            item(key = "preset_chips_strip") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        items = uiState.availablePresets,
                        key = { it.id }
                    ) { preset ->
                        FilterChip(
                            selected = uiState.selectedPresetId == preset.id,
                            onClick = { onSelectPreset(preset.id) },
                            label = {
                                Text(
                                    text = preset.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                ExclusionConflictWarningCard(conflicts = editor.exclusionConflicts)
                ExpressiveSectionHeader(title = context.getString(R.string.operation_backup))
                val hasBackupTaskEnabled = (hasRoot && editor.backupEnabled) ||
                    editor.customDirectoriesBackupEnabled ||
                    editor.deviceDataBackupEnabled
                ExpressiveCardGroup {
                    ExpressiveSegmentedSurface(index = 0, count = 4) {
                        TaskRow(
                            title = context.getString(R.string.run_tasks_applications),
                            subtitle = if (hasRoot) buildBackupSubtitle(editor.apps, editor.appBackupTypes, editor.backupTypes, context) else context.getString(R.string.root_access_denied),
                            icon = Icons.Outlined.Apps,
                            checked = if (hasRoot) editor.backupEnabled else false,
                            onCheckedChange = onSetBackupEnabled,
                            onNavigate = if (hasRoot) onNavigateToBackupConfig else null,
                            enabled = hasRoot
                        )
                    }
                    ExpressiveSegmentedSurface(index = 1, count = 4) {
                        TaskRow(
                            title = context.getString(R.string.run_tasks_custom_directories),
                            subtitle = buildCustomDirectoriesSubtitle(editor.customDirectories, editor.excludedCustomDirectories, context),
                            icon = Icons.Outlined.Folder,
                            checked = editor.customDirectoriesBackupEnabled,
                            onCheckedChange = onSetCustomDirectoriesBackupEnabled,
                            onNavigate = onNavigateToCustomDirectoriesConfig
                        )
                    }
                    ExpressiveSegmentedSurface(index = 2, count = 4) {
                        TaskRow(
                            title = stringResource(R.string.run_tasks_device_data),
                            subtitle = buildDeviceDataSubtitle(
                                contactsEnabled = editor.contactsBackupEnabled,
                                callLogsEnabled = editor.callLogsBackupEnabled,
                                calendarEnabled = editor.calendarBackupEnabled,
                                deviceDataBackupEnabled = editor.deviceDataBackupEnabled,
                                hasReadContactsPermission = hasReadContactsPermission,
                                hasReadCallLogPermission = hasReadCallLogPermission,
                                hasReadCalendarPermission = hasReadCalendarPermission,
                                context = context
                            ),
                            icon = Icons.Outlined.Smartphone,
                            checked = editor.deviceDataBackupEnabled,
                            onCheckedChange = onSetDeviceDataBackupEnabled,
                            onNavigate = onNavigateToDeviceDataConfig
                        )
                    }
                    ExpressiveSegmentedSurface(index = 3, count = 4) {
                        BackupTagsRow(
                            selectedTags = editor.backupTags,
                            availableTags = editor.availableTags,
                            enabled = hasBackupTaskEnabled,
                            onClick = onOpenTagPicker
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
                            checked = editor.maintenance.unlockRepo,
                            onCheckedChange = onSetUnlockRepo
                        )
                    }
                    ExpressiveSegmentedSurface(index = 1, count = 4) {
                        TaskRow(
                            title = context.getString(R.string.run_tasks_forget_snapshots),
                            subtitle = buildForgetSubtitle(editor.maintenance, context),
                            icon = Icons.Outlined.AutoDelete,
                            checked = editor.maintenance.forgetSnapshots,
                            onCheckedChange = onSetForgetSnapshots,
                            onNavigate = onNavigateToForgetConfig
                        )
                    }
                    ExpressiveSegmentedSurface(index = 2, count = 4) {
                        TaskRow(
                            title = context.getString(R.string.maintenance_task_prune_repository),
                            icon = Icons.Outlined.CleaningServices,
                            checked = editor.maintenance.pruneRepo,
                            onCheckedChange = onSetPruneRepo
                        )
                    }
                    ExpressiveSegmentedSurface(index = 3, count = 4) {
                        TaskRow(
                            title = context.getString(R.string.run_tasks_check_integrity),
                            subtitle = buildCheckSubtitle(editor.maintenance, context),
                            icon = Icons.AutoMirrored.Outlined.FactCheck,
                            checked = editor.maintenance.checkRepo,
                            onCheckedChange = onSetCheckRepo,
                            onNavigate = onNavigateToCheckConfig
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BackupConfigScreen(
    viewModel: RunTasksViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val hasRoot by viewModel.hasRoot.collectAsStateWithLifecycle()
    if (!hasRoot) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.root_access_denied))
        }
        return
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editor = uiState.editor
    io.github.hddq.restoid.ui.shared.BackupConfigScreen(
        isLoadingApps = editor.isLoadingApps,
        apps = editor.apps,
        appBackupTypes = editor.appBackupTypes,
        backupTypes = editor.backupTypes,
        onRefreshApps = viewModel::refreshAppsList,
        onToggleAllApps = viewModel::toggleAllApps,
        onToggleAppSelection = viewModel::toggleAppSelection,
        onSetSelectedAppsBackupTypes = viewModel::setSelectedAppsBackupTypes,
        onSetAppBackupTypes = viewModel::setAppBackupTypes,
        modifier = modifier,
        contentPadding = contentPadding
    )
}

@Composable
fun ForgetConfigScreen(
    viewModel: RunTasksViewModel,
    onOpenDryRun: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editor = uiState.editor
    io.github.hddq.restoid.ui.shared.ForgetConfigScreen(
        maintenance = editor.maintenance,
        onKeepLastChange = viewModel::setKeepLast,
        onKeepDailyChange = viewModel::setKeepDaily,
        onKeepWeeklyChange = viewModel::setKeepWeekly,
        onKeepMonthlyChange = viewModel::setKeepMonthly,
        availableHosts = editor.availableForgetHosts,
        areRepoSnapshotsLoaded = editor.areRepoSnapshotsLoaded,
        isLoadingHosts = editor.isLoadingForgetHosts,
        hostsError = editor.forgetHostsError,
        onToggleAllHosts = viewModel::toggleAllForgetHosts,
        onToggleHost = viewModel::toggleForgetHost,
        onAddHost = viewModel::addCustomForgetHost,
        onRemoveHost = viewModel::removeCustomForgetHost,
        onRefreshHosts = { viewModel.refreshForgetHosts(userInitiated = true) },
        onAutoLoadHosts = { viewModel.refreshForgetHosts(userInitiated = false) },
        availableTags = editor.availableTags,
        onToggleForgetTag = viewModel::toggleForgetTag,
        onCreateForgetTag = viewModel::createForgetTag,
        onUpdateTagColor = viewModel::updateTagColor,
        onDeleteTag = viewModel::deleteTag,
        onOpenDryRun = onOpenDryRun,
        modifier = modifier,
        contentPadding = contentPadding
    )
}

@Composable
fun ForgetDryRunScreen(
    viewModel: RunTasksViewModel,
    snapshotsWithMetadata: List<SnapshotWithMetadata>,
    appInfoMap: Map<String, List<AppInfo>>,
    repoTagsByName: Map<String, BackupTag>,
    onSnapshotClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editor = uiState.editor
    io.github.hddq.restoid.ui.shared.ForgetDryRunScreen(
        isRunning = editor.isRunningForgetDryRun,
        result = editor.forgetDryRunResult,
        error = editor.forgetDryRunError,
        snapshotsWithMetadata = snapshotsWithMetadata,
        appInfoMap = appInfoMap,
        repoTagsByName = repoTagsByName,
        onRunDryRun = viewModel::runForgetDryRun,
        onSnapshotClick = onSnapshotClick,
        modifier = modifier,
        contentPadding = contentPadding
    )
}

@Composable
fun CheckConfigScreen(
    viewModel: RunTasksViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    io.github.hddq.restoid.ui.shared.CheckConfigScreen(
        maintenance = uiState.editor.maintenance,
        onReadDataChange = viewModel::setReadData,
        modifier = modifier,
        contentPadding = contentPadding
    )
}

@Composable
fun CustomDirectoriesConfigScreen(
    viewModel: RunTasksViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editor = uiState.editor
    io.github.hddq.restoid.ui.shared.CustomDirectoriesConfigScreen(
        customDirectories = editor.customDirectories,
        onAddDirectory = viewModel::addCustomDirectory,
        onToggleDirectory = viewModel::toggleCustomDirectory,
        onRemoveDirectory = viewModel::removeCustomDirectory,
        excludedDirectories = editor.excludedCustomDirectories,
        onAddExcludedDirectory = viewModel::addExcludedCustomDirectory,
        onToggleExcludedDirectory = viewModel::toggleExcludedCustomDirectory,
        onRemoveExcludedDirectory = viewModel::removeExcludedCustomDirectory,
        modifier = modifier,
        contentPadding = contentPadding
    )
}

@Composable
fun DeviceDataConfigScreen(
    viewModel: RunTasksViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editor = uiState.editor
    io.github.hddq.restoid.ui.shared.DeviceDataConfigScreen(
        contactsEnabled = editor.contactsBackupEnabled,
        selectAllContactAccounts = editor.selectAllContactAccounts,
        selectedContactAccountKeys = editor.selectedContactAccountKeys,
        availableContactAccounts = editor.availableContactAccounts,
        isLoadingContactAccounts = editor.isLoadingContactAccounts,
        callLogsEnabled = editor.callLogsBackupEnabled,
        calendarEnabled = editor.calendarBackupEnabled,
        selectAllCalendars = editor.selectAllCalendars,
        selectedCalendarKeys = editor.selectedCalendarKeys,
        availableCalendars = editor.availableCalendars,
        isLoadingCalendars = editor.isLoadingCalendars,
        onContactsEnabledChange = viewModel::setContactsBackupEnabled,
        onToggleAllContactAccounts = viewModel::toggleAllContactAccounts,
        onToggleContactAccount = viewModel::toggleContactAccount,
        onRefreshContactAccounts = viewModel::refreshContactAccounts,
        onCallLogsEnabledChange = viewModel::setCallLogsBackupEnabled,
        onCalendarEnabledChange = viewModel::setCalendarBackupEnabled,
        onToggleAllCalendars = viewModel::toggleAllCalendars,
        onToggleCalendar = viewModel::toggleCalendar,
        onRefreshCalendars = viewModel::refreshCalendars,
        modifier = modifier,
        contentPadding = contentPadding
    )
}
