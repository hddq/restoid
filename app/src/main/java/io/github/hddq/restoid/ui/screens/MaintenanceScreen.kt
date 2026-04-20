package io.github.hddq.restoid.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.hddq.restoid.R
import io.github.hddq.restoid.RestoidApplication
import io.github.hddq.restoid.ui.maintenance.MaintenanceUiEvent
import io.github.hddq.restoid.ui.maintenance.MaintenanceUiState
import io.github.hddq.restoid.ui.maintenance.MaintenanceViewModel
import io.github.hddq.restoid.ui.maintenance.MaintenanceViewModelFactory
import kotlin.math.roundToInt
import android.widget.Toast

@Composable
fun MaintenanceScreen(onNavigateToOperationProgress: () -> Unit, modifier: Modifier = Modifier) {
    val application = LocalContext.current.applicationContext as RestoidApplication
    val viewModel: MaintenanceViewModel = viewModel(
        factory = MaintenanceViewModelFactory(
            application,
            application.repositoriesRepository,
            application.resticBinaryManager,
            application.preferencesRepository,
            application.operationWorkRepository
        )
    )
    val uiState by viewModel.uiState.collectAsState()
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
                MaintenanceUiEvent.NavigateToOperationProgress -> onNavigateToOperationProgress()
            }
        }
    }

    MaintenanceSelectionContent(
        uiState = uiState,
        onSetCheckRepo = viewModel::setCheckRepo,
        onSetPruneRepo = viewModel::setPruneRepo,
        onSetUnlockRepo = viewModel::setUnlockRepo,
        onSetReadData = viewModel::setReadData,
        onSetForgetSnapshots = viewModel::setForgetSnapshots,
        onSetKeepLast = viewModel::setKeepLast,
        onSetKeepDaily = viewModel::setKeepDaily,
        onSetKeepWeekly = viewModel::setKeepWeekly,
        onSetKeepMonthly = viewModel::setKeepMonthly
    )
}

@Composable
fun MaintenanceSelectionContent(
    uiState: MaintenanceUiState,
    onSetCheckRepo: (Boolean) -> Unit,
    onSetPruneRepo: (Boolean) -> Unit,
    onSetUnlockRepo: (Boolean) -> Unit,
    onSetReadData: (Boolean) -> Unit,
    onSetForgetSnapshots: (Boolean) -> Unit,
    onSetKeepLast: (Int) -> Unit,
    onSetKeepDaily: (Int) -> Unit,
    onSetKeepWeekly: (Int) -> Unit,
    onSetKeepMonthly: (Int) -> Unit
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
                        text = stringResource(R.string.maintenance_tasks_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                    MaintenanceTaskToggle(
                        label = stringResource(R.string.maintenance_task_unlock_repository),
                        checked = uiState.unlockRepo,
                        onCheckedChange = onSetUnlockRepo
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    MaintenanceTaskToggle(
                        label = stringResource(R.string.maintenance_task_forget_old_snapshots),
                        checked = uiState.forgetSnapshots,
                        onCheckedChange = onSetForgetSnapshots
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    MaintenanceTaskToggle(
                        label = stringResource(R.string.maintenance_task_prune_repository),
                        checked = uiState.pruneRepo,
                        onCheckedChange = onSetPruneRepo
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    MaintenanceTaskToggle(
                        label = stringResource(R.string.maintenance_task_check_repository_integrity),
                        checked = uiState.checkRepo,
                        onCheckedChange = onSetCheckRepo
                    )
                }
            }
        }

        item {
            AnimatedVisibility(visible = uiState.forgetSnapshots) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.maintenance_forget_policy_options),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                        PolicySlider(label = stringResource(R.string.maintenance_keep_last), value = uiState.keepLast, range = 0..20, onValueChange = onSetKeepLast)
                        PolicySlider(label = stringResource(R.string.maintenance_keep_daily), value = uiState.keepDaily, range = 0..30, onValueChange = onSetKeepDaily)
                        PolicySlider(label = stringResource(R.string.maintenance_keep_weekly), value = uiState.keepWeekly, range = 0..12, onValueChange = onSetKeepWeekly)
                        PolicySlider(label = stringResource(R.string.maintenance_keep_monthly), value = uiState.keepMonthly, range = 0..24, onValueChange = onSetKeepMonthly)
                    }
                }
            }
        }

        item {
            AnimatedVisibility(visible = uiState.checkRepo) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.maintenance_check_options),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                        MaintenanceTaskToggle(
                            label = stringResource(R.string.maintenance_read_all_data),
                            checked = uiState.readData,
                            onCheckedChange = onSetReadData
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PolicySlider(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(value.toString(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0)
        )
    }
}

@Composable
fun MaintenanceTaskToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
