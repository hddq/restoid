package io.github.hddq.restoid.ui.runtasks

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.SwitchDefaults
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.hddq.restoid.R
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.ui.shared.*

@Composable
fun RunTasksScreen(
    viewModel: RunTasksViewModel,
    onNavigateToOperationProgress: () -> Unit,
    onNavigateToBackupConfig: () -> Unit,
    onNavigateToCustomDirectoriesConfig: () -> Unit,
    onNavigateToForgetConfig: () -> Unit,
    onNavigateToCheckConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val operationBlocked by viewModel.operationBlocked.collectAsState()

    LaunchedEffect(operationBlocked) {
        if (operationBlocked) {
            Toast.makeText(context, context.getString(R.string.error_operation_already_running), Toast.LENGTH_SHORT).show()
            viewModel.consumeOperationBlocked()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                RunTasksUiEvent.NavigateToOperationProgress -> onNavigateToOperationProgress()
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = context.getString(R.string.operation_backup),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    val rootState by viewModel.rootState.collectAsState()
                    val hasRoot = rootState == io.github.hddq.restoid.data.RootState.Granted
                    
                    TaskRow(
                        title = context.getString(R.string.run_tasks_applications),
                        subtitle = if (hasRoot) buildBackupSubtitle(uiState.apps, uiState.appBackupTypes, uiState.backupTypes, context) else context.getString(R.string.root_access_denied),
                        checked = if (hasRoot) uiState.backupEnabled else false,
                        onCheckedChange = viewModel::setBackupEnabled,
                        onNavigate = if (hasRoot) onNavigateToBackupConfig else null,
                        enabled = hasRoot
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    TaskRow(
                        title = context.getString(R.string.run_tasks_custom_directories),
                        subtitle = buildCustomDirectoriesSubtitle(uiState.customDirectories, context),
                        checked = uiState.customDirectoriesBackupEnabled,
                        onCheckedChange = viewModel::setCustomDirectoriesBackupEnabled,
                        onNavigate = onNavigateToCustomDirectoriesConfig
                    )
                }
            }
        }

        item {
            Column {
                Text(
                    text = context.getString(R.string.operation_maintenance),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column {
                        TaskRow(
                            title = context.getString(R.string.maintenance_task_unlock_repository),
                            checked = uiState.maintenance.unlockRepo,
                            onCheckedChange = viewModel::setUnlockRepo
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.background)
                        TaskRow(
                            title = context.getString(R.string.run_tasks_forget_snapshots),
                            subtitle = buildForgetSubtitle(uiState.maintenance, context),
                            checked = uiState.maintenance.forgetSnapshots,
                            onCheckedChange = viewModel::setForgetSnapshots,
                            onNavigate = onNavigateToForgetConfig
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.background)
                        TaskRow(
                            title = context.getString(R.string.maintenance_task_prune_repository),
                            checked = uiState.maintenance.pruneRepo,
                            onCheckedChange = viewModel::setPruneRepo
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.background)
                        TaskRow(
                            title = context.getString(R.string.run_tasks_check_integrity),
                            subtitle = buildCheckSubtitle(uiState.maintenance, context),
                            checked = uiState.maintenance.checkRepo,
                            onCheckedChange = viewModel::setCheckRepo,
                            onNavigate = onNavigateToCheckConfig
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BackupConfigScreen(
    viewModel: RunTasksViewModel,
    modifier: Modifier = Modifier
) {
    val rootState by viewModel.rootState.collectAsState()
    if (rootState != io.github.hddq.restoid.data.RootState.Granted) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.root_access_denied))
        }
        return
    }

    val uiState by viewModel.uiState.collectAsState()
    io.github.hddq.restoid.ui.shared.BackupConfigScreen(
        isLoadingApps = uiState.isLoadingApps,
        apps = uiState.apps,
        appBackupTypes = uiState.appBackupTypes,
        backupTypes = uiState.backupTypes,
        onRefreshApps = viewModel::refreshAppsList,
        onToggleAllApps = viewModel::toggleAllApps,
        onToggleAppSelection = viewModel::toggleAppSelection,
        onSetSelectedAppsBackupTypes = viewModel::setSelectedAppsBackupTypes,
        onSetAppBackupTypes = viewModel::setAppBackupTypes,
        modifier = modifier
    )
}

@Composable
fun ForgetConfigScreen(
    viewModel: RunTasksViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    io.github.hddq.restoid.ui.shared.ForgetConfigScreen(
        maintenance = uiState.maintenance,
        onKeepLastChange = viewModel::setKeepLast,
        onKeepDailyChange = viewModel::setKeepDaily,
        onKeepWeeklyChange = viewModel::setKeepWeekly,
        onKeepMonthlyChange = viewModel::setKeepMonthly,
        modifier = modifier
    )
}

@Composable
fun CheckConfigScreen(
    viewModel: RunTasksViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    io.github.hddq.restoid.ui.shared.CheckConfigScreen(
        maintenance = uiState.maintenance,
        onReadDataChange = viewModel::setReadData,
        modifier = modifier
    )
}



@Composable
fun CustomDirectoriesConfigScreen(
    viewModel: RunTasksViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    io.github.hddq.restoid.ui.shared.CustomDirectoriesConfigScreen(
        customDirectories = uiState.customDirectories,
        onAddDirectory = viewModel::addCustomDirectory,
        onToggleDirectory = viewModel::toggleCustomDirectory,
        onRemoveDirectory = viewModel::removeCustomDirectory,
        modifier = modifier
    )
}
