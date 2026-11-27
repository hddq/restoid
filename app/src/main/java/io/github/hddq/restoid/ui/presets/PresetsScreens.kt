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
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import io.github.hddq.restoid.ui.shared.summarizeTaskConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PresetsScreen(
    viewModel: PresetsViewModel,
    onAddPreset: () -> Unit,
    onEditPreset: (TaskPreset) -> Unit,
    onNavigateToOperationProgress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hasRoot by viewModel.hasRoot.collectAsStateWithLifecycle()
    var presetToDelete by remember { mutableStateOf<TaskPreset?>(null) }
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
        onAddPreset = onAddPreset,
        onEditPreset = onEditPreset,
        onRunPresetNow = viewModel::runPresetNow,
        onDuplicatePreset = viewModel::duplicatePreset,
        onRequestDeletePreset = { presetToDelete = it },
        modifier = modifier
    )

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
    onAddPreset: () -> Unit = {},
    onEditPreset: (TaskPreset) -> Unit = {},
    onRunPresetNow: (TaskPreset) -> Unit = {},
    onDuplicatePreset: (TaskPreset) -> Unit = {},
    onRequestDeletePreset: (TaskPreset) -> Unit = {},
    modifier: Modifier = Modifier
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
                    .padding(16.dp)
            ) {
                val presets = uiState.presets
                ExpressiveCardGroup {
                    presets.forEachIndexed { index, preset ->
                        val linkedCount = uiState.schedulesByPresetId[preset.id]?.size ?: 0
                        ExpressiveSegmentedSurface(
                            index = index,
                            count = presets.size
                        ) {
                            PresetItem(
                                preset = preset,
                                linkedScheduleCount = linkedCount,
                                hasRoot = hasRoot,
                                canRunNow = !uiState.isOperationRunning,
                                onEdit = { onEditPreset(preset) },
                                onRunNow = { onRunPresetNow(preset) },
                                onDuplicate = { onDuplicatePreset(preset) },
                                onDelete = { onRequestDeletePreset(preset) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(88.dp))
            }
        }

        val isRepoSelected = uiState.selectedRepoKey != null
        ExtendedFloatingActionButton(
            onClick = {
                if (isRepoSelected) {
                    onAddPreset()
                }
            },
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.fab_new_preset)) },
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = if (isRepoSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (isRepoSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetItem(
    preset: TaskPreset,
    linkedScheduleCount: Int,
    hasRoot: Boolean,
    canRunNow: Boolean,
    onEdit: () -> Unit,
    onRunNow: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
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

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onEdit,
                    onLongClick = { menuExpanded = true }
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Bookmarks,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 16.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = taskSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$scheduleMeta · $lastRunMeta",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
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
        }

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
    modifier: Modifier = Modifier
) {
    val state by viewModel.addEditState.collectAsStateWithLifecycle()
    val hasRoot by viewModel.hasRoot.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appBarActions = LocalAppBarActions.current
    var showGuideSheet by remember { mutableStateOf(false) }
    var showTagPickerSheet by remember { mutableStateOf(false) }
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

    DisposableEffect(state.id) {
        appBarActions.value = {
            IconButton(onClick = { showGuideSheet = true }) {
                Icon(
                    Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = stringResource(R.string.cd_help)
                )
            }
            if (state.id != null) {
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "general") {
            Column(modifier = Modifier.fillMaxWidth()) {
                ExpressiveSectionHeader(title = stringResource(R.string.section_general))
                ExpressiveCardGroup {
                    ExpressiveSegmentedSurface(index = 0, count = 1) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
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
                    ExpressiveSegmentedSurface(index = 0, count = 4) {
                        TaskRow(
                            title = stringResource(R.string.run_tasks_applications),
                            subtitle = if (hasRoot) buildBackupSubtitle(editor.apps, editor.appBackupTypes, editor.backupTypes, context) else stringResource(R.string.root_access_denied),
                            icon = Icons.Outlined.Apps,
                            checked = if (hasRoot) editor.backupEnabled else false,
                            onCheckedChange = viewModel::setBackupEnabled,
                            onNavigate = if (hasRoot) onNavigateToBackupConfig else null,
                            enabled = hasRoot
                        )
                    }
                    ExpressiveSegmentedSurface(index = 1, count = 4) {
                        TaskRow(
                            title = stringResource(R.string.run_tasks_custom_directories),
                            subtitle = buildCustomDirectoriesSubtitle(editor.customDirectories, editor.excludedCustomDirectories, context),
                            icon = Icons.Outlined.Folder,
                            checked = editor.customDirectoriesBackupEnabled,
                            onCheckedChange = viewModel::setCustomDirectoriesBackupEnabled,
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
                            onNavigate = onNavigateToDeviceDataConfig
                        )
                    }
                    ExpressiveSegmentedSurface(index = 3, count = 4) {
                        BackupTagsRow(
                            selectedTags = editor.backupTags,
                            availableTags = editor.availableTags,
                            enabled = hasBackupTaskEnabled,
                            onClick = { showTagPickerSheet = true }
                        )
                    }
                }
            }
        }

        item(key = "maintenance") {
            Column(modifier = Modifier.fillMaxWidth()) {
                ExpressiveSectionHeader(title = stringResource(R.string.operation_maintenance))
                ExpressiveCardGroup {
                    ExpressiveSegmentedSurface(index = 0, count = 4) {
                        TaskRow(
                            title = stringResource(R.string.maintenance_task_unlock_repository),
                            icon = Icons.Outlined.LockOpen,
                            checked = editor.maintenance.unlockRepo,
                            onCheckedChange = viewModel::setUnlockRepo
                        )
                    }
                    ExpressiveSegmentedSurface(index = 1, count = 4) {
                        TaskRow(
                            title = stringResource(R.string.run_tasks_forget_snapshots),
                            subtitle = buildForgetSubtitle(editor.maintenance, context),
                            icon = Icons.Outlined.AutoDelete,
                            checked = editor.maintenance.forgetSnapshots,
                            onCheckedChange = viewModel::setForgetSnapshots,
                            onNavigate = onNavigateToForgetConfig
                        )
                    }
                    ExpressiveSegmentedSurface(index = 2, count = 4) {
                        TaskRow(
                            title = stringResource(R.string.maintenance_task_prune_repository),
                            icon = Icons.Outlined.CleaningServices,
                            checked = editor.maintenance.pruneRepo,
                            onCheckedChange = viewModel::setPruneRepo
                        )
                    }
                    ExpressiveSegmentedSurface(index = 3, count = 4) {
                        TaskRow(
                            title = stringResource(R.string.run_tasks_check_integrity),
                            subtitle = buildCheckSubtitle(editor.maintenance, context),
                            icon = Icons.AutoMirrored.Outlined.FactCheck,
                            checked = editor.maintenance.checkRepo,
                            onCheckedChange = viewModel::setCheckRepo,
                            onNavigate = onNavigateToCheckConfig
                        )
                    }
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
                            ExpressiveSegmentedSurface(index = 0, count = 1) {
                                Text(
                                    text = stringResource(R.string.preset_schedules_save_first_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                                )
                            }
                        }
                    } else {
                        val linked = state.linkedSchedules
                        val totalRows = (if (linked.isEmpty()) 1 else linked.size) + 1
                        ExpressiveCardGroup {
                            if (linked.isEmpty()) {
                                ExpressiveSegmentedSurface(index = 0, count = totalRows) {
                                    Text(
                                        text = stringResource(R.string.preset_schedules_empty),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                                    )
                                }
                            } else {
                                linked.forEachIndexed { idx, schedule ->
                                    ExpressiveSegmentedSurface(index = idx, count = totalRows) {
                                        LinkedScheduleRow(
                                            schedule = schedule,
                                            fallbackName = state.name.ifBlank { stringResource(R.string.preset_default_name) },
                                            onClick = { onEditSchedule(schedule) },
                                            onToggleEnabled = { viewModel.toggleLinkedScheduleEnabled(schedule) }
                                        )
                                    }
                                }
                            }

                            ExpressiveSegmentedSurface(index = totalRows - 1, count = totalRows) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onAddScheduleForPreset(presetId) }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Add,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 16.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.action_add_schedule),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
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
private fun LinkedScheduleRow(
    schedule: Schedule,
    fallbackName: String,
    onClick: () -> Unit,
    onToggleEnabled: () -> Unit
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.EventRepeat,
            contentDescription = null,
            tint = if (schedule.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = schedule.name?.takeIf { it.isNotBlank() } ?: fallbackName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
            checked = schedule.isEnabled,
            onCheckedChange = { onToggleEnabled() },
            thumbContent = if (schedule.isEnabled) {
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

@Composable
fun PresetBackupConfigScreen(
    viewModel: PresetsViewModel,
    modifier: Modifier = Modifier
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
        modifier = modifier
    )
}

@Composable
fun PresetForgetConfigScreen(
    viewModel: PresetsViewModel,
    onOpenDryRun: () -> Unit,
    modifier: Modifier = Modifier
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
        modifier = modifier
    )
}

@Composable
fun PresetForgetDryRunScreen(
    viewModel: PresetsViewModel,
    snapshotsWithMetadata: List<SnapshotWithMetadata>,
    appInfoMap: Map<String, List<AppInfo>>,
    repoTagsByName: Map<String, BackupTag>,
    onSnapshotClick: (String) -> Unit,
    modifier: Modifier = Modifier
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
        modifier = modifier
    )
}

@Composable
fun PresetCheckConfigScreen(
    viewModel: PresetsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.addEditState.collectAsStateWithLifecycle()
    io.github.hddq.restoid.ui.shared.CheckConfigScreen(
        maintenance = state.editor.maintenance,
        onReadDataChange = viewModel::setReadData,
        modifier = modifier
    )
}

@Composable
fun PresetCustomDirectoriesConfigScreen(
    viewModel: PresetsViewModel,
    modifier: Modifier = Modifier
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
        modifier = modifier
    )
}

@Composable
fun PresetDeviceDataConfigScreen(
    viewModel: PresetsViewModel,
    modifier: Modifier = Modifier
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
        modifier = modifier
    )
}
