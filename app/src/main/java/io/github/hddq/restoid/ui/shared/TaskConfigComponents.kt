package io.github.hddq.restoid.ui.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import io.github.hddq.restoid.R
import io.github.hddq.restoid.model.AppInfo
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
@Composable
fun TaskRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onNavigate: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val rowClick = onNavigate ?: { onCheckedChange(!checked) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, onClick = rowClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (onNavigate != null) {
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
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            thumbContent = if (checked) {
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
fun PolicySlider(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    val isDiscrete = (range.last - range.first) <= 30

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = if (isDiscrete) (range.last - range.first - 1).coerceAtLeast(0) else 0
        )
    }
}

@Composable
fun BackupTypeToggle(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
fun SelectAllListItem(
    isChecked: Boolean,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick ?: onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.SelectAll,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .padding(8.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.toggle_all),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (onClick != null) {
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
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = isChecked,
            onCheckedChange = { onToggle() },
            thumbContent = if (isChecked) {
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
fun AppListItem(
    app: AppInfo,
    subtitle: String? = null,
    onClick: () -> Unit,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = rememberAsyncImagePainter(model = app.icon),
            contentDescription = app.name,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
            checked = app.isSelected,
            onCheckedChange = { onToggle() },
            thumbContent = if (app.isSelected) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupTypesBottomSheet(
    title: String,
    backupTypes: BackupTypes,
    onBackupTypesChange: (BackupTypes) -> Unit,
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column {
                    BackupTypeToggle(
                        label = stringResource(R.string.backup_type_apk),
                        description = stringResource(R.string.backup_type_apk_desc),
                        checked = backupTypes.apk
                    ) {
                        onBackupTypesChange(backupTypes.copy(apk = it))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    BackupTypeToggle(
                        label = stringResource(R.string.backup_type_data),
                        description = stringResource(R.string.backup_type_data_desc),
                        checked = backupTypes.data
                    ) {
                        onBackupTypesChange(backupTypes.copy(data = it))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    BackupTypeToggle(
                        label = stringResource(R.string.backup_type_device_protected_data),
                        description = stringResource(R.string.backup_type_device_protected_data_desc),
                        checked = backupTypes.deviceProtectedData
                    ) {
                        onBackupTypesChange(backupTypes.copy(deviceProtectedData = it))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    BackupTypeToggle(
                        label = stringResource(R.string.backup_type_external_data),
                        description = stringResource(R.string.backup_type_external_data_desc),
                        checked = backupTypes.externalData
                    ) {
                        onBackupTypesChange(backupTypes.copy(externalData = it))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    BackupTypeToggle(
                        label = stringResource(R.string.backup_type_obb_data),
                        description = stringResource(R.string.backup_type_obb_data_desc),
                        checked = backupTypes.obb
                    ) {
                        onBackupTypesChange(backupTypes.copy(obb = it))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    BackupTypeToggle(
                        label = stringResource(R.string.backup_type_media_data),
                        description = stringResource(R.string.backup_type_media_data_desc),
                        checked = backupTypes.media
                    ) {
                        onBackupTypesChange(backupTypes.copy(media = it))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    BackupTypeToggle(
                        label = stringResource(R.string.backup_type_permissions),
                        description = stringResource(R.string.backup_type_permissions_desc),
                        checked = backupTypes.permissions
                    ) {
                        onBackupTypesChange(backupTypes.copy(permissions = it))
                    }
                }
            }
        }
    }
}

@Composable
fun BackupConfigScreen(
    isLoadingApps: Boolean,
    apps: List<AppInfo>,
    appBackupTypes: Map<String, BackupTypes>,
    backupTypes: BackupTypes,
    onRefreshApps: () -> Unit,
    onToggleAllApps: () -> Unit,
    onToggleAppSelection: (String) -> Unit,
    onSetSelectedAppsBackupTypes: (BackupTypes) -> Unit,
    onSetAppBackupTypes: (String, BackupTypes) -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedAppPackageName by remember { mutableStateOf<String?>(null) }
    var showBulkBackupTypesSheet by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshApps()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isLoadingApps) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
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
                        val isAllSelected = apps.isNotEmpty() && apps.all { it.isSelected }
                        SelectAllListItem(
                            isChecked = isAllSelected,
                            subtitle = buildSelectedBackupTypesSummary(apps, appBackupTypes, backupTypes, LocalContext.current),
                            onClick = { showBulkBackupTypesSheet = true },
                            onToggle = onToggleAllApps
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.background)
                        apps.forEachIndexed { index, app ->
                            val currentAppBackupTypes = appBackupTypes[app.packageName] ?: backupTypes
                            AppListItem(
                                app = app,
                                subtitle = buildBackupTypesSummary(currentAppBackupTypes, LocalContext.current),
                                onClick = { selectedAppPackageName = app.packageName },
                                onToggle = { onToggleAppSelection(app.packageName) }
                            )
                            if (index < apps.size - 1) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.background)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBulkBackupTypesSheet) {
        BackupTypesBottomSheet(
            title = stringResource(R.string.backup_types_for_selected_apps),
            backupTypes = selectedBulkBackupTypes(apps, appBackupTypes, backupTypes),
            onBackupTypesChange = onSetSelectedAppsBackupTypes,
            onDismissRequest = { showBulkBackupTypesSheet = false }
        )
    }

    selectedAppPackageName?.let { packageName ->
        val app = apps.firstOrNull { it.packageName == packageName }
        if (app != null) {
            BackupTypesBottomSheet(
                title = app.name,
                backupTypes = appBackupTypes[packageName] ?: backupTypes,
                onBackupTypesChange = { onSetAppBackupTypes(packageName, it) },
                onDismissRequest = { selectedAppPackageName = null }
            )
        }
    }
}

@Composable
fun ForgetConfigScreen(
    maintenance: io.github.hddq.restoid.model.MaintenanceConfig,
    onKeepLastChange: (Int) -> Unit,
    onKeepDailyChange: (Int) -> Unit,
    onKeepWeeklyChange: (Int) -> Unit,
    onKeepMonthlyChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column {
                    PolicySlider(stringResource(R.string.maintenance_keep_last), maintenance.keepLast, 0..20, onKeepLastChange)
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    PolicySlider(stringResource(R.string.maintenance_keep_daily), maintenance.keepDaily, 0..30, onKeepDailyChange)
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    PolicySlider(stringResource(R.string.maintenance_keep_weekly), maintenance.keepWeekly, 0..12, onKeepWeeklyChange)
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    PolicySlider(stringResource(R.string.maintenance_keep_monthly), maintenance.keepMonthly, 0..24, onKeepMonthlyChange)
                }
            }
        }
    }
}

@Composable
fun CheckConfigScreen(
    maintenance: io.github.hddq.restoid.model.MaintenanceConfig,
    onReadDataChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp, top = 8.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                TaskRow(
                    title = stringResource(R.string.maintenance_read_all_data),
                    subtitle = stringResource(R.string.run_tasks_check_read_all_data_description),
                    checked = maintenance.readData,
                    onCheckedChange = onReadDataChange
                )
            }
        }
    }
}

fun buildBackupSubtitle(
    apps: List<AppInfo>,
    appBackupTypes: Map<String, BackupTypes>,
    defaultBackupTypes: BackupTypes,
    context: android.content.Context
): String {
    val selectedCount = apps.count { it.isSelected }
    return context.getString(
        R.string.run_tasks_backup_subtitle,
        selectedCount,
        buildSelectedBackupTypesSummary(apps, appBackupTypes, defaultBackupTypes, context)
    )
}

fun buildBackupTypesSummary(backupTypes: BackupTypes, context: android.content.Context): String {
    val types = buildList {
        if (backupTypes.apk) add(context.getString(R.string.backup_type_apk))
        if (backupTypes.data) add(context.getString(R.string.backup_type_data))
        if (backupTypes.deviceProtectedData) add(context.getString(R.string.backup_type_device_protected_data))
        if (backupTypes.externalData) add(context.getString(R.string.backup_type_external_data))
        if (backupTypes.obb) add(context.getString(R.string.backup_type_obb_data))
        if (backupTypes.media) add(context.getString(R.string.backup_type_media_data))
        if (backupTypes.permissions) add(context.getString(R.string.backup_type_permissions))
    }.joinToString(", ")

    return types.ifBlank { context.getString(R.string.backup_types_none) }
}

fun buildSelectedBackupTypesSummary(
    apps: List<AppInfo>,
    appBackupTypes: Map<String, BackupTypes>,
    defaultBackupTypes: BackupTypes,
    context: android.content.Context
): String {
    val selectedTypes = apps
        .filter { it.isSelected }
        .map { appBackupTypes[it.packageName] ?: defaultBackupTypes }
        .distinct()

    return when (selectedTypes.size) {
        0 -> buildBackupTypesSummary(defaultBackupTypes, context)
        1 -> buildBackupTypesSummary(selectedTypes.first(), context)
        else -> context.getString(R.string.backup_types_mixed)
    }
}

fun selectedBulkBackupTypes(
    apps: List<AppInfo>,
    appBackupTypes: Map<String, BackupTypes>,
    defaultBackupTypes: BackupTypes
): BackupTypes {
    return apps
        .firstOrNull { it.isSelected }
        ?.let { appBackupTypes[it.packageName] ?: defaultBackupTypes }
        ?: defaultBackupTypes
}

fun buildForgetSubtitle(config: io.github.hddq.restoid.model.MaintenanceConfig, context: android.content.Context): String {
    return context.getString(
        R.string.run_tasks_forget_subtitle,
        config.keepLast,
        config.keepDaily,
        config.keepWeekly,
        config.keepMonthly
    )
}

fun buildCheckSubtitle(config: io.github.hddq.restoid.model.MaintenanceConfig, context: android.content.Context): String {
    return if (config.readData) {
        context.getString(R.string.maintenance_read_all_data)
    } else {
        context.getString(R.string.run_tasks_check_metadata_only)
    }
}

fun buildCustomDirectoriesSubtitle(directories: List<io.github.hddq.restoid.model.CustomDirectory>, context: android.content.Context): String {
    val selectedCount = directories.count { it.isSelected }
    return if (selectedCount == 0) {
        context.getString(io.github.hddq.restoid.R.string.run_tasks_custom_directories_subtitle_none)
    } else {
        context.getString(io.github.hddq.restoid.R.string.run_tasks_custom_directories_subtitle_count, selectedCount)
    }
}

@Composable
fun CustomDirectoriesConfigScreen(
    customDirectories: List<io.github.hddq.restoid.model.CustomDirectory>,
    onAddDirectory: (String) -> Unit,
    onToggleDirectory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var typedPath by remember { mutableStateOf("") }
    var typedError by remember { mutableStateOf<String?>(null) }
    var showBrowser by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            if (uri.scheme == "content") {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onAddDirectory(uri.toString())
        }
    }
    val invalidMsg = androidx.compose.ui.res.stringResource(io.github.hddq.restoid.R.string.custom_directories_typed_path_invalid)
    val submitTypedPath = {
        val normalized = io.github.hddq.restoid.util.StorageUtils.normalizeTypedPathToFileUri(typedPath)
        if (normalized == null) {
            typedError = invalidMsg
        } else {
            typedError = null
            typedPath = ""
            onAddDirectory(normalized)
        }
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp, top = 8.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
    ) {
        item {
            androidx.compose.material3.Text(
                text = androidx.compose.ui.res.stringResource(io.github.hddq.restoid.R.string.custom_directories_description),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (customDirectories.isNotEmpty()) {
            item {
                androidx.compose.material3.Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    androidx.compose.foundation.layout.Column {
                        customDirectories.forEachIndexed { index, customDir ->
                            val uri = android.net.Uri.parse(customDir.uri)
                            val realPath = if (uri.scheme == "file") {
                                uri.path
                            } else {
                                io.github.hddq.restoid.util.StorageUtils.getPathFromTreeUri(uri)
                            }
                            val displayPath = realPath ?: (uri.lastPathSegment ?: customDir.uri)
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleDirectory(customDir.uri) }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                                    val title = displayPath.substringAfterLast("/")
                                    androidx.compose.material3.Text(
                                        text = title.ifEmpty { displayPath },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                                    )
                                    androidx.compose.material3.Text(
                                        text = displayPath,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(16.dp))
                                androidx.compose.material3.Switch(
                                    checked = customDir.isSelected,
                                    onCheckedChange = { onToggleDirectory(customDir.uri) },
                                    thumbContent = if (customDir.isSelected) {
                                        {
                                            androidx.compose.material3.Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(androidx.compose.material3.SwitchDefaults.IconSize)
                                            )
                                        }
                                    } else {
                                        null
                                    }
                                )
                            }
                            if (index < customDirectories.size - 1) {
                                androidx.compose.material3.HorizontalDivider(color = androidx.compose.material3.MaterialTheme.colorScheme.background)
                            }
                        }
                    }
                }
            }
        }

        item {
            FilledTonalButton(
                onClick = { launcher.launch(null) },
                shape = androidx.compose.material3.MaterialTheme.shapes.extraLarge,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Add,
                    contentDescription = null
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(io.github.hddq.restoid.R.string.action_add_directory))
            }
        }

        item {
            FilledTonalButton(
                onClick = { showBrowser = true },
                shape = androidx.compose.material3.MaterialTheme.shapes.extraLarge,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(io.github.hddq.restoid.R.string.action_browse_folders))
            }
        }

        item {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.OutlinedTextField(
                    value = typedPath,
                    onValueChange = {
                        typedPath = it
                        typedError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(io.github.hddq.restoid.R.string.custom_directories_typed_path_label)) },
                    placeholder = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(io.github.hddq.restoid.R.string.custom_directories_typed_path_hint)) },
                    supportingText = {
                        androidx.compose.material3.Text(
                            typedError ?: androidx.compose.ui.res.stringResource(io.github.hddq.restoid.R.string.custom_directories_all_files_access_hint)
                        )
                    },
                    isError = typedError != null,
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { submitTypedPath() }
                    )
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = { submitTypedPath() },
                    shape = androidx.compose.material3.MaterialTheme.shapes.extraLarge
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Add,
                        contentDescription = null
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(io.github.hddq.restoid.R.string.action_add_path))
                }
            }
        }
    }

    if (showBrowser) {
        FolderBrowserBottomSheet(
            initialPath = typedPath,
            onSelectPath = {
                typedPath = it
                typedError = null
                showBrowser = false
            },
            onDismissRequest = { showBrowser = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderBrowserBottomSheet(
    initialPath: String?,
    onSelectPath: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val storageRoots = remember {
        io.github.hddq.restoid.util.StorageUtils.listStorageRoots()
    }
    var currentPath by remember {
        mutableStateOf(
            initialPath?.let { raw ->
                io.github.hddq.restoid.util.StorageUtils.normalizeTypedPathToFileUri(raw)
                    ?.let { io.github.hddq.restoid.util.StorageUtils.pathFromFileUri(it) }
                    ?.takeIf { java.io.File(it).isDirectory }
            } ?: storageRoots.firstOrNull() ?: "/"
        )
    }
    var atRoots by remember {
        mutableStateOf(
            initialPath?.let { raw ->
                io.github.hddq.restoid.util.StorageUtils.normalizeTypedPathToFileUri(raw)
                    ?.let { io.github.hddq.restoid.util.StorageUtils.pathFromFileUri(it) }
                    ?.let { java.io.File(it).isDirectory } ?: false
            } != true && storageRoots.isNotEmpty()
        )
    }
    var showHidden by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<String>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    androidx.compose.runtime.LaunchedEffect(currentPath, showHidden, atRoots) {
        isLoading = true
        entries = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (atRoots) {
                storageRoots
            } else {
                io.github.hddq.restoid.util.StorageUtils.listChildDirectories(currentPath, showHidden)
            }
        }
        isLoading = false
    }

    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismissRequest) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            Text(
                text = if (atRoots) {
                    stringResource(io.github.hddq.restoid.R.string.folder_browser_roots_title)
                } else {
                    currentPath
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (!atRoots) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showHidden = !showHidden }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(io.github.hddq.restoid.R.string.folder_browser_show_hidden),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    val showHiddenLabel = stringResource(io.github.hddq.restoid.R.string.folder_browser_show_hidden)
                    Switch(
                        checked = showHidden,
                        onCheckedChange = { showHidden = it },
                        modifier = Modifier.semantics {
                            contentDescription = showHiddenLabel
                        }
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.background)
            }
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    entries == null -> {
                        Text(
                            text = stringResource(io.github.hddq.restoid.R.string.folder_browser_cannot_list),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                    else -> {
                        val parent = if (atRoots) null else java.io.File(currentPath).parent
                        if (!atRoots && parent != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val listable = try {
                                            java.io.File(parent).let { it.isDirectory && it.list() != null }
                                        } catch (_: Exception) {
                                            false
                                        }
                                        if (listable) {
                                            currentPath = parent
                                        } else {
                                            atRoots = true
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = stringResource(io.github.hddq.restoid.R.string.folder_browser_up),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.background)
                        }
                        if (entries!!.isEmpty()) {
                            Text(
                                text = stringResource(io.github.hddq.restoid.R.string.folder_browser_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                        entries!!.forEachIndexed { index, name ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (atRoots) {
                                            currentPath = name
                                            atRoots = false
                                        } else {
                                            currentPath = java.io.File(currentPath, name).absolutePath
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (index < entries!!.size - 1) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.background)
                            }
                        }
                    }
                }
            }
            if (!atRoots) {
                Spacer(modifier = Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = { onSelectPath(currentPath) },
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(io.github.hddq.restoid.R.string.folder_browser_select_this_folder))
                }
            }
        }
    }
}
