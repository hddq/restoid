package io.github.hddq.restoid.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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

@Composable
fun HomeScreen(
    onSnapshotClick: (String) -> Unit,
    onMaintenanceClick: () -> Unit,
    modifier: Modifier = Modifier
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
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
            OutlinedButton(
                onClick = onMaintenanceClick,
                enabled = uiState.selectedRepo != null
            ) {
                Icon(Icons.Default.Build, contentDescription = "Maintenance")
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Maintenance")
            }
        }
        Spacer(Modifier.height(24.dp))


        when {
            uiState.selectedRepo == null -> {
                Text(
                    "No repository selected. Go to settings to add one.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            uiState.resticState !is ResticState.Installed -> {
                Text(
                    "Restic not available. Check settings.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Text(
                    text = "Error: ${uiState.error}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
            uiState.snapshotsWithMetadata.isEmpty() && !uiState.isLoading -> {
                Text(
                    text = "No snapshots found for the selected repository.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                Text(
                    text = "Snapshots (${uiState.snapshotsWithMetadata.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.snapshotsWithMetadata.sortedByDescending { it.snapshotInfo.time }) { item ->
                        SnapshotCard(
                            snapshotWithMetadata = item,
                            apps = uiState.appInfoMap[item.snapshotInfo.id],
                            onClick = { onSnapshotClick(item.snapshotInfo.id) }
                        )
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
private fun SnapshotCard(snapshotWithMetadata: SnapshotWithMetadata, apps: List<AppInfo>?, onClick: () -> Unit) {
    val snapshot = snapshotWithMetadata.snapshotInfo
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
}
