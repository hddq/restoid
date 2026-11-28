package io.github.hddq.restoid.ui.presets

import android.Manifest
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import io.github.hddq.restoid.model.Schedule
import io.github.hddq.restoid.model.TaskPreset
import io.github.hddq.restoid.ui.components.BackupTagPickerSheet
import io.github.hddq.restoid.ui.components.BackupTagsRow
import io.github.hddq.restoid.ui.components.ExpressiveCardGroup
import io.github.hddq.restoid.ui.components.ExpressiveEmptyState
import io.github.hddq.restoid.ui.components.ExpressiveSectionHeader
import io.github.hddq.restoid.ui.components.TasksGuideBottomSheet
import io.github.hddq.restoid.ui.components.expressiveListItemColors
import io.github.hddq.restoid.ui.home.SnapshotWithMetadata
import io.github.hddq.restoid.ui.shared.ExclusionConflictWarningCard
import io.github.hddq.restoid.ui.shared.TaskRow
import io.github.hddq.restoid.ui.shared.buildBackupSubtitle
import io.github.hddq.restoid.ui.shared.buildCheckSubtitle
import io.github.hddq.restoid.ui.shared.buildCustomDirectoriesSubtitle
import io.github.hddq.restoid.ui.shared.buildDeviceDataSubtitle
import io.github.hddq.restoid.ui.shared.buildForgetSubtitle
import io.github.hddq.restoid.ui.shared.rememberPermissionsMap
import io.github.hddq.restoid.ui.shared.summarizeTaskConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PresetsScreen(
    viewModel: PresetsViewModel,
    onEditPreset: (TaskPreset) -> Unit,
    onNavigateToOperationProgress: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hasRoot by viewModel.hasRoot.collectAsStateWithLifecycle()
    val allowExternalIntents by viewModel.allowExternalIntents.collectAsStateWithLifecycle()
    var presetToDelete by remember { mutableStateOf<TaskPreset?>(null) }
    var presetForIntentDetails by remember { mutableStateOf<TaskPreset?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadPresets()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            if (event is PresetsUiEvent.NavigateToOperationProgress) {
                onNavigateToOperationProgress()
            }
        }
    }

    PresetsContent(
        uiState = uiState,
        hasRoot = hasRoot,
        allowExternalIntents = allowExternalIntents,
        onEditPreset = onEditPreset,
        onRunPresetNow = viewModel::runPresetNow,
        onDuplicatePreset = viewModel::duplicatePreset,
        onShowIntentDetails = { presetForIntentDetails = it },
        onRequestDeletePreset = { presetToDelete = it },
        modifier = modifier,
        contentPadding = contentPadding
    )

    presetForIntentDetails?.let { preset ->
        PresetIntentSheet(
            presetId = preset.id,
            presetName = preset.name,
            repositoryName = uiState.selectedRepoName,
            onDismissRequest = { presetForIntentDetails = null }
        )
    }

    presetToDelete?.let { preset ->
        val linkedCount = uiState.schedulesByPresetId[preset.id]?.size ?: 0
        AlertDialog(
            onDismissRequest = { presetToDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_preset_title)) },
            text = {
                Text(
                    if (linkedCount > 0) {
                        pluralStringResource(
                            R.plurals.dialog_delete_preset_message_with_schedules,
                            linkedCount,
                            linkedCount
                        )
                    } else {
                        stringResource(R.string.dialog_delete_preset_message)
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePreset(preset.id)
                        presetToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { presetToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PresetsContent(
    uiState: PresetsUiState,
    hasRoot: Boolean,
    allowExternalIntents: Boolean = false,
    onEditPreset: (TaskPreset) -> Unit = {},
    onRunPresetNow: (TaskPreset) -> Unit = {},
    onDuplicatePreset: (TaskPreset) -> Unit = {},
    onShowIntentDetails: (TaskPreset) -> Unit = {},
    onRequestDeletePreset: (TaskPreset) -> Unit = {},
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            LoadingIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (uiState.presets.isEmpty()) {
            ExpressiveEmptyState(
                icon = Icons.Outlined.Bookmarks,
                title = stringResource(R.string.no_presets_configured),
                subtitle = stringResource(R.string.no_presets_configured_hint),
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
            ) {
                val presets = uiState.presets
                ExpressiveCardGroup {
                    presets.forEachIndexed { index, preset ->
                        val linkedCount = uiState.schedulesByPresetId[preset.id]?.size ?: 0
                        PresetItem(
                            preset = preset,
                            linkedScheduleCount = linkedCount,
                            hasRoot = hasRoot,
                            canRunNow = !uiState.isOperationRunning,
                            allowExternalIntents = allowExternalIntents,
                            shapes = ListItemDefaults.segmentedShapes(index = index, count = presets.size),
                            onEdit = { onEditPreset(preset) },
                            onRunNow = { onRunPresetNow(preset) },
                            onDuplicate = { onDuplicatePreset(preset) },
                            onShowIntentDetails = { onShowIntentDetails(preset) },
                            onDelete = { onRequestDeletePreset(preset) }
                        )
                    }
                }
                Spacer(Modifier.height(88.dp + contentPadding.calculateBottomPadding()))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PresetItem(
    preset: TaskPreset,
    linkedScheduleCount: Int,
    hasRoot: Boolean,
    canRunNow: Boolean,
    allowExternalIntents: Boolean,
    shapes: ListItemShapes,
    onEdit: () -> Unit,
    onRunNow: () -> Unit,
    onDuplicate: () -> Unit,
    onShowIntentDetails: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    val taskSummary = remember(preset.config, hasRoot) {
        summarizeTaskConfig(context, preset.config, hasRoot)
    }
    val scheduleMeta = if (linkedScheduleCount > 0) {
        pluralStringResource(R.plurals.preset_schedule_count, linkedScheduleCount, linkedScheduleCount)
    } else {
        stringResource(R.string.preset_manual_only)
    }
    val lastRunMeta = preset.lastRunTimestamp?.let { ts ->
        val relative = DateUtils.getRelativeTimeSpanString(
            ts,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
        val exact = dateFormat.format(Date(ts))
        stringResource(R.string.schedule_last_run, relative, exact)
    } ?: stringResource(R.string.schedule_never_run)

    Box(modifier = modifier.fillMaxWidth()) {
        SegmentedListItem(
            onClick = onEdit,
            onLongClick = { menuExpanded = true },
            shapes = shapes,
            verticalAlignment = Alignment.CenterVertically,
            colors = expressiveListItemColors(),
            leadingContent = {
                Icon(
                    imageVector = Icons.Outlined.Bookmarks,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = taskSummary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$scheduleMeta · $lastRunMeta",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            trailingContent = {
                FilledTonalIconButton(
                    onClick = onRunNow,
                    enabled = canRunNow,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = stringResource(R.string.action_run_now)
                    )
                }
            },
            content = {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_run_now)) },
                leadingIcon = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onRunNow()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_duplicate)) },
                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onDuplicate()
                }
            )
            if (allowExternalIntents) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_intent_details)) },
                    leadingIcon = { Icon(Icons.Outlined.Bolt, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onShowIntentDetails()
                    }
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    menuExpanded = false
                    onDelete()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddEditPresetScreen(
    viewModel: PresetsViewModel,
    onNavigateBack: (savedPresetId: String?) -> Unit,
    onNavigateToOperationProgress: () -> Unit,
    onNavigateToBackupConfig: () -> Unit,
    onNavigateToCustomDirectoriesConfig: () -> Unit,
    onNavigateToDeviceDataConfig: () -> Unit,
    onNavigateToForgetConfig: () -> Unit,
    onNavigateToCheckConfig: () -> Unit,
    onAddScheduleForPreset: ((presetId: String) -> Unit)? = null,
    onEditSchedule: ((Schedule) -> Unit)? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val state by viewModel.addEditState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hasRoot by viewModel.hasRoot.collectAsStateWithLifecycle()
    val allowExternalIntents by viewModel.allowExternalIntents.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appBarActions = LocalAppBarActions.current
    var showGuideSheet by remember { mutableStateOf(false) }
    var showTagPickerSheet by remember { mutableStateOf(false) }
    var showIntentSheet by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val (deviceDataPermissions, requestDeviceDataPermissions) = rememberPermissionsMap(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CALENDAR
    )
    val hasReadContactsPermission = deviceDataPermissions[Manifest.permission.READ_CONTACTS] == true
    val hasReadCallLogPermission = deviceDataPermissions[Manifest.permission.READ_CALL_LOG] == true
    val hasReadCalendarPermission = deviceDataPermissions[Manifest.permission.READ_CALENDAR] == true

    val hasUnsavedChanges = remember(state) { viewModel.hasChanges(state) }

    BackHandler(enabled = hasUnsavedChanges) {
        viewModel.onBackPress()
    }

    DisposableEffect(lifecycleOwner, state.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshLinkedSchedules()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(state.id, allowExternalIntents) {
        appBarActions.value = {
            IconButton(onClick = { showGuideSheet = true }) {
                Icon(
                    Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = stringResource(R.string.cd_help)
                )
            }
            if (state.id != null) {
                if (allowExternalIntents) {
                    IconButton(onClick = { showIntentSheet = true }) {
                        Icon(
                            Icons.Outlined.Bolt,
                            contentDescription = stringResource(R.string.action_intent_details)
                        )
                    }
                }
                IconButton(onClick = { viewModel.runCurrentPresetNow() }) {
                    Icon(
                        Icons.Outlined.PlayArrow,
                        contentDescription = stringResource(R.string.action_run_now),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { viewModel.onDeletePresetClick() }) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        onDispose { appBarActions.value = null }
    }

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is PresetsUiEvent.NavigateBack -> onNavigateBack(event.savedPresetId)
                PresetsUiEvent.NavigateToOperationProgress -> onNavigateToOperationProgress()
            }
        }
    }

    val editor = state.editor

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
        item(key = "general") {
            Column(modifier = Modifier.fillMaxWidth()) {
                ExpressiveSectionHeader(title = stringResource(R.string.section_general))
                ExpressiveCardGroup {
                    SegmentedListItem(
                        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                        colors = expressiveListItemColors()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = state.name,
                                onValueChange = viewModel::setName,
                                label = { Text(stringResource(R.string.preset_name_label)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Label,
                                        contentDescription = null
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (state.id != null) {
                                Spacer(Modifier.height(10.dp))
                                val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
                                val lastRunText = state.lastRunTimestamp?.let { ts ->
                                    val relative = DateUtils.getRelativeTimeSpanString(
                                        ts,
                                        System.currentTimeMillis(),
                                        DateUtils.MINUTE_IN_MILLIS
                                    ).toString()
                                    val exact = dateFormat.format(Date(ts))
                                    stringResource(R.string.schedule_last_run, relative, exact)
                                } ?: stringResource(R.string.schedule_never_run)
                                Text(
                                    text = lastRunText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "backup") {
            Column(modifier = Modifier.fillMaxWidth()) {
                ExclusionConflictWarningCard(conflicts = editor.exclusionConflicts)
                ExpressiveSectionHeader(title = stringResource(R.string.operation_backup))
                val hasBackupTaskEnabled = (hasRoot && editor.backupEnabled) ||
                    editor.customDirectoriesBackupEnabled ||
                    editor.deviceDataBackupEnabled
                ExpressiveCardGroup {
                    TaskRow(
                        title = stringResource(R.string.run_tasks_applications),
                        subtitle = if (hasRoot) {
                            buildBackupSubtitle(
                                apps = editor.apps,
                                appBackupTypes = editor.appBackupTypes,
                                defaultBackupTypes = editor.backupTypes,
                                context = context,
                                selectAllApps = editor.selectAllApps,
                                excludedCount = editor.excludedPackageNames.size
                            )
                        } else {
                            stringResource(R.string.root_access_denied)
                        },
                        icon = Icons.Outlined.Apps,
                        checked = if (hasRoot) editor.backupEnabled else false,
                        onCheckedChange = viewModel::setBackupEnabled,
                        onNavigate = if (hasRoot) onNavigateToBackupConfig else null,
                        enabled = hasRoot,
                        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 4)
                    )
                    TaskRow(
                        title = stringResource(R.string.run_tasks_custom_directories),
                        subtitle = buildCustomDirectoriesSubtitle(editor.customDirectories, editor.excludedCustomDirectories, context),
                        icon = Icons.Outlined.Folder,
                        checked = editor.customDirectoriesBackupEnabled,
                        onCheckedChange = viewModel::setCustomDirectoriesBackupEnabled,
                        onNavigate = onNavigateToCustomDirectoriesConfig,
                        shapes = ListItemDefaults.segmentedShapes(index = 1, count = 4)
                    )
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
                        onCheckedChange = { enabled ->
                            viewModel.setDeviceDataBackupEnabled(enabled)
                            if (enabled) {
                                val missing = buildList {
                                    if (editor.contactsBackupEnabled && !hasReadContactsPermission) {
                                        add(Manifest.permission.READ_CONTACTS)
                                    }
                                    if (editor.callLogsBackupEnabled && !hasReadCallLogPermission) {
                                        add(Manifest.permission.READ_CALL_LOG)
                                    }
                                    if (editor.calendarBackupEnabled && !hasReadCalendarPermission) {
                                        add(Manifest.permission.READ_CALENDAR)
                                    }
                                }
                                if (missing.isNotEmpty()) {
                                    requestDeviceDataPermissions(missing.toTypedArray())
                                }
                            }
                        },
                        onNavigate = onNavigateToDeviceDataConfig,
                        shapes = ListItemDefaults.segmentedShapes(index = 2, count = 4)
                    )
                    BackupTagsRow(
                        selectedTags = editor.backupTags,
                        availableTags = editor.availableTags,
                        enabled = hasBackupTaskEnabled,
                        onClick = { showTagPickerSheet = true },
                        shapes = ListItemDefaults.segmentedShapes(index = 3, count = 4)
                    )
                }
            }
        }

        item(key = "maintenance") {
            Column(modifier = Modifier.fillMaxWidth()) {
                ExpressiveSectionHeader(title = stringResource(R.string.operation_maintenance))
                ExpressiveCardGroup {
                    TaskRow(
                        title = stringResource(R.string.maintenance_task_unlock_repository),
                        icon = Icons.Outlined.LockOpen,
                        checked = editor.maintenance.unlockRepo,
                        onCheckedChange = viewModel::setUnlockRepo,
                        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 4)
                    )
                    TaskRow(
                        title = stringResource(R.string.run_tasks_forget_snapshots),
                        subtitle = buildForgetSubtitle(editor.maintenance, context),
                        icon = Icons.Outlined.AutoDelete,
                        checked = editor.maintenance.forgetSnapshots,
                        onCheckedChange = viewModel::setForgetSnapshots,
                        onNavigate = onNavigateToForgetConfig,
                        shapes = ListItemDefaults.segmentedShapes(index = 1, count = 4)
                    )
                    TaskRow(
                        title = stringResource(R.string.maintenance_task_prune_repository),
                        icon = Icons.Outlined.CleaningServices,
                        checked = editor.maintenance.pruneRepo,
                        onCheckedChange = viewModel::setPruneRepo,
                        shapes = ListItemDefaults.segmentedShapes(index = 2, count = 4)
                    )
                    TaskRow(
                        title = stringResource(R.string.run_tasks_check_integrity),
                        subtitle = buildCheckSubtitle(editor.maintenance, context),
                        icon = Icons.AutoMirrored.Outlined.FactCheck,
                        checked = editor.maintenance.checkRepo,
                        onCheckedChange = viewModel::setCheckRepo,
                        onNavigate = onNavigateToCheckConfig,
                        shapes = ListItemDefaults.segmentedShapes(index = 3, count = 4)
                    )
                }
            }
        }

        if (onAddScheduleForPreset != null && onEditSchedule != null) {
            item(key = "linked_schedules") {
                val presetId = state.id
                Column(modifier = Modifier.fillMaxWidth()) {
                    ExpressiveSectionHeader(title = stringResource(R.string.section_schedules))
                    if (presetId == null) {
                        ExpressiveCardGroup {
                            SegmentedListItem(
                                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                                verticalAlignment = Alignment.CenterVertically,
            colors = expressiveListItemColors(),
                                content = {
                                    Text(
                                        text = stringResource(R.string.preset_schedules_save_first_hint),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }
                    } else {
                        val linked = state.linkedSchedules
                        val totalRows = (if (linked.isEmpty()) 1 else linked.size) + 1
                        ExpressiveCardGroup {
                            if (linked.isEmpty()) {
                                SegmentedListItem(
                                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = totalRows),
                                    verticalAlignment = Alignment.CenterVertically,
            colors = expressiveListItemColors(),
                                    content = {
                                        Text(
                                            text = stringResource(R.string.preset_schedules_empty),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )
                            } else {
                                linked.forEachIndexed { idx, schedule ->
                                    LinkedScheduleRow(
                                        schedule = schedule,
                                        fallbackName = state.name.ifBlank { stringResource(R.string.preset_default_name) },
                                        shapes = ListItemDefaults.segmentedShapes(index = idx, count = totalRows),
                                        onClick = { onEditSchedule(schedule) },
                                        onToggleEnabled = { viewModel.toggleLinkedScheduleEnabled(schedule) }
                                    )
                                }
                            }

                            SegmentedListItem(
                                onClick = { onAddScheduleForPreset(presetId) },
                                shapes = ListItemDefaults.segmentedShapes(index = totalRows - 1, count = totalRows),
                                verticalAlignment = Alignment.CenterVertically,
                                colors = expressiveListItemColors(
                                    contentColor = MaterialTheme.colorScheme.primary,
                                    leadingContentColor = MaterialTheme.colorScheme.primary
                                ),
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Outlined.Add,
                                        contentDescription = null
                                    )
                                },
                                content = {
                                    Text(
                                        text = stringResource(R.string.action_add_schedule),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.showConfirmDeleteDialog) {
        val linkedCount = state.linkedSchedules.size
        AlertDialog(
            onDismissRequest = { viewModel.onCancelDeletePreset() },
            title = { Text(stringResource(R.string.dialog_delete_preset_title)) },
            text = {
                Text(
                    if (linkedCount > 0) {
                        pluralStringResource(
                            R.plurals.dialog_delete_preset_message_with_schedules,
                            linkedCount,
                            linkedCount
                        )
                    } else {
                        stringResource(R.string.dialog_delete_preset_message)
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDeletePreset() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onCancelDeletePreset() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (state.showDiscardChangesDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onDismissDiscard() },
            title = { Text(stringResource(R.string.dialog_discard_changes_title)) },
            text = { Text(stringResource(R.string.dialog_discard_changes_message)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.onConfirmDiscard() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDismissDiscard() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showGuideSheet) {
        TasksGuideBottomSheet(onDismissRequest = { showGuideSheet = false })
    }

    val savedPresetId = state.id
    if (showIntentSheet && allowExternalIntents && savedPresetId != null) {
        val savedPresetName = uiState.presets.firstOrNull { it.id == savedPresetId }?.name ?: state.name.trim()
        PresetIntentSheet(
            presetId = savedPresetId,
            presetName = savedPresetName,
            repositoryName = uiState.selectedRepoName,
            onDismissRequest = { showIntentSheet = false }
        )
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LinkedScheduleRow(
    schedule: Schedule,
    fallbackName: String,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    onToggleEnabled: () -> Unit,
    modifier: Modifier = Modifier
) {
    val conditionChips = buildList {
        if (schedule.triggerConditions.requireUnmeteredNetwork) add(stringResource(R.string.condition_chip_wifi))
        if (schedule.triggerConditions.requireCharging) add(stringResource(R.string.condition_chip_charging))
        if (schedule.triggerConditions.requireBatteryNotLow) add(stringResource(R.string.condition_chip_battery))
    }
    val intervalText = when (schedule.intervalHours) {
        168 -> stringResource(R.string.preset_1_week)
        720 -> stringResource(R.string.preset_1_month)
        else -> stringResource(R.string.preset_hours, schedule.intervalHours)
    }
    val subtitle = buildList {
        add(intervalText)
        if (conditionChips.isNotEmpty()) add(conditionChips.joinToString(" · "))
    }.joinToString(" · ")

    TaskRow(
        title = schedule.name?.takeIf { it.isNotBlank() } ?: fallbackName,
        subtitle = subtitle,
        icon = Icons.Outlined.EventRepeat,
        checked = schedule.isEnabled,
        onCheckedChange = { onToggleEnabled() },
        onNavigate = onClick,
        shapes = shapes,
        modifier = modifier
    )
}

@Composable
fun PresetBackupConfigScreen(
    viewModel: PresetsViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val state by viewModel.addEditState.collectAsStateWithLifecycle()
    val editor = state.editor
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
        contentPadding = contentPadding,
        selectAllApps = editor.selectAllApps,
        onSetSelectAllApps = viewModel::setSelectAllApps
    )
}

@Composable
fun PresetForgetConfigScreen(
    viewModel: PresetsViewModel,
    onOpenDryRun: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val state by viewModel.addEditState.collectAsStateWithLifecycle()
    val editor = state.editor
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
fun PresetForgetDryRunScreen(
    viewModel: PresetsViewModel,
    snapshotsWithMetadata: List<SnapshotWithMetadata>,
    appInfoMap: Map<String, List<AppInfo>>,
    repoTagsByName: Map<String, BackupTag>,
    onSnapshotClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val state by viewModel.addEditState.collectAsStateWithLifecycle()
    val editor = state.editor
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
fun PresetCheckConfigScreen(
    viewModel: PresetsViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val state by viewModel.addEditState.collectAsStateWithLifecycle()
    io.github.hddq.restoid.ui.shared.CheckConfigScreen(
        maintenance = state.editor.maintenance,
        onReadDataChange = viewModel::setReadData,
        modifier = modifier,
        contentPadding = contentPadding
    )
}

@Composable
fun PresetCustomDirectoriesConfigScreen(
    viewModel: PresetsViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val state by viewModel.addEditState.collectAsStateWithLifecycle()
    val editor = state.editor
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
fun PresetDeviceDataConfigScreen(
    viewModel: PresetsViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val state by viewModel.addEditState.collectAsStateWithLifecycle()
    val editor = state.editor
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
