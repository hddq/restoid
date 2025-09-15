package app.restoid.ui.screens

import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import app.restoid.RestoidApplication
import app.restoid.model.AppInfo
import app.restoid.ui.backup.BackupProgress
import app.restoid.ui.backup.BackupTypes
import app.restoid.ui.backup.BackupViewModel
import app.restoid.ui.backup.BackupViewModelFactory
import coil.compose.rememberAsyncImagePainter
import java.util.concurrent.TimeUnit

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
                BackupProgressContent(
                    progress = backupProgress,
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
fun BackupProgressContent(progress: BackupProgress, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (progress.isFinished) {
            // Finished State
            val icon = if (progress.error == null) Icons.Default.CheckCircle else Icons.Default.Error
            val iconColor = if (progress.error == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = iconColor
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (progress.error == null) "Backup Complete" else "Backup Failed",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = progress.finalSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onDone) {
                Text("Done")
            }

        } else {
            // In-Progress State
            Text(text = progress.currentAction, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    // Progress Bar
                    LinearProgressIndicator(
                        progress = { progress.percentage },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))

                    // Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        ProgressStat(label = "Elapsed", value = formatElapsedTime(progress.elapsedTime))
                        ProgressStat(
                            label = "Files",
                            value = "${progress.filesProcessed}/${progress.totalFiles}"
                        )
                        ProgressStat(
                            label = "Size",
                            value = "${Formatter.formatFileSize(context, progress.bytesProcessed)} / ${Formatter.formatFileSize(context, progress.totalBytes)}"
                        )
                    }
                    Spacer(Modifier.height(16.dp))

                    // Current File Text
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Processing: ",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        AnimatedContent(
                            targetState = progress.currentFile,
                            label = "CurrentFileAnimation",
                            transitionSpec = {
                                slideInVertically { height -> height } togetherWith
                                        slideOutVertically { height -> -height }
                            }
                        ) { targetFile ->
                            Text(
                                text = targetFile.ifEmpty { "..." },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

private fun formatElapsedTime(seconds: Long): String {
    val hours = TimeUnit.SECONDS.toHours(seconds)
    val minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
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
            if (isLoading) {
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
