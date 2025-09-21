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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceScreen(onNavigateUp: () -> Unit) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showProgressScreen) "Running Maintenance" else "Maintenance") },
                navigationIcon = {
                    if (!uiState.isRunning) {
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
                    onClick = { viewModel.runTasks() },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Run Tasks") },
                    text = { Text("Run Tasks") }
                )
            }
        }
    ) { paddingValues ->
        Crossfade(targetState = showProgressScreen, label = "MaintenanceScreenCrossfade") { showProgress ->
            if (showProgress) {
                ProgressScreenContent(
                    progress = uiState.progress,
                    operationType = "Maintenance",
                    onDone = {
                        viewModel.onDone()
                        onNavigateUp()
                    },
                    modifier = Modifier.padding(paddingValues)
                )
            } else {
                MaintenanceSelectionContent(
                    paddingValues = paddingValues,
                    uiState = uiState, // Pass the whole state
                    onSetCheckRepo = viewModel::setCheckRepo,
                    onSetPruneRepo = viewModel::setPruneRepo,
                    onSetReadData = viewModel::setReadData // Add this
                )
            }
        }
    }
}

@Composable
fun MaintenanceSelectionContent(
    paddingValues: PaddingValues,
    uiState: MaintenanceUiState, // Use the specific type
    onSetCheckRepo: (Boolean) -> Unit,
    onSetPruneRepo: (Boolean) -> Unit,
    onSetReadData: (Boolean) -> Unit // Add this
) {
    LazyColumn(
        modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                        text = "Tasks",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    MaintenanceTaskToggle(
                        label = "Check repository integrity",
                        checked = uiState.checkRepo,
                        onCheckedChange = onSetCheckRepo
                    )
                    // Indent the read-data option and only show if check is enabled
                    if (uiState.checkRepo) {
                        Row(Modifier.padding(start = 16.dp)) {
                            MaintenanceTaskToggle(
                                label = "Read all data",
                                checked = uiState.readData,
                                onCheckedChange = onSetReadData
                            )
                        }
                    }
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
