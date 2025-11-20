package io.github.hddq.restoid.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import io.github.hddq.restoid.RestoidApplication
import io.github.hddq.restoid.data.ResticState
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.ui.components.PasswordDialog
import io.github.hddq.restoid.ui.home.HomeViewModel
import io.github.hddq.restoid.ui.home.HomeViewModelFactory
import io.github.hddq.restoid.ui.home.SnapshotWithMetadata

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSnapshotClick: (String) -> Unit,
    onMaintenanceClick: () -> Unit,
    modifier: Modifier = Modifier // Accept a modifier
) {
    val application = LocalContext.current.applicationContext as RestoidApplication
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(
            application.repositoriesRepository,
            application.resticRepository,
            application.appInfoRepository,
            application.metadataRepository
        )
    )
    val uiState by viewModel.uiState.collectAsState()

    // Apply the modifier passed from the NavHost to the root Column.
    // This modifier contains the padding from the main Scaffold.
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        // Header Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Restoid",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            val isMaintenanceEnabled = uiState.selectedRepo != null && uiState.hasPasswordForSelectedRepo
            OutlinedButton(
                onClick = onMaintenanceClick,
                enabled = isMaintenanceEnabled,
                // Manually set border and content colors to fix styling issue with new compose bom
                border = BorderStroke(1.dp, if (isMaintenanceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Build, contentDescription = "Maintenance")
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Maintenance")
            }
        }
        Spacer(Modifier.height(24.dp))

        // Refreshable Content Area
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refreshSnapshots() },
            modifier = Modifier.fillMaxSize()
        ) {
            // Use Box to ensure content fills space and handles scroll for non-list items
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.selectedRepo == null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                "No repository selected. Go to settings to add one.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    uiState.resticState !is ResticState.Installed -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                "Restic not available. Check settings.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    uiState.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    uiState.error != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "Error: ${uiState.error}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    uiState.snapshotsWithMetadata.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "No snapshots found for the selected repository.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        // Main list content
                        // We use a Scrollable Column that fills max size so the swipe gesture works anywhere.
                        // Inside the card, we use a simple Column instead of LazyColumn to avoid nested scrolling issues
                        // and to ensure the Card height wraps its content correctly within the scrollable parent.
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "Snapshots (${uiState.snapshotsWithMetadata.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                )
                            ) {
                                Column {
                                    val snapshots = uiState.snapshotsWithMetadata.sortedByDescending { it.snapshotInfo.time }
                                    snapshots.forEachIndexed { index, item ->
                                        SnapshotItem(
                                            snapshotWithMetadata = item,
                                            apps = uiState.appInfoMap[item.snapshotInfo.id],
                                            onClick = { onSnapshotClick(item.snapshotInfo.id) }
                                        )
                                        if (index < snapshots.size - 1) {
                                            Divider(color = MaterialTheme.colorScheme.background)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    if (uiState.showPasswordDialogFor != null) {
        PasswordDialog(
            title = "Repository Password Required",
            message = "Please enter the password for repository: ${uiState.showPasswordDialogFor}",
            onPasswordEntered = { password, save -> viewModel.onPasswordEntered(password, save) },
            onDismiss = { viewModel.onDismissPasswordDialog() }
        )
    }
}

@Composable
private fun SnapshotItem(snapshotWithMetadata: SnapshotWithMetadata, apps: List<AppInfo>?, onClick: () -> Unit) {
    val snapshot = snapshotWithMetadata.snapshotInfo
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = snapshot.id.take(8),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = snapshot.time.take(10), // Just the date part
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val appCount = snapshotWithMetadata.metadata?.apps?.size ?: 0
        if (appCount > 0) {
            Text("Apps ($appCount):", style = MaterialTheme.typography.labelMedium)
            if (apps != null && apps.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val shownApps = apps.take(10)
                    shownApps.forEach { app ->
                        Image(
                            painter = rememberAsyncImagePainter(model = app.icon),
                            contentDescription = app.name,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    val moreCount = appCount - shownApps.size
                    if (moreCount > 0) {
                        Text(
                            "+$moreCount more",
                        )
                    }
                }
            }
        } else if (snapshot.paths.isNotEmpty()) {
            Text(
                text = "Paths: ${snapshot.paths.firstOrNull() ?: ""}...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "No app information available for this snapshot.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}