package io.github.hddq.restoid.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import io.github.hddq.restoid.LocalAppBarActions
import io.github.hddq.restoid.R
import io.github.hddq.restoid.RestoidApplication
import io.github.hddq.restoid.data.SnapshotInfo
import io.github.hddq.restoid.model.BackupDetail
import io.github.hddq.restoid.ui.components.ExpressiveSectionHeader
import io.github.hddq.restoid.ui.components.ExpressiveSegmentedSurface
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
    val metadata by viewModel.metadata.collectAsState()
    val backupDetails by viewModel.backupDetails.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val showConfirmDialog by viewModel.showConfirmForgetDialog.collectAsState()
    val isForgetting by viewModel.isForgetting.collectAsState()
    val forgetResult by viewModel.forgetResult.collectAsState()

    val appBarActions = LocalAppBarActions.current
    DisposableEffect(snapshotId) {
        appBarActions.value = {
            IconButton(onClick = { viewModel.onForgetSnapshot() }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.cd_forget_snapshot),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        onDispose { appBarActions.value = null }
    }

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
            error != null -> Text(stringResource(R.string.error_with_message, error ?: ""), color = MaterialTheme.colorScheme.error)
            snapshot != null -> {
                val customDirs = metadata?.customDirectories ?: emptyMap()
                val dirsList = customDirs.toList()

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 88.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    item(key = "header") {
                        SnapshotDetailsHeader(
                            snapshot = snapshot!!,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )
                    }
                    if (backupDetails.isNotEmpty()) {
                        item(key = "apps_header") {
                            ExpressiveSectionHeader(
                                title = pluralStringResource(
                                    R.plurals.snapshot_backed_up_apps_count,
                                    backupDetails.size,
                                    backupDetails.size
                                )
                            )
                        }
                        itemsIndexed(backupDetails, key = { _, detail -> "app_${detail.appInfo.packageName}" }) { index, detail ->
                            ExpressiveSegmentedSurface(
                                index = index,
                                count = backupDetails.size
                            ) {
                                BackedUpAppItem(detail = detail)
                            }
                        }
                    }
                    if (dirsList.isNotEmpty()) {
                        item(key = "dirs_header") {
                            ExpressiveSectionHeader(
                                title = pluralStringResource(
                                    R.plurals.snapshot_backed_up_custom_directories_count,
                                    dirsList.size,
                                    dirsList.size
                                ),
                                modifier = Modifier.padding(top = if (backupDetails.isNotEmpty()) 14.dp else 0.dp)
                            )
                        }
                        itemsIndexed(dirsList, key = { _, (path, _) -> "dir_$path" }) { index, (path, dirMetadata) ->
                            ExpressiveSegmentedSurface(
                                index = index,
                                count = dirsList.size
                            ) {
                                BackedUpCustomDirItem(path = path, metadata = dirMetadata)
                            }
                        }
                    }

                    if (backupDetails.isEmpty() && customDirs.isEmpty() && !isLoading) {
                        item(key = "empty_metadata") {
                            if (metadata == null) {
                                Text(
                                    stringResource(R.string.metadata_missing_or_corrupted),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(
                                    stringResource(R.string.no_app_info_for_snapshot),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            else -> if (!isLoading) Text(stringResource(R.string.snapshot_not_found))
        }

        if (isForgetting) {
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.snapshot_forgetting), modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun SnapshotDetailsHeader(
    snapshot: SnapshotInfo,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.snapshot_id, snapshot.id),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                stringResource(R.string.snapshot_time, snapshot.time),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackedUpAppItem(detail: BackupDetail) {
    val context = LocalContext.current
    val notAvailable = stringResource(R.string.not_available)
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Image(
            painter = rememberAsyncImagePainter(model = detail.appInfo.icon),
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(detail.appInfo.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

            val versionInfo = detail.versionName ?: notAvailable
            val sizeInfo = detail.backupSize?.let { Formatter.formatShortFileSize(context, it) } ?: notAvailable
            Text(
                stringResource(R.string.snapshot_version_size, versionInfo, sizeInfo),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                detail.backedUpItems.forEach { item ->
                    val sizeKey = when (item) {
                        stringResource(R.string.backup_type_apk) -> "apk"
                        stringResource(R.string.backup_type_data) -> "data"
                        stringResource(R.string.backup_type_device_protected_data) -> "user_de"
                        stringResource(R.string.backup_type_external_data) -> "external_data"
                        stringResource(R.string.backup_item_obb) -> "obb"
                        stringResource(R.string.backup_item_media) -> "media"
                        else -> null
                    }
                    val size = sizeKey?.let { detail.detailedSizes[it] }
                    val labelText = if (size != null) {
                        "$item (${Formatter.formatShortFileSize(context, size)})"
                    } else {
                        item
                    }
                    SuggestionChip(onClick = {}, label = { Text(labelText, style = MaterialTheme.typography.labelSmall) })
                }
            }
        }
    }
}

@Composable
fun ConfirmForgetDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_forget_snapshot_title)) },
        text = { Text(stringResource(R.string.dialog_forget_snapshot_message)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.action_forget))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun BackedUpCustomDirItem(path: String, metadata: io.github.hddq.restoid.model.CustomDirectoryMetadata) {
    val context = LocalContext.current
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(path.substringAfterLast("/").ifEmpty { path }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            val sizeInfo = Formatter.formatShortFileSize(context, metadata.size)
            Text(
                stringResource(R.string.progress_size) + ": " + sizeInfo + " • " + path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
