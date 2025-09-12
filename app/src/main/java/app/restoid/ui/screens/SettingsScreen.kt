package app.restoid.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.restoid.RestoidApplication
import app.restoid.data.ResticState
import app.restoid.data.RootState
import app.restoid.ui.settings.SettingsViewModel
import app.restoid.ui.settings.SettingsViewModelFactory

@Composable
fun SettingsScreen() {
    val application = LocalContext.current.applicationContext as RestoidApplication
    // Update ViewModel creation to pass both repositories from the application context
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(application.rootRepository, application.resticRepository)
    )

    val rootState by settingsViewModel.rootState.collectAsStateWithLifecycle()
    val resticState by settingsViewModel.resticState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card for Root Access status
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
                            onClick = { settingsViewModel.requestRootAccess() }
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
            }
        }

        // Card for Restic Dependency management
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Dependencies", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                ResticDependencyRow(
                    state = resticState,
                    onDownloadClick = { settingsViewModel.downloadRestic() }
                )
            }
        }
    }
}

@Composable
fun ResticDependencyRow(
    state: ResticState,
    onDownloadClick: () -> Unit
) {
    // This composable shows the status of the restic binary and provides an action button
    AnimatedContent(targetState = state, label = "ResticStatusAnimation") { targetState ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left side: Icon and Text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val icon = when (targetState) {
                    is ResticState.Installed -> Icons.Default.CheckCircle
                    is ResticState.Error -> Icons.Default.Error
                    else -> Icons.Default.CloudDownload
                }
                val iconColor = when (targetState) {
                    is ResticState.Installed -> MaterialTheme.colorScheme.primary
                    is ResticState.Error -> MaterialTheme.colorScheme.error
                    else -> LocalContentColor.current
                }
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.padding(end = 16.dp), tint = iconColor)

                Column {
                    Text("Restic Binary", style = MaterialTheme.typography.bodyLarge)
                    val supportingText = when(targetState) {
                        ResticState.Idle -> "Checking status..."
                        ResticState.NotInstalled -> "Required for backups"
                        is ResticState.Downloading -> "Downloading & extracting..."
                        is ResticState.Installed -> "Version ${targetState.version}"
                        is ResticState.Error -> "Error: ${targetState.message}"
                    }
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right side: Button or Progress Indicator
            when (targetState) {
                ResticState.NotInstalled, is ResticState.Error -> {
                    Button(onClick = onDownloadClick) {
                        Text(if (targetState is ResticState.Error) "Retry" else "Download")
                    }
                }
                is ResticState.Downloading -> {
                    CircularProgressIndicator()
                }
                is ResticState.Installed, ResticState.Idle -> {
                    // No action needed when installed or idle
                }
            }
        }
    }
}


// Helper composables for Root Status (unchanged from original logic)
@Composable
fun RootRequestRow(
    text: String,
    buttonText: String,
    icon: ImageVector?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Text(text = text)
        }
        Button(onClick = onClick) {
            Text(buttonText)
        }
    }
}

@Composable
fun RootStatusRow(text: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}
