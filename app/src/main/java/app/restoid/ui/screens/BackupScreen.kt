package app.restoid.ui.screens

import androidx.compose.animation.Crossfade
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import app.restoid.RestoidApplication
import app.restoid.model.AppInfo
import app.restoid.ui.backup.BackupTypes
import app.restoid.ui.backup.BackupViewModel
import app.restoid.ui.backup.BackupViewModelFactory
import app.restoid.ui.shared.ProgressScreenContent
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
            application.notificationRepository,
            application.appInfoRepository
        )
    )
    val apps by viewModel.apps.collectAsState()
    val isLoadingApps by viewModel.isLoadingApps.collectAsState()
    val backupTypes by viewModel.backupTypes.collectAsState()
    val isBackingUp by viewModel.isBackingUp.collectAsState()
    val backupProgress by viewModel.backupProgress.collectAsState()

    // Refresh the app list every time the screen becomes visible.
    // The cache will make this fast. This handles newly installed apps.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAppsList()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val showProgressScreen = isBackingUp || backupProgress.isFinished

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showProgressScreen) "Backup Progress" else "New Backup") },
                navigationIcon = {
                    if (!isBackingUp) {
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!showProgressScreen) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.startBackup() },
                    icon = { Icon(Icons.Default.Backup, contentDescription = "Start Backup") },
                    text = { Text("Start Backup") }
                )
            }
        }
    ) { paddingValues ->
        Crossfade(targetState = showProgressScreen, label = "BackupScreenCrossfade") { showProgress ->
            if (showProgress) {
                ProgressScreenContent(
                    progress = backupProgress,
                    operationType = "Backup",
                    onDone = {
                        viewModel.onDone()
                        onNavigateUp()
                    },
                    modifier = Modifier.padding(paddingValues)
                )
            } else {
                BackupSelectionContent(
                    paddingValues = paddingValues,
                    viewModel = viewModel,
                    apps = apps,
                    isLoading = isLoadingApps,
                    backupTypes = backupTypes
                )
            }
        }
    }
}

@Composable
fun BackupSelectionContent(
    paddingValues: PaddingValues,
    viewModel: BackupViewModel,
    apps: List<AppInfo>,
    isLoading: Boolean,
    backupTypes: BackupTypes
) {
    LazyColumn(
        modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Backup Types Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceBright
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Backup Types",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    BackupTypeToggle("APK", backupTypes.apk) { viewModel.setBackupApk(it) }
                    BackupTypeToggle("Data", backupTypes.data) { viewModel.setBackupData(it) }
                    BackupTypeToggle("Device Protected Data", backupTypes.deviceProtectedData) { viewModel.setBackupDeviceProtectedData(it) }
                    BackupTypeToggle("External Data", backupTypes.externalData) { viewModel.setBackupExternalData(it) }
                    BackupTypeToggle("OBB Data", backupTypes.obb) { viewModel.setBackupObb(it) }
                    BackupTypeToggle("Media Data", backupTypes.media) { viewModel.setBackupMedia(it) }
                }
            }
        }

        // Apps Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Apps",
                    style = MaterialTheme.typography.titleMedium
                )
                FilledTonalButton(onClick = { viewModel.toggleAll() }) {
                    Text("Toggle All")
                }
            }
        }

        // Apps List
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else {
            items(apps, key = { it.packageName }) { app ->
                AppListItem(app = app) { viewModel.toggleAppSelection(app.packageName) }
            }
        }
    }
}

@Composable
fun AppListItem(app: AppInfo, onToggle: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = rememberAsyncImagePainter(model = app.icon),
                contentDescription = app.name,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = app.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Switch(
                checked = app.isSelected,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

@Composable
fun BackupTypeToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
