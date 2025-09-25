package io.github.hddq.restoid.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.hddq.restoid.RestoidApplication
import io.github.hddq.restoid.model.BackupDetail
import io.github.hddq.restoid.ui.restore.RestoreTypes
import io.github.hddq.restoid.ui.restore.RestoreViewModel
import io.github.hddq.restoid.ui.restore.RestoreViewModelFactory
import io.github.hddq.restoid.ui.shared.ProgressScreenContent
import io.github.hddq.restoid.ui.theme.Orange
import coil.compose.rememberAsyncImagePainter

@Composable
fun RestoreScreen(navController: NavController, snapshotId: String?, modifier: Modifier = Modifier) {
    val application = LocalContext.current.applicationContext as RestoidApplication
    val viewModel: RestoreViewModel = viewModel(
        factory = RestoreViewModelFactory(
            application,
            application.repositoriesRepository,
            application.resticRepository,
            application.appInfoRepository,
            application.notificationRepository,
            application.metadataRepository,
            snapshotId ?: ""
        )
    )

    val backupDetails by viewModel.backupDetails.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val restoreTypes by viewModel.restoreTypes.collectAsState()
    val allowDowngrade by viewModel.allowDowngrade.collectAsState()
    val isRestoring by viewModel.isRestoring.collectAsState()
    val restoreProgress by viewModel.restoreProgress.collectAsState()

    val showProgressScreen = isRestoring || restoreProgress.isFinished

    Crossfade(
        targetState = showProgressScreen,
        label = "RestoreScreenCrossfade",
        modifier = modifier.fillMaxSize()
    ) { showProgress ->
        if (showProgress) {
            ProgressScreenContent(
                progress = restoreProgress,
                operationType = "Restore",
                onDone = {
                    viewModel.onDone()
                    navController.popBackStack()
                }
            )
        } else {
            RestoreSelectionContent(
                backupDetails = backupDetails,
                isLoading = isLoading,
                restoreTypes = restoreTypes,
                allowDowngrade = allowDowngrade,
                onToggleApp = viewModel::toggleRestoreAppSelection,
                onToggleAll = viewModel::toggleAllRestoreSelection,
                onToggleRestoreType = { type, value ->
                    when (type) {
                        "APK" -> viewModel.setRestoreApk(value)
                        "Data" -> viewModel.setRestoreData(value)
                        "Device Protected Data" -> viewModel.setRestoreDeviceProtectedData(value)
                        "External Data" -> viewModel.setRestoreExternalData(value)
                        "OBB Data" -> viewModel.setRestoreObb(value)
                        "Media Data" -> viewModel.setRestoreMedia(value)
                    }
                },
                onToggleAllowDowngrade = viewModel::setAllowDowngrade
            )
        }
    }
}

@Composable
fun RestoreSelectionContent(
    backupDetails: List<BackupDetail>,
    isLoading: Boolean,
    restoreTypes: RestoreTypes,
    allowDowngrade: Boolean,
    onToggleApp: (String) -> Unit,
    onToggleAll: () -> Unit,
    onToggleRestoreType: (String, Boolean) -> Unit,
    onToggleAllowDowngrade: (Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Restore Options",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    RestoreTypeToggle("APK", checked = restoreTypes.apk) { onToggleRestoreType("APK", it) }
                    RestoreTypeToggle("Data", checked = restoreTypes.data) { onToggleRestoreType("Data", it) }
                    RestoreTypeToggle("Device Protected Data", checked = restoreTypes.deviceProtectedData) { onToggleRestoreType("Device Protected Data", it) }
                    RestoreTypeToggle("External Data", checked = restoreTypes.externalData) { onToggleRestoreType("External Data", it) }
                    RestoreTypeToggle("OBB Data", checked = restoreTypes.obb) { onToggleRestoreType("OBB Data", it) }
                    RestoreTypeToggle("Media Data", checked = restoreTypes.media) { onToggleRestoreType("Media Data", it) }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    RestoreTypeToggle("Allow Downgrade", checked = allowDowngrade) { onToggleAllowDowngrade(it) }
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
                    text = "Apps to Restore",
                    style = MaterialTheme.typography.titleMedium
                )
                FilledTonalButton(onClick = onToggleAll) {
                    Text("Toggle All")
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
        } else if (backupDetails.isEmpty() && !isLoading) {
            item {
                Text(
                    "No app data found in this snapshot.",
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(backupDetails, key = { it.appInfo.packageName }) { detail ->
                RestoreAppListItem(
                    detail = detail,
                    allowDowngrade = allowDowngrade,
                    onToggle = { onToggleApp(detail.appInfo.packageName) }
                )
            }
        }
    }
}

@Composable
fun RestoreTypeToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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

@Composable
private fun RestoreAppListItem(detail: BackupDetail, allowDowngrade: Boolean, onToggle: () -> Unit) {
    val app = detail.appInfo
    val isEnabled = allowDowngrade || !detail.isDowngrade

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isEnabled, onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
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
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = app.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )

                val versionColor = if (detail.isDowngrade) Orange else MaterialTheme.colorScheme.onSurfaceVariant
                val versionText = if (detail.isInstalled) {
                    "Backup: ${detail.versionName ?: "N/A"} → Installed: ${app.versionName}"
                } else {
                    "Backup: ${detail.versionName ?: "N/A"}"
                }

                Text(
                    text = versionText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isEnabled) versionColor else versionColor.copy(alpha = 0.38f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = app.isSelected,
                onCheckedChange = { onToggle() },
                enabled = isEnabled
            )
        }
    }
}
