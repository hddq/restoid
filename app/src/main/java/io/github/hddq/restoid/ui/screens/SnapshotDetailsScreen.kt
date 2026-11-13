package io.github.hddq.restoid.ui.screens

import android.content.ClipData
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.hddq.restoid.LocalAppBarActions
import io.github.hddq.restoid.R
import io.github.hddq.restoid.RestoidApplication
import io.github.hddq.restoid.data.SnapshotInfo
import io.github.hddq.restoid.model.BackupDetail
import io.github.hddq.restoid.ui.components.ExpressiveSectionHeader
import io.github.hddq.restoid.ui.components.ExpressiveSegmentedSurface
import io.github.hddq.restoid.ui.components.SortableSectionHeader
import coil.compose.AsyncImage
import io.github.hddq.restoid.ui.snapshot.ForgetResult
import io.github.hddq.restoid.ui.snapshot.SnapshotDetailsViewModel
import io.github.hddq.restoid.ui.snapshot.SnapshotDetailsViewModelFactory

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val metadata by viewModel.metadata.collectAsStateWithLifecycle()
    val backupDetails by viewModel.backupDetails.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val showConfirmDialog by viewModel.showConfirmForgetDialog.collectAsStateWithLifecycle()
    val isForgetting by viewModel.isForgetting.collectAsStateWithLifecycle()
    val forgetResult by viewModel.forgetResult.collectAsStateWithLifecycle()

    val appBarActions = LocalAppBarActions.current
    DisposableEffect(snapshotId, isForgetting) {
        appBarActions.value = {
            IconButton(
                onClick = { viewModel.onForgetSnapshot() },
                enabled = !isForgetting
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.cd_forget_snapshot),
                    tint = if (isForgetting) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.error
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
            isForgetting = isForgetting,
            onConfirm = {
                viewModel.confirmForgetSnapshot()
            },
            onDismiss = { viewModel.cancelForgetSnapshot() }
        )
    }

    if (forgetResult is ForgetResult.Error) {
        ForgetErrorDialog(
            errorMessage = (forgetResult as ForgetResult.Error).message,
            onDismiss = { viewModel.dismissForgetError() }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            error != null -> Text(stringResource(R.string.error_with_message, error ?: ""), color = MaterialTheme.colorScheme.error)
            snapshot != null -> {
                val customDirs = metadata?.customDirectories ?: emptyMap()
                val dirsList = customDirs.toList()

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 88.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    item(key = "header", contentType = "header") {
                        SnapshotDetailsHeader(
                            snapshot = snapshot!!,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )
                    }
                    if (backupDetails.isNotEmpty()) {
                        item(key = "apps_header", contentType = "apps_header") {
                            SortableSectionHeader(
                                title = pluralStringResource(
                                    R.plurals.snapshot_backed_up_apps_count,
                                    backupDetails.size,
                                    backupDetails.size
                                ),
                                sortOrder = sortOrder,
                                onSortOrderChange = viewModel::setSortOrder
                            )
                        }
                        itemsIndexed(
                            items = backupDetails,
                            key = { _, detail -> "app_${detail.appInfo.packageName}" },
                            contentType = { _, _ -> "backed_up_app" }
                        ) { index, detail ->
                            ExpressiveSegmentedSurface(
                                index = index,
                                count = backupDetails.size
                            ) {
                                BackedUpAppItem(detail = detail)
                            }
                        }
                    }
                    if (dirsList.isNotEmpty()) {
                        item(key = "dirs_header", contentType = "dirs_header") {
                            ExpressiveSectionHeader(
                                title = pluralStringResource(
                                    R.plurals.snapshot_backed_up_custom_directories_count,
                                    dirsList.size,
                                    dirsList.size
                                ),
                                modifier = Modifier.padding(top = if (backupDetails.isNotEmpty()) 14.dp else 0.dp)
                            )
                        }
                        itemsIndexed(
                            items = dirsList,
                            key = { _, (path, _) -> "dir_$path" },
                            contentType = { _, _ -> "backed_up_dir" }
                        ) { index, (path, dirMetadata) ->
                            ExpressiveSegmentedSurface(
                                index = index,
                                count = dirsList.size
                            ) {
                                BackedUpCustomDirItem(path = path, metadata = dirMetadata)
                            }
                        }
                    }

                    if (backupDetails.isEmpty() && customDirs.isEmpty() && !isLoading) {
                        item(key = "empty_metadata", contentType = "empty_metadata") {
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
            snapshot.hostname?.takeIf { it.isNotBlank() }?.let { hostname ->
                Text(
                    stringResource(R.string.snapshot_hostname, hostname),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                stringResource(R.string.snapshot_time, snapshot.time),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackedUpAppItem(
    detail: BackupDetail,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val notAvailable = stringResource(R.string.not_available)
    val apkLabel = stringResource(R.string.backup_type_apk)
    val dataLabel = stringResource(R.string.backup_type_data)
    val deDataLabel = stringResource(R.string.backup_type_device_protected_data)
    val extDataLabel = stringResource(R.string.backup_type_external_data)
    val obbLabel = stringResource(R.string.backup_item_obb)
    val mediaLabel = stringResource(R.string.backup_item_media)

    val badges = remember(detail, context, apkLabel, dataLabel, deDataLabel, extDataLabel, obbLabel, mediaLabel) {
        detail.backedUpItems.map { item ->
            val sizeKey = when (item) {
                apkLabel -> "apk"
                dataLabel -> "data"
                deDataLabel -> "user_de"
                extDataLabel -> "external_data"
                obbLabel -> "obb"
                mediaLabel -> "media"
                else -> null
            }
            val size = sizeKey?.let { detail.detailedSizes[it] }
            if (size != null) {
                "$item (${Formatter.formatShortFileSize(context, size)})"
            } else {
                item
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        AsyncImage(
            model = detail.appInfo.icon,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = detail.appInfo.name,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val versionInfo = detail.versionName ?: notAvailable
            val sizeInfo = detail.backupSize?.let { Formatter.formatShortFileSize(context, it) } ?: notAvailable
            Text(
                text = stringResource(R.string.snapshot_version_size, versionInfo, sizeInfo),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (badges.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    badges.forEach { labelText ->
                        BackedUpItemBadge(text = labelText)
                    }
                }
            }
        }
    }
}

@Composable
fun BackedUpItemBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConfirmForgetDialog(
    isForgetting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val forgettingDescription = stringResource(R.string.snapshot_forgetting)

    AlertDialog(
        onDismissRequest = {
            if (!isForgetting) {
                onDismiss()
            }
        },
        title = { Text(stringResource(R.string.dialog_forget_snapshot_title)) },
        text = { Text(stringResource(R.string.dialog_forget_snapshot_message)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isForgetting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = if (isForgetting) {
                    Modifier.semantics { contentDescription = forgettingDescription }
                } else {
                    Modifier
                }
            ) {
                if (isForgetting) {
                    LoadingIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .semantics { contentDescription = forgettingDescription },
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(stringResource(R.string.action_forget))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isForgetting
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        modifier = modifier
    )
}

@Composable
fun ForgetErrorDialog(
    errorMessage: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_forget_snapshot_failed_title)) },
        text = {
            SelectionContainer {
                Text(
                    text = errorMessage,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_ok))
            }
        },
        modifier = modifier
    )
}

@Composable
fun BackedUpCustomDirItem(
    path: String,
    metadata: io.github.hddq.restoid.model.CustomDirectoryMetadata,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    metadata.isDirectory?.let { isDirectory ->
        BackedUpCustomDirItemContent(path, metadata, isDirectory, context, modifier)
        return
    }
    val initialIsDir = io.github.hddq.restoid.util.StorageUtils.isDirectoryFast(path)
    val isDirectory by produceState(
        initialValue = initialIsDir ?: true,
        key1 = path
    ) {
        if (initialIsDir == null) {
            value = withContext(Dispatchers.IO) {
                io.github.hddq.restoid.util.StorageUtils.isDirectoryPath(path)
            }
        }
    }
    BackedUpCustomDirItemContent(path, metadata, isDirectory, context, modifier)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackedUpCustomDirItemContent(
    path: String,
    metadata: io.github.hddq.restoid.model.CustomDirectoryMetadata,
    isDirectory: Boolean,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copiedText = stringResource(R.string.path_copied_to_clipboard)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { isExpanded = !isExpanded },
                onLongClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Path", path)))
                        Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                    }
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .animateContentSize(),
        verticalAlignment = if (isExpanded) Alignment.Top else Alignment.CenterVertically
    ) {
        Icon(
            if (isDirectory) Icons.Outlined.Folder else Icons.Outlined.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                path.substringAfterLast("/").ifEmpty { path },
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis
            )
            val sizeInfo = Formatter.formatShortFileSize(context, metadata.size)
            Text(
                stringResource(R.string.progress_size) + ": " + sizeInfo + " • " + path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = if (isExpanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Preview(name = "Snapshot Details Apps Preview", showBackground = true)
@Composable
private fun BackedUpAppItemPreview() {
    io.github.hddq.restoid.ui.theme.RestoidTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SnapshotDetailsHeader(
                    snapshot = SnapshotInfo(
                        id = "a1b2c3d4e5f67890",
                        time = "2026-11-10 14:30:00",
                        paths = listOf("/data/app/org.fdroid.fdroid"),
                        tags = emptyList(),
                        hostname = "Pixel-8"
                    )
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ExpressiveSegmentedSurface(index = 0, count = 2) {
                    BackedUpAppItem(
                        detail = BackupDetail(
                            appInfo = io.github.hddq.restoid.model.AppInfo(
                                name = "F-Droid",
                                packageName = "org.fdroid.fdroid",
                                versionName = "1.20.0",
                                versionCode = 1020050,
                                icon = android.graphics.drawable.ColorDrawable(0xFF1E88E5.toInt()),
                                apkPaths = emptyList(),
                                isSelected = true
                            ),
                            backedUpItems = listOf("APK", "Data", "Device Protected Data", "External Data"),
                            versionName = "1.20.0",
                            versionCode = 1020050,
                            backupSize = 42_500_000L,
                            isDowngrade = false,
                            isInstalled = true,
                            detailedSizes = mapOf(
                                "apk" to 12_500_000L,
                                "data" to 28_000_000L,
                                "user_de" to 4_096L,
                                "external_data" to 1_995_904L
                            )
                        )
                    )
                }
                ExpressiveSegmentedSurface(index = 1, count = 2) {
                    BackedUpAppItem(
                        detail = BackupDetail(
                            appInfo = io.github.hddq.restoid.model.AppInfo(
                                name = "K-9 Mail",
                                packageName = "com.fsck.k9",
                                versionName = "6.802",
                                versionCode = 39002,
                                icon = android.graphics.drawable.ColorDrawable(0xFF43A047.toInt()),
                                apkPaths = emptyList(),
                                isSelected = true
                            ),
                            backedUpItems = listOf("APK", "Data"),
                            versionName = "6.802",
                            versionCode = 39002,
                            backupSize = 85_200_000L,
                            isDowngrade = false,
                            isInstalled = true,
                            detailedSizes = mapOf(
                                "apk" to 32_000_000L,
                                "data" to 53_200_000L
                            )
                        )
                    )
                }
            }
        }
    }
}
}
