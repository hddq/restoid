package io.github.hddq.restoid.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import io.github.hddq.restoid.data.ResticState
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.ui.components.PasswordDialog
import io.github.hddq.restoid.ui.home.HomeUiState
import io.github.hddq.restoid.ui.home.SnapshotWithMetadata

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSnapshotClick: (String) -> Unit,
    onMaintenanceClick: () -> Unit,
    uiState: HomeUiState,
    onRefresh: () -> Unit,
    onPasswordEntered: (String, Boolean) -> Unit,
    onDismissPasswordDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                border = BorderStroke(1.dp, if (isMaintenanceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
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
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.selectedRepo == null -> {
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            Text("No repository selected. Go to settings to add one.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    uiState.resticState !is ResticState.Installed -> {
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            Text("Restic not available. Check settings.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    uiState.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    uiState.error != null -> {
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            Text(text = "Error: ${uiState.error}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    uiState.snapshotsWithMetadata.isEmpty() -> {
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            Text(text = "No snapshots found for the selected repository.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> {
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            Text(text = "Snapshots (${uiState.snapshotsWithMetadata.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 8.dp))
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                Column {
                                    val snapshots = uiState.snapshotsWithMetadata.sortedByDescending { it.snapshotInfo.time }
                                    snapshots.forEachIndexed { index, item ->
                                        SnapshotItem(snapshotWithMetadata = item, apps = uiState.appInfoMap[item.snapshotInfo.id], onClick = { onSnapshotClick(item.snapshotInfo.id) })
                                        if (index < snapshots.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.background)
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
            onPasswordEntered = onPasswordEntered,
            onDismiss = onDismissPasswordDialog
        )
    }
}

@Composable
private fun SnapshotItem(snapshotWithMetadata: SnapshotWithMetadata, apps: List<AppInfo>?, onClick: () -> Unit) {
    val snapshot = snapshotWithMetadata.snapshotInfo
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = snapshot.id.take(8), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(text = snapshot.time.take(10), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val appCount = snapshotWithMetadata.metadata?.apps?.size ?: 0
        if (appCount > 0) {
            Text("Apps ($appCount):", style = MaterialTheme.typography.labelMedium)
            if (apps != null && apps.isNotEmpty()) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val iconSize = 32.dp
                    val spacing = 8.dp
                    val itemWidthWithSpacing = iconSize + spacing
                    val maxIconsPossible = ((maxWidth + spacing) / itemWidthWithSpacing).toInt().coerceAtLeast(1)
                    val showCounter = appCount > maxIconsPossible
                    val limit = if (showCounter) maxIconsPossible - 1 else maxIconsPossible

                    Row(horizontalArrangement = Arrangement.spacedBy(spacing), verticalAlignment = Alignment.CenterVertically) {
                        apps.take(limit).forEach { app ->
                            Image(painter = rememberAsyncImagePainter(model = app.icon), contentDescription = app.name, modifier = Modifier.size(iconSize))
                        }
                        val moreCount = appCount - apps.size.coerceAtMost(limit) // Fix logic to use limit correctly
                        if (moreCount > 0) { // Only show if we actually hid something
                            // Recalculate based on total apps
                            val realMoreCount = appCount - limit
                            if(realMoreCount > 0) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(iconSize).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer)) {
                                    Text(text = "+$realMoreCount", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        } else if (snapshot.paths.isNotEmpty()) {
            Text(text = "Paths: ${snapshot.paths.firstOrNull() ?: ""}...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(text = "No app information available for this snapshot.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}