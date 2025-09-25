package io.github.hddq.restoid.ui.screens

import androidx.compose.animation.Crossfade
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
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.hddq.restoid.RestoidApplication
import io.github.hddq.restoid.ui.maintenance.MaintenanceUiState
import io.github.hddq.restoid.ui.maintenance.MaintenanceViewModel
import io.github.hddq.restoid.ui.maintenance.MaintenanceViewModelFactory
import io.github.hddq.restoid.ui.shared.ProgressScreenContent

@Composable
fun MaintenanceScreen(onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    val application = LocalContext.current.applicationContext as RestoidApplication
    val viewModel: MaintenanceViewModel = viewModel(
        factory = MaintenanceViewModelFactory(
            application.repositoriesRepository,
            application.resticRepository,
            application.notificationRepository
        )
    )
    val uiState by viewModel.uiState.collectAsState()
    val showProgressScreen = uiState.isRunning || uiState.progress.isFinished

    Crossfade(
        targetState = showProgressScreen,
        label = "MaintenanceScreenCrossfade",
        modifier = modifier.fillMaxSize()
    ) { showProgress ->
        if (showProgress) {
            ProgressScreenContent(
                progress = uiState.progress,
                operationType = "Maintenance",
                onDone = {
                    viewModel.onDone()
                    onNavigateUp()
                }
            )
        } else {
            MaintenanceSelectionContent(
                uiState = uiState,
                onSetCheckRepo = viewModel::setCheckRepo,
                onSetPruneRepo = viewModel::setPruneRepo,
                onSetUnlockRepo = viewModel::setUnlockRepo,
                onSetReadData = viewModel::setReadData
            )
        }
    }
}

@Composable
fun MaintenanceSelectionContent(
    uiState: MaintenanceUiState,
    onSetCheckRepo: (Boolean) -> Unit,
    onSetPruneRepo: (Boolean) -> Unit,
    onSetUnlockRepo: (Boolean) -> Unit,
    onSetReadData: (Boolean) -> Unit
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
                        text = "Tasks",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                    MaintenanceTaskToggle(
                        label = "Unlock repository",
                        checked = uiState.unlockRepo,
                        onCheckedChange = onSetUnlockRepo
                    )
                    Divider(color = MaterialTheme.colorScheme.background)
                    MaintenanceTaskToggle(
                        label = "Check repository integrity",
                        checked = uiState.checkRepo,
                        onCheckedChange = onSetCheckRepo
                    )
                    if (uiState.checkRepo) {
                        Row(Modifier.padding(start = 16.dp)) {
                            MaintenanceTaskToggle(
                                label = "Read all data",
                                checked = uiState.readData,
                                onCheckedChange = onSetReadData
                            )
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.background)
                    MaintenanceTaskToggle(
                        label = "Prune repository",
                        checked = uiState.pruneRepo,
                        onCheckedChange = onSetPruneRepo
                    )
                }
            }
        }
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
