package io.github.hddq.restoid.ui.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.hddq.restoid.R
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.work.ExclusionConflict
import io.github.hddq.restoid.ui.components.ExpressiveCardGroup
import io.github.hddq.restoid.ui.components.ExpressiveSectionHeader
import io.github.hddq.restoid.ui.components.ExpressiveSegmentedSurface
import coil.compose.AsyncImage
import kotlin.math.roundToInt

@Composable
fun TaskRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onNavigate: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val rowClick = onNavigate ?: { onCheckedChange(!checked) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, onClick = rowClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .height(24.dp)
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
    icon: ImageVector? = null,
    onValueChange: (Int) -> Unit
) {
    val isDiscrete = (range.last - range.first) <= 30

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (value > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
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
fun BackupTypeToggle(
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
    onToggle: () -> Unit,
    iconSize: Dp = 48.dp,
    iconPadding: Dp = 8.dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick ?: onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.SelectAll,
            contentDescription = null,
            modifier = Modifier
                .size(iconSize)
                .padding(iconPadding),
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
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .height(24.dp)
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
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = app.icon,
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
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
            ExpressiveCardGroup {
                ExpressiveSegmentedSurface(index = 0, count = 9) {
                    BackupTypeToggle(
                        label = stringResource(R.string.backup_type_apk),
                        description = stringResource(R.string.backup_type_apk_desc),
                        icon = Icons.Outlined.Android,
                        checked = backupTypes.apk
                    ) {
                        onBackupTypesChange(backupTypes.copy(apk = it))
                    }
                }
                ExpressiveSegmentedSurface(index = 1, count = 9) {
                    BackupTypeToggle(
                        label = stringResource(R.string.backup_type_data),
                        description = stringResource(R.string.backup_type_data_desc),
                        icon = Icons.Outlined.Storage,
                        checked = backupTypes.data
                    ) {
                        onBackupTypesChange(backupTypes.copy(data = it))
                    }
                }
                ExpressiveSegmentedSurface(index = 2, count = 9) {
                    BackupTypeToggle(
                        label = stringResource(R.string.backup_type_device_protected_data),
                        description = stringResource(R.string.backup_type_device_protected_data_desc),
                        icon = Icons.Outlined.Shield,
                        checked = backupTypes.deviceProtectedData
                    ) {
                        onBackupTypesChange(backupTypes.copy(deviceProtectedData = it))
                    }
                }
                ExpressiveSegmentedSurface(index = 3, count = 9) {
                    BackupTypeToggle(
                        label = stringResource(R.string.backup_type_external_data),
                        description = stringResource(R.string.backup_type_external_data_desc),
                        icon = Icons.Outlined.SdCard,
                        checked = backupTypes.externalData
                    ) {
                        onBackupTypesChange(backupTypes.copy(externalData = it))
                    }
                }
                ExpressiveSegmentedSurface(index = 4, count = 9) {
                    BackupTypeToggle(
                        label = stringResource(R.string.backup_type_obb_data),
                        description = stringResource(R.string.backup_type_obb_data_desc),
                        icon = Icons.Outlined.Inventory2,
                        checked = backupTypes.obb
                    ) {
                        onBackupTypesChange(backupTypes.copy(obb = it))
                    }
                }
                ExpressiveSegmentedSurface(index = 5, count = 9) {
                    BackupTypeToggle(
                        label = stringResource(R.string.backup_type_media_data),
                        description = stringResource(R.string.backup_type_media_data_desc),
                        icon = Icons.Outlined.PermMedia,
                        checked = backupTypes.media
                    ) {
                        onBackupTypesChange(backupTypes.copy(media = it))
                    }
                }
                ExpressiveSegmentedSurface(index = 6, count = 9) {
                    BackupTypeToggle(
                        label = stringResource(R.string.backup_type_permissions),
                        description = stringResource(R.string.backup_type_permissions_desc),
                        icon = Icons.Outlined.Key,
                        checked = backupTypes.permissions
                    ) {
                        onBackupTypesChange(backupTypes.copy(permissions = it))
                    }
                }
                ExpressiveSegmentedSurface(index = 7, count = 9) {
                    BackupTypeToggle(
                        label = stringResource(R.string.backup_type_special_access),
                        description = stringResource(R.string.backup_type_special_access_desc),
                        icon = Icons.Outlined.AdminPanelSettings,
                        checked = backupTypes.specialAccess
                    ) {
                        onBackupTypesChange(backupTypes.copy(specialAccess = it))
                    }
                }
                ExpressiveSegmentedSurface(index = 8, count = 9) {
                    BackupTypeToggle(
                        label = stringResource(R.string.backup_type_accessibility),
                        description = stringResource(R.string.backup_type_accessibility_desc),
                        icon = Icons.Outlined.AccessibilityNew,
                        checked = backupTypes.accessibility
                    ) {
                        onBackupTypesChange(backupTypes.copy(accessibility = it))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    val context = LocalContext.current

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

    val isAllSelected = remember(apps) {
        apps.isNotEmpty() && apps.all { it.isSelected }
    }
    val selectAllSubtitle = remember(apps, appBackupTypes, backupTypes, context) {
        buildSelectedBackupTypesSummary(apps, appBackupTypes, backupTypes, context)
    }
    val defaultBackupTypesSummary = remember(backupTypes, context) {
        buildBackupTypesSummary(backupTypes, context)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (isLoadingApps) {
            item(key = "loading", contentType = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }
        } else {
            val totalRows = apps.size + 1
            item(key = "header", contentType = "header") {
                ExpressiveSectionHeader(title = stringResource(R.string.apps_title))
            }
            item(key = "select_all", contentType = "select_all") {
                ExpressiveSegmentedSurface(index = 0, count = totalRows) {
                    SelectAllListItem(
                        isChecked = isAllSelected,
                        subtitle = selectAllSubtitle,
                        onClick = { showBulkBackupTypesSheet = true },
                        onToggle = onToggleAllApps
                    )
                }
            }
            itemsIndexed(
                items = apps,
                key = { _, app -> app.packageName },
                contentType = { _, _ -> "app_item" }
            ) { index, app ->
                val currentAppBackupTypes = appBackupTypes[app.packageName] ?: backupTypes
                val itemSubtitle = if (currentAppBackupTypes == backupTypes) {
                    defaultBackupTypesSummary
                } else {
                    remember(currentAppBackupTypes, context) {
                        buildBackupTypesSummary(currentAppBackupTypes, context)
                    }
                }
                ExpressiveSegmentedSurface(index = index + 1, count = totalRows) {
                    AppListItem(
                        app = app,
                        subtitle = itemSubtitle,
                        onClick = { selectedAppPackageName = app.packageName },
                        onToggle = { onToggleAppSelection(app.packageName) }
                    )
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
            ExpressiveCardGroup {
                ExpressiveSegmentedSurface(index = 0, count = 4) {
                    PolicySlider(
                        label = stringResource(R.string.maintenance_keep_last),
                        value = maintenance.keepLast,
                        range = 0..20,
                        icon = Icons.Outlined.History,
                        onValueChange = onKeepLastChange
                    )
                }
                ExpressiveSegmentedSurface(index = 1, count = 4) {
                    PolicySlider(
                        label = stringResource(R.string.maintenance_keep_daily),
                        value = maintenance.keepDaily,
                        range = 0..30,
                        icon = Icons.Outlined.Today,
                        onValueChange = onKeepDailyChange
                    )
                }
                ExpressiveSegmentedSurface(index = 2, count = 4) {
                    PolicySlider(
                        label = stringResource(R.string.maintenance_keep_weekly),
                        value = maintenance.keepWeekly,
                        range = 0..12,
                        icon = Icons.Outlined.DateRange,
                        onValueChange = onKeepWeeklyChange
                    )
                }
                ExpressiveSegmentedSurface(index = 3, count = 4) {
                    PolicySlider(
                        label = stringResource(R.string.maintenance_keep_monthly),
                        value = maintenance.keepMonthly,
                        range = 0..24,
                        icon = Icons.Outlined.CalendarMonth,
                        onValueChange = onKeepMonthlyChange
                    )
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
            ExpressiveSegmentedSurface(index = 0, count = 1) {
                TaskRow(
                    title = stringResource(R.string.maintenance_read_all_data),
                    subtitle = stringResource(R.string.run_tasks_check_read_all_data_description),
                    icon = Icons.Outlined.ManageSearch,
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
        if (backupTypes.specialAccess) add(context.getString(R.string.backup_type_special_access))
        if (backupTypes.accessibility) add(context.getString(R.string.backup_type_accessibility))
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

fun buildCustomDirectoriesSubtitle(
    directories: List<io.github.hddq.restoid.model.CustomDirectory>,
    excludedDirectories: List<io.github.hddq.restoid.model.CustomDirectory>,
    context: android.content.Context
): String {
    val selectedCount = directories.count { it.isSelected }
    val excludedCount = excludedDirectories.count { it.isSelected }
    return when {
        selectedCount == 0 -> {
            context.getString(io.github.hddq.restoid.R.string.run_tasks_custom_directories_subtitle_none)
        }
        excludedCount > 0 -> {
            context.getString(io.github.hddq.restoid.R.string.run_tasks_custom_directories_subtitle_with_exclusions, selectedCount, excludedCount)
        }
        else -> {
            context.getString(io.github.hddq.restoid.R.string.run_tasks_custom_directories_subtitle_count, selectedCount)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CustomDirectoriesConfigScreen(
    customDirectories: List<io.github.hddq.restoid.model.CustomDirectory>,
    onAddDirectory: (String) -> Unit,
    onToggleDirectory: (String) -> Unit,
    onRemoveDirectory: (String) -> Unit = {},
    excludedDirectories: List<io.github.hddq.restoid.model.CustomDirectory> = emptyList(),
    onAddExcludedDirectory: (String) -> Unit = {},
    onToggleExcludedDirectory: (String) -> Unit = {},
    onRemoveExcludedDirectory: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showFolderPicker by remember { mutableStateOf(false) }
    var showExcludePicker by remember { mutableStateOf(false) }
    var excludePickerInitialPath by remember { mutableStateOf<String?>(null) }
    var menuExpandedForUri by remember { mutableStateOf<String?>(null) }
    var menuExpandedForExcludedUri by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.custom_directories_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (customDirectories.isNotEmpty() || excludedDirectories.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.custom_directories_long_press_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            ExpressiveSectionHeader(
                title = stringResource(R.string.custom_directories_section_include)
            )
        }

        if (customDirectories.isNotEmpty()) {
            item {
                ExpressiveCardGroup {
                    customDirectories.forEachIndexed { index, customDir ->
                        key(customDir.uri) {
                        val resolvedPath = io.github.hddq.restoid.util.StorageUtils.resolveCustomDirectoryPath(customDir.uri)
                        val displayPath = resolvedPath ?: runCatching {
                            android.net.Uri.parse(customDir.uri).lastPathSegment
                        }.getOrNull() ?: customDir.uri

                        ExpressiveSegmentedSurface(
                            index = index,
                            count = customDirectories.size
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { onToggleDirectory(customDir.uri) },
                                            onLongClick = { menuExpandedForUri = customDir.uri }
                                        )
                                        .padding(vertical = 12.dp, horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val initialIsDir = io.github.hddq.restoid.util.StorageUtils.isDirectoryFast(customDir.uri)
                                    val isDirectory by produceState(
                                        initialValue = initialIsDir ?: true,
                                        key1 = customDir.uri
                                    ) {
                                        if (initialIsDir == null) {
                                            value = withContext(Dispatchers.IO) {
                                                io.github.hddq.restoid.util.StorageUtils.isDirectoryPath(customDir.uri)
                                            }
                                        }
                                    }
                                    Icon(
                                        imageVector = if (isDirectory) Icons.Outlined.Folder else Icons.Outlined.Description,
                                        contentDescription = null,
                                        tint = if (customDir.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 16.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        val title = displayPath.substringAfterLast("/")
                                        Text(
                                            text = title.ifEmpty { displayPath },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = displayPath,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Switch(
                                        checked = customDir.isSelected,
                                        onCheckedChange = { onToggleDirectory(customDir.uri) },
                                        thumbContent = if (customDir.isSelected) {
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

                                DropdownMenu(
                                    expanded = menuExpandedForUri == customDir.uri,
                                    onDismissRequest = { menuExpandedForUri = null }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(text = stringResource(R.string.action_exclude_subfolder))
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.FolderOff,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            menuExpandedForUri = null
                                            excludePickerInitialPath = resolvedPath ?: customDir.uri
                                            showExcludePicker = true
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
                                            menuExpandedForUri = null
                                            onRemoveDirectory(customDir.uri)
                                        }
                                    )
                                }
                            }
                        }
                        }
                    }
                }
            }
        }

        item {
            FilledTonalButton(
                onClick = { showFolderPicker = true },
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_add_directory))
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ExpressiveSectionHeader(
                    title = stringResource(R.string.custom_directories_section_exclude)
                )
                Text(
                    text = stringResource(R.string.custom_directories_exclude_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (excludedDirectories.isNotEmpty()) {
            item {
                ExpressiveCardGroup {
                    excludedDirectories.forEachIndexed { index, excludedDir ->
                        key(excludedDir.uri) {
                            val resolvedPath = io.github.hddq.restoid.util.StorageUtils.resolveCustomDirectoryPath(excludedDir.uri)
                            val displayPath = resolvedPath ?: runCatching {
                                android.net.Uri.parse(excludedDir.uri).lastPathSegment
                            }.getOrNull() ?: excludedDir.uri

                            ExpressiveSegmentedSurface(
                                index = index,
                                count = excludedDirectories.size
                            ) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = { onToggleExcludedDirectory(excludedDir.uri) },
                                                onLongClick = { menuExpandedForExcludedUri = excludedDir.uri }
                                            )
                                            .padding(vertical = 12.dp, horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val initialIsDir = io.github.hddq.restoid.util.StorageUtils.isDirectoryFast(excludedDir.uri)
                                        val isDirectory by produceState(
                                            initialValue = initialIsDir ?: true,
                                            key1 = excludedDir.uri
                                        ) {
                                            if (initialIsDir == null) {
                                                value = withContext(Dispatchers.IO) {
                                                    io.github.hddq.restoid.util.StorageUtils.isDirectoryPath(excludedDir.uri)
                                                }
                                            }
                                        }
                                        Icon(
                                            imageVector = if (isDirectory) Icons.Outlined.FolderOff else Icons.Outlined.Description,
                                            contentDescription = null,
                                            tint = if (excludedDir.isSelected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(end = 16.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            val title = displayPath.substringAfterLast("/")
                                            Text(
                                                text = title.ifEmpty { displayPath },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Text(
                                                text = displayPath,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Switch(
                                            checked = excludedDir.isSelected,
                                            onCheckedChange = { onToggleExcludedDirectory(excludedDir.uri) },
                                            thumbContent = if (excludedDir.isSelected) {
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

                                    DropdownMenu(
                                        expanded = menuExpandedForExcludedUri == excludedDir.uri,
                                        onDismissRequest = { menuExpandedForExcludedUri = null }
                                    ) {
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
                                                menuExpandedForExcludedUri = null
                                                onRemoveExcludedDirectory(excludedDir.uri)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            FilledTonalButton(
                onClick = {
                    excludePickerInitialPath = customDirectories.firstOrNull()?.let {
                        io.github.hddq.restoid.util.StorageUtils.resolveCustomDirectoryPath(it.uri)
                    }
                    showExcludePicker = true
                },
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_add_excluded_directory))
            }
        }
    }

    if (showFolderPicker) {
        io.github.hddq.restoid.ui.components.FolderPickerSheet(
            allowSystemPaths = true,
            allowFiles = true,
            onSelectPath = { selectedPath ->
                showFolderPicker = false
                onAddDirectory(selectedPath)
            },
            onDismissRequest = { showFolderPicker = false }
        )
    }

    if (showExcludePicker) {
        io.github.hddq.restoid.ui.components.FolderPickerSheet(
            initialPath = excludePickerInitialPath,
            allowSystemPaths = true,
            allowFiles = true,
            onSelectPath = { selectedPath ->
                showExcludePicker = false
                onAddExcludedDirectory(selectedPath)
            },
            onDismissRequest = { showExcludePicker = false }
        )
    }
}

@Composable
fun ExclusionConflictWarningCard(
    conflicts: List<ExclusionConflict>,
    modifier: Modifier = Modifier
) {
    var lastNonEmptyConflicts by remember { mutableStateOf(conflicts) }
    if (conflicts.isNotEmpty()) {
        lastNonEmptyConflicts = conflicts
    }

    AnimatedVisibility(
        visible = conflicts.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        val displayConflicts = lastNonEmptyConflicts
        var expanded by remember { mutableStateOf(false) }
        val maxInitial = 3
        val hasMore = displayConflicts.size > maxInitial
        val visibleConflicts = if (expanded || !hasMore) displayConflicts else displayConflicts.take(maxInitial)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .animateContentSize()
                .then(
                    if (hasMore) {
                        Modifier.clickable { expanded = !expanded }
                    } else {
                        Modifier
                    }
                ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.warning_exclusion_conflict_header),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    visibleConflicts.forEach { conflict ->
                        val appName = conflict.app.name.ifBlank { conflict.app.packageName }
                        Text(
                            text = buildAnnotatedString {
                                append("• ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(appName)
                                }
                                append(" — ")
                                append(conflict.conflictingPath)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f)
                        )
                    }
                    if (hasMore) {
                        Text(
                            text = if (!expanded) {
                                stringResource(R.string.warning_exclusion_conflict_show_more, displayConflicts.size - maxInitial)
                            } else {
                                stringResource(R.string.warning_exclusion_conflict_show_less)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
