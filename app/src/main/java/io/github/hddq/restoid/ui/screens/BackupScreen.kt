package io.github.hddq.restoid.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import android.widget.Toast
import io.github.hddq.restoid.R
import io.github.hddq.restoid.RestoidApplication
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.ui.backup.BackupTypes
import io.github.hddq.restoid.ui.backup.BackupViewModel
import io.github.hddq.restoid.ui.backup.BackupViewModelFactory
import io.github.hddq.restoid.ui.shared.ProgressScreenContent

@Composable
fun BackupScreen(onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    val application = LocalContext.current.applicationContext as RestoidApplication
    val viewModel: BackupViewModel = viewModel(
        factory = BackupViewModelFactory(
            application,
            application.repositoriesRepository,
            application.resticBinaryManager, // Added this!
            application.resticRepository,
            application.notificationRepository,
            application.appInfoRepository,
            application.preferencesRepository,
            application.operationCoordinator,
            application.operationLockManager
        )
    )
    val apps by viewModel.apps.collectAsState()
    val isLoadingApps by viewModel.isLoadingApps.collectAsState()
    val backupTypes by viewModel.backupTypes.collectAsState()
    val isBackingUp by viewModel.isBackingUp.collectAsState()
    val backupProgress by viewModel.backupProgress.collectAsState()
    val operationBlocked by viewModel.operationBlocked.collectAsState()

    LaunchedEffect(operationBlocked) {
        if (operationBlocked) {
            Toast.makeText(application, application.getString(R.string.error_operation_already_running), Toast.LENGTH_SHORT).show()
            viewModel.consumeOperationBlocked()
        }
    }

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

    Crossfade(
        targetState = showProgressScreen,
        label = "BackupScreenCrossfade",
        modifier = modifier.fillMaxSize()
    ) { showProgress ->
        if (showProgress) {
            ProgressScreenContent(
                progress = backupProgress,
                operationType = stringResource(R.string.operation_backup),
                onDone = {
                    viewModel.onDone()
                    onNavigateUp()
                }
            )
        } else {
            BackupSelectionContent(
                viewModel = viewModel,
                apps = apps,
                isLoading = isLoadingApps,
                backupTypes = backupTypes
            )
        }
    }
}

@Composable
fun BackupSelectionContent(
    viewModel: BackupViewModel,
    apps: List<AppInfo>,
    isLoading: Boolean,
    backupTypes: BackupTypes
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.backup_types_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                    BackupTypeToggle(stringResource(R.string.backup_type_apk), backupTypes.apk) { viewModel.setBackupApk(it) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    BackupTypeToggle(stringResource(R.string.backup_type_data), backupTypes.data) { viewModel.setBackupData(it) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    BackupTypeToggle(stringResource(R.string.backup_type_device_protected_data), backupTypes.deviceProtectedData) { viewModel.setBackupDeviceProtectedData(it) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    BackupTypeToggle(stringResource(R.string.backup_type_external_data), backupTypes.externalData) { viewModel.setBackupExternalData(it) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    BackupTypeToggle(stringResource(R.string.backup_type_obb_data), backupTypes.obb) { viewModel.setBackupObb(it) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    BackupTypeToggle(stringResource(R.string.backup_type_media_data), backupTypes.media) { viewModel.setBackupMedia(it) }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.apps_title),
                    style = MaterialTheme.typography.titleMedium
                )
                FilledTonalButton(onClick = { viewModel.toggleAll() }) {
                    Text(stringResource(R.string.toggle_all))
                }
            }
        }

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
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        apps.forEachIndexed { index, app ->
                            AppListItem(app = app) { viewModel.toggleAppSelection(app.packageName) }
                            if (index < apps.size - 1) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.background)
                            }
                        }
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
            onCheckedChange = { onToggle() },
            thumbContent = if (app.isSelected) {
                {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            } else {
                null
            }
        )
    }
}

@Composable
fun BackupTypeToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            thumbContent = if (checked) {
                {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            } else {
                null
            }
        )
    }
}
