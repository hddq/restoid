package app.restoid.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.restoid.RestoidApplication
import app.restoid.data.ResticState
import app.restoid.data.SnapshotInfo
import app.restoid.model.AppInfo
import app.restoid.ui.components.PasswordDialog
import app.restoid.ui.home.HomeViewModel
import app.restoid.ui.home.HomeViewModelFactory
import coil.compose.rememberAsyncImagePainter

@Composable
fun HomeScreen(
    onNavigateToBackup: () -> Unit,
    onSnapshotClick: (String) -> Unit
) {
    val application = LocalContext.current.applicationContext as RestoidApplication
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(
            application.repositoriesRepository,
            application.resticRepository,
            application.appInfoRepository
        )
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // This effect will trigger a refresh of the snapshot list whenever the screen is resumed.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, uiState.selectedRepo) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (uiState.selectedRepo != null && uiState.resticState is ResticState.Installed) {
                    viewModel.loadSnapshots(uiState.selectedRepo, uiState.resticState)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Backup") },
                icon = { Icon(Icons.Filled.Add, contentDescription = "Backup") },
                onClick = onNavigateToBackup,
                // Only show if a repo is selected and restic is ready
                expanded = uiState.selectedRepo != null && uiState.resticState is ResticState.Installed
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                text = "Restoid",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

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
                uiState.snapshots.isEmpty() && !uiState.isLoading -> {
                    Text(
                        text = "No snapshots found for the selected repository.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Text(
                        text = "Snapshots (${uiState.snapshots.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.snapshots.sortedByDescending { it.time }) { snapshot ->
                            SnapshotCard(
                                snapshot = snapshot,
                                apps = uiState.appInfoMap[snapshot.id],
                                onClick = { onSnapshotClick(snapshot.id) }
                            )
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
            onPasswordEntered = { password -> viewModel.onPasswordEntered(password) },
            onDismiss = { viewModel.onDismissPasswordDialog() }
        )
    }
}

@Composable
private fun SnapshotCard(snapshot: SnapshotInfo, apps: List<AppInfo>?, onClick: () -> Unit) {
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

            if (apps != null && apps.isNotEmpty()) {
                Text("Apps (${apps.size}):", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    apps.take(10).forEach { app ->
                        Image(
                            painter = rememberAsyncImagePainter(model = app.icon),
                            contentDescription = app.name,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    if (apps.size > 10) {
                        Text(
                            "+${apps.size - 10} more",
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            } else if (snapshot.tags.isEmpty() && snapshot.paths.isNotEmpty()) {
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
