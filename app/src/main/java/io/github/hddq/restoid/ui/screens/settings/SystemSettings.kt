package io.github.hddq.restoid.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.LocalNetworkPermissionState
import io.github.hddq.restoid.data.RootState
import io.github.hddq.restoid.data.StoragePermissionState
import io.github.hddq.restoid.ui.components.ExpressiveCardGroup
import io.github.hddq.restoid.ui.components.ExpressiveSectionHeader
import io.github.hddq.restoid.ui.components.ExpressiveSegmentedSurface
import io.github.hddq.restoid.ui.screens.settings.components.NotificationPermissionRow
import io.github.hddq.restoid.ui.screens.settings.components.RootRequestRow
import io.github.hddq.restoid.ui.screens.settings.components.RootStatusRow
import io.github.hddq.restoid.ui.settings.SettingsViewModel

@Composable
fun SystemSettings(
    viewModel: SettingsViewModel,
    notificationPermissionLauncher: () -> Unit,
    localNetworkPermissionLauncher: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val rootState by viewModel.rootState.collectAsStateWithLifecycle()
    val storagePermissionState by viewModel.storagePermissionState.collectAsStateWithLifecycle()
    val localNetworkPermissionState by viewModel.localNetworkPermissionState.collectAsStateWithLifecycle()
    val notificationPermissionState by viewModel.notificationPermissionState.collectAsStateWithLifecycle()
    val batteryOptimizationDisabled by viewModel.isIgnoringBatteryOptimizations.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    val showStorageRow = storagePermissionState != StoragePermissionState.NotRequested
    val showNotificationRow = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
    val showLocalNetworkRow = android.os.Build.VERSION.SDK_INT >= 37 &&
        localNetworkPermissionState != LocalNetworkPermissionState.NotRequested

    val rows = buildList<@Composable () -> Unit> {
        add {
            AnimatedContent(targetState = rootState, label = "RootStatusAnimation") { state ->
                when (state) {
                    RootState.Denied -> RootRequestRow(
                        text = stringResource(R.string.root_access_denied),
                        buttonText = stringResource(R.string.action_try_again),
                        icon = Icons.Default.Error,
                        onClick = { viewModel.requestRootAccess() }
                    )
                    RootState.Checking -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.checking_for_root))
                        }
                    }
                    RootState.Granted -> RootStatusRow(
                        text = stringResource(R.string.root_access_granted),
                        icon = Icons.Default.CheckCircle
                    )
                }
            }
        }
        if (showStorageRow) {
            add {
                StoragePermissionRow(
                    state = storagePermissionState,
                    onRequestGrant = {
                        val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        }
                        runCatching { context.startActivity(intent) }
                    }
                )
            }
        }
        if (showNotificationRow) {
            add {
                NotificationPermissionRow(
                    state = notificationPermissionState,
                    onRequestPermission = notificationPermissionLauncher,
                    onOpenSettings = onOpenSettings
                )
            }
        }
        if (showLocalNetworkRow) {
            add {
                LocalNetworkPermissionRow(
                    state = localNetworkPermissionState,
                    onRequestGrant = localNetworkPermissionLauncher
                )
            }
        }
        add {
            BatteryOptimizationRow(
                disabled = batteryOptimizationDisabled,
                onRequestDisable = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    runCatching { context.startActivity(intent) }
                        .onFailure {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                }
            )
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        ExpressiveSectionHeader(title = stringResource(R.string.system_title))
        ExpressiveCardGroup {
            rows.forEachIndexed { index, rowContent ->
                ExpressiveSegmentedSurface(index = index, count = rows.size) {
                    rowContent()
                }
            }
        }
    }
}

@Composable
private fun StoragePermissionRow(
    state: StoragePermissionState,
    onRequestGrant: () -> Unit
) {
    if (state == StoragePermissionState.NotRequested) return
    val granted = state == StoragePermissionState.Granted

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.padding(end = 16.dp),
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Column {
                Text(
                    text = if (granted) {
                        stringResource(R.string.storage_permission_enabled)
                    } else {
                        stringResource(R.string.storage_permission_disabled)
                    }
                )
                if (!granted) {
                    Text(
                        text = stringResource(R.string.storage_permission_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (!granted) {
            Button(onClick = onRequestGrant) {
                Text(stringResource(R.string.action_grant))
            }
        }
    }
}

@Composable
private fun BatteryOptimizationRow(
    disabled: Boolean,
    onRequestDisable: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (disabled) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.padding(end = 16.dp),
                tint = if (disabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Column {
                Text(
                    text = if (disabled) {
                        stringResource(R.string.battery_optimization_disabled)
                    } else {
                        stringResource(R.string.battery_optimization_enabled)
                    }
                )
                if (!disabled) {
                    Text(
                        text = stringResource(R.string.battery_optimization_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (!disabled) {
            Button(onClick = onRequestDisable) {
                Text(stringResource(R.string.action_disable))
            }
        }
    }
}

@Composable
private fun LocalNetworkPermissionRow(
    state: LocalNetworkPermissionState,
    onRequestGrant: () -> Unit
) {
    if (state == LocalNetworkPermissionState.NotRequested) return
    val granted = state == LocalNetworkPermissionState.Granted

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.padding(end = 16.dp),
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Column {
                Text(
                    text = if (granted) {
                        stringResource(R.string.local_network_permission_enabled)
                    } else {
                        stringResource(R.string.settings_system_permission_local_network)
                    }
                )
            }
        }
        if (!granted) {
            Button(onClick = onRequestGrant) {
                Text(stringResource(R.string.action_grant))
            }
        }
    }
}
