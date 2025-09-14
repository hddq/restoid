package app.restoid.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.restoid.RestoidApplication
import app.restoid.model.AppInfo
import app.restoid.ui.backup.BackupTypes
import app.restoid.ui.backup.BackupViewModel
import app.restoid.ui.backup.BackupViewModelFactory
import coil.compose.rememberAsyncImagePainter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onNavigateUp: () -> Unit) {
    val application = LocalContext.current.applicationContext as RestoidApplication
    val viewModel: BackupViewModel = viewModel(
        factory = BackupViewModelFactory(
            application,
            application.repositoriesRepository,
            application.resticRepository,
            application.notificationRepository
        )
    )
    val apps by viewModel.apps.collectAsState()
    val backupTypes by viewModel.backupTypes.collectAsState()
    val isBackingUp by viewModel.isBackingUp.collectAsState()
    val backupLogs by viewModel.backupLogs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isBackingUp || backupLogs.isNotEmpty()) "Backup Progress" else "New Backup") },
                navigationIcon = {
                    // Only show back arrow if not currently backing up
                    if (!isBackingUp) {
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            // Only show FAB on the selection screen
            if (!isBackingUp && backupLogs.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.startBackup() },
                    icon = { Icon(Icons.Default.Backup, contentDescription = "Start Backup") },
                    text = { Text("Start Backup") }
                )
            }
        }
    ) { paddingValues ->
        // Show backup progress/logs if backup is running or has finished
        if (isBackingUp || backupLogs.isNotEmpty()) {
            BackupProgressScreen(
                isBackingUp = isBackingUp,
                logs = backupLogs,
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            // Otherwise, show the app and type selection
            BackupSelectionContent(
                paddingValues = paddingValues,
                viewModel = viewModel,
                apps = apps,
                backupTypes = backupTypes
            )
        }
    }
}

@Composable
fun BackupProgressScreen(isBackingUp: Boolean, logs: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isBackingUp) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Backup in progress...", style = MaterialTheme.typography.titleMedium)
            Text(
                "Please don't close the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text("Backup Finished", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                reverseLayout = true // Show latest logs at the bottom
            ) {
                items(logs.asReversed()) { logLine ->
                    Text(
                        text = logLine,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun BackupSelectionContent(
    paddingValues: PaddingValues,
    viewModel: BackupViewModel,
    apps: List<AppInfo>,
    backupTypes: BackupTypes
) {
    Column(
        modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize()
    ) {
        // Top side: Backup types
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Backup Types", style = MaterialTheme.typography.titleMedium)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            BackupTypeCheckbox("APK", backupTypes.apk) { viewModel.setBackupApk(it) }
            BackupTypeCheckbox("Data", backupTypes.data) { viewModel.setBackupData(it) }
            BackupTypeCheckbox("Device Protected Data", backupTypes.deviceProtectedData) { viewModel.setBackupDeviceProtectedData(it) }
            BackupTypeCheckbox("External Data", backupTypes.externalData) { viewModel.setBackupExternalData(it) }
            BackupTypeCheckbox("OBB Data", backupTypes.obb) { viewModel.setBackupObb(it) }
            BackupTypeCheckbox("Media Data", backupTypes.media) { viewModel.setBackupMedia(it) }
        }

        Divider()

        // Bottom side: App selection
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Apps", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { viewModel.toggleAll() }) {
                    Text("Toggle All")
                }
            }
            if (apps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        AppListItem(app = app) { viewModel.toggleAppSelection(app.packageName) }
                    }
                }
            }
        }
    }
}

@Composable
fun AppListItem(app: AppInfo, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = rememberAsyncImagePainter(model = app.icon),
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = app.name,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.width(16.dp))
        Checkbox(
            checked = app.isSelected,
            onCheckedChange = { onToggle() }
        )
    }
}

@Composable
fun BackupTypeCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
