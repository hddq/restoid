package io.github.hddq.restoid.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import io.github.hddq.restoid.R
import io.github.hddq.restoid.RestoidApplication
import io.github.hddq.restoid.model.BackupDetail
import io.github.hddq.restoid.ui.components.ExpressiveCardGroup
import io.github.hddq.restoid.ui.components.ExpressiveSectionHeader
import io.github.hddq.restoid.ui.components.ExpressiveSegmentedSurface
import io.github.hddq.restoid.ui.restore.RestoreTypes
import io.github.hddq.restoid.ui.restore.RestoreUiEvent
import io.github.hddq.restoid.ui.restore.RestoreViewModel
import io.github.hddq.restoid.ui.shared.TaskRow

@Composable
fun RestoreScreen(
    viewModel: RestoreViewModel,
    onNavigateToOperationProgress: () -> Unit,
    onNavigateToAppsConfig: () -> Unit,
    onNavigateToCustomDirectoriesConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    val application = LocalContext.current.applicationContext as RestoidApplication
    val backupDetails by viewModel.backupDetails.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val allowDowngrade by viewModel.allowDowngrade.collectAsState()
    val operationBlocked by viewModel.operationBlocked.collectAsState()
    val customDirectories by viewModel.customDirectories.collectAsState()

    val restoreAppsEnabled by viewModel.restoreAppsEnabled.collectAsState()
    val restoreCustomDirectoriesEnabled by viewModel.restoreCustomDirectoriesEnabled.collectAsState()
    val restoreTypes by viewModel.restoreTypes.collectAsState()
    val appRestoreTypes by viewModel.appRestoreTypes.collectAsState()

    val context = LocalContext.current

    LaunchedEffect(operationBlocked) {
        if (operationBlocked) {
            Toast.makeText(application, application.getString(R.string.error_operation_already_running), Toast.LENGTH_SHORT).show()
            viewModel.consumeOperationBlocked()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                RestoreUiEvent.NavigateToOperationProgress -> onNavigateToOperationProgress()
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            val rootState by viewModel.rootState.collectAsState()
            val hasRoot = rootState == io.github.hddq.restoid.data.RootState.Granted
            val selectableBackupDetails = backupDetails.filter { allowDowngrade || !it.isDowngrade }
            val customDirsList = customDirectories.map { io.github.hddq.restoid.model.CustomDirectory(it.key, it.value) }

            Column(modifier = Modifier.fillMaxWidth()) {
                ExpressiveSectionHeader(title = stringResource(R.string.operation_restore))
                ExpressiveCardGroup {
                    ExpressiveSegmentedSurface(index = 0, count = 2) {
                        TaskRow(
                            title = stringResource(R.string.run_tasks_applications),
                            subtitle = if (hasRoot) buildSelectedRestoreTypesSummary(selectableBackupDetails, appRestoreTypes, restoreTypes, context) else stringResource(R.string.root_access_denied),
                            checked = if (hasRoot) restoreAppsEnabled else false,
                            onCheckedChange = viewModel::setRestoreAppsEnabled,
                            onNavigate = if (hasRoot) onNavigateToAppsConfig else null,
                            enabled = hasRoot
                        )
                    }
                    ExpressiveSegmentedSurface(index = 1, count = 2) {
                        TaskRow(
                            title = stringResource(R.string.run_tasks_custom_directories),
                            subtitle = io.github.hddq.restoid.ui.shared.buildCustomDirectoriesSubtitle(customDirsList, context),
                            checked = restoreCustomDirectoriesEnabled,
                            onCheckedChange = viewModel::setRestoreCustomDirectoriesEnabled,
                            onNavigate = onNavigateToCustomDirectoriesConfig
                        )
                    }
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
        }
    }
}

@Composable
fun RestoreAppsConfigScreen(
    viewModel: RestoreViewModel,
    modifier: Modifier = Modifier
) {
    val backupDetails by viewModel.backupDetails.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val allowDowngrade by viewModel.allowDowngrade.collectAsState()
    val restoreTypes by viewModel.restoreTypes.collectAsState()
    val appRestoreTypes by viewModel.appRestoreTypes.collectAsState()
    val rootState by viewModel.rootState.collectAsState()

    if (rootState != io.github.hddq.restoid.data.RootState.Granted) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.root_access_denied))
        }
        return
    }

    val selectableBackupDetails = backupDetails.filter { allowDowngrade || !it.isDowngrade }
    val isAllSelected = selectableBackupDetails.isNotEmpty() && selectableBackupDetails.all { it.appInfo.isSelected }
    var selectedAppPackageName by remember { mutableStateOf<String?>(null) }
    var showBulkRestoreTypesSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item(key = "options_section") {
            Column(modifier = Modifier.padding(bottom = 14.dp)) {
                ExpressiveSectionHeader(title = stringResource(R.string.restore_options_title))
                ExpressiveSegmentedSurface(index = 0, count = 1) {
                    TaskRow(
                        title = stringResource(R.string.allow_downgrade),
                        checked = allowDowngrade,
                        onCheckedChange = viewModel::setAllowDowngrade
                    )
                }
            }
        }

        if (backupDetails.isEmpty() && !isLoading) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.no_app_data_found_in_snapshot),
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            val totalAppRows = backupDetails.size + 1
            item(key = "apps_header") {
                ExpressiveSectionHeader(title = stringResource(R.string.apps_to_restore_title))
            }
            item(key = "apps_select_all") {
                ExpressiveSegmentedSurface(index = 0, count = totalAppRows) {
                    io.github.hddq.restoid.ui.shared.SelectAllListItem(
                        isChecked = isAllSelected,
                        subtitle = buildSelectedRestoreTypesSummary(selectableBackupDetails, appRestoreTypes, restoreTypes, context),
                        onClick = { showBulkRestoreTypesSheet = true },
                        onToggle = viewModel::toggleAllRestoreSelection
                    )
                }
            }
            itemsIndexed(backupDetails, key = { _, detail -> detail.appInfo.packageName }) { index, detail ->
                ExpressiveSegmentedSurface(index = index + 1, count = totalAppRows) {
                    RestoreAppListItem(
                        detail = detail,
                        allowDowngrade = allowDowngrade,
                        restoreTypes = appRestoreTypes[detail.appInfo.packageName] ?: restoreTypes,
                        onClick = { selectedAppPackageName = detail.appInfo.packageName },
                        onToggle = { viewModel.toggleRestoreAppSelection(detail.appInfo.packageName) }
                    )
                }
            }
        }
    }

    if (showBulkRestoreTypesSheet) {
        RestoreTypesBottomSheet(
            title = stringResource(R.string.toggle_all),
            restoreTypes = selectedBulkRestoreTypes(selectableBackupDetails, appRestoreTypes, restoreTypes),
            onRestoreTypesChange = viewModel::setSelectedAppsRestoreTypes,
            onDismissRequest = { showBulkRestoreTypesSheet = false }
        )
    }

    selectedAppPackageName?.let { packageName ->
        val detail = backupDetails.firstOrNull { it.appInfo.packageName == packageName }
        if (detail != null) {
            RestoreTypesBottomSheet(
                title = detail.appInfo.name,
                restoreTypes = appRestoreTypes[packageName] ?: restoreTypes,
                onRestoreTypesChange = { viewModel.setAppRestoreTypes(packageName, it) },
                onDismissRequest = { selectedAppPackageName = null }
            )
        }
    }
}

@Composable
fun RestoreCustomDirectoriesConfigScreen(
    viewModel: RestoreViewModel,
    modifier: Modifier = Modifier
) {
    val customDirectories by viewModel.customDirectories.collectAsState()
    val customDirectoriesList = customDirectories.toList()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (customDirectoriesList.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.run_tasks_custom_directories_subtitle_none),
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            item {
                val isAllCustomDirsSelected = customDirectoriesList.all { it.second }
                val selectedCount = customDirectoriesList.count { it.second }
                val totalRows = customDirectoriesList.size + 1

                ExpressiveCardGroup {
                    ExpressiveSegmentedSurface(index = 0, count = totalRows) {
                        io.github.hddq.restoid.ui.shared.SelectAllListItem(
                            isChecked = isAllCustomDirsSelected,
                            subtitle = if (selectedCount > 0) "$selectedCount selected" else "None selected",
                            onClick = null,
                            onToggle = viewModel::toggleAllCustomDirectoriesSelection
                        )
                    }
                    customDirectoriesList.forEachIndexed { index, (path, isSelected) ->
                        ExpressiveSegmentedSurface(index = index + 1, count = totalRows) {
                            RestoreCustomDirListItem(
                                path = path,
                                isSelected = isSelected,
                                onToggle = { viewModel.toggleCustomDirectory(path) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RestoreTypeToggle(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
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
            ExpressiveCardGroup {
                ExpressiveSegmentedSurface(index = 0, count = 7) {
                    RestoreTypeToggle(
                        label = stringResource(R.string.backup_type_apk),
                        description = stringResource(R.string.backup_type_apk_desc),
                        checked = restoreTypes.apk
                    ) {
                        onRestoreTypesChange(restoreTypes.copy(apk = it))
                    }
                }
                ExpressiveSegmentedSurface(index = 1, count = 7) {
                    RestoreTypeToggle(
                        label = stringResource(R.string.backup_type_data),
                        description = stringResource(R.string.backup_type_data_desc),
                        checked = restoreTypes.data
                    ) {
                        onRestoreTypesChange(restoreTypes.copy(data = it))
                    }
                }
                ExpressiveSegmentedSurface(index = 2, count = 7) {
                    RestoreTypeToggle(
                        label = stringResource(R.string.backup_type_device_protected_data),
                        description = stringResource(R.string.backup_type_device_protected_data_desc),
                        checked = restoreTypes.deviceProtectedData
                    ) {
                        onRestoreTypesChange(restoreTypes.copy(deviceProtectedData = it))
                    }
                }
                ExpressiveSegmentedSurface(index = 3, count = 7) {
                    RestoreTypeToggle(
                        label = stringResource(R.string.backup_type_external_data),
                        description = stringResource(R.string.backup_type_external_data_desc),
                        checked = restoreTypes.externalData
                    ) {
                        onRestoreTypesChange(restoreTypes.copy(externalData = it))
                    }
                }
                ExpressiveSegmentedSurface(index = 4, count = 7) {
                    RestoreTypeToggle(
                        label = stringResource(R.string.backup_type_obb_data),
                        description = stringResource(R.string.backup_type_obb_data_desc),
                        checked = restoreTypes.obb
                    ) {
                        onRestoreTypesChange(restoreTypes.copy(obb = it))
                    }
                }
                ExpressiveSegmentedSurface(index = 5, count = 7) {
                    RestoreTypeToggle(
                        label = stringResource(R.string.backup_type_media_data),
                        description = stringResource(R.string.backup_type_media_data_desc),
                        checked = restoreTypes.media
                    ) {
                        onRestoreTypesChange(restoreTypes.copy(media = it))
                    }
                }
                ExpressiveSegmentedSurface(index = 6, count = 7) {
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

@Composable
private fun RestoreCustomDirListItem(
    path: String,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = path.substringAfterLast("/").ifEmpty { path },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            thumbContent = if (isSelected) {
                {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            } else null
        )
    }
}
