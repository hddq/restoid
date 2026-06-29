package io.github.hddq.restoid.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.github.hddq.restoid.Screen
import io.github.hddq.restoid.model.BackupDetail
import io.github.hddq.restoid.ui.restore.RestoreUiEvent
import io.github.hddq.restoid.ui.restore.RestoreTypes
import io.github.hddq.restoid.ui.restore.RestoreViewModel
import io.github.hddq.restoid.ui.restore.RestoreViewModelFactory
import io.github.hddq.restoid.ui.shared.TaskRow

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
    val appRestoreTypes by viewModel.appRestoreTypes.collectAsState()
    val allowDowngrade by viewModel.allowDowngrade.collectAsState()
    val operationBlocked by viewModel.operationBlocked.collectAsState()

    LaunchedEffect(operationBlocked) {
        if (operationBlocked) {
            Toast.makeText(application, application.getString(R.string.error_operation_already_running), Toast.LENGTH_SHORT).show()
            viewModel.consumeOperationBlocked()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                RestoreUiEvent.NavigateToOperationProgress -> navController.navigate(Screen.OperationProgress.route) {
                    launchSingleTop = true
                }
            }
        }
    }

    RestoreSelectionContent(
        modifier = modifier,
        backupDetails = backupDetails,
        isLoading = isLoading,
        restoreTypes = restoreTypes,
        appRestoreTypes = appRestoreTypes,
        allowDowngrade = allowDowngrade,
        onToggleApp = viewModel::toggleRestoreAppSelection,
        onToggleAll = viewModel::toggleAllRestoreSelection,
        onSetAppRestoreTypes = viewModel::setAppRestoreTypes,
        onSetSelectedAppsRestoreTypes = viewModel::setSelectedAppsRestoreTypes,
        onToggleAllowDowngrade = viewModel::setAllowDowngrade
    )
}

@Composable
fun RestoreSelectionContent(
    modifier: Modifier = Modifier,
    backupDetails: List<BackupDetail>,
    isLoading: Boolean,
    restoreTypes: RestoreTypes,
    appRestoreTypes: Map<String, RestoreTypes>,
    allowDowngrade: Boolean,
    onToggleApp: (String) -> Unit,
    onToggleAll: () -> Unit,
    onSetAppRestoreTypes: (String, RestoreTypes) -> Unit,
    onSetSelectedAppsRestoreTypes: (RestoreTypes) -> Unit,
    onToggleAllowDowngrade: (Boolean) -> Unit
) {
    val selectableBackupDetails = backupDetails.filter { allowDowngrade || !it.isDowngrade }
    val isAllSelected = selectableBackupDetails.isNotEmpty() && selectableBackupDetails.all { it.appInfo.isSelected }
    var selectedAppPackageName by remember { mutableStateOf<String?>(null) }
    var showBulkRestoreTypesSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = stringResource(R.string.restore_options_title),
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
                    TaskRow(
                        title = stringResource(R.string.allow_downgrade),
                        checked = allowDowngrade,
                        onCheckedChange = onToggleAllowDowngrade
                    )
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
                Column {
                    Text(
                        text = stringResource(R.string.apps_to_restore_title),
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
                            io.github.hddq.restoid.ui.shared.SelectAllListItem(
                                isChecked = isAllSelected,
                                subtitle = buildSelectedRestoreTypesSummary(selectableBackupDetails, appRestoreTypes, restoreTypes, context),
                                onClick = { showBulkRestoreTypesSheet = true },
                                onToggle = onToggleAll
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.background)

                            backupDetails.forEachIndexed { index, detail ->
                                RestoreAppListItem(
                                    detail = detail,
                                    allowDowngrade = allowDowngrade,
                                    restoreTypes = appRestoreTypes[detail.appInfo.packageName] ?: restoreTypes,
                                    onClick = { selectedAppPackageName = detail.appInfo.packageName },
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

    if (showBulkRestoreTypesSheet) {
        RestoreTypesBottomSheet(
            title = stringResource(R.string.toggle_all),
            restoreTypes = selectedBulkRestoreTypes(selectableBackupDetails, appRestoreTypes, restoreTypes),
            onRestoreTypesChange = onSetSelectedAppsRestoreTypes,
            onDismissRequest = { showBulkRestoreTypesSheet = false }
        )
    }

    selectedAppPackageName?.let { packageName ->
        val detail = backupDetails.firstOrNull { it.appInfo.packageName == packageName }
        if (detail != null) {
            RestoreTypesBottomSheet(
                title = detail.appInfo.name,
                restoreTypes = appRestoreTypes[packageName] ?: restoreTypes,
                onRestoreTypesChange = { onSetAppRestoreTypes(packageName, it) },
                onDismissRequest = { selectedAppPackageName = null }
            )
        }
    }
}

@Composable
fun RestoreTypeToggle(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
private fun RestoreAppListItem(
    detail: BackupDetail,
    allowDowngrade: Boolean,
    restoreTypes: RestoreTypes,
    onClick: () -> Unit,
    onToggle: () -> Unit
) {
    val app = detail.appInfo
    val isEnabled = allowDowngrade || !detail.isDowngrade
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(enabled = isEnabled, onClick = onClick)
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

            val versionColor = if (detail.isDowngrade) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
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
            Text(
                text = buildRestoreTypesSummary(restoreTypes, context),
                style = MaterialTheme.typography.bodySmall,
                color = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        )
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .fillMaxHeight(0.5f)
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(Modifier.width(16.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestoreTypesBottomSheet(
    title: String,
    restoreTypes: RestoreTypes,
    onRestoreTypesChange: (RestoreTypes) -> Unit,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column {
                    RestoreTypeToggle(
                        label = stringResource(R.string.backup_type_apk),
                        description = stringResource(R.string.backup_type_apk_desc),
                        checked = restoreTypes.apk
                    ) {
                        onRestoreTypesChange(restoreTypes.copy(apk = it))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    RestoreTypeToggle(
                        label = stringResource(R.string.backup_type_data),
                        description = stringResource(R.string.backup_type_data_desc),
                        checked = restoreTypes.data
                    ) {
                        onRestoreTypesChange(restoreTypes.copy(data = it))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    RestoreTypeToggle(
                        label = stringResource(R.string.backup_type_device_protected_data),
                        description = stringResource(R.string.backup_type_device_protected_data_desc),
                        checked = restoreTypes.deviceProtectedData
                    ) {
                        onRestoreTypesChange(restoreTypes.copy(deviceProtectedData = it))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    RestoreTypeToggle(
                        label = stringResource(R.string.backup_type_external_data),
                        description = stringResource(R.string.backup_type_external_data_desc),
                        checked = restoreTypes.externalData
                    ) {
                        onRestoreTypesChange(restoreTypes.copy(externalData = it))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    RestoreTypeToggle(
                        label = stringResource(R.string.backup_type_obb_data),
                        description = stringResource(R.string.backup_type_obb_data_desc),
                        checked = restoreTypes.obb
                    ) {
                        onRestoreTypesChange(restoreTypes.copy(obb = it))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    RestoreTypeToggle(
                        label = stringResource(R.string.backup_type_media_data),
                        description = stringResource(R.string.backup_type_media_data_desc),
                        checked = restoreTypes.media
                    ) {
                        onRestoreTypesChange(restoreTypes.copy(media = it))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    RestoreTypeToggle(
                        label = stringResource(R.string.backup_type_permissions),
                        description = stringResource(R.string.backup_type_permissions_desc),
                        checked = restoreTypes.permissions
                    ) {
                        onRestoreTypesChange(restoreTypes.copy(permissions = it))
                    }
                }
            }
        }
    }
}

private fun buildRestoreTypesSummary(restoreTypes: RestoreTypes, context: android.content.Context): String {
    val types = buildList {
        if (restoreTypes.apk) add(context.getString(R.string.backup_type_apk))
        if (restoreTypes.data) add(context.getString(R.string.backup_type_data))
        if (restoreTypes.deviceProtectedData) add(context.getString(R.string.backup_type_device_protected_data))
        if (restoreTypes.externalData) add(context.getString(R.string.backup_type_external_data))
        if (restoreTypes.obb) add(context.getString(R.string.backup_type_obb_data))
        if (restoreTypes.media) add(context.getString(R.string.backup_type_media_data))
        if (restoreTypes.permissions) add(context.getString(R.string.backup_type_permissions))
    }.joinToString(", ")

    return types.ifBlank { context.getString(R.string.backup_types_none) }
}

private fun buildSelectedRestoreTypesSummary(
    details: List<BackupDetail>,
    appRestoreTypes: Map<String, RestoreTypes>,
    defaultRestoreTypes: RestoreTypes,
    context: android.content.Context
): String {
    val selectedTypes = details
        .filter { it.appInfo.isSelected }
        .map { appRestoreTypes[it.appInfo.packageName] ?: defaultRestoreTypes }
        .distinct()

    return when (selectedTypes.size) {
        0 -> buildRestoreTypesSummary(defaultRestoreTypes, context)
        1 -> buildRestoreTypesSummary(selectedTypes.first(), context)
        else -> context.getString(R.string.backup_types_mixed)
    }
}

private fun selectedBulkRestoreTypes(
    details: List<BackupDetail>,
    appRestoreTypes: Map<String, RestoreTypes>,
    defaultRestoreTypes: RestoreTypes
): RestoreTypes {
    return details
        .firstOrNull { it.appInfo.isSelected }
        ?.let { appRestoreTypes[it.appInfo.packageName] ?: defaultRestoreTypes }
        ?: defaultRestoreTypes
}
