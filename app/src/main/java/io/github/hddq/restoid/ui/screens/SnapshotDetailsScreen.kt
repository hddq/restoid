package io.github.hddq.restoid.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import coil.compose.rememberAsyncImagePainter
import io.github.hddq.restoid.RestoidApplication
import io.github.hddq.restoid.data.SnapshotInfo
import io.github.hddq.restoid.model.BackupDetail
import io.github.hddq.restoid.ui.snapshot.ForgetResult
import io.github.hddq.restoid.ui.snapshot.SnapshotDetailsViewModel
import io.github.hddq.restoid.ui.snapshot.SnapshotDetailsViewModelFactory

@Composable
fun SnapshotDetailsScreen(
    navController: NavController,
    snapshotId: String?,
    modifier: Modifier = Modifier
) {
    val application = LocalContext.current.applicationContext as RestoidApplication
    val viewModel: SnapshotDetailsViewModel = viewModel(
        factory = SnapshotDetailsViewModelFactory(
            application,
            application.repositoriesRepository,
            application.resticRepository,
            application.appInfoRepository,
            application.metadataRepository
        )
    )

    val snapshot by viewModel.snapshot.collectAsState()
    val backupDetails by viewModel.backupDetails.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val showConfirmDialog by viewModel.showConfirmForgetDialog.collectAsState()
    val isForgetting by viewModel.isForgetting.collectAsState()
    val forgetResult by viewModel.forgetResult.collectAsState()

    LaunchedEffect(forgetResult) {
        if (forgetResult is ForgetResult.Success) {
            navController.popBackStack()
        }
    }

    LaunchedEffect(snapshotId) {
        if (snapshotId != null) {
            viewModel.loadSnapshotDetails(snapshotId)
        }
    }

    if (showConfirmDialog) {
        ConfirmForgetDialog(
            onConfirm = {
                viewModel.confirmForgetSnapshot()
            },
            onDismiss = { viewModel.cancelForgetSnapshot() }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> Text("Error: $error", color = MaterialTheme.colorScheme.error)
            snapshot != null -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        SnapshotDetailsHeader(snapshot = snapshot!!)
                    }
                    if (backupDetails.isNotEmpty()) {
                        item {
                            Text("Backed up Apps (${backupDetails.size})", style = MaterialTheme.typography.titleMedium)
                        }
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                )
                            ) {
                                Column {
                                    backupDetails.forEachIndexed { index, detail ->
                                        BackedUpAppItem(detail = detail)
                                        if (index < backupDetails.size - 1) {
                                            Divider(color = MaterialTheme.colorScheme.background)
                                        }
                                    }
                                }
                            }
                        }
                    } else if (!isLoading) {
                        item {
                            Text("Backed up paths:", style = MaterialTheme.typography.titleMedium)
                        }
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
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun SnapshotDetailsHeader(snapshot: SnapshotInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ID: ${snapshot.id}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text("Time: ${snapshot.time}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackedUpAppItem(detail: BackupDetail) {
    val context = LocalContext.current
    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
        Image(
            painter = rememberAsyncImagePainter(model = detail.appInfo.icon),
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(detail.appInfo.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

            val versionInfo = detail.versionName ?: "N/A"
            val sizeInfo = detail.backupSize?.let { Formatter.formatShortFileSize(context, it) } ?: "N/A"
            Text(
                "Version: $versionInfo • Size: $sizeInfo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

