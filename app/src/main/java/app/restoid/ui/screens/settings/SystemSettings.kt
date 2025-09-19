package app.restoid.ui.screens.settings

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
import app.restoid.data.RootState
import app.restoid.ui.screens.settings.components.NotificationPermissionRow
import app.restoid.ui.screens.settings.components.RootRequestRow
import app.restoid.ui.screens.settings.components.RootStatusRow
import app.restoid.ui.settings.SettingsViewModel

@Composable
fun SystemSettings(
    viewModel: SettingsViewModel,
    notificationPermissionLauncher: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val rootState by viewModel.rootState.collectAsStateWithLifecycle()
    val notificationPermissionState by viewModel.notificationPermissionState.collectAsStateWithLifecycle()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("System", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            AnimatedContent(targetState = rootState, label = "RootStatusAnimation") { state ->
                when (state) {
                    RootState.Denied -> RootRequestRow(
                        text = "Root access denied",
                        buttonText = "Try Again",
                        icon = Icons.Default.Error,
                        onClick = { viewModel.requestRootAccess() }
                    )
                    RootState.Checking -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            NotificationPermissionRow(
                state = notificationPermissionState,
                onRequestPermission = notificationPermissionLauncher,
                onOpenSettings = onOpenSettings
            )
        }
    }
}
