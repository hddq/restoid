package io.github.hddq.restoid.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import io.github.hddq.restoid.ui.components.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.OperationRuntimeState
import io.github.hddq.restoid.data.OperationType
import io.github.hddq.restoid.data.ResticState
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.model.BackupTagRules
import io.github.hddq.restoid.ui.components.ExpressiveEmptyState
import io.github.hddq.restoid.ui.components.ExpressiveSectionHeader
import io.github.hddq.restoid.ui.components.ExpressiveSegmentedSurface
import io.github.hddq.restoid.ui.components.PasswordDialog
import io.github.hddq.restoid.ui.components.SshPrivateKeyField
import io.github.hddq.restoid.ui.components.TagColorDot
import io.github.hddq.restoid.ui.components.UsernamePasswordDialog
import coil.compose.AsyncImage
import io.github.hddq.restoid.ui.home.HomeAuthFailure
import io.github.hddq.restoid.ui.home.HomeCredentialPrompt
import io.github.hddq.restoid.ui.home.HomeUiState
import io.github.hddq.restoid.ui.home.SnapshotWithMetadata
import io.github.hddq.restoid.ui.home.extractAvailableFilterTags
import io.github.hddq.restoid.ui.home.filterSnapshotsByTags

private fun homeCredentialsSaveLabel(prompt: HomeCredentialPrompt): Int {
    return if (prompt == HomeCredentialPrompt.S3_CREDENTIALS) R.string.action_save_passwords else R.string.save_credentials
}

@Composable
private fun HomeCenteredStatus(
    minHeight: Dp,
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    action: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = minHeight),
        contentAlignment = Alignment.Center
    ) {
        ExpressiveEmptyState(
            icon = icon,
            title = title,
            subtitle = subtitle,
            iconTint = iconTint,
            titleColor = titleColor,
            action = action
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    onSnapshotClick: (String) -> Unit,
    uiState: HomeUiState,
    onRefresh: () -> Unit,
    onCheckStoragePermission: () -> Unit = {},
    onPasswordEntered: (String, Boolean) -> Unit,
    onSftpPasswordEntered: (String, String, Boolean) -> Unit,
    onRestCredentialsEntered: (String, String, Boolean) -> Unit,
    onS3CredentialsEntered: (String, String, Boolean) -> Unit,
    onRetryRepositoryPasswordEntry: () -> Unit,
    onRetrySftpPasswordEntry: () -> Unit,
    onRetryRestCredentialsEntry: () -> Unit,
    onRetryS3CredentialsEntry: () -> Unit,
    onDismissPasswordDialog: () -> Unit,
    onDismissSftpPasswordDialog: () -> Unit,
    onDismissRestCredentialsDialog: () -> Unit,
    onDismissS3CredentialsDialog: () -> Unit,
    onRestoreSnapshotClick: (String) -> Unit = {},
    onRequestForgetSnapshot: (String) -> Unit = {},
    onConfirmForgetSnapshot: () -> Unit = {},
    onDismissForgetDialog: () -> Unit = {},
    onDismissForgetErrorDialog: () -> Unit = {},
    onOpenOperationProgress: () -> Unit = {},
    onForceStopOperation: () -> Unit = {},
    onToggleFilterTag: (String) -> Unit = {},
    onClearFilterTags: () -> Unit = {},
    onRefreshRepoTags: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showForceStopDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCheckStoragePermission by rememberUpdatedState(onCheckStoragePermission)
    val currentOnRefreshRepoTags by rememberUpdatedState(onRefreshRepoTags)
    var nowEpochMinute by remember { mutableLongStateOf(System.currentTimeMillis() / 60_000L) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                nowEpochMinute = System.currentTimeMillis() / 60_000L
                currentOnCheckStoragePermission()
                currentOnRefreshRepoTags()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                nowEpochMinute = System.currentTimeMillis() / 60_000L
                val millisIntoMinute = System.currentTimeMillis() % 60_000L
                delay((60_000L - millisIntoMinute).coerceAtLeast(1_000L))
            }
        }
    }
    val activeOp = uiState.activeOperation
    val isOperationActive = activeOp.isRunning || activeOp.stopRequested

    LaunchedEffect(isOperationActive) {
        if (!isOperationActive) {
            showForceStopDialog = false
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (isOperationActive) {
            ActiveOperationBanner(
                activeOp = activeOp,
                onOpenOperationProgress = onOpenOperationProgress,
                onForceStopClick = { showForceStopDialog = true },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
            )
        }

        val sortedSnapshots = remember(uiState.snapshotsWithMetadata) {
            uiState.snapshotsWithMetadata.sortedByDescending { it.snapshotInfo.time }
        }
        val availableFilterTags = remember(uiState.snapshotsWithMetadata) {
            extractAvailableFilterTags(uiState.snapshotsWithMetadata)
        }
        val filteredSnapshots = remember(sortedSnapshots, uiState.selectedFilterTags) {
            filterSnapshotsByTags(sortedSnapshots, uiState.selectedFilterTags)
        }
        val fallbackColorArgb = MaterialTheme.colorScheme.outline.toArgb()

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val emptyStateMinHeight = (maxHeight - 96.dp).coerceAtLeast(0.dp)
            val maxIconsPossible = remember(maxWidth) {
                val contentWidth = (maxWidth - 64.dp).coerceAtLeast(32.dp)
                val iconSize = 32.dp
                val spacing = 8.dp
                ((contentWidth + spacing) / (iconSize + spacing)).toInt().coerceAtLeast(1)
            }

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp)
                ) {
                    when {
                    uiState.selectedRepo == null -> {
                        item(key = "no_repo_selected") {
                            HomeCenteredStatus(
                                minHeight = emptyStateMinHeight,
                                icon = Icons.Outlined.Storage,
                                title = stringResource(R.string.no_repository_selected_title),
                                subtitle = stringResource(R.string.no_repository_selected_hint)
                            )
                        }
                    }
                    uiState.resticState !is ResticState.Installed -> {
                        item(key = "restic_not_available") {
                            HomeCenteredStatus(
                                minHeight = emptyStateMinHeight,
                                icon = Icons.Outlined.ErrorOutline,
                                title = stringResource(R.string.restic_not_available_check_settings),
                                iconTint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                                titleColor = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    uiState.isLoading && (uiState.snapshotsWithMetadata.isEmpty() || !uiState.isRefreshing) -> {
                        item(key = "loading") {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                LoadingIndicator()
                            }
                        }
                    }
                uiState.error != null -> {
                    val errorMessage = uiState.error
                    item(key = "error_content") {
                        HomeCenteredStatus(
                            minHeight = emptyStateMinHeight,
                            icon = Icons.Outlined.ErrorOutline,
                            title = stringResource(R.string.error_with_message, errorMessage),
                            iconTint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                            titleColor = MaterialTheme.colorScheme.error,
                            action = when {
                                uiState.isMissingLocalPermissions -> {
                                    {
                                        Button(
                                            onClick = {
                                                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                                        data = Uri.parse("package:${context.packageName}")
                                                    }
                                                } else {
                                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                        data = Uri.parse("package:${context.packageName}")
                                                    }
                                                }
                                                runCatching { context.startActivity(intent) }
                                            }
                                        ) {
                                            Text(stringResource(R.string.action_grant))
                                        }
                                    }
                                }
                                uiState.authFailure != null -> {
                                    val (labelRes, onClick) = when (uiState.authFailure) {
                                        HomeAuthFailure.REPOSITORY_PASSWORD ->
                                            R.string.action_enter_password_again to onRetryRepositoryPasswordEntry
                                        HomeAuthFailure.SFTP_PASSWORD ->
                                            R.string.action_enter_sftp_password_again to onRetrySftpPasswordEntry
                                        HomeAuthFailure.REST_CREDENTIALS ->
                                            R.string.action_enter_rest_credentials_again to onRetryRestCredentialsEntry
                                        HomeAuthFailure.S3_CREDENTIALS ->
                                            R.string.action_enter_s3_credentials_again to onRetryS3CredentialsEntry
                                    }
                                    {
                                        Button(onClick = onClick) {
                                            Text(stringResource(labelRes))
                                        }
                                    }
                                }
                                else -> null
                            }
                        )
                    }
                }
                uiState.openPrompt != null -> {
                    item(key = "open_prompt") {
                        val (labelRes, onClick) = when (uiState.openPrompt) {
                            HomeCredentialPrompt.REPOSITORY_PASSWORD ->
                                R.string.action_open_repository to onRetryRepositoryPasswordEntry
                            HomeCredentialPrompt.SFTP_PASSWORD ->
                                R.string.action_open_sftp to onRetrySftpPasswordEntry
                            HomeCredentialPrompt.REST_CREDENTIALS ->
                                R.string.action_open_rest to onRetryRestCredentialsEntry
                            HomeCredentialPrompt.S3_CREDENTIALS ->
                                R.string.action_open_s3 to onRetryS3CredentialsEntry
                        }
                        HomeCenteredStatus(
                            minHeight = emptyStateMinHeight,
                            icon = Icons.Outlined.Lock,
                            title = stringResource(R.string.home_open_needed_message),
                            action = {
                                Button(onClick = onClick) {
                                    Text(stringResource(labelRes))
                                }
                            }
                        )
                    }
                }
                uiState.snapshotsWithMetadata.isEmpty() -> {
                    item(key = "empty_snapshots") {
                        HomeCenteredStatus(
                            minHeight = emptyStateMinHeight,
                            icon = Icons.Outlined.History,
                            title = stringResource(R.string.no_snapshots_found_for_repository),
                            subtitle = stringResource(R.string.no_snapshots_found_hint)
                        )
                    }
                }
                else -> {
                    if (availableFilterTags.isNotEmpty()) {
                        item(key = "tag_filter_bar", contentType = "tag_filter_bar") {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    items = availableFilterTags,
                                    key = { it }
                                ) { tagName ->
                                    ExpressiveTagFilterChip(
                                        label = tagName,
                                        selected = tagName in uiState.selectedFilterTags,
                                        tagColorArgb = uiState.repoTagsByName[tagName]?.colorArgb,
                                        onClick = { onToggleFilterTag(tagName) }
                                    )
                                }
                            }
                        }
                    }
                    if (filteredSnapshots.isEmpty()) {
                        item(key = "empty_filtered_snapshots") {
                            HomeCenteredStatus(
                                minHeight = emptyStateMinHeight,
                                icon = Icons.Outlined.FilterAltOff,
                                title = stringResource(R.string.home_no_matching_snapshots_title),
                                action = {
                                    Button(onClick = onClearFilterTags) {
                                        Text(stringResource(R.string.action_clear_filters))
                                    }
                                }
                            )
                        }
                    } else {
                        item(key = "snapshots_header", contentType = "snapshots_header") {
                            ExpressiveSectionHeader(
                                title = pluralStringResource(
                                    R.plurals.snapshots_count,
                                    filteredSnapshots.size,
                                    filteredSnapshots.size
                                )
                            )
                        }
                        itemsIndexed(
                            items = filteredSnapshots,
                            key = { _, item -> item.snapshotInfo.id },
                            contentType = { _, _ -> "snapshot_item" }
                        ) { index, item ->
                            val userTagColors = remember(item.snapshotInfo.tags, uiState.repoTagsByName, fallbackColorArgb) {
                                BackupTagRules.filterUserTags(item.snapshotInfo.tags).map { tagName ->
                                    uiState.repoTagsByName[tagName]?.colorArgb ?: fallbackColorArgb
                                }
                            }
                            ExpressiveSegmentedSurface(
                                index = index,
                                count = filteredSnapshots.size,
                                modifier = Modifier.padding(bottom = if (index < filteredSnapshots.size - 1) 2.dp else 0.dp)
                            ) {
                                SnapshotItem(
                                    snapshotWithMetadata = item,
                                    apps = uiState.appInfoMap[item.snapshotInfo.id],
                                    tagColors = userTagColors,
                                    nowEpochMinute = nowEpochMinute,
                                    maxIconsPossible = maxIconsPossible,
                                    onClick = { onSnapshotClick(item.snapshotInfo.id) },
                                    onRestoreClick = { onRestoreSnapshotClick(item.snapshotInfo.id) },
                                    onForgetClick = { onRequestForgetSnapshot(item.snapshotInfo.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}

    if (showForceStopDialog && isOperationActive) {
        AlertDialog(
            onDismissRequest = { showForceStopDialog = false },
            title = { Text(stringResource(R.string.dialog_force_stop_operation_title)) },
            text = { Text(stringResource(R.string.dialog_force_stop_operation_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showForceStopDialog = false
                        onForceStopOperation()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.action_force_stop),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showForceStopDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (uiState.snapshotPendingForget != null) {
        ConfirmForgetDialog(
            isForgetting = uiState.isForgettingSnapshot,
            onConfirm = onConfirmForgetSnapshot,
            onDismiss = onDismissForgetDialog
        )
    }

    if (uiState.forgetSnapshotError != null) {
        ForgetErrorDialog(
            errorMessage = uiState.forgetSnapshotError,
            onDismiss = onDismissForgetErrorDialog
        )
    }

    if (uiState.showPasswordDialogFor != null) {
        val repositoryKey = uiState.showPasswordDialogFor
        PasswordDialog(
            title = stringResource(R.string.repository_password_required),
            message = stringResource(R.string.enter_password_for_repository, repositoryKey),
            onPasswordEntered = onPasswordEntered,
            onDismiss = onDismissPasswordDialog
        )
    }

    if (uiState.showSftpPasswordDialogFor != null) {
        val repositoryKey = uiState.showSftpPasswordDialogFor
        val isKeyAuth = uiState.isSftpKeyAuthRequired

        if (isKeyAuth) {
            var credentials by remember { mutableStateOf("") }
            var passphrase by remember { mutableStateOf("") }
            var passphraseVisible by remember { mutableStateOf(false) }
            var saveCredentials by remember { mutableStateOf(false) }
            var importErrorMessage by remember { mutableStateOf<String?>(null) }

            AlertDialog(
                onDismissRequest = onDismissSftpPasswordDialog,
                title = { Text(stringResource(R.string.sftp_key_required)) },
                text = {
                    Column {
                        Text(stringResource(R.string.enter_sftp_credentials_for_repository, repositoryKey))
                        Spacer(modifier = Modifier.height(16.dp))
                        SshPrivateKeyField(
                            value = credentials,
                            onValueChange = {
                                credentials = it
                                importErrorMessage = null
                            },
                            onImportError = { importErrorMessage = it },
                            label = stringResource(R.string.label_sftp_private_key),
                            placeholder = stringResource(R.string.placeholder_sftp_private_key),
                            isError = importErrorMessage != null,
                            supportingText = importErrorMessage?.let { message -> { Text(message) } },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (uiState.isSftpKeyPassphraseRequired) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = passphrase,
                                onValueChange = { passphrase = it },
                                label = { Text(stringResource(R.string.label_sftp_key_passphrase)) },
                                singleLine = true,
                                visualTransformation = if (passphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    val image = if (passphraseVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility
                                    val description = if (passphraseVisible) stringResource(R.string.cd_hide_password) else stringResource(R.string.cd_show_password)
                                    IconButton(onClick = { passphraseVisible = !passphraseVisible }) {
                                        Icon(imageVector = image, contentDescription = description)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = saveCredentials,
                                onCheckedChange = { saveCredentials = it }
                            )
                            Text(stringResource(R.string.save_credentials))
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (credentials.isNotBlank() && (!uiState.isSftpKeyPassphraseRequired || passphrase.isNotBlank())) {
                                onSftpPasswordEntered(credentials, passphrase, saveCredentials)
                            }
                        },
                        enabled = credentials.isNotBlank() && (!uiState.isSftpKeyPassphraseRequired || passphrase.isNotBlank())
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissSftpPasswordDialog) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        } else {
            PasswordDialog(
                title = stringResource(R.string.sftp_password_required),
                message = stringResource(R.string.enter_sftp_password_for_repository, repositoryKey),
                onPasswordEntered = { password, save -> onSftpPasswordEntered(password, "", save) },
                onDismiss = onDismissSftpPasswordDialog
            )
        }
    }

    if (uiState.showRestCredentialsDialogFor != null) {
        val repositoryKey = uiState.showRestCredentialsDialogFor
        UsernamePasswordDialog(
            title = stringResource(R.string.rest_credentials_required),
            message = stringResource(R.string.enter_rest_credentials_for_repository, repositoryKey),
            usernameLabel = stringResource(R.string.label_rest_username),
            passwordLabel = stringResource(R.string.label_rest_password),
            onCredentialsEntered = onRestCredentialsEntered,
            onDismiss = onDismissRestCredentialsDialog,
            saveCredentialsLabel = stringResource(homeCredentialsSaveLabel(HomeCredentialPrompt.REST_CREDENTIALS))
        )
    }

    if (uiState.showS3CredentialsDialogFor != null) {
        val repositoryKey = uiState.showS3CredentialsDialogFor
        UsernamePasswordDialog(
            title = stringResource(R.string.s3_credentials_required),
            message = stringResource(R.string.enter_s3_credentials_for_repository, repositoryKey),
            usernameLabel = stringResource(R.string.label_s3_access_key_id),
            passwordLabel = stringResource(R.string.label_s3_secret_access_key),
            onCredentialsEntered = onS3CredentialsEntered,
            onDismiss = onDismissS3CredentialsDialog,
            saveCredentialsLabel = stringResource(homeCredentialsSaveLabel(HomeCredentialPrompt.S3_CREDENTIALS))
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActiveOperationBanner(
    activeOp: OperationRuntimeState,
    onOpenOperationProgress: () -> Unit,
    onForceStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val opLabel = when (activeOp.operationType) {
        OperationType.BACKUP -> stringResource(R.string.operation_backup)
        OperationType.RUN_TASKS -> stringResource(R.string.operation_run_tasks)
        OperationType.RESTORE -> stringResource(R.string.operation_restore)
        OperationType.MAINTENANCE -> stringResource(R.string.operation_maintenance)
        null -> null
    }
    val cardTitle = if (opLabel != null) {
        stringResource(R.string.home_operation_running_title, opLabel)
    } else {
        stringResource(R.string.home_operation_running_default_title)
    }
    val stageSubtitle = activeOp.progress.stageTitle.takeIf { it.isNotBlank() }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onOpenOperationProgress),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LoadingIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Column {
                    Text(
                        text = cardTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (stageSubtitle != null) {
                        Text(
                            text = stageSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Text(
                        text = stringResource(R.string.home_operation_running_tap_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            TextButton(
                onClick = onForceStopClick
            ) {
                Text(
                    text = stringResource(R.string.action_force_stop),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveTagFilterChip(
    label: String,
    selected: Boolean,
    tagColorArgb: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motionScheme = MaterialTheme.motionScheme
    val cornerRadius by animateDpAsState(
        targetValue = if (selected) 10.dp else 16.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "tag_chip_corner_radius"
    )
    val targetContainerColor = if (tagColorArgb != null) {
        val tagColor = Color(tagColorArgb)
        if (selected) tagColor else tagColor.copy(alpha = 0.16f)
    } else {
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val targetBorderColor = if (tagColorArgb != null) {
        val tagColor = Color(tagColorArgb)
        if (selected) tagColor else tagColor.copy(alpha = 0.45f)
    } else {
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.outlineVariant
    }
    val targetContentColor = if (tagColorArgb != null) {
        val tagColor = Color(tagColorArgb)
        if (selected) {
            if (tagColor.luminance() > 0.179f) Color.Black else Color.White
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    } else {
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    }

    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "tag_chip_container_color"
    )
    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "tag_chip_border_color"
    )
    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "tag_chip_content_color"
    )

    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(32.dp),
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(visible = selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(16.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SnapshotItem(
    snapshotWithMetadata: SnapshotWithMetadata,
    apps: List<AppInfo>?,
    tagColors: List<Int> = emptyList(),
    nowEpochMinute: Long = System.currentTimeMillis() / 60_000L,
    maxIconsPossible: Int,
    onClick: () -> Unit,
    onRestoreClick: (() -> Unit)? = null,
    onForgetClick: (() -> Unit)? = null,
    showMetadataStatusWhenMissing: Boolean = true,
    secondaryContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    statusContent: (@Composable () -> Unit)? = null
) {
    val hasContextMenu = onRestoreClick != null || onForgetClick != null
    var menuExpanded by remember { mutableStateOf(false) }
    val snapshot = snapshotWithMetadata.snapshotInfo
    val context = androidx.compose.ui.platform.LocalContext.current

    val timeText = remember(snapshot.time, nowEpochMinute, context) {
        try {
            val instant = java.time.Instant.parse(snapshot.time)
            val now = java.time.Instant.now()
            val duration = java.time.Duration.between(instant, now)

            val relativeTime = when {
                duration.isNegative -> snapshot.time.take(10)
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
            relativeTime
        } catch (e: Exception) {
            snapshot.time.take(10)
        }
    }

    val clickModifier = if (hasContextMenu) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = { menuExpanded = true }
        )
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(clickModifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SnapshotHeader(
                snapshotId = snapshot.id,
                hostname = snapshot.hostname,
                timeText = timeText,
                tagColors = tagColors,
                secondaryContentColor = secondaryContentColor
            )

            statusContent?.invoke()

            val appCount = snapshotWithMetadata.metadata?.apps?.size ?: 0
            val customDirCount = snapshotWithMetadata.metadata?.customDirectories?.size ?: 0
            val hasContacts = snapshotWithMetadata.metadata?.deviceData?.contacts != null
            val hasCallLogs = snapshotWithMetadata.metadata?.deviceData?.callLogs != null
            val hasCalendar = snapshotWithMetadata.metadata?.deviceData?.calendar != null
            val totalCount = appCount + customDirCount +
                (if (hasContacts) 1 else 0) +
                (if (hasCallLogs) 1 else 0) +
                (if (hasCalendar) 1 else 0)

            if (totalCount > 0) {
                Text(
                    pluralStringResource(R.plurals.backed_up_items_count, totalCount, totalCount),
                    style = MaterialTheme.typography.labelMedium
                )
                val iconSize = 32.dp
                val spacing = 8.dp
                val customDirSlot = if (customDirCount > 0) 1 else 0
                val contactsSlot = if (hasContacts) 1 else 0
                val callLogsSlot = if (hasCallLogs) 1 else 0
                val calendarSlot = if (hasCalendar) 1 else 0
                val availableSlotsForApps = (maxIconsPossible - customDirSlot - contactsSlot - callLogsSlot - calendarSlot).coerceAtLeast(0)
                val showCounter = appCount > availableSlotsForApps
                val limit = if (showCounter) (availableSlotsForApps - 1).coerceAtLeast(0) else availableSlotsForApps

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (apps != null && apps.isNotEmpty() && appCount > 0) {
                        apps.take(limit).forEach { app ->
                            AsyncImage(
                                model = app.icon,
                                contentDescription = app.name,
                                modifier = Modifier.size(iconSize)
                            )
                        }
                        val realMoreCount = appCount - limit
                        if (realMoreCount > 0) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(iconSize)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Text(
                                    text = "+$realMoreCount",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    if (customDirCount > 0) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(iconSize)) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                tint = MaterialTheme.colorScheme.secondaryContainer
                            )
                            Text(
                                text = customDirCount.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (hasContacts) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(iconSize)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Contacts,
                                contentDescription = stringResource(R.string.device_data_contacts),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    if (hasCallLogs) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(iconSize)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Call,
                                contentDescription = stringResource(R.string.device_data_call_logs),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    if (hasCalendar) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(iconSize)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = stringResource(R.string.device_data_calendar),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            } else if (showMetadataStatusWhenMissing && snapshotWithMetadata.metadata == null) {
                Text(
                    text = stringResource(R.string.metadata_missing_or_corrupted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (showMetadataStatusWhenMissing) {
                Text(
                    text = stringResource(R.string.no_app_info_for_snapshot),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryContentColor
                )
            }
        }

        if (hasContextMenu) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                if (onRestoreClick != null) {
                    DropdownMenuItem(
                        text = {
                            Text(text = stringResource(R.string.screen_restore))
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Restore,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onRestoreClick()
                        }
                    )
                }
                if (onForgetClick != null) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.action_forget),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onForgetClick()
                        }
                    )
                }
            }
        }
    }
}

internal const val SNAPSHOT_HEADER_ID_ROW_TAG = "snapshot_header_id_row"
internal const val SNAPSHOT_HEADER_CHEVRON_TAG = "snapshot_header_chevron"

internal data class SnapshotHeaderPlacement(
    val placeOnSingleLine: Boolean,
    val layoutWidth: Int,
    val layoutHeight: Int,
    val idRowX: Int,
    val idRowY: Int,
    val timeX: Int,
    val timeY: Int,
    val chevronX: Int,
    val chevronY: Int
)

internal fun computeSnapshotHeaderPlacement(
    maxWidth: Int,
    minHeight: Int,
    hasBoundedWidth: Boolean,
    idRowWidth: Int,
    idRowHeight: Int,
    singleLineTimeWidth: Int,
    timeWidth: Int,
    timeHeight: Int,
    chevronWidth: Int,
    chevronHeight: Int,
    minHorizontalGapPx: Int,
    chevronGapPx: Int,
    lineGapPx: Int
): SnapshotHeaderPlacement {
    val contentMaxWidth = if (hasBoundedWidth) {
        (maxWidth - chevronWidth - chevronGapPx).coerceAtLeast(0)
    } else {
        Int.MAX_VALUE
    }
    val placeOnSingleLine = !hasBoundedWidth || (
        idRowWidth.toLong() + minHorizontalGapPx + singleLineTimeWidth <= contentMaxWidth &&
            idRowWidth.toLong() + minHorizontalGapPx + timeWidth <= contentMaxWidth
        )
    val layoutWidth = if (hasBoundedWidth) {
        maxWidth
    } else {
        idRowWidth + minHorizontalGapPx + timeWidth + chevronGapPx + chevronWidth
    }
    val resolvedContentMaxWidth = (layoutWidth - chevronWidth - chevronGapPx).coerceAtLeast(0)

    return if (placeOnSingleLine) {
        val layoutHeight = maxOf(minHeight, idRowHeight, timeHeight, chevronHeight)
        SnapshotHeaderPlacement(
            placeOnSingleLine = true,
            layoutWidth = layoutWidth,
            layoutHeight = layoutHeight,
            idRowX = 0,
            idRowY = (layoutHeight - idRowHeight) / 2,
            timeX = (resolvedContentMaxWidth - timeWidth)
                .coerceAtLeast(idRowWidth + minHorizontalGapPx),
            timeY = (layoutHeight - timeHeight) / 2,
            chevronX = (layoutWidth - chevronWidth).coerceAtLeast(0),
            chevronY = (layoutHeight - chevronHeight) / 2
        )
    } else {
        val contentHeight = idRowHeight + lineGapPx + timeHeight
        val layoutHeight = maxOf(minHeight, contentHeight, chevronHeight)
        val contentTop = (layoutHeight - contentHeight) / 2
        SnapshotHeaderPlacement(
            placeOnSingleLine = false,
            layoutWidth = layoutWidth,
            layoutHeight = layoutHeight,
            idRowX = 0,
            idRowY = contentTop,
            timeX = 0,
            timeY = contentTop + idRowHeight + lineGapPx,
            chevronX = (layoutWidth - chevronWidth).coerceAtLeast(0),
            chevronY = (layoutHeight - chevronHeight) / 2
        )
    }
}

@Composable
internal fun SnapshotHeader(
    snapshotId: String,
    hostname: String?,
    timeText: String,
    tagColors: List<Int> = emptyList(),
    modifier: Modifier = Modifier,
    secondaryContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val density = LocalDensity.current
    val minHorizontalGapPx = with(density) { 8.dp.roundToPx() }
    val chevronGapPx = with(density) { 4.dp.roundToPx() }
    val lineGapPx = with(density) { 2.dp.roundToPx() }

    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Row(
                modifier = Modifier.testTag(SNAPSHOT_HEADER_ID_ROW_TAG),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = snapshotId.take(8),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
                hostname?.takeIf { it.isNotBlank() }?.let { host ->
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .widthIn(max = 120.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = host,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (tagColors.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tagColors.take(4).forEach { color ->
                            TagColorDot(colorArgb = color, size = 12.dp)
                        }
                        if (tagColors.size > 4) {
                            Text(
                                text = "+${tagColors.size - 4}",
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryContentColor
                            )
                        }
                    }
                }
            }
            Text(
                text = timeText,
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryContentColor
            )
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = secondaryContentColor,
                modifier = Modifier
                    .size(18.dp)
                    .testTag(SNAPSHOT_HEADER_CHEVRON_TAG)
            )
        }
    ) { measurables, constraints ->
        val idRowMeasurable = measurables[0]
        val timeMeasurable = measurables[1]
        val chevronMeasurable = measurables[2]

        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val chevronPlaceable = chevronMeasurable.measure(looseConstraints)
        val contentMaxWidth = if (constraints.hasBoundedWidth) {
            (constraints.maxWidth - chevronPlaceable.width - chevronGapPx).coerceAtLeast(0)
        } else {
            Constraints.Infinity
        }
        val contentConstraints = looseConstraints.copy(maxWidth = contentMaxWidth)

        val idRowPlaceable = idRowMeasurable.measure(contentConstraints)
        val singleLineTimeWidth = timeMeasurable.maxIntrinsicWidth(Constraints.Infinity)
        val timePlaceable = timeMeasurable.measure(contentConstraints)

        val placement = computeSnapshotHeaderPlacement(
            maxWidth = constraints.maxWidth,
            minHeight = constraints.minHeight,
            hasBoundedWidth = constraints.hasBoundedWidth,
            idRowWidth = idRowPlaceable.width,
            idRowHeight = idRowPlaceable.height,
            singleLineTimeWidth = singleLineTimeWidth,
            timeWidth = timePlaceable.width,
            timeHeight = timePlaceable.height,
            chevronWidth = chevronPlaceable.width,
            chevronHeight = chevronPlaceable.height,
            minHorizontalGapPx = minHorizontalGapPx,
            chevronGapPx = chevronGapPx,
            lineGapPx = lineGapPx
        )

        layout(placement.layoutWidth, placement.layoutHeight) {
            idRowPlaceable.placeRelative(x = placement.idRowX, y = placement.idRowY)
            timePlaceable.placeRelative(x = placement.timeX, y = placement.timeY)
            chevronPlaceable.placeRelative(x = placement.chevronX, y = placement.chevronY)
        }
    }
}


