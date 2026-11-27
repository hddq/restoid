package io.github.hddq.restoid.ui.screens

import android.content.ClipData
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Tag
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.hddq.restoid.LocalAppBarActions
import io.github.hddq.restoid.R
import io.github.hddq.restoid.RestoidApplication
import io.github.hddq.restoid.data.SnapshotInfo
import io.github.hddq.restoid.model.BackupDetail
import io.github.hddq.restoid.model.BackupTag
import io.github.hddq.restoid.model.BackupTagRules
import io.github.hddq.restoid.model.CalendarBackupMetadata
import io.github.hddq.restoid.model.CallLogsBackupMetadata
import io.github.hddq.restoid.model.ContactsBackupMetadata
import io.github.hddq.restoid.ui.components.AppSortMenuButton
import io.github.hddq.restoid.ui.components.ExpressiveSectionHeader
import io.github.hddq.restoid.ui.components.ExpressiveSegmentedSurface
import coil.compose.AsyncImage
import io.github.hddq.restoid.ui.snapshot.ForgetResult
import io.github.hddq.restoid.ui.snapshot.SnapshotDetailsViewModel
import io.github.hddq.restoid.ui.snapshot.SnapshotDetailsViewModelFactory

internal const val SNAPSHOT_FORGOTTEN_RESULT_KEY = "snapshot_forgotten"

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
    val availableTags by viewModel.availableTags.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val showConfirmDialog by viewModel.showConfirmForgetDialog.collectAsStateWithLifecycle()
    val isForgetting by viewModel.isForgetting.collectAsStateWithLifecycle()
    val forgetResult by viewModel.forgetResult.collectAsStateWithLifecycle()

    val appBarActions = LocalAppBarActions.current
    val hasBackedUpApps = backupDetails.isNotEmpty()
    val currentSortOrder by rememberUpdatedState(sortOrder)
    DisposableEffect(snapshotId, isForgetting, hasBackedUpApps, viewModel) {
        appBarActions.value = {
            if (hasBackedUpApps) {
                AppSortMenuButton(
                    sortOrder = currentSortOrder,
                    onSortOrderChange = viewModel::setSortOrder
                )
            }
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
            navController.previousBackStackEntry?.savedStateHandle?.set(SNAPSHOT_FORGOTTEN_RESULT_KEY, true)
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
                val contactsMeta = metadata?.deviceData?.contacts
                val callLogsMeta = metadata?.deviceData?.callLogs
                val calendarMeta = metadata?.deviceData?.calendar
                val deviceDataItemsCount =
                    (if (contactsMeta != null) 1 else 0) +
                        (if (callLogsMeta != null) 1 else 0) +
                        (if (calendarMeta != null) 1 else 0)

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 88.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    item(key = "header", contentType = "header") {
                        SnapshotDetailsHeader(
                            snapshot = snapshot!!,
                            availableTags = availableTags,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )
                    }
                    if (backupDetails.isNotEmpty()) {
                        item(key = "apps_header", contentType = "apps_header") {
                            ExpressiveSectionHeader(
                                title = pluralStringResource(
                                    R.plurals.snapshot_backed_up_apps_count,
                                    backupDetails.size,
                                    backupDetails.size
                                )
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
                    if (deviceDataItemsCount > 0) {
                        item(key = "device_data_header", contentType = "device_data_header") {
                            ExpressiveSectionHeader(
                                title = stringResource(R.string.snapshot_backed_up_device_data),
                                modifier = Modifier.padding(
                                    top = if (backupDetails.isNotEmpty() || dirsList.isNotEmpty()) 14.dp else 0.dp
                                )
                            )
                        }
                        var deviceDataIndex = 0
                        if (contactsMeta != null) {
                            val currentIndex = deviceDataIndex++
                            item(key = "device_data_contacts", contentType = "device_data_contacts") {
                                ExpressiveSegmentedSurface(
                                    index = currentIndex,
                                    count = deviceDataItemsCount
                                ) {
                                    BackedUpContactsItem(metadata = contactsMeta)
                                }
                            }
                        }
                        if (callLogsMeta != null) {
                            val currentIndex = deviceDataIndex++
                            item(key = "device_data_call_logs", contentType = "device_data_call_logs") {
                                ExpressiveSegmentedSurface(
                                    index = currentIndex,
                                    count = deviceDataItemsCount
                                ) {
                                    BackedUpCallLogsItem(metadata = callLogsMeta)
                                }
                            }
                        }
                        if (calendarMeta != null) {
                            val currentIndex = deviceDataIndex++
                            item(key = "device_data_calendar", contentType = "device_data_calendar") {
                                ExpressiveSegmentedSurface(
                                    index = currentIndex,
                                    count = deviceDataItemsCount
                                ) {
                                    BackedUpCalendarItem(metadata = calendarMeta)
                                }
                            }
                        }
                    }

                    if (backupDetails.isEmpty() && customDirs.isEmpty() && contactsMeta == null && callLogsMeta == null && calendarMeta == null && !isLoading) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SnapshotDetailsHeader(
    snapshot: SnapshotInfo,
    availableTags: List<BackupTag> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copiedText = stringResource(R.string.snapshot_id_copied_to_clipboard)
    val shortId = remember(snapshot.id) { snapshot.id.take(8) }
    val parsedTime = remember(snapshot.time) { parseSnapshotHeaderTime(snapshot.time) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val now by produceState(
        initialValue = java.time.Instant.now(),
        key1 = parsedTime.instant,
        key2 = lifecycleOwner
    ) {
        if (parsedTime.instant == null) return@produceState
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = java.time.Instant.now()
                delay(60_000L)
            }
        }
    }
    val relativeTime = remember(parsedTime.instant, now, context) {
        parsedTime.instant?.let { formatRelativeSnapshotTime(it, now, context) }
    }
    val hostname = snapshot.hostname?.takeIf { it.isNotBlank() }
    val userTags = remember(snapshot.tags) { BackupTagRules.filterUserTags(snapshot.tags) }
    val tagColorsByName = remember(availableTags) {
        availableTags.associate { it.name.lowercase() to it.colorArgb }
    }

    val copySnapshotId: () -> Unit = {
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Snapshot ID", snapshot.id)))
            Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
        }
    }

    val cardShape = RoundedCornerShape(24.dp)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = parsedTime.localDateTime,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    relativeTime?.let { relative ->
                        Text(
                            text = relative,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(cardShape)
                    .clickable(onClick = copySnapshotId),
                shape = cardShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tag,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.cd_copy_snapshot_id),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = shortId,
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.snapshot_label_id),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            if (hostname != null) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = cardShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Smartphone,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = hostname,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.snapshot_label_device),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }

        if (userTags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                userTags.forEach { tagName ->
                    val tagColorArgb = tagColorsByName[tagName.lowercase()]
                    val containerColor = if (tagColorArgb != null) {
                        Color(tagColorArgb).copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                    val borderColor = if (tagColorArgb != null) {
                        Color(tagColorArgb).copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                    val contentColor = if (tagColorArgb != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Surface(
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = containerColor,
                        contentColor = contentColor,
                        border = BorderStroke(1.dp, borderColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tagName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ParsedSnapshotTime(
    val instant: java.time.Instant?,
    val localDateTime: String
)

private fun parseSnapshotHeaderTime(rawTime: String): ParsedSnapshotTime {
    return try {
        val instant = try {
            java.time.OffsetDateTime.parse(rawTime).toInstant()
        } catch (_: Exception) {
            java.time.Instant.parse(rawTime)
        }
        val localZoned = instant.atZone(java.time.ZoneId.systemDefault())
        val formatter = java.time.format.DateTimeFormatter.ofLocalizedDateTime(
            java.time.format.FormatStyle.MEDIUM,
            java.time.format.FormatStyle.SHORT
        )
        ParsedSnapshotTime(instant = instant, localDateTime = formatter.format(localZoned))
    } catch (_: Exception) {
        ParsedSnapshotTime(instant = null, localDateTime = rawTime)
    }
}

private fun formatRelativeSnapshotTime(
    instant: java.time.Instant,
    now: java.time.Instant,
    context: android.content.Context
): String? {
    val duration = java.time.Duration.between(instant, now)
    if (duration.isNegative) return null
    return when {
        duration.toMinutes() < 1 -> context.getString(R.string.time_just_now)
        duration.toHours() < 1 -> {
            val mins = duration.toMinutes().toInt()
            context.resources.getQuantityString(R.plurals.time_mins_ago, mins, mins)
        }
        duration.toDays() < 1 -> {
            val hours = duration.toHours().toInt()
            context.resources.getQuantityString(R.plurals.time_hours_ago, hours, hours)
        }
        duration.toDays() < 30 -> {
            val days = duration.toDays().toInt()
            context.resources.getQuantityString(R.plurals.time_days_ago, days, days)
        }
        duration.toDays() < 365 -> {
            val months = (duration.toDays() / 30).toInt()
            context.resources.getQuantityString(R.plurals.time_months_ago, months, months)
        }
        else -> {
            val years = (duration.toDays() / 365).toInt()
            context.resources.getQuantityString(R.plurals.time_years_ago, years, years)
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
    var titleOverflows by remember(path) { mutableStateOf(false) }
    var subtitleOverflows by remember(path, metadata.size) { mutableStateOf(false) }
    val canExpand = titleOverflows || subtitleOverflows
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copiedText = stringResource(R.string.path_copied_to_clipboard)
    val copyPath: () -> Unit = {
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Path", path)))
            Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
        }
    }
    val interactionModifier = if (canExpand || isExpanded) {
        Modifier.combinedClickable(
            onClick = { isExpanded = !isExpanded },
            onLongClick = copyPath
        )
    } else {
        Modifier
            .semantics {
                onLongClick {
                    copyPath()
                    true
                }
            }
            .pointerInput(path, copiedText) {
                detectTapGestures(onLongPress = { copyPath() })
            }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(interactionModifier)
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
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result ->
                    if (!isExpanded) {
                        titleOverflows = result.hasVisualOverflow
                    }
                }
            )
            val sizeInfo = Formatter.formatShortFileSize(context, metadata.size)
            Text(
                stringResource(R.string.progress_size) + ": " + sizeInfo + " • " + path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result ->
                    if (!isExpanded) {
                        subtitleOverflows = result.hasVisualOverflow
                    }
                }
            )
        }
        if (canExpand || isExpanded) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (isExpanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun BackedUpContactsItem(
    metadata: ContactsBackupMetadata,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val countText = pluralStringResource(R.plurals.device_data_contacts_count, metadata.count, metadata.count)
    val sizeText = Formatter.formatShortFileSize(context, metadata.size)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Contacts,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.device_data_contacts),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.device_data_contacts_count_size, countText, sizeText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BackedUpCallLogsItem(
    metadata: CallLogsBackupMetadata,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val countText = pluralStringResource(R.plurals.device_data_call_logs_count, metadata.count, metadata.count)
    val sizeText = Formatter.formatShortFileSize(context, metadata.size)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Call,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.device_data_call_logs),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.device_data_call_logs_count_size, countText, sizeText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BackedUpCalendarItem(
    metadata: CalendarBackupMetadata,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val eventsText = pluralStringResource(R.plurals.device_data_calendar_events_count, metadata.count, metadata.count)
    val sizeText = Formatter.formatShortFileSize(context, metadata.size)
    val summaryText = metadata.calendarCount?.let { calCount ->
        val calendarsText = pluralStringResource(R.plurals.device_data_calendar_calendars_count, calCount, calCount)
        stringResource(R.string.device_data_calendar_calendars_events_size, calendarsText, eventsText, sizeText)
    } ?: stringResource(R.string.device_data_calendar_count_size, eventsText, sizeText)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.CalendarMonth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.device_data_calendar),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                summaryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
                        time = "2026-11-10T14:30:00Z",
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
                                isSelected = true,
                                firstInstallTime = 0L
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
                                isSelected = true,
                                firstInstallTime = 0L
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
