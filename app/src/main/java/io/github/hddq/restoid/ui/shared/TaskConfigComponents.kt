package io.github.hddq.restoid.ui.shared

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
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
import io.github.hddq.restoid.LocalAppBarActions
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.ForgetDryRunResult
import io.github.hddq.restoid.data.SnapshotInfo
import io.github.hddq.restoid.ui.components.ExpressiveEmptyState
import io.github.hddq.restoid.ui.components.ForgetGuideBottomSheet
import io.github.hddq.restoid.ui.home.SnapshotWithMetadata
import io.github.hddq.restoid.ui.screens.SnapshotItem
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.model.BackupTag
import io.github.hddq.restoid.model.BackupTagRules
import io.github.hddq.restoid.model.CalendarInfo
import io.github.hddq.restoid.model.ContactAccountInfo
import io.github.hddq.restoid.work.ExclusionConflict
import io.github.hddq.restoid.ui.components.BackupTagPickerSheet
import io.github.hddq.restoid.ui.components.ExpressiveCardGroup
import io.github.hddq.restoid.ui.components.ExpressiveSectionHeader
import io.github.hddq.restoid.ui.components.ExpressiveSegmentedSurface
import io.github.hddq.restoid.ui.components.PullToRefreshBox
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
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    subtitleContent: (@Composable () -> Unit)? = null
) {
    val rowClick = onNavigate ?: { onCheckedChange(!checked) }

    Row(
        modifier = modifier
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
            if (subtitleContent != null) {
                subtitleContent()
            } else if (subtitle != null) {
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
        } else {
            Spacer(Modifier.width(12.dp))
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
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    selectAllApps: Boolean = false,
    onSetSelectAllApps: (Boolean) -> Unit = {}
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
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 24.dp + contentPadding.calculateBottomPadding(),
            top = 8.dp
        ),
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
            item(key = "include_new_apps", contentType = "include_new_apps") {
                ExpressiveSegmentedSurface(
                    index = 0,
                    count = 1,
                    modifier = Modifier.padding(bottom = 14.dp)
                ) {
                    TaskRow(
                        title = stringResource(R.string.backup_include_new_apps_title),
                        subtitle = stringResource(R.string.backup_include_new_apps_subtitle),
                        icon = Icons.Outlined.InstallMobile,
                        checked = selectAllApps,
                        onCheckedChange = onSetSelectAllApps
                    )
                }
            }
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
    availableHosts: List<ForgetHostOption>,
    areRepoSnapshotsLoaded: Boolean,
    isLoadingHosts: Boolean,
    hostsError: String?,
    onToggleAllHosts: () -> Unit,
    onToggleHost: (String) -> Unit,
    onAddHost: (String) -> Unit,
    onRemoveHost: (String) -> Unit,
    onRefreshHosts: () -> Unit,
    onAutoLoadHosts: () -> Unit,
    availableTags: List<BackupTag>,
    onToggleForgetTag: (String) -> Unit,
    onCreateForgetTag: suspend (name: String, colorArgb: Int) -> Result<BackupTag>,
    onUpdateTagColor: suspend (name: String, colorArgb: Int) -> Result<BackupTag>,
    onDeleteTag: suspend (name: String) -> Result<Unit>,
    onOpenDryRun: () -> Unit = {},
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    var showHostsSheet by rememberSaveable { mutableStateOf(false) }
    var showTagsSheet by rememberSaveable { mutableStateOf(false) }
    var showGuideSheet by rememberSaveable { mutableStateOf(false) }
    val appBarActions = LocalAppBarActions.current

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

    LaunchedEffect(Unit) {
        if (!areRepoSnapshotsLoaded) {
            onAutoLoadHosts()
        }
    }

    val selectedHosts = remember(maintenance.forgetHosts) {
        normalizeForgetHosts(maintenance.forgetHosts)
    }
    val selectedHostSet = remember(selectedHosts) {
        selectedHosts.toSet()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 24.dp + contentPadding.calculateBottomPadding(),
            top = 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "retention_policy_section", contentType = "retention_policy") {
            Column(modifier = Modifier.fillMaxWidth()) {
                ExpressiveSectionHeader(title = stringResource(R.string.forget_section_retention_policy))
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

        item(key = "hosts_mode_section", contentType = "hosts_mode") {
            Column(modifier = Modifier.fillMaxWidth()) {
                ExpressiveSectionHeader(title = stringResource(R.string.forget_section_hosts))

                val isEmptySelection = !maintenance.forgetAllHosts && selectedHosts.isEmpty()
                val containerColor by animateColorAsState(
                    targetValue = if (isEmptySelection) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                    label = "ForgetHostsPickerContainerColor"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isEmptySelection) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                    label = "ForgetHostsPickerContentColor"
                )
                val secondaryColor by animateColorAsState(
                    targetValue = if (isEmptySelection) {
                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                    label = "ForgetHostsPickerSecondaryColor"
                )
                val pickerSubtitle = when {
                    maintenance.forgetAllHosts -> stringResource(R.string.forget_hosts_all_title)
                    isEmptySelection -> stringResource(R.string.forget_hosts_empty_warning)
                    else -> selectedHosts.joinToString(", ")
                }

                ExpressiveSegmentedSurface(
                    index = 0,
                    count = 1,
                    containerColor = containerColor,
                    contentColor = contentColor,
                    modifier = Modifier.animateContentSize(
                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showHostsSheet = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isEmptySelection) Icons.Outlined.WarningAmber else Icons.Outlined.Dns,
                            contentDescription = null,
                            tint = if (isEmptySelection) contentColor else MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.forget_hosts_picker_title),
                                style = MaterialTheme.typography.bodyLarge,
                                color = contentColor
                            )
                            Text(
                                text = pickerSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        if (!isEmptySelection && !maintenance.forgetAllHosts) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Text(
                                    text = selectedHosts.size.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = secondaryColor
                        )
                    }
                }
            }
        }

        item(key = "tags_filter_section", contentType = "tags_filter") {
            val selectedTags = remember(maintenance.forgetTags) {
                BackupTagRules.sanitizeBackupTags(maintenance.forgetTags)
            }
            val tagsSubtitle = if (selectedTags.isEmpty()) {
                stringResource(R.string.forget_tags_all)
            } else {
                selectedTags.joinToString(", ")
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                ExpressiveSectionHeader(title = stringResource(R.string.run_tasks_tags))
                ExpressiveSegmentedSurface(
                    index = 0,
                    count = 1
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTagsSheet = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Label,
                            contentDescription = null,
                            tint = if (selectedTags.isNotEmpty()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.forget_tags_picker_title),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = tagsSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        if (selectedTags.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Text(
                                    text = selectedTags.size.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item(key = "dry_run_action", contentType = "dry_run_action") {
            FilledTonalButton(
                onClick = onOpenDryRun,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.forget_dry_run_action))
            }
        }
    }

    if (showHostsSheet) {
        ForgetHostsBottomSheet(
            availableHosts = availableHosts,
            selectedHostSet = selectedHostSet,
            selectedCount = selectedHosts.size,
            forgetAllHosts = maintenance.forgetAllHosts,
            areRepoSnapshotsLoaded = areRepoSnapshotsLoaded,
            isLoadingHosts = isLoadingHosts,
            hostsError = hostsError,
            onToggleAllHosts = onToggleAllHosts,
            onToggleHost = onToggleHost,
            onAddHost = onAddHost,
            onRemoveHost = onRemoveHost,
            onRefreshHosts = onRefreshHosts,
            onDismissRequest = { showHostsSheet = false }
        )
    }

    if (showTagsSheet) {
        BackupTagPickerSheet(
            availableTags = availableTags,
            selectedTags = maintenance.forgetTags,
            onToggleTag = onToggleForgetTag,
            onCreateTag = onCreateForgetTag,
            onUpdateTagColor = onUpdateTagColor,
            onDeleteTag = onDeleteTag,
            onDismissRequest = { showTagsSheet = false },
            title = stringResource(R.string.forget_tags_picker_title)
        )
    }

    if (showGuideSheet) {
        ForgetGuideBottomSheet(
            onDismissRequest = { showGuideSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ForgetDryRunScreen(
    isRunning: Boolean,
    result: ForgetDryRunResult?,
    error: String?,
    snapshotsWithMetadata: List<SnapshotWithMetadata>,
    appInfoMap: Map<String, List<AppInfo>>,
    repoTagsByName: Map<String, BackupTag>,
    onRunDryRun: () -> Unit,
    onSnapshotClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    LaunchedEffect(Unit) {
        if (!isRunning && result == null && error == null) {
            onRunDryRun()
        }
    }

    val isRefreshing = isRunning && result != null
    val snapshotsById = remember(snapshotsWithMetadata) {
        snapshotsWithMetadata.associateBy { it.snapshotInfo.id }
    }
    val snapshotsByShortId = remember(snapshotsWithMetadata) {
        snapshotsWithMetadata.associateBy { it.snapshotInfo.id.take(8) }
    }
    val nowEpochMinute = remember(result) { System.currentTimeMillis() / 60_000L }
    val fallbackColorArgb = MaterialTheme.colorScheme.outline.toArgb()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxIconsPossible = remember(maxWidth) {
            val contentWidth = (maxWidth - 64.dp).coerceAtLeast(32.dp)
            val iconSize = 32.dp
            val spacing = 8.dp
            ((contentWidth + spacing) / (iconSize + spacing)).toInt().coerceAtLeast(1)
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                if (!isRunning) {
                    onRunDryRun()
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 8.dp,
                    end = 16.dp,
                    bottom = 24.dp + contentPadding.calculateBottomPadding()
                )
            ) {
                when {
                    isRunning && result == null -> {
                        item(key = "dry_run_loading", contentType = "dry_run_loading") {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    LoadingIndicator()
                                    Text(
                                        text = stringResource(R.string.forget_dry_run_running),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    error != null -> {
                        item(key = "dry_run_error", contentType = "dry_run_error") {
                            Column(
                                modifier = Modifier
                                    .fillParentMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ForgetHostStatusCard(
                                    icon = Icons.Outlined.WarningAmber,
                                    text = error,
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                                FilledTonalButton(
                                    onClick = onRunDryRun,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.action_retry))
                                }
                            }
                        }
                    }

                    result == null || result.groups.isEmpty() -> {
                        item(key = "dry_run_empty", contentType = "dry_run_empty") {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                ExpressiveEmptyState(
                                    icon = Icons.Outlined.History,
                                    title = stringResource(R.string.forget_dry_run_empty)
                                )
                            }
                        }
                    }

                    else -> {
                        item(key = "dry_run_summary", contentType = "dry_run_summary") {
                            val summaryText = if (result.totalRemove == 0) {
                                stringResource(R.string.forget_dry_run_no_removals, result.totalKeep)
                            } else {
                                stringResource(R.string.forget_dry_run_summary, result.totalKeep, result.totalRemove)
                            }
                            Text(
                                text = summaryText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (result.totalRemove > 0) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }

                        result.groups.forEachIndexed { groupIdx, group ->
                            val userTags = BackupTagRules.filterUserTags(group.tags)
                            val orderedRows = (group.remove.map { it to true } + group.keep.map { it to false })
                                .sortedByDescending { parseSnapshotTimeInstant(it.first.time) ?: java.time.Instant.MIN }

                            item(key = "group_header_$groupIdx", contentType = "dry_run_header") {
                                val groupTitle = buildString {
                                    append(group.host.ifBlank { stringResource(R.string.restic_unknown) })
                                    if (userTags.isNotEmpty()) {
                                        append(" · ")
                                        append(userTags.joinToString(", "))
                                    }
                                }
                                ExpressiveSectionHeader(
                                    title = groupTitle,
                                    modifier = if (groupIdx > 0) Modifier.padding(top = 12.dp) else Modifier
                                )
                            }

                            itemsIndexed(
                                items = orderedRows,
                                key = { idx, (snapshot, isRemove) ->
                                    "group_${groupIdx}_${if (isRemove) "rm" else "keep"}_${snapshot.id}_$idx"
                                },
                                contentType = { _, _ -> "dry_run_snapshot" }
                            ) { idx, (snapshot, isRemove) ->
                                val matched = snapshotsById[snapshot.id] ?: snapshotsByShortId[snapshot.shortId]
                                val effectiveSnapshotWithMetadata = matched ?: SnapshotWithMetadata(
                                    snapshotInfo = SnapshotInfo(
                                        id = snapshot.id,
                                        time = snapshot.time,
                                        paths = emptyList(),
                                        tags = snapshot.tags,
                                        hostname = snapshot.hostname ?: group.host.takeIf { it.isNotBlank() }
                                    ),
                                    metadata = null
                                )
                                val userTagColors = remember(
                                    effectiveSnapshotWithMetadata.snapshotInfo.tags,
                                    repoTagsByName,
                                    fallbackColorArgb
                                ) {
                                    BackupTagRules.filterUserTags(effectiveSnapshotWithMetadata.snapshotInfo.tags).map { tagName ->
                                        repoTagsByName[tagName]?.colorArgb ?: fallbackColorArgb
                                    }
                                }
                                val containerColor = if (isRemove) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                }
                                val contentColor = if (isRemove) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                                val secondaryColor = if (isRemove) {
                                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }

                                ExpressiveSegmentedSurface(
                                    index = idx,
                                    count = orderedRows.size,
                                    containerColor = containerColor,
                                    contentColor = contentColor,
                                    modifier = Modifier.padding(bottom = if (idx < orderedRows.size - 1) 2.dp else 0.dp)
                                ) {
                                    SnapshotItem(
                                        snapshotWithMetadata = effectiveSnapshotWithMetadata,
                                        apps = matched?.let { appInfoMap[it.snapshotInfo.id] },
                                        tagColors = userTagColors,
                                        nowEpochMinute = nowEpochMinute,
                                        maxIconsPossible = maxIconsPossible,
                                        onClick = { onSnapshotClick(matched?.snapshotInfo?.id ?: snapshot.id) },
                                        showMetadataStatusWhenMissing = matched != null,
                                        secondaryContentColor = secondaryColor,
                                        statusContent = {
                                            ForgetDryRunStatusRow(
                                                isRemove = isRemove,
                                                keepReasons = snapshot.keepReasons
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
    }
}

internal fun parseSnapshotTimeInstant(time: String): java.time.Instant? =
    runCatching { java.time.OffsetDateTime.parse(time).toInstant() }.getOrNull()

internal fun formatForgetKeepReason(reason: String): String {
    val cleaned = reason.trim().removeSuffix(" snapshot").trim()
    if (cleaned.isEmpty()) return ""
    return cleaned.replaceFirstChar { it.uppercase() }
}

@Composable
private fun ForgetDryRunStatusRow(
    isRemove: Boolean,
    keepReasons: List<String>
) {
    val formattedReasons = remember(keepReasons) {
        keepReasons.mapNotNull { formatForgetKeepReason(it).takeIf(String::isNotEmpty) }.distinct()
    }
    val statusText = when {
        isRemove -> stringResource(R.string.forget_dry_run_status_remove)
        formattedReasons.isNotEmpty() -> stringResource(
            R.string.forget_dry_run_status_keep_with_reasons,
            formattedReasons.joinToString(", ")
        )
        else -> stringResource(R.string.forget_dry_run_status_keep)
    }
    val statusColor = if (isRemove) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.primary
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = if (isRemove) Icons.Outlined.DeleteOutline else Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = statusColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ForgetHostsBottomSheet(
    availableHosts: List<ForgetHostOption>,
    selectedHostSet: Set<String>,
    selectedCount: Int,
    forgetAllHosts: Boolean,
    areRepoSnapshotsLoaded: Boolean,
    isLoadingHosts: Boolean,
    hostsError: String?,
    onToggleAllHosts: () -> Unit,
    onToggleHost: (String) -> Unit,
    onAddHost: (String) -> Unit,
    onRemoveHost: (String) -> Unit,
    onRefreshHosts: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAddHostDialog by rememberSaveable { mutableStateOf(false) }
    var customHostInput by rememberSaveable { mutableStateOf("") }

    val isAllSelected = forgetAllHosts ||
        (availableHosts.isNotEmpty() && availableHosts.all { it.hostname in selectedHostSet })

    val selectionSubtitle = when {
        forgetAllHosts -> stringResource(R.string.forget_hosts_all_title)
        selectedCount == 0 -> stringResource(R.string.forget_hosts_subtitle_none)
        selectedCount == 1 -> stringResource(R.string.forget_hosts_subtitle_single, selectedHostSet.first())
        else -> pluralStringResource(R.plurals.forget_hosts_subtitle_count, selectedCount, selectedCount)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                )
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.forget_hosts_sheet_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = selectionSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            customHostInput = ""
                            showAddHostDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.forget_hosts_action_add),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onRefreshHosts,
                        enabled = !isLoadingHosts
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.forget_hosts_action_refresh),
                            tint = if (isLoadingHosts) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            AnimatedVisibility(
                visible = hostsError != null && !isLoadingHosts,
                enter = fadeIn(tween(200)) + expandVertically(tween(220)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    ForgetHostStatusCard(
                        icon = Icons.Outlined.WarningAmber,
                        text = hostsError.orEmpty(),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            AnimatedVisibility(
                visible = !isLoadingHosts && hostsError == null && !areRepoSnapshotsLoaded,
                enter = fadeIn(tween(200)) + expandVertically(tween(220)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    ForgetHostStatusCard(
                        icon = Icons.Outlined.Info,
                        text = stringResource(R.string.forget_hosts_not_loaded_hint),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
            ) {
                when {
                    isLoadingHosts -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                LoadingIndicator()
                                Text(
                                    text = stringResource(R.string.forget_hosts_loading),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    availableHosts.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.forget_hosts_sheet_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }

                    else -> {
                        val totalRows = availableHosts.size + 1
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            item(key = "toggle_all_hosts") {
                                ExpressiveSegmentedSurface(
                                    index = 0,
                                    count = totalRows
                                ) {
                                    ForgetToggleAllHostsRow(
                                        checked = isAllSelected,
                                        onToggle = onToggleAllHosts
                                    )
                                }
                            }
                            itemsIndexed(
                                items = availableHosts,
                                key = { _, option -> "host_${option.hostname}" }
                            ) { index, hostOption ->
                                ExpressiveSegmentedSurface(
                                    index = index + 1,
                                    count = totalRows
                                ) {
                                    ForgetHostItemRow(
                                        option = hostOption,
                                        selected = forgetAllHosts || hostOption.hostname in selectedHostSet,
                                        onToggle = { onToggleHost(hostOption.hostname) },
                                        onRemove = { onRemoveHost(hostOption.hostname) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddHostDialog) {
        val sanitizedInput = normalizeCustomForgetHostname(customHostInput)
        AlertDialog(
            onDismissRequest = { showAddHostDialog = false },
            title = { Text(stringResource(R.string.forget_hosts_dialog_add_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.forget_hosts_dialog_add_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = customHostInput,
                        onValueChange = { customHostInput = it },
                        label = { Text(stringResource(R.string.forget_hosts_dialog_add_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = sanitizedInput.isNotEmpty(),
                    onClick = {
                        onAddHost(sanitizedInput)
                        showAddHostDialog = false
                        customHostInput = ""
                    }
                ) {
                    Text(stringResource(R.string.forget_hosts_dialog_add_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddHostDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun ForgetToggleAllHostsRow(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.SelectAll,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.forget_hosts_toggle_all_title),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.forget_hosts_toggle_all_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
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
private fun ForgetHostItemRow(
    option: ForgetHostOption,
    selected: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (option.isCurrentDevice) Icons.Outlined.Smartphone else Icons.Outlined.Dns,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = option.hostname,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (option.isCurrentDevice) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.forget_hosts_this_device),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            val subtitle = when {
                option.snapshotCount != null -> pluralStringResource(
                    R.plurals.forget_host_snapshot_count,
                    option.snapshotCount,
                    option.snapshotCount
                )
                option.isRemovable -> stringResource(R.string.forget_hosts_manual_entry)
                else -> null
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (option.isRemovable) {
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.cd_remove_host),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(4.dp))
        } else {
            Spacer(Modifier.width(16.dp))
        }
        Switch(
            checked = selected,
            onCheckedChange = { onToggle() },
            thumbContent = if (selected) {
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
private fun ForgetHostStatusCard(
    icon: ImageVector,
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor
            )
        }
    }
}

@Composable
fun CheckConfigScreen(
    maintenance: io.github.hddq.restoid.model.MaintenanceConfig,
    onReadDataChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 24.dp + contentPadding.calculateBottomPadding(),
            top = 8.dp
        )
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
    context: android.content.Context,
    selectAllApps: Boolean = false,
    excludedCount: Int = 0
): String {
    val typesSummary = buildSelectedBackupTypesSummary(apps, appBackupTypes, defaultBackupTypes, context)
    if (apps.isEmpty() && selectAllApps) {
        val appsLabel = if (excludedCount == 0) {
            context.getString(R.string.preset_summary_all_apps)
        } else {
            context.resources.getQuantityString(
                R.plurals.preset_summary_all_apps_except_count,
                excludedCount,
                excludedCount
            )
        }
        return context.getString(R.string.run_tasks_backup_subtitle_all_apps, appsLabel, typesSummary)
    }
    val selectedCount = apps.count { it.isSelected }
    return context.getString(
        R.string.run_tasks_backup_subtitle,
        selectedCount,
        typesSummary
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
    val policyText = context.getString(
        R.string.run_tasks_forget_subtitle,
        config.keepLast,
        config.keepDaily,
        config.keepWeekly,
        config.keepMonthly
    )
    val parts = mutableListOf(policyText)
    if (!config.forgetAllHosts) {
        val selectedHosts = normalizeForgetHosts(config.forgetHosts)
        val hostsText = when (selectedHosts.size) {
            0 -> context.getString(R.string.forget_hosts_subtitle_none)
            1 -> context.getString(R.string.forget_hosts_subtitle_single, selectedHosts.first())
            else -> context.resources.getQuantityString(
                R.plurals.forget_hosts_subtitle_count,
                selectedHosts.size,
                selectedHosts.size
            )
        }
        parts.add(hostsText)
    }
    val selectedTags = BackupTagRules.sanitizeBackupTags(config.forgetTags)
    if (selectedTags.isNotEmpty()) {
        val tagsText = if (selectedTags.size == 1) {
            selectedTags.first()
        } else {
            context.resources.getQuantityString(
                R.plurals.forget_tags_subtitle_count,
                selectedTags.size,
                selectedTags.size
            )
        }
        parts.add(tagsText)
    }
    return parts.joinToString(" · ")
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
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    var showFolderPicker by remember { mutableStateOf(false) }
    var showExcludePicker by remember { mutableStateOf(false) }
    var excludePickerInitialPath by remember { mutableStateOf<String?>(null) }
    var menuExpandedForUri by remember { mutableStateOf<String?>(null) }
    var menuExpandedForExcludedUri by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 80.dp + contentPadding.calculateBottomPadding(),
            top = 8.dp
        ),
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

@Composable
fun rememberPermissionsState(
    vararg permissions: String,
    onGranted: () -> Unit = {},
    onDenied: () -> Unit = {}
): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    fun checkAll() = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    var granted by remember { mutableStateOf(checkAll()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = checkAll()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        granted = checkAll()
        if (granted) onGranted() else onDenied()
    }

    return granted to { launcher.launch(arrayOf(*permissions)) }
}

@Composable
fun rememberPermissionsMap(
    vararg permissions: String,
    onResult: (Map<String, Boolean>) -> Unit = {}
): Pair<Map<String, Boolean>, (Array<String>) -> Unit> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    fun checkAll(): Map<String, Boolean> = permissions.associateWith {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    var statuses by remember { mutableStateOf(checkAll()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                statuses = checkAll()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val updated = checkAll()
        statuses = updated
        onResult(updated)
    }

    return statuses to { toRequest ->
        if (toRequest.isNotEmpty()) {
            launcher.launch(toRequest)
        } else {
            val updated = checkAll()
            statuses = updated
            onResult(updated)
        }
    }
}

fun buildDeviceDataSubtitle(
    contactsEnabled: Boolean,
    callLogsEnabled: Boolean,
    calendarEnabled: Boolean = false,
    deviceDataBackupEnabled: Boolean,
    hasReadContactsPermission: Boolean,
    hasReadCallLogPermission: Boolean,
    hasReadCalendarPermission: Boolean = true,
    context: android.content.Context
): String {
    if (!contactsEnabled && !callLogsEnabled && !calendarEnabled) {
        return context.getString(R.string.device_data_none_selected)
    }
    if (deviceDataBackupEnabled) {
        val missingContacts = contactsEnabled && !hasReadContactsPermission
        val missingCallLogs = callLogsEnabled && !hasReadCallLogPermission
        val missingCalendar = calendarEnabled && !hasReadCalendarPermission
        val missingCount = (if (missingContacts) 1 else 0) +
            (if (missingCallLogs) 1 else 0) +
            (if (missingCalendar) 1 else 0)
        when {
            missingCount > 1 -> return context.getString(R.string.device_data_permissions_required)
            missingContacts -> return context.getString(R.string.device_data_contacts_permission_required)
            missingCallLogs -> return context.getString(R.string.device_data_call_logs_permission_required)
            missingCalendar -> return context.getString(R.string.device_data_calendar_permission_required)
        }
    }
    return buildList {
        if (contactsEnabled) add(context.getString(R.string.device_data_contacts))
        if (callLogsEnabled) add(context.getString(R.string.device_data_call_logs))
        if (calendarEnabled) add(context.getString(R.string.device_data_calendar))
    }.joinToString(", ")
}

@Composable
fun DeviceDataConfigScreen(
    contactsEnabled: Boolean,
    selectAllContactAccounts: Boolean = true,
    selectedContactAccountKeys: Set<String> = emptySet(),
    availableContactAccounts: List<ContactAccountInfo> = emptyList(),
    isLoadingContactAccounts: Boolean = false,
    callLogsEnabled: Boolean,
    calendarEnabled: Boolean = false,
    selectAllCalendars: Boolean = true,
    selectedCalendarKeys: Set<String> = emptySet(),
    availableCalendars: List<CalendarInfo> = emptyList(),
    isLoadingCalendars: Boolean = false,
    onContactsEnabledChange: (Boolean) -> Unit,
    onToggleAllContactAccounts: () -> Unit = {},
    onToggleContactAccount: (String) -> Unit = {},
    onRefreshContactAccounts: () -> Unit = {},
    onCallLogsEnabledChange: (Boolean) -> Unit,
    onCalendarEnabledChange: (Boolean) -> Unit = {},
    onToggleAllCalendars: () -> Unit = {},
    onToggleCalendar: (String) -> Unit = {},
    onRefreshCalendars: () -> Unit = {},
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val (hasReadPermission, requestReadPermission) = rememberPermissionsState(
        Manifest.permission.READ_CONTACTS,
        onGranted = {
            onContactsEnabledChange(true)
            onRefreshContactAccounts()
        }
    )
    val (hasReadCallLogPermission, requestReadCallLogPermission) = rememberPermissionsState(
        Manifest.permission.READ_CALL_LOG,
        onGranted = { onCallLogsEnabledChange(true) }
    )
    val (hasReadCalendarPermission, requestReadCalendarPermission) = rememberPermissionsState(
        Manifest.permission.READ_CALENDAR,
        onGranted = {
            onCalendarEnabledChange(true)
            onRefreshCalendars()
        }
    )
    var showContactsOptionsSheet by rememberSaveable { mutableStateOf(false) }
    var showCalendarOptionsSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(hasReadPermission) {
        if (hasReadPermission) {
            onRefreshContactAccounts()
        }
    }

    LaunchedEffect(hasReadCalendarPermission) {
        if (hasReadCalendarPermission) {
            onRefreshCalendars()
        }
    }

    val contactsSubtitle = when {
        contactsEnabled && !hasReadPermission -> stringResource(R.string.device_data_contacts_permission_required)
        selectAllContactAccounts -> stringResource(R.string.device_data_contacts_all_accounts)
        else -> {
            val selectedCount = if (availableContactAccounts.isNotEmpty()) {
                availableContactAccounts.count { it.selectionKey in selectedContactAccountKeys }
            } else {
                selectedContactAccountKeys.size
            }
            if (selectedCount == 0) {
                stringResource(R.string.device_data_contacts_none_selected)
            } else {
                pluralStringResource(R.plurals.device_data_contacts_accounts_selected_count, selectedCount, selectedCount)
            }
        }
    }

    val calendarSubtitle = when {
        calendarEnabled && !hasReadCalendarPermission -> stringResource(R.string.device_data_calendar_permission_required)
        selectAllCalendars -> stringResource(R.string.device_data_calendar_all)
        else -> {
            val selectedCount = if (availableCalendars.isNotEmpty()) {
                availableCalendars.count { it.selectionKey in selectedCalendarKeys }
            } else {
                selectedCalendarKeys.size
            }
            if (selectedCount == 0) {
                stringResource(R.string.device_data_calendar_none_selected)
            } else {
                pluralStringResource(R.plurals.device_data_calendars_selected_count, selectedCount, selectedCount)
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 24.dp + contentPadding.calculateBottomPadding(),
            top = 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "device_data_items") {
            ExpressiveCardGroup {
                ExpressiveSegmentedSurface(index = 0, count = 3) {
                    TaskRow(
                        title = stringResource(R.string.device_data_contacts),
                        subtitle = contactsSubtitle,
                        icon = Icons.Outlined.Contacts,
                        checked = contactsEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !hasReadPermission) requestReadPermission() else onContactsEnabledChange(enabled)
                        },
                        onNavigate = {
                            if (hasReadPermission) {
                                onRefreshContactAccounts()
                            }
                            showContactsOptionsSheet = true
                        }
                    )
                }
                ExpressiveSegmentedSurface(index = 1, count = 3) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!callLogsEnabled && !hasReadCallLogPermission) requestReadCallLogPermission() else onCallLogsEnabledChange(!callLogsEnabled)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Call,
                            contentDescription = null,
                            tint = if (callLogsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.device_data_call_logs),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (callLogsEnabled && !hasReadCallLogPermission) {
                                Text(
                                    text = stringResource(R.string.device_data_call_logs_permission_required),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Switch(
                            checked = callLogsEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && !hasReadCallLogPermission) requestReadCallLogPermission() else onCallLogsEnabledChange(enabled)
                            },
                            thumbContent = if (callLogsEnabled) {
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
                        checked = calendarEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !hasReadCalendarPermission) {
                                requestReadCalendarPermission()
                            } else {
                                onCalendarEnabledChange(enabled)
                            }
                        },
                        onNavigate = {
                            if (hasReadCalendarPermission) {
                                onRefreshCalendars()
                            }
                            showCalendarOptionsSheet = true
                        }
                    )
                }
            }
        }
    }

    if (showContactsOptionsSheet) {
        ContactsOptionsBottomSheet(
            selectAllContactAccounts = selectAllContactAccounts,
            selectedContactAccountKeys = selectedContactAccountKeys,
            availableContactAccounts = availableContactAccounts,
            isLoadingContactAccounts = isLoadingContactAccounts,
            hasReadContactsPermission = hasReadPermission,
            onRequestPermission = requestReadPermission,
            onToggleAllContactAccounts = onToggleAllContactAccounts,
            onToggleContactAccount = onToggleContactAccount,
            onDismissRequest = { showContactsOptionsSheet = false }
        )
    }

    if (showCalendarOptionsSheet) {
        CalendarOptionsBottomSheet(
            selectAllCalendars = selectAllCalendars,
            selectedCalendarKeys = selectedCalendarKeys,
            availableCalendars = availableCalendars,
            isLoadingCalendars = isLoadingCalendars,
            hasReadCalendarPermission = hasReadCalendarPermission,
            onRequestPermission = requestReadCalendarPermission,
            onToggleAllCalendars = onToggleAllCalendars,
            onToggleCalendar = onToggleCalendar,
            onDismissRequest = { showCalendarOptionsSheet = false }
        )
    }
}

internal const val CONTACTS_OPTIONS_SELECT_ALL_TAG = "contacts_options_select_all"
internal const val CONTACTS_OPTION_ROW_TAG_PREFIX = "contacts_option_row_"
internal const val CONTACTS_OPTIONS_LOADING_TAG = "contacts_options_loading"
internal const val CALENDAR_OPTIONS_SELECT_ALL_TAG = "calendar_options_select_all"
internal const val CALENDAR_OPTION_ROW_TAG_PREFIX = "calendar_option_row_"
internal const val CALENDAR_OPTIONS_LOADING_TAG = "calendar_options_loading"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ContactsOptionsBottomSheet(
    selectAllContactAccounts: Boolean,
    selectedContactAccountKeys: Set<String>,
    availableContactAccounts: List<ContactAccountInfo>,
    isLoadingContactAccounts: Boolean,
    hasReadContactsPermission: Boolean,
    onRequestPermission: () -> Unit,
    onToggleAllContactAccounts: () -> Unit,
    onToggleContactAccount: (String) -> Unit,
    onDismissRequest: () -> Unit,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    emptyMessage: String = stringResource(R.string.device_data_contacts_accounts_empty)
) {
    val readOnlyLabel = stringResource(R.string.device_data_contacts_read_only)
    val isAllSelected = selectAllContactAccounts ||
        (availableContactAccounts.isNotEmpty() && availableContactAccounts.all { it.selectionKey in selectedContactAccountKeys })

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.device_data_contacts_options_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            when {
                isLoadingContactAccounts && availableContactAccounts.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(CONTACTS_OPTIONS_LOADING_TAG)
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator()
                    }
                }
                errorMessage != null && availableContactAccounts.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        if (onRetry != null) {
                            FilledTonalButton(
                                onClick = onRetry,
                                shape = MaterialTheme.shapes.extraLarge
                            ) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                }
                !hasReadContactsPermission && availableContactAccounts.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.device_data_contacts_permission_required),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilledTonalButton(
                            onClick = onRequestPermission,
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            Text(stringResource(R.string.action_grant))
                        }
                    }
                }
                availableContactAccounts.isEmpty() -> {
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                else -> {
                    val totalRows = availableContactAccounts.size + 1
                    ExpressiveCardGroup {
                        ExpressiveSegmentedSurface(index = 0, count = totalRows) {
                            Box(modifier = Modifier.testTag(CONTACTS_OPTIONS_SELECT_ALL_TAG)) {
                                SelectAllListItem(
                                    isChecked = isAllSelected,
                                    subtitle = null,
                                    onClick = null,
                                    onToggle = onToggleAllContactAccounts,
                                    iconSize = 24.dp,
                                    iconPadding = 0.dp
                                )
                            }
                        }
                        availableContactAccounts.forEachIndexed { index, account ->
                            val isChecked = selectAllContactAccounts || account.selectionKey in selectedContactAccountKeys
                            val subtitle = buildList {
                                val typeLabel = account.accountTypeLabel?.trim().orEmpty()
                                if (typeLabel.isNotEmpty() && typeLabel != account.displayName) {
                                    add(typeLabel)
                                }
                                if (account.isReadOnly) {
                                    add(readOnlyLabel)
                                }
                                val itemCount = account.itemCount
                                if (itemCount != null) {
                                    add(pluralStringResource(R.plurals.device_data_contacts_count, itemCount, itemCount))
                                }
                            }.joinToString(" • ").ifEmpty { null }

                            ExpressiveSegmentedSurface(index = index + 1, count = totalRows) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("$CONTACTS_OPTION_ROW_TAG_PREFIX${account.selectionKey}")
                                        .clickable { onToggleContactAccount(account.selectionKey) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (account.isLocal) Icons.Outlined.Smartphone else Icons.Outlined.AccountCircle,
                                        contentDescription = null,
                                        tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 16.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = account.displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (subtitle != null) {
                                            Text(
                                                text = subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Switch(
                                        checked = isChecked,
                                        onCheckedChange = { onToggleContactAccount(account.selectionKey) },
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
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CalendarOptionsBottomSheet(
    selectAllCalendars: Boolean,
    selectedCalendarKeys: Set<String>,
    availableCalendars: List<CalendarInfo>,
    isLoadingCalendars: Boolean,
    hasReadCalendarPermission: Boolean,
    onRequestPermission: () -> Unit,
    onToggleAllCalendars: () -> Unit,
    onToggleCalendar: (String) -> Unit,
    onDismissRequest: () -> Unit,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    emptyMessage: String = stringResource(R.string.device_data_calendar_empty)
) {
    val readOnlyLabel = stringResource(R.string.device_data_calendar_read_only)
    val isAllSelected = selectAllCalendars ||
        (availableCalendars.isNotEmpty() && availableCalendars.all { it.selectionKey in selectedCalendarKeys })

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.device_data_calendar_options_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            when {
                isLoadingCalendars && availableCalendars.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(CALENDAR_OPTIONS_LOADING_TAG)
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator()
                    }
                }
                errorMessage != null && availableCalendars.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        if (onRetry != null) {
                            FilledTonalButton(
                                onClick = onRetry,
                                shape = MaterialTheme.shapes.extraLarge
                            ) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                }
                !hasReadCalendarPermission && availableCalendars.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.device_data_calendar_permission_required),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilledTonalButton(
                            onClick = onRequestPermission,
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            Text(stringResource(R.string.action_grant))
                        }
                    }
                }
                availableCalendars.isEmpty() -> {
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                else -> {
                    val totalRows = availableCalendars.size + 1
                    ExpressiveCardGroup {
                        ExpressiveSegmentedSurface(index = 0, count = totalRows) {
                            Box(modifier = Modifier.testTag(CALENDAR_OPTIONS_SELECT_ALL_TAG)) {
                                SelectAllListItem(
                                    isChecked = isAllSelected,
                                    subtitle = null,
                                    onClick = null,
                                    onToggle = onToggleAllCalendars,
                                    iconSize = 24.dp,
                                    iconPadding = 0.dp
                                )
                            }
                        }
                        availableCalendars.forEachIndexed { index, calendar ->
                            val isChecked = selectAllCalendars || calendar.selectionKey in selectedCalendarKeys
                            val subtitle = buildList {
                                val account = calendar.accountName.trim()
                                if (account.isNotEmpty() && account != calendar.displayName) {
                                    add(account)
                                }
                                if (calendar.isReadOnly) {
                                    add(readOnlyLabel)
                                }
                                val itemCount = calendar.itemCount
                                if (itemCount != null) {
                                    add(pluralStringResource(R.plurals.device_data_calendar_events_count, itemCount, itemCount))
                                }
                            }.joinToString(" • ").ifEmpty { null }

                            ExpressiveSegmentedSurface(index = index + 1, count = totalRows) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("$CALENDAR_OPTION_ROW_TAG_PREFIX${calendar.selectionKey}")
                                        .clickable { onToggleCalendar(calendar.selectionKey) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(Color(calendar.color))
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = calendar.displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (subtitle != null) {
                                            Text(
                                                text = subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Switch(
                                        checked = isChecked,
                                        onCheckedChange = { onToggleCalendar(calendar.selectionKey) },
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
                        }
                    }
                }
            }
        }
    }
}
