package app.restoid.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import app.restoid.RestoidApplication
import app.restoid.ui.snapshot.SnapshotDetailsViewModel
import app.restoid.ui.snapshot.SnapshotDetailsViewModelFactory
import app.restoid.data.SnapshotInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapshotDetailsScreen(
    navController: NavController,
    snapshotId: String?
) {
    val application = LocalContext.current.applicationContext as RestoidApplication
    val viewModel: SnapshotDetailsViewModel = viewModel(
        factory = SnapshotDetailsViewModelFactory(
            application.repositoriesRepository,
            application.resticRepository
        )
    )

    val snapshot by viewModel.snapshot.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val showConfirmDialog by viewModel.showConfirmForgetDialog.collectAsState()
    val isForgetting by viewModel.isForgetting.collectAsState()

    LaunchedEffect(snapshotId) {
        if (snapshotId != null) {
            viewModel.loadSnapshotDetails(snapshotId)
        }
    }

    if (showConfirmDialog) {
        ConfirmForgetDialog(
            onConfirm = {
                viewModel.confirmForgetSnapshot()
                navController.popBackStack()
            },
            onDismiss = { viewModel.cancelForgetSnapshot() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Snapshot Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (snapshot != null) {
                ExtendedFloatingActionButton(
                    text = { Text("Forget") },
                    icon = { Icon(Icons.Default.Delete, contentDescription = "Forget Snapshot") },
                    onClick = { viewModel.onForgetSnapshot() },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            when {
                isLoading -> CircularProgressIndicator()
                error != null -> Text("Error: $error", color = MaterialTheme.colorScheme.error)
                snapshot != null -> {
                    SnapshotDetailsContent(snapshot!!)
                }
                else -> Text("No snapshot found.")
            }

            if(isForgetting) {
                // TODO: Could show a better loading indicator here
                Text("Forgetting snapshot...")
            }
        }
    }
}

@Composable
fun SnapshotDetailsContent(snapshot: SnapshotInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("ID: ${snapshot.id}", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Time: ${snapshot.time}", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Paths:", style = MaterialTheme.typography.bodyMedium)
            snapshot.paths.forEach { path ->
                Text("  - $path", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ConfirmForgetDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forget Snapshot?") },
        text = { Text("This will remove the snapshot entry. The actual data will only be deleted after running a prune. Are you sure?") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Forget")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

