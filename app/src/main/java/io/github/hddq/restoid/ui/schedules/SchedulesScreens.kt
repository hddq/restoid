package io.github.hddq.restoid.ui.schedules

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Battery5Bar
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hddq.restoid.LocalAppBarActions
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.BatteryConditionStatus
import io.github.hddq.restoid.data.ChargingPowerSource
import io.github.hddq.restoid.data.ChargingState
import io.github.hddq.restoid.data.NetworkConditionStatus
import io.github.hddq.restoid.data.NetworkTransport
import io.github.hddq.restoid.data.TriggerConditionStatusSnapshot
import io.github.hddq.restoid.model.Schedule
import io.github.hddq.restoid.model.TaskPreset
import io.github.hddq.restoid.model.TriggerConditions
import io.github.hddq.restoid.ui.components.ExpressiveCardGroup
import io.github.hddq.restoid.ui.components.ExpressiveEmptyState
import io.github.hddq.restoid.ui.components.ExpressiveSectionHeader
import io.github.hddq.restoid.ui.components.expressiveListItemColors
import io.github.hddq.restoid.ui.shared.TaskRow
import io.github.hddq.restoid.ui.shared.summarizeTaskConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SchedulesScreen(
    viewModel: SchedulesViewModel,
    onEditSchedule: (Schedule) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var scheduleToDelete by remember { mutableStateOf<Schedule?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadSchedules()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            LoadingIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (uiState.schedules.isEmpty()) {
            ExpressiveEmptyState(
                icon = Icons.Outlined.EventRepeat,
                title = stringResource(R.string.no_schedules_configured),
                subtitle = stringResource(R.string.no_schedules_configured_hint),
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
            ) {
                val schedules = uiState.schedules
                ExpressiveCardGroup {
                    schedules.forEachIndexed { index, schedule ->
                        ScheduleItem(
                            schedule = schedule,
                            preset = uiState.presetsById[schedule.presetId],
                            shapes = ListItemDefaults.segmentedShapes(index = index, count = schedules.size),
                            onEdit = { onEditSchedule(schedule) },
                            onDelete = { scheduleToDelete = schedule },
                            onToggleEnabled = { viewModel.toggleScheduleEnabled(schedule) },
                            onRunNow = { viewModel.runNow(schedule) }
                        )
                    }
                }
                Spacer(Modifier.height(88.dp + contentPadding.calculateBottomPadding()))
            }
        }
    }

    scheduleToDelete?.let { schedule ->
        AlertDialog(
            onDismissRequest = { scheduleToDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_schedule_title)) },
            text = { Text(stringResource(R.string.dialog_delete_schedule_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSchedule(schedule.id)
                        scheduleToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { scheduleToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditScheduleScreen(
    viewModel: SchedulesViewModel,
    onNavigateBack: () -> Unit,
    onCreatePreset: (() -> Unit)? = null,
    onEditPreset: ((TaskPreset) -> Unit)? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val state by viewModel.addEditState.collectAsStateWithLifecycle()
    val conditionStatus by viewModel.conditionStatus.collectAsStateWithLifecycle()
    val hasRoot by viewModel.hasRoot.collectAsStateWithLifecycle()
    var showPresetPickerSheet by remember { mutableStateOf(false) }
    val appBarActions = LocalAppBarActions.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val hasUnsavedChanges = remember(state) { viewModel.hasChanges(state) }

    BackHandler(enabled = hasUnsavedChanges) {
        viewModel.onBackPress()
    }

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

    DisposableEffect(state.id) {
        appBarActions.value = {
            if (state.id != null) {
                IconButton(onClick = { viewModel.runCurrentScheduleNow() }) {
                    Icon(
                        Icons.Outlined.PlayArrow,
                        contentDescription = stringResource(R.string.action_run_now),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { viewModel.onDeleteScheduleClick() }) {
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
                SchedulesUiEvent.NavigateBack -> onNavigateBack()
            }
        }
    }

    AddEditScheduleContent(
        state = state,
        conditionStatus = conditionStatus,
        hasRoot = hasRoot,
        onSelectPresetClick = { showPresetPickerSheet = true },
        onCreatePreset = onCreatePreset,
        onEditPreset = onEditPreset,
        onNameChange = viewModel::setName,
        onIntervalHoursChange = viewModel::setIntervalHours,
        onRequireBatteryNotLowChange = viewModel::setRequireBatteryNotLow,
        onRequireChargingChange = viewModel::setRequireCharging,
        onRequireUnmeteredNetworkChange = viewModel::setRequireUnmeteredNetwork,
        modifier = modifier,
        contentPadding = contentPadding
    )

    if (showPresetPickerSheet) {
        TaskPresetPickerBottomSheet(
            presets = state.availablePresets,
            selectedPresetId = state.presetId,
            hasRoot = hasRoot,
            onSelectPreset = { presetId ->
                viewModel.setPresetId(presetId)
                showPresetPickerSheet = false
            },
            onCreatePreset = onCreatePreset?.let { create ->
                {
                    showPresetPickerSheet = false
                    create()
                }
            },
            onDismissRequest = { showPresetPickerSheet = false }
        )
    }

    if (state.showConfirmDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onCancelDeleteSchedule() },
            title = { Text(stringResource(R.string.dialog_delete_schedule_title)) },
            text = { Text(stringResource(R.string.dialog_delete_schedule_message)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDeleteSchedule() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onCancelDeleteSchedule() }) {
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
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AddEditScheduleContent(
    state: AddEditScheduleUiState,
    conditionStatus: TriggerConditionStatusSnapshot,
    hasRoot: Boolean,
    onSelectPresetClick: () -> Unit = {},
    onCreatePreset: (() -> Unit)? = null,
    onEditPreset: ((TaskPreset) -> Unit)? = null,
    onNameChange: (String) -> Unit = {},
    onIntervalHoursChange: (Int) -> Unit = {},
    onRequireBatteryNotLowChange: (Boolean) -> Unit = {},
    onRequireChargingChange: (Boolean) -> Unit = {},
    onRequireUnmeteredNetworkChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    var intervalText by remember(state.intervalHours) { mutableStateOf(state.intervalHours.toString()) }
    val selectedPreset = remember(state.presetId, state.availablePresets) {
        state.availablePresets.find { it.id == state.presetId }
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
            Column(modifier = Modifier.fillMaxWidth()) {
                ExpressiveSectionHeader(title = stringResource(R.string.section_task_preset))
                if (state.availablePresets.isEmpty()) {
                    ExpressiveCardGroup {
                        SegmentedListItem(
                            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                            colors = expressiveListItemColors()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Bookmarks,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = stringResource(R.string.schedule_no_presets_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.schedule_no_presets_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (onCreatePreset != null) {
                                    FilledTonalButton(
                                        onClick = onCreatePreset,
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.action_create_new_preset))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val showPresetActions = (selectedPreset != null && onEditPreset != null) || onCreatePreset != null
                    val cardCount = if (showPresetActions) 2 else 1
                    ExpressiveCardGroup {
                        SegmentedListItem(
                            onClick = onSelectPresetClick,
                            shapes = ListItemDefaults.segmentedShapes(index = 0, count = cardCount),
                        verticalAlignment = Alignment.CenterVertically,
                        colors = expressiveListItemColors(),
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Outlined.Bookmarks,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            supportingContent = selectedPreset?.let { preset ->
                                {
                                    Text(
                                        text = summarizeTaskConfig(context, preset.config, hasRoot),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.Outlined.UnfoldMore,
                                    contentDescription = stringResource(R.string.schedule_select_preset)
                                )
                            },
                            content = {
                                Text(
                                    text = selectedPreset?.name ?: stringResource(R.string.schedule_select_preset),
                                    color = if (selectedPreset != null) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                        if (showPresetActions) {
                            SegmentedListItem(
                                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                            verticalAlignment = Alignment.CenterVertically,
                        colors = expressiveListItemColors(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (selectedPreset != null && onEditPreset != null) {
                                        TextButton(onClick = { onEditPreset(selectedPreset) }) {
                                            Icon(
                                                imageVector = Icons.Outlined.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.action_edit_preset))
                                        }
                                    }
                                    if (onCreatePreset != null) {
                                        TextButton(onClick = onCreatePreset) {
                                            Icon(
                                                imageVector = Icons.Outlined.Add,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.action_create_new_preset))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                ExpressiveSectionHeader(title = stringResource(R.string.section_schedule))
                ExpressiveCardGroup {
                    SegmentedListItem(
                        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                        colors = expressiveListItemColors()
                    ) {
                        OutlinedTextField(
                            value = state.name,
                            onValueChange = onNameChange,
                            label = { Text(stringResource(R.string.schedule_label_optional)) },
                            placeholder = {
                                Text(
                                    text = selectedPreset?.name ?: stringResource(R.string.schedule_label_placeholder),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Label,
                                    contentDescription = null
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    SegmentedListItem(
                        shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                        colors = expressiveListItemColors()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val isError = intervalText.toIntOrNull()?.let { it !in 1..720 } ?: true
                            Text(
                                text = stringResource(R.string.schedule_interval_label),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            OutlinedTextField(
                                value = intervalText,
                                onValueChange = { input ->
                                    intervalText = input.filter { it.isDigit() }
                                    intervalText.toIntOrNull()?.let { parsed ->
                                        if (parsed in 1..720) onIntervalHoursChange(parsed)
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Update,
                                        contentDescription = null
                                    )
                                },
                                suffix = { Text(stringResource(R.string.unit_hours)) },
                                isError = isError,
                                supportingText = if (isError) {
                                    { Text(stringResource(R.string.schedule_interval_range_hint)) }
                                } else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(6, 12, 24, 48, 168, 720).forEach { preset ->
                                    FilterChip(
                                        selected = state.intervalHours == preset,
                                        onClick = {
                                            onIntervalHoursChange(preset)
                                            intervalText = preset.toString()
                                        },
                                        label = {
                                            Text(
                                                when (preset) {
                                                    168 -> stringResource(R.string.preset_1_week)
                                                    720 -> stringResource(R.string.preset_1_month)
                                                    else -> stringResource(R.string.preset_hours, preset)
                                                }
                                            )
                                        }
                                    )
                                }
                            }

                            if (state.id != null) {
                                Spacer(Modifier.height(16.dp))
                                val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
                                val currentLastRun = state.lastRunTimestamp
                                val lastRunText = if (currentLastRun != null) {
                                    val relativeTime = android.text.format.DateUtils.getRelativeTimeSpanString(
                                        currentLastRun,
                                        System.currentTimeMillis(),
                                        android.text.format.DateUtils.MINUTE_IN_MILLIS
                                    ).toString()
                                    val exactTime = dateFormat.format(Date(currentLastRun))
                                    stringResource(R.string.schedule_last_run, relativeTime, exactTime)
                                } else {
                                    stringResource(R.string.schedule_never_run)
                                }
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

        item {
            ScheduleConditionsSection(
                triggerConditions = state.triggerConditions,
                conditionStatus = conditionStatus,
                onRequireBatteryNotLowChange = onRequireBatteryNotLowChange,
                onRequireChargingChange = onRequireChargingChange,
                onRequireUnmeteredNetworkChange = onRequireUnmeteredNetworkChange
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TaskPresetPickerBottomSheet(
    presets: List<TaskPreset>,
    selectedPresetId: String?,
    hasRoot: Boolean,
    onSelectPreset: (String) -> Unit,
    onCreatePreset: (() -> Unit)?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp)
        ) {
            Text(
                text = stringResource(R.string.schedule_select_preset),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            ExpressiveCardGroup(modifier = Modifier.selectableGroup()) {
                presets.forEachIndexed { index, preset ->
                    val isSelected = preset.id == selectedPresetId
                    SegmentedListItem(
                        selected = isSelected,
                        onClick = { onSelectPreset(preset.id) },
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = presets.size),
                        verticalAlignment = Alignment.CenterVertically,
                        colors = expressiveListItemColors(),
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.Bookmarks,
                                contentDescription = null
                            )
                        },
                        supportingContent = {
                            Text(
                                text = summarizeTaskConfig(context, preset.config, hasRoot),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingContent = {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        },
                        content = {
                            Text(
                                text = preset.name,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
            if (onCreatePreset != null) {
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = onCreatePreset,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_create_new_preset))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScheduleItem(
    schedule: Schedule,
    preset: TaskPreset?,
    shapes: ListItemShapes,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: () -> Unit,
    onRunNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    val customLabel = schedule.name?.takeIf { it.isNotBlank() }
    val displayTitle = customLabel ?: preset?.name ?: stringResource(R.string.schedule_missing_preset)
    val intervalText = when (schedule.intervalHours) {
        168 -> stringResource(R.string.preset_1_week)
        720 -> stringResource(R.string.preset_1_month)
        else -> stringResource(R.string.preset_hours, schedule.intervalHours)
    }

    Box(modifier = modifier.fillMaxWidth()) {
        SegmentedListItem(
            onClick = onEdit,
            onLongClick = { menuExpanded = true },
            shapes = shapes,
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            colors = expressiveListItemColors(),
            leadingContent = {
                Icon(
                    imageVector = Icons.Outlined.EventRepeat,
                    contentDescription = null,
                    tint = if (schedule.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .height(24.dp)
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
            },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val metaLine = buildList {
                        if (customLabel != null && preset != null && !customLabel.equals(preset.name, ignoreCase = true)) {
                            add(preset.name)
                        }
                        add("${stringResource(R.string.schedule_interval_label)}: $intervalText")
                    }.joinToString(" · ")

                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val conditions = buildList {
                        if (schedule.triggerConditions.requireUnmeteredNetwork) {
                            add(stringResource(R.string.condition_chip_wifi))
                        }
                        if (schedule.triggerConditions.requireCharging) {
                            add(stringResource(R.string.condition_chip_charging))
                        }
                        if (schedule.triggerConditions.requireBatteryNotLow) {
                            add(stringResource(R.string.condition_chip_battery))
                        }
                    }
                    if (conditions.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            conditions.forEach { label ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    val lastRunText = if (schedule.lastRunTimestamp != null) {
                        val relativeTime = android.text.format.DateUtils.getRelativeTimeSpanString(
                            schedule.lastRunTimestamp,
                            System.currentTimeMillis(),
                            android.text.format.DateUtils.MINUTE_IN_MILLIS
                        ).toString()
                        val exactTime = dateFormat.format(Date(schedule.lastRunTimestamp))
                        stringResource(R.string.schedule_last_run, relativeTime, exactTime)
                    } else {
                        stringResource(R.string.schedule_never_run)
                    }

                    Text(
                        text = lastRunText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        )

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = {
                    Text(text = stringResource(R.string.action_run_now))
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null
                    )
                },
                onClick = {
                    menuExpanded = false
                    onRunNow()
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ScheduleConditionsSection(
    triggerConditions: TriggerConditions,
    conditionStatus: TriggerConditionStatusSnapshot,
    onRequireBatteryNotLowChange: (Boolean) -> Unit,
    onRequireChargingChange: (Boolean) -> Unit,
    onRequireUnmeteredNetworkChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ExpressiveSectionHeader(title = stringResource(R.string.section_conditions))
        ExpressiveCardGroup {
            TaskRow(
                title = stringResource(R.string.condition_battery_not_low),
                icon = Icons.Outlined.Battery5Bar,
                checked = triggerConditions.requireBatteryNotLow,
                onCheckedChange = onRequireBatteryNotLowChange,
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 3)
            ) {
                ConditionStatusSubtitle(
                    currentStateText = batteryNotLowStateText(conditionStatus.battery),
                    isMet = conditionStatus.battery.isBatteryNotLow,
                    isRequired = triggerConditions.requireBatteryNotLow
                )
            }
            TaskRow(
                title = stringResource(R.string.condition_charging),
                icon = Icons.Outlined.Bolt,
                checked = triggerConditions.requireCharging,
                onCheckedChange = onRequireChargingChange,
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 3)
            ) {
                ConditionStatusSubtitle(
                    currentStateText = chargingStateText(conditionStatus.battery),
                    isMet = conditionStatus.battery.isChargingMet,
                    isRequired = triggerConditions.requireCharging
                )
            }
            TaskRow(
                title = stringResource(R.string.condition_unmetered_network),
                icon = Icons.Outlined.Wifi,
                checked = triggerConditions.requireUnmeteredNetwork,
                onCheckedChange = onRequireUnmeteredNetworkChange,
                shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3)
            ) {
                ConditionStatusSubtitle(
                    currentStateText = networkStateText(conditionStatus.network),
                    isMet = conditionStatus.network.isUnmeteredMet,
                    isRequired = triggerConditions.requireUnmeteredNetwork
                )
            }
        }
    }
}

@Composable
private fun ConditionStatusSubtitle(
    currentStateText: String,
    isMet: Boolean?,
    isRequired: Boolean,
    modifier: Modifier = Modifier
) {
    val statusColor = when (isMet) {
        true -> MaterialTheme.colorScheme.primary
        false -> if (isRequired) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusText = when (isMet) {
        true -> stringResource(R.string.condition_status_met)
        false -> stringResource(R.string.condition_status_not_met)
        null -> null
    }

    val formattedText = if (statusText != null) {
        val fullText = stringResource(R.string.condition_status_format, currentStateText, statusText)
        val statusStart = fullText.lastIndexOf(statusText)
        buildAnnotatedString {
            append(fullText)
            if (statusStart >= 0) {
                addStyle(
                    style = SpanStyle(
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    ),
                    start = statusStart,
                    end = statusStart + statusText.length
                )
            }
        }
    } else {
        buildAnnotatedString {
            append(currentStateText)
        }
    }

    Row(
        modifier = modifier.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (isMet != null) {
            Icon(
                imageVector = if (isMet) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = formattedText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun batteryNotLowStateText(battery: BatteryConditionStatus): String {
    val levelPercent = battery.levelPercent
    return when (battery.isBatteryNotLow) {
        true -> if (levelPercent != null) {
            stringResource(R.string.condition_battery_level, levelPercent)
        } else {
            stringResource(R.string.condition_battery_ok)
        }
        false -> if (levelPercent != null) {
            stringResource(R.string.condition_battery_level_low, levelPercent)
        } else {
            stringResource(R.string.condition_battery_low)
        }
        null -> stringResource(R.string.condition_status_unavailable)
    }
}

@Composable
private fun chargingStateText(battery: BatteryConditionStatus): String {
    return when (battery.chargingState) {
        ChargingState.CHARGING -> when (battery.powerSource) {
            ChargingPowerSource.AC -> stringResource(R.string.condition_state_charging_ac)
            ChargingPowerSource.USB -> stringResource(R.string.condition_state_charging_usb)
            ChargingPowerSource.WIRELESS -> stringResource(R.string.condition_state_charging_wireless)
            ChargingPowerSource.DOCK -> stringResource(R.string.condition_state_charging_dock)
            ChargingPowerSource.UNKNOWN -> stringResource(R.string.condition_state_charging)
        }
        ChargingState.FULL -> stringResource(R.string.condition_state_battery_full)
        ChargingState.PLUGGED_NOT_CHARGING -> stringResource(R.string.condition_state_plugged_not_charging)
        ChargingState.NOT_CHARGING -> stringResource(R.string.condition_state_not_charging)
        ChargingState.UNKNOWN -> stringResource(R.string.condition_status_unavailable)
    }
}

@Composable
private fun networkStateText(network: NetworkConditionStatus): String {
    if (network.isBlocked) {
        return stringResource(R.string.condition_state_network_blocked)
    }
    if (!network.isConnected) {
        return stringResource(R.string.condition_state_network_none)
    }
    return when (network.transport) {
        NetworkTransport.WIFI -> if (network.isUnmetered) {
            stringResource(R.string.condition_state_network_wifi_unmetered)
        } else {
            stringResource(R.string.condition_state_network_wifi_metered)
        }
        NetworkTransport.ETHERNET -> if (network.isUnmetered) {
            stringResource(R.string.condition_state_network_ethernet_unmetered)
        } else {
            stringResource(R.string.condition_state_network_ethernet_metered)
        }
        NetworkTransport.CELLULAR -> if (network.isUnmetered) {
            stringResource(R.string.condition_state_network_cellular_unmetered)
        } else {
            stringResource(R.string.condition_state_network_cellular_metered)
        }
        NetworkTransport.VPN -> if (network.isUnmetered) {
            stringResource(R.string.condition_state_network_vpn_unmetered)
        } else {
            stringResource(R.string.condition_state_network_vpn_metered)
        }
        NetworkTransport.OTHER -> if (network.isUnmetered) {
            stringResource(R.string.condition_state_network_other_unmetered)
        } else {
            stringResource(R.string.condition_state_network_other_metered)
        }
        NetworkTransport.NONE -> stringResource(R.string.condition_state_network_none)
    }
}
