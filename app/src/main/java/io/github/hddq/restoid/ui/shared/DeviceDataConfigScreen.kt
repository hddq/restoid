package io.github.hddq.restoid.ui.shared

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.DeviceDataPermissionHelper
import io.github.hddq.restoid.data.DeviceDataRepository
import io.github.hddq.restoid.data.PreferencesRepository
import io.github.hddq.restoid.model.DeviceDataMetadata
import io.github.hddq.restoid.model.DeviceDataSelection
import io.github.hddq.restoid.model.DeviceDataType
import io.github.hddq.restoid.ui.components.ExpressiveCardGroup
import io.github.hddq.restoid.ui.components.ExpressiveSectionHeader
import io.github.hddq.restoid.ui.components.ExpressiveSegmentedSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun deviceDataLabelRes(type: DeviceDataType): Int = when (type) {
    DeviceDataType.CONTACTS -> R.string.device_data_contacts
    DeviceDataType.CALL_HISTORY -> R.string.device_data_call_history
    DeviceDataType.CALENDAR -> R.string.device_data_calendar
}

fun deviceDataDescRes(type: DeviceDataType): Int = when (type) {
    DeviceDataType.CONTACTS -> R.string.device_data_contacts_desc
    DeviceDataType.CALL_HISTORY -> R.string.device_data_call_history_desc
    DeviceDataType.CALENDAR -> R.string.device_data_calendar_desc
}

fun deviceDataIcon(type: DeviceDataType): ImageVector = when (type) {
    DeviceDataType.CONTACTS -> Icons.Outlined.Contacts
    DeviceDataType.CALL_HISTORY -> Icons.Outlined.Call
    DeviceDataType.CALENDAR -> Icons.Outlined.CalendarMonth
}

fun buildDeviceDataSummary(
    selection: DeviceDataSelection,
    context: Context,
    availableTypes: Set<DeviceDataType> = DeviceDataType.entries.toSet()
): String = if (availableTypes.isEmpty()) {
    context.getString(R.string.device_data_not_in_snapshot)
} else {
    selection.selectedTypes()
        .filter { it in availableTypes }
        .joinToString(", ") { context.getString(deviceDataLabelRes(it)) }
        .ifBlank { context.getString(R.string.device_data_summary_none) }
}

@Composable
fun DeviceDataConfigScreen(
    selection: DeviceDataSelection,
    isRestoreMode: Boolean,
    onToggleType: (DeviceDataType) -> Unit,
    modifier: Modifier = Modifier,
    availableTypes: Set<DeviceDataType> = DeviceDataType.entries.toSet(),
    snapshotMetadata: Map<DeviceDataType, DeviceDataMetadata> = emptyMap()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember(context) { DeviceDataRepository(context.applicationContext) }
    val preferencesRepository = remember(context) { PreferencesRepository(context.applicationContext) }

    var permissionRevision by remember { mutableIntStateOf(0) }
    var deniedPermissions by remember(preferencesRepository) {
        mutableStateOf(
            preferencesRepository.loadDeniedDeviceDataPermissions()
                .filterNot { DeviceDataPermissionHelper.hasPermission(context, it) }
                .toSet()
        )
    }
    var liveCounts by remember { mutableStateOf<Map<DeviceDataType, Int?>>(emptyMap()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val stillDenied = deniedPermissions.filterNot { DeviceDataPermissionHelper.hasPermission(context, it) }.toSet()
                if (stillDenied != deniedPermissions) {
                    deniedPermissions = stillDenied
                    preferencesRepository.saveDeniedDeviceDataPermissions(stillDenied)
                }
                permissionRevision++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val updatedDenied = (deniedPermissions - results.filterValues { it }.keys + results.filterValues { !it }.keys)
            .filterNot { DeviceDataPermissionHelper.hasPermission(context, it) }
            .toSet()
        deniedPermissions = updatedDenied
        preferencesRepository.saveDeniedDeviceDataPermissions(updatedDenied)
        permissionRevision++
    }

    LaunchedEffect(isRestoreMode, permissionRevision) {
        if (!isRestoreMode) {
            liveCounts = withContext(Dispatchers.IO) {
                DeviceDataType.entries.associateWith { type ->
                    if (DeviceDataPermissionHelper.hasBackupPermissions(context, type)) repository.queryAvailableItemCount(type)
                    else null
                }
            }
        }
    }

    val activeSelection = remember(selection, availableTypes) {
        DeviceDataSelection.fromTypes(selection.selectedTypes().filter { it in availableTypes }.toSet())
    }
    val missingSelectedPermissions = remember(activeSelection, isRestoreMode, permissionRevision) {
        DeviceDataPermissionHelper.missingPermissionsFor(context, activeSelection, isRestoreMode)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (missingSelectedPermissions.isNotEmpty()) {
            item(key = "permissions_actions") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(
                                if (isRestoreMode) R.string.device_data_permission_missing_restore
                                else R.string.device_data_permission_missing_backup
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        FilledTonalButton(
                            onClick = {
                                val toPrompt = DeviceDataPermissionHelper.selectPermissionsToPrompt(
                                    missingSelectedPermissions, deniedPermissions
                                )
                                if (toPrompt.isEmpty()) {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                        )
                                    }
                                } else {
                                    permissionLauncher.launch(toPrompt.toTypedArray())
                                }
                            }
                        ) {
                            Text(stringResource(R.string.device_data_grant_all_permissions))
                        }
                    }
                }
            }
        }

        item(key = "categories_section") {
            Column(modifier = Modifier.fillMaxWidth()) {
                ExpressiveSectionHeader(title = stringResource(R.string.device_data_categories_header))
                ExpressiveCardGroup {
                    DeviceDataType.entries.forEachIndexed { index, type ->
                        val isAvailable = type in availableTypes
                        val hasPerms = remember(type, isRestoreMode, permissionRevision) {
                            DeviceDataPermissionHelper.hasPermissionsForType(context, type, isRestoreMode)
                        }
                        ExpressiveSegmentedSurface(index = index, count = DeviceDataType.entries.size) {
                            DeviceDataCategoryRow(
                                type = type,
                                isAvailable = isAvailable,
                                isSelected = isAvailable && selection.isEnabled(type),
                                hasPermissions = hasPerms,
                                isRestoreMode = isRestoreMode,
                                snapshotMeta = snapshotMetadata[type],
                                liveCount = liveCounts[type],
                                onToggle = { onToggleType(type) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceDataCategoryRow(
    type: DeviceDataType,
    isAvailable: Boolean,
    isSelected: Boolean,
    hasPermissions: Boolean,
    isRestoreMode: Boolean,
    snapshotMeta: DeviceDataMetadata?,
    liveCount: Int?,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isAvailable) 1f else 0.5f)
            .clickable(enabled = isAvailable, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = deviceDataIcon(type),
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = stringResource(deviceDataLabelRes(type)), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(deviceDataDescRes(type)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isAvailable) {
                Text(
                    text = stringResource(R.string.device_data_not_in_snapshot),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val statsText = if (isRestoreMode) {
                    snapshotMeta?.let { meta ->
                        val countLabel = pluralStringResource(R.plurals.device_data_items_count, meta.itemCount, meta.itemCount)
                        if (meta.size > 0L) stringResource(
                            R.string.device_data_size_and_count,
                            Formatter.formatShortFileSize(context, meta.size),
                            countLabel
                        ) else countLabel
                    }
                } else {
                    liveCount?.let { count -> pluralStringResource(R.plurals.device_data_items_count, count, count) }
                }
                if (statsText != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = statsText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = stringResource(
                        when {
                            hasPermissions -> R.string.device_data_permission_granted
                            isRestoreMode -> R.string.device_data_permission_missing_restore
                            else -> R.string.device_data_permission_missing_backup
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasPermissions) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            enabled = isAvailable,
            thumbContent = if (isSelected) {
                { Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
            } else null
        )
    }
}
