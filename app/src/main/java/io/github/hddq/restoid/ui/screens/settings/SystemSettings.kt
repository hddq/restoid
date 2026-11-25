package io.github.hddq.restoid.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.LocalNetworkPermissionState
import io.github.hddq.restoid.data.NotificationPermissionState
import io.github.hddq.restoid.data.StoragePermissionState
import io.github.hddq.restoid.ui.components.ExpressiveCardGroup
import io.github.hddq.restoid.ui.components.ExpressiveSectionHeader
import io.github.hddq.restoid.ui.components.ExpressiveSegmentedSurface
import io.github.hddq.restoid.ui.settings.DeviceDataPermissionsState
import io.github.hddq.restoid.ui.settings.SettingsViewModel

@Composable
fun SystemSettings(
    viewModel: SettingsViewModel,
    notificationPermissionLauncher: () -> Unit,
    localNetworkPermissionLauncher: () -> Unit,
    onRequestDeviceDataPermissions: (Array<String>) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasRoot by viewModel.hasRoot.collectAsStateWithLifecycle()
    val storagePermissionState by viewModel.storagePermissionState.collectAsStateWithLifecycle()
    val localNetworkPermissionState by viewModel.localNetworkPermissionState.collectAsStateWithLifecycle()
    val notificationPermissionState by viewModel.notificationPermissionState.collectAsStateWithLifecycle()
    val batteryOptimizationDisabled by viewModel.isIgnoringBatteryOptimizations.collectAsStateWithLifecycle()
    val deviceDataPermissions by viewModel.deviceDataPermissions.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showPermissionsSheet by rememberSaveable { mutableStateOf(false) }

    val showStorageRow = storagePermissionState != StoragePermissionState.NotRequested
    val showNotificationRow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val showLocalNetworkRow = Build.VERSION.SDK_INT >= 37 &&
        localNetworkPermissionState != LocalNetworkPermissionState.NotRequested

    val missingRecommendedCount = listOf(
        showStorageRow && !hasRoot && storagePermissionState != StoragePermissionState.Granted,
        showNotificationRow && notificationPermissionState != NotificationPermissionState.Granted,
        !batteryOptimizationDisabled,
        showLocalNetworkRow && localNetworkPermissionState != LocalNetworkPermissionState.Granted
    ).count { it }

    val summaryText = when {
        hasRoot && missingRecommendedCount == 0 ->
            stringResource(R.string.permissions_summary_root_all_ready)
        hasRoot ->
            pluralStringResource(R.plurals.permissions_summary_root_recommended, missingRecommendedCount, missingRecommendedCount)
        missingRecommendedCount == 0 ->
            stringResource(R.string.permissions_summary_standard_all_ready)
        else ->
            pluralStringResource(R.plurals.permissions_summary_standard_recommended, missingRecommendedCount, missingRecommendedCount)
    }

    if (showPermissionsSheet) {
        PermissionsBottomSheet(
            hasRoot = hasRoot,
            storagePermissionState = storagePermissionState,
            notificationPermissionState = notificationPermissionState,
            localNetworkPermissionState = localNetworkPermissionState,
            batteryOptimizationDisabled = batteryOptimizationDisabled,
            deviceDataPermissions = deviceDataPermissions,
            showStorageRow = showStorageRow,
            showNotificationRow = showNotificationRow,
            showLocalNetworkRow = showLocalNetworkRow,
            onRefreshRoot = { viewModel.refreshRootAccess() },
            onRequestStorage = {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                }
                runCatching { context.startActivity(intent) }
            },
            onRequestNotification = notificationPermissionLauncher,
            onOpenNotificationSettings = onOpenSettings,
            onRequestBatteryUnrestricted = {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                runCatching { context.startActivity(intent) }
                    .onFailure {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
            },
            onRequestLocalNetwork = localNetworkPermissionLauncher,
            onRequestDeviceDataPermissions = onRequestDeviceDataPermissions,
            onDismissRequest = { showPermissionsSheet = false }
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        ExpressiveSectionHeader(title = stringResource(R.string.system_title))
        ExpressiveCardGroup {
            ExpressiveSegmentedSurface(index = 0, count = 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPermissionsSheet = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (hasRoot) Icons.Outlined.AdminPanelSettings else Icons.Outlined.Security,
                            contentDescription = null,
                            tint = if (hasRoot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.permissions_and_access_title),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = summaryText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionsBottomSheet(
    hasRoot: Boolean,
    storagePermissionState: StoragePermissionState,
    notificationPermissionState: NotificationPermissionState,
    localNetworkPermissionState: LocalNetworkPermissionState,
    batteryOptimizationDisabled: Boolean,
    deviceDataPermissions: DeviceDataPermissionsState,
    showStorageRow: Boolean,
    showNotificationRow: Boolean,
    showLocalNetworkRow: Boolean,
    onRefreshRoot: () -> Unit,
    onRequestStorage: () -> Unit,
    onRequestNotification: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRequestBatteryUnrestricted: () -> Unit,
    onRequestLocalNetwork: () -> Unit,
    onRequestDeviceDataPermissions: (Array<String>) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val systemRows = buildList<@Composable () -> Unit> {
        if (showStorageRow) {
            val storageGranted = storagePermissionState == StoragePermissionState.Granted
            val coveredByRoot = hasRoot && !storageGranted
            add {
                PermissionItemRow(
                    icon = Icons.Outlined.Folder,
                    title = stringResource(R.string.permissions_storage_title),
                    subtitle = when {
                        storageGranted -> stringResource(R.string.permissions_status_granted)
                        coveredByRoot -> stringResource(R.string.permissions_status_covered_by_root)
                        else -> stringResource(R.string.storage_permission_description)
                    },
                    granted = storageGranted || coveredByRoot,
                    actionLabel = if (!storageGranted && !coveredByRoot) stringResource(R.string.action_grant) else null,
                    onAction = onRequestStorage
                )
            }
        }
        if (showNotificationRow) {
            val granted = notificationPermissionState == NotificationPermissionState.Granted
            val isDenied = notificationPermissionState == NotificationPermissionState.Denied
            add {
                PermissionItemRow(
                    icon = Icons.Outlined.Notifications,
                    title = stringResource(R.string.permissions_notifications_title),
                    subtitle = if (granted) {
                        stringResource(R.string.notifications_enabled)
                    } else {
                        stringResource(R.string.permissions_notifications_description)
                    },
                    granted = granted,
                    actionLabel = when {
                        granted -> null
                        isDenied -> stringResource(R.string.action_settings)
                        else -> stringResource(R.string.action_grant)
                    },
                    onAction = if (isDenied) onOpenNotificationSettings else onRequestNotification,
                    trailingIconAction = if (granted) {
                        {
                            IconButton(onClick = onOpenNotificationSettings) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = stringResource(R.string.action_settings)
                                )
                            }
                        }
                    } else {
                        null
                    }
                )
            }
        }
        add {
            PermissionItemRow(
                icon = Icons.Outlined.BatteryChargingFull,
                title = stringResource(R.string.permissions_battery_title),
                subtitle = if (batteryOptimizationDisabled) {
                    stringResource(R.string.permissions_status_granted)
                } else {
                    stringResource(R.string.battery_optimization_description)
                },
                granted = batteryOptimizationDisabled,
                actionLabel = if (!batteryOptimizationDisabled) stringResource(R.string.action_allow) else null,
                onAction = onRequestBatteryUnrestricted
            )
        }
        if (showLocalNetworkRow) {
            val granted = localNetworkPermissionState == LocalNetworkPermissionState.Granted
            add {
                PermissionItemRow(
                    icon = Icons.Outlined.Lan,
                    title = stringResource(R.string.settings_system_permission_local_network),
                    subtitle = if (granted) {
                        stringResource(R.string.permissions_status_granted)
                    } else {
                        stringResource(R.string.permissions_local_network_description)
                    },
                    granted = granted,
                    actionLabel = if (!granted) stringResource(R.string.action_grant) else null,
                    onAction = onRequestLocalNetwork
                )
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.permissions_and_access_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            AccessModeCard(
                hasRoot = hasRoot,
                onRefreshRoot = onRefreshRoot
            )

            Spacer(modifier = Modifier.height(20.dp))

            ExpressiveSectionHeader(title = stringResource(R.string.system_title))
            ExpressiveCardGroup {
                systemRows.forEachIndexed { index, rowContent ->
                    ExpressiveSegmentedSurface(index = index, count = systemRows.size) {
                        rowContent()
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            ExpressiveSectionHeader(title = stringResource(R.string.permissions_section_device_data))
            ExpressiveCardGroup {
                ExpressiveSegmentedSurface(index = 0, count = 3) {
                    PermissionItemRow(
                        icon = Icons.Outlined.Contacts,
                        title = stringResource(R.string.permissions_contacts_title),
                        subtitle = if (deviceDataPermissions.contactsGranted) {
                            stringResource(R.string.permissions_status_granted)
                        } else {
                            stringResource(R.string.permissions_contacts_description)
                        },
                        granted = deviceDataPermissions.contactsGranted,
                        actionLabel = if (!deviceDataPermissions.contactsGranted) stringResource(R.string.action_grant) else null,
                        onAction = {
                            onRequestDeviceDataPermissions(
                                arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
                            )
                        }
                    )
                }
                ExpressiveSegmentedSurface(index = 1, count = 3) {
                    PermissionItemRow(
                        icon = Icons.Outlined.Call,
                        title = stringResource(R.string.permissions_call_logs_title),
                        subtitle = if (deviceDataPermissions.callLogsGranted) {
                            stringResource(R.string.permissions_status_granted)
                        } else {
                            stringResource(R.string.permissions_call_logs_description)
                        },
                        granted = deviceDataPermissions.callLogsGranted,
                        actionLabel = if (!deviceDataPermissions.callLogsGranted) stringResource(R.string.action_grant) else null,
                        onAction = {
                            onRequestDeviceDataPermissions(
                                arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG)
                            )
                        }
                    )
                }
                ExpressiveSegmentedSurface(index = 2, count = 3) {
                    PermissionItemRow(
                        icon = Icons.Outlined.CalendarMonth,
                        title = stringResource(R.string.permissions_calendar_title),
                        subtitle = if (deviceDataPermissions.calendarGranted) {
                            stringResource(R.string.permissions_status_granted)
                        } else {
                            stringResource(R.string.permissions_calendar_description)
                        },
                        granted = deviceDataPermissions.calendarGranted,
                        actionLabel = if (!deviceDataPermissions.calendarGranted) stringResource(R.string.action_grant) else null,
                        onAction = {
                            onRequestDeviceDataPermissions(
                                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccessModeCard(
    hasRoot: Boolean,
    onRefreshRoot: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (hasRoot) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (hasRoot) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryTextColor = if (hasRoot) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = if (hasRoot) Icons.Outlined.AdminPanelSettings else Icons.Outlined.Security,
                    contentDescription = null,
                    tint = if (hasRoot) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(end = 12.dp, top = 2.dp)
                        .size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (hasRoot) R.string.permissions_mode_root_title else R.string.permissions_mode_standard_title
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            if (hasRoot) R.string.permissions_mode_root_desc else R.string.permissions_mode_standard_desc
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor
                    )
                }
            }
            if (!hasRoot) {
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onRefreshRoot,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.action_check_again))
                }
            }
        }
    }
}

@Composable
private fun PermissionItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIconAction: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (granted) Icons.Outlined.CheckCircle else icon,
            contentDescription = null,
            tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (actionLabel != null) {
            FilledTonalButton(onClick = onAction) {
                Text(actionLabel)
            }
        } else if (trailingIconAction != null) {
            trailingIconAction()
        }
    }
}
