package io.github.hddq.restoid.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column {
            Text(
                "System",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )
            AnimatedContent(targetState = rootState, label = "RootStatusAnimation") { state ->
                when (state) {
                    RootState.Denied -> RootRequestRow(
                        text = "Root access denied",
                        buttonText = "Try Again",
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
                            Text("Checking for root...")
                        }
                    }
                    RootState.Granted -> RootStatusRow(
                        text = "Root access granted",
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
        }
    }
}

