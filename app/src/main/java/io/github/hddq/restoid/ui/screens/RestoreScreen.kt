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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import android.widget.Toast
import io.github.hddq.restoid.R
import io.github.hddq.restoid.RestoidApplication
import io.github.hddq.restoid.model.BackupDetail
import io.github.hddq.restoid.ui.restore.RestoreTypes
import io.github.hddq.restoid.ui.restore.RestoreViewModel
import io.github.hddq.restoid.ui.restore.RestoreViewModelFactory
import io.github.hddq.restoid.ui.shared.ProgressScreenContent
import io.github.hddq.restoid.ui.theme.Orange

@Composable
fun RestoreScreen(navController: NavController, snapshotId: String?, modifier: Modifier = Modifier) {
    val application = LocalContext.current.applicationContext as RestoidApplication
    val viewModel: RestoreViewModel = viewModel(
        factory = RestoreViewModelFactory(
            application,
            application.repositoriesRepository,
            application.resticBinaryManager,
            application.resticRepository,
            application.appInfoRepository,
            application.metadataRepository,
            application.preferencesRepository,
            application.operationWorkRepository,
            snapshotId ?: ""
        )
    )

    val backupDetails by viewModel.backupDetails.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val restoreTypes by viewModel.restoreTypes.collectAsState()
    val allowDowngrade by viewModel.allowDowngrade.collectAsState()
    val isRestoring by viewModel.isRestoring.collectAsState()
    val restoreProgress by viewModel.restoreProgress.collectAsState()
    val operationBlocked by viewModel.operationBlocked.collectAsState()

    LaunchedEffect(operationBlocked) {
        if (operationBlocked) {
            Toast.makeText(application, application.getString(R.string.error_operation_already_running), Toast.LENGTH_SHORT).show()
            viewModel.consumeOperationBlocked()
        }
    }

    val showProgressScreen = isRestoring || restoreProgress.isFinished
    val apkLabel = stringResource(R.string.backup_type_apk)
    val dataLabel = stringResource(R.string.backup_type_data)
    val deviceProtectedDataLabel = stringResource(R.string.backup_type_device_protected_data)
    val externalDataLabel = stringResource(R.string.backup_type_external_data)
    val obbDataLabel = stringResource(R.string.backup_type_obb_data)
    val mediaDataLabel = stringResource(R.string.backup_type_media_data)
    val allowDowngradeLabel = stringResource(R.string.allow_downgrade)

    Crossfade(
        targetState = showProgressScreen,
        label = "RestoreScreenCrossfade",
        modifier = modifier.fillMaxSize()
    ) { showProgress ->
        if (showProgress) {
            ProgressScreenContent(
                progress = restoreProgress,
                operationType = stringResource(R.string.operation_restore),
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
                        apkLabel -> viewModel.setRestoreApk(value)
                        dataLabel -> viewModel.setRestoreData(value)
                        deviceProtectedDataLabel -> viewModel.setRestoreDeviceProtectedData(value)
                        externalDataLabel -> viewModel.setRestoreExternalData(value)
                        obbDataLabel -> viewModel.setRestoreObb(value)
                        mediaDataLabel -> viewModel.setRestoreMedia(value)
                        allowDowngradeLabel -> viewModel.setAllowDowngrade(value)
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
    val apkLabel = stringResource(R.string.backup_type_apk)
    val dataLabel = stringResource(R.string.backup_type_data)
    val deviceProtectedDataLabel = stringResource(R.string.backup_type_device_protected_data)
    val externalDataLabel = stringResource(R.string.backup_type_external_data)
    val obbDataLabel = stringResource(R.string.backup_type_obb_data)
    val mediaDataLabel = stringResource(R.string.backup_type_media_data)

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
                        text = stringResource(R.string.restore_options_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                    RestoreTypeToggle(apkLabel, checked = restoreTypes.apk) { onToggleRestoreType(apkLabel, it) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    RestoreTypeToggle(dataLabel, checked = restoreTypes.data) { onToggleRestoreType(dataLabel, it) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    RestoreTypeToggle(deviceProtectedDataLabel, checked = restoreTypes.deviceProtectedData) { onToggleRestoreType(deviceProtectedDataLabel, it) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    RestoreTypeToggle(externalDataLabel, checked = restoreTypes.externalData) { onToggleRestoreType(externalDataLabel, it) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    RestoreTypeToggle(obbDataLabel, checked = restoreTypes.obb) { onToggleRestoreType(obbDataLabel, it) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    RestoreTypeToggle(mediaDataLabel, checked = restoreTypes.media) { onToggleRestoreType(mediaDataLabel, it) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    RestoreTypeToggle(stringResource(R.string.allow_downgrade), checked = allowDowngrade) { onToggleAllowDowngrade(it) }
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
                    text = stringResource(R.string.apps_to_restore_title),
                    style = MaterialTheme.typography.titleMedium
                )
                FilledTonalButton(onClick = onToggleAll) {
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
        } else if (backupDetails.isEmpty() && !isLoading) {
            item {
                Text(
                    stringResource(R.string.no_app_data_found_in_snapshot),
                    modifier = Modifier.padding(16.dp)
                )
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
                        backupDetails.forEachIndexed { index, detail ->
                            RestoreAppListItem(
                                detail = detail,
                                allowDowngrade = allowDowngrade,
                                onToggle = { onToggleApp(detail.appInfo.packageName) }
                            )
                            if (index < backupDetails.size - 1) {
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
fun RestoreTypeToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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

@Composable
private fun RestoreAppListItem(detail: BackupDetail, allowDowngrade: Boolean, onToggle: () -> Unit) {
    val app = detail.appInfo
    val isEnabled = allowDowngrade || !detail.isDowngrade

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isEnabled, onClick = onToggle)
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
                stringResource(
                    R.string.restore_backup_version_with_installed,
                    detail.versionName ?: stringResource(R.string.not_available),
                    app.versionName
                )
            } else {
                stringResource(
                    R.string.restore_backup_version_only,
                    detail.versionName ?: stringResource(R.string.not_available)
                )
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
            enabled = isEnabled,
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
