package io.github.hddq.restoid.ui.schedules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import io.github.hddq.restoid.LocalAppBarActions
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.hddq.restoid.R
import io.github.hddq.restoid.model.Schedule
import io.github.hddq.restoid.ui.shared.AppListItem
import io.github.hddq.restoid.ui.shared.BackupTypeToggle
import io.github.hddq.restoid.ui.shared.BackupTypes
import io.github.hddq.restoid.ui.shared.PolicySlider
import io.github.hddq.restoid.ui.shared.SelectAllListItem
import io.github.hddq.restoid.ui.shared.TaskRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SchedulesScreen(
    viewModel: SchedulesViewModel,
    onNavigateToAddEditSchedule: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
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
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (uiState.schedules.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.no_schedules_configured),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column {
                        val schedules = uiState.schedules
                        schedules.forEachIndexed { index, schedule ->
                            ScheduleItem(
                                schedule = schedule,
                                onEdit = {
                                    viewModel.startEditSchedule(schedule)
                                    onNavigateToAddEditSchedule()
                                },
                                onDelete = { scheduleToDelete = schedule },
                                onToggleEnabled = { viewModel.toggleScheduleEnabled(schedule) },
                                onRunNow = { viewModel.runNow(schedule) }
                            )
                            if (index < schedules.size - 1) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.background)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(88.dp))
            }
        }

        FloatingActionButton(
            onClick = {
                viewModel.startAddSchedule()
                onNavigateToAddEditSchedule()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.fab_add_schedule))
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
                    }
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
@Composable
fun AddEditScheduleScreen(
    viewModel: SchedulesViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToBackupConfig: () -> Unit,
    onNavigateToForgetConfig: () -> Unit,
    onNavigateToCheckConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.addEditState.collectAsState()
    val context = LocalContext.current
    var intervalText by remember(state.intervalHours) { mutableStateOf(state.intervalHours.toString()) }
    val appBarActions = LocalAppBarActions.current

    DisposableEffect(state.id) {
        if (state.id != null) {
            appBarActions.value = {
                IconButton(onClick = { viewModel.onDeleteScheduleClick() }) {
                    Icon(
                        Icons.Default.Delete,
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        item {
            Column {
                Text(
                    text = stringResource(R.string.section_general),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column {
                        OutlinedTextField(
                            value = state.name,
                            onValueChange = viewModel::setName,
                            label = { Text(stringResource(R.string.schedule_name_label)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.background)

                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
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
                                        if (parsed in 1..720) viewModel.setIntervalHours(parsed)
                                    }
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
                                            viewModel.setIntervalHours(preset)
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
                        }
                    }
                }
            }
        }

        item {
            Column {
                Text(
                    text = stringResource(R.string.section_conditions),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column {
                        TaskRow(
                            title = stringResource(R.string.condition_battery_not_low),
                            checked = state.triggerConditions.requireBatteryNotLow,
                            onCheckedChange = viewModel::setRequireBatteryNotLow
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.background)
                        TaskRow(
                            title = stringResource(R.string.condition_charging),
                            checked = state.triggerConditions.requireCharging,
                            onCheckedChange = viewModel::setRequireCharging
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.background)
                        TaskRow(
                            title = stringResource(R.string.condition_unmetered_network),
                            checked = state.triggerConditions.requireUnmeteredNetwork,
                            onCheckedChange = viewModel::setRequireUnmeteredNetwork
                        )
                    }
                }
            }
        }

        item {
            Column {
                Text(
                    text = stringResource(R.string.operation_backup),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    TaskRow(
                        title = stringResource(R.string.run_tasks_applications),
                        subtitle = buildBackupSubtitle(state.apps, state.backupTypes, context),
                        checked = state.backupEnabled,
                        onCheckedChange = viewModel::setBackupEnabled,
                        onNavigate = onNavigateToBackupConfig
                    )
                }
            }
        }

        item {
            Column {
                Text(
                    text = stringResource(R.string.operation_maintenance),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column {
                        TaskRow(
                            title = stringResource(R.string.maintenance_task_unlock_repository),
                            checked = state.maintenance.unlockRepo,
                            onCheckedChange = viewModel::setUnlockRepo
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.background)
                        TaskRow(
                            title = stringResource(R.string.run_tasks_forget_snapshots),
                            subtitle = buildForgetSubtitle(state.maintenance, context),
                            checked = state.maintenance.forgetSnapshots,
                            onCheckedChange = viewModel::setForgetSnapshots,
                            onNavigate = onNavigateToForgetConfig
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.background)
                        TaskRow(
                            title = stringResource(R.string.maintenance_task_prune_repository),
                            checked = state.maintenance.pruneRepo,
                            onCheckedChange = viewModel::setPruneRepo
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.background)
                        TaskRow(
                            title = stringResource(R.string.run_tasks_check_integrity),
                            subtitle = buildCheckSubtitle(state.maintenance, context),
                            checked = state.maintenance.checkRepo,
                            onCheckedChange = viewModel::setCheckRepo,
                            onNavigate = onNavigateToCheckConfig
                        )
                    }
                }
            }
        }
    }

    if (state.showConfirmDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onCancelDeleteSchedule() },
            title = { Text(stringResource(R.string.dialog_delete_schedule_title)) },
            text = { Text(stringResource(R.string.dialog_delete_schedule_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDeleteSchedule() }) {
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
}

@Composable
fun ScheduleBackupConfigScreen(
    viewModel: SchedulesViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.addEditState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAppsList()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = stringResource(R.string.backup_types_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column {
                        BackupTypeToggle(stringResource(R.string.backup_type_apk), state.backupTypes.apk, viewModel::setBackupApk)
                        HorizontalDivider(color = MaterialTheme.colorScheme.background)
                        BackupTypeToggle(stringResource(R.string.backup_type_data), state.backupTypes.data, viewModel::setBackupData)
                        HorizontalDivider(color = MaterialTheme.colorScheme.background)
                        BackupTypeToggle(stringResource(R.string.backup_type_device_protected_data), state.backupTypes.deviceProtectedData, viewModel::setBackupDeviceProtectedData)
                        HorizontalDivider(color = MaterialTheme.colorScheme.background)
                        BackupTypeToggle(stringResource(R.string.backup_type_external_data), state.backupTypes.externalData, viewModel::setBackupExternalData)
                        HorizontalDivider(color = MaterialTheme.colorScheme.background)
                        BackupTypeToggle(stringResource(R.string.backup_type_obb_data), state.backupTypes.obb, viewModel::setBackupObb)
                        HorizontalDivider(color = MaterialTheme.colorScheme.background)
                        BackupTypeToggle(stringResource(R.string.backup_type_media_data), state.backupTypes.media, viewModel::setBackupMedia)
                    }
                }
            }
        }

        if (state.isLoadingApps) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            item {
                Column {
                    Text(
                        text = stringResource(R.string.apps_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column {
                        val isAllSelected = state.apps.isNotEmpty() && state.apps.all { it.isSelected }
                        SelectAllListItem(isChecked = isAllSelected, onToggle = viewModel::toggleAllApps)
                        HorizontalDivider(color = MaterialTheme.colorScheme.background)
                        state.apps.forEachIndexed { index, app ->
                            AppListItem(app = app) { viewModel.toggleAppSelection(app.packageName) }
                            if (index < state.apps.size - 1) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.background)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleForgetConfigScreen(viewModel: SchedulesViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.addEditState.collectAsState()
    val maintenance = state.maintenance

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column {
                    PolicySlider(stringResource(R.string.maintenance_keep_last), maintenance.keepLast, 0..20, viewModel::setKeepLast)
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    PolicySlider(stringResource(R.string.maintenance_keep_daily), maintenance.keepDaily, 0..30, viewModel::setKeepDaily)
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    PolicySlider(stringResource(R.string.maintenance_keep_weekly), maintenance.keepWeekly, 0..12, viewModel::setKeepWeekly)
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    PolicySlider(stringResource(R.string.maintenance_keep_monthly), maintenance.keepMonthly, 0..24, viewModel::setKeepMonthly)
                }
            }
        }
    }
}

@Composable
fun ScheduleCheckConfigScreen(viewModel: SchedulesViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.addEditState.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                TaskRow(
                    title = stringResource(R.string.maintenance_read_all_data),
                    subtitle = stringResource(R.string.run_tasks_check_read_all_data_description),
                    checked = state.maintenance.readData,
                    onCheckedChange = viewModel::setReadData
                )
            }
        }
    }
}

@Composable
private fun ScheduleItem(
    schedule: Schedule,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: () -> Unit,
    onRunNow: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = schedule.name,
                style = MaterialTheme.typography.bodyLarge
            )
            
            val lastRunText = if (schedule.lastRunTimestamp != null) {
                stringResource(R.string.schedule_last_run, dateFormat.format(Date(schedule.lastRunTimestamp)))
            } else {
                stringResource(R.string.schedule_never_run)
            }
            
            Text(
                text = stringResource(R.string.schedule_interval_label) + ": ${schedule.intervalHours}h\n" + lastRunText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(12.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
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

private fun buildBackupSubtitle(apps: List<io.github.hddq.restoid.model.AppInfo>, backupTypes: BackupTypes, context: android.content.Context): String {
    val selectedCount = apps.count { it.isSelected }
    val types = buildList {
        if (backupTypes.apk) add(context.getString(R.string.backup_type_apk))
        if (backupTypes.data) add(context.getString(R.string.backup_type_data))
        if (backupTypes.deviceProtectedData) add(context.getString(R.string.backup_type_device_protected_data))
        if (backupTypes.externalData) add(context.getString(R.string.backup_type_external_data))
        if (backupTypes.obb) add(context.getString(R.string.backup_type_obb_data))
        if (backupTypes.media) add(context.getString(R.string.backup_type_media_data))
    }.joinToString(", ")

    return context.getString(R.string.run_tasks_backup_subtitle, selectedCount, types)
}

private fun buildForgetSubtitle(config: io.github.hddq.restoid.ui.runtasks.RunTasksMaintenanceConfig, context: android.content.Context): String {
    return context.getString(
        R.string.run_tasks_forget_subtitle,
        config.keepLast,
        config.keepDaily,
        config.keepWeekly,
        config.keepMonthly
    )
}

private fun buildCheckSubtitle(config: io.github.hddq.restoid.ui.runtasks.RunTasksMaintenanceConfig, context: android.content.Context): String {
    return if (config.readData) {
        context.getString(R.string.maintenance_read_all_data)
    } else {
        context.getString(R.string.run_tasks_check_metadata_only)
    }
}
