package io.github.hddq.restoid.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
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
import io.github.hddq.restoid.data.RootState
import io.github.hddq.restoid.ui.screens.settings.components.NotificationPermissionRow
import io.github.hddq.restoid.ui.screens.settings.components.RootRequestRow
import io.github.hddq.restoid.ui.screens.settings.components.RootStatusRow
import io.github.hddq.restoid.ui.settings.SettingsViewModel

@Composable
fun SystemSettings(
    viewModel: SettingsViewModel,
    notificationPermissionLauncher: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val rootState by viewModel.rootState.collectAsStateWithLifecycle()
    val notificationPermissionState by viewModel.notificationPermissionState.collectAsStateWithLifecycle()
    val batteryOptimizationDisabled by viewModel.isIgnoringBatteryOptimizations.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    Column {
        Text(
            text = stringResource(R.string.system_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column {
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
                Divider(color = MaterialTheme.colorScheme.background)
                NotificationPermissionRow(
                    state = notificationPermissionState,
                    onRequestPermission = notificationPermissionLauncher,
                    onOpenSettings = onOpenSettings
                )
                Divider(color = MaterialTheme.colorScheme.background)
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
                // App unlock moved to separate Options card
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
