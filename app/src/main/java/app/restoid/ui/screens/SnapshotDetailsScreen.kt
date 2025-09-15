package app.restoid.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import app.restoid.RestoidApplication
import app.restoid.data.SnapshotInfo
import app.restoid.model.BackupDetail
import app.restoid.ui.snapshot.SnapshotDetailsViewModel
import app.restoid.ui.snapshot.SnapshotDetailsViewModelFactory
import coil.compose.rememberAsyncImagePainter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapshotDetailsScreen(
    navController: NavController,
    snapshotId: String?
) {
    val application = LocalContext.current.applicationContext as RestoidApplication
    val viewModel: SnapshotDetailsViewModel = viewModel(
        factory = SnapshotDetailsViewModelFactory(
            application,
            application.repositoriesRepository,
            application.resticRepository,
            application.appInfoRepository
        )
    )

    val snapshot by viewModel.snapshot.collectAsState()
    val backupDetails by viewModel.backupDetails.collectAsState()
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
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                error != null -> Text("Error: $error", color = MaterialTheme.colorScheme.error)
                snapshot != null -> {
                    SnapshotDetailsHeader(snapshot!!)
                    Spacer(Modifier.height(16.dp))
                    if (backupDetails.isNotEmpty()) {
                        BackedUpAppsList(backupDetails)
                    } else if (!isLoading) {
                        // For legacy snapshots or if processing fails
                        Text("Backed up paths:", style = MaterialTheme.typography.titleMedium)
                        LazyColumn {
                            items(snapshot!!.paths) { path ->
                                Text(path, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
                else -> if (!isLoading) Text("No snapshot found.")
            }

            if (isForgetting) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Forgetting snapshot...", modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
fun SnapshotDetailsHeader(snapshot: SnapshotInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Snapshot Details", style = MaterialTheme.typography.titleLarge)
            Divider()
            Text("ID: ${snapshot.id}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text("Time: ${snapshot.time}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun BackedUpAppsList(details: List<BackupDetail>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Backed up Apps (${details.size})", style = MaterialTheme.typography.titleMedium)
        }
        items(details) { detail ->
            BackedUpAppCard(detail = detail)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackedUpAppCard(detail: BackupDetail) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Image(
                painter = rememberAsyncImagePainter(model = detail.appInfo.icon),
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(detail.appInfo.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(detail.appInfo.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    detail.backedUpItems.forEach { item ->
                        SuggestionChip(onClick = {}, label = { Text(item, style = MaterialTheme.typography.labelSmall) })
                    }
                }
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
