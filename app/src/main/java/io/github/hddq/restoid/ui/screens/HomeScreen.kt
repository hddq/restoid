package io.github.hddq.restoid.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.OperationRuntimeState
import io.github.hddq.restoid.data.OperationType
import io.github.hddq.restoid.data.ResticState
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.ui.components.ExpressiveSectionHeader
import io.github.hddq.restoid.ui.components.ExpressiveSegmentedSurface
import io.github.hddq.restoid.ui.components.PasswordDialog
import io.github.hddq.restoid.ui.components.SshPrivateKeyField
import io.github.hddq.restoid.ui.components.UsernamePasswordDialog
import io.github.hddq.restoid.ui.home.HomeAuthFailure
import io.github.hddq.restoid.ui.home.HomeCredentialPrompt
import io.github.hddq.restoid.ui.home.HomeUiState
import io.github.hddq.restoid.ui.home.SnapshotWithMetadata

private fun homeCredentialsSaveLabel(prompt: HomeCredentialPrompt): Int {
    return if (prompt == HomeCredentialPrompt.S3_CREDENTIALS) R.string.action_save_passwords else R.string.save_credentials
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    onSnapshotClick: (String) -> Unit,
    uiState: HomeUiState,
    onRefresh: () -> Unit,
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
    onOpenOperationProgress: () -> Unit = {},
    onForceStopOperation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showForceStopDialog by remember { mutableStateOf(false) }
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
                            Text(
                                text = stringResource(R.string.no_repository_selected),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    uiState.resticState !is ResticState.Installed -> {
                        item(key = "restic_not_available") {
                            Text(
                                text = stringResource(R.string.restic_not_available_check_settings),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
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
                        Column {
                            Text(
                                text = stringResource(R.string.error_with_message, errorMessage),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )

                            when (uiState.authFailure) {
                                HomeAuthFailure.REPOSITORY_PASSWORD -> {
                                    Spacer(Modifier.height(12.dp))
                                    Button(onClick = onRetryRepositoryPasswordEntry) {
                                        Text(stringResource(R.string.action_enter_password_again))
                                    }
                                }
                                HomeAuthFailure.SFTP_PASSWORD -> {
                                    Spacer(Modifier.height(12.dp))
                                    Button(onClick = onRetrySftpPasswordEntry) {
                                        Text(stringResource(R.string.action_enter_sftp_password_again))
                                    }
                                }
                                HomeAuthFailure.REST_CREDENTIALS -> {
                                    Spacer(Modifier.height(12.dp))
                                    Button(onClick = onRetryRestCredentialsEntry) {
                                        Text(stringResource(R.string.action_enter_rest_credentials_again))
                                    }
                                }
                                HomeAuthFailure.S3_CREDENTIALS -> {
                                    Spacer(Modifier.height(12.dp))
                                    Button(onClick = onRetryS3CredentialsEntry) {
                                        Text(stringResource(R.string.action_enter_s3_credentials_again))
                                    }
                                }
                                null -> Unit
                            }
                        }
                    }
                }
                uiState.openPrompt != null -> {
                    item(key = "open_prompt") {
                        Column {
                            Text(
                                text = stringResource(R.string.home_open_needed_message),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            when (uiState.openPrompt) {
                                HomeCredentialPrompt.REPOSITORY_PASSWORD -> {
                                    Button(onClick = onRetryRepositoryPasswordEntry) {
                                        Text(stringResource(R.string.action_open_repository))
                                    }
                                }
                                HomeCredentialPrompt.SFTP_PASSWORD -> {
                                    Button(onClick = onRetrySftpPasswordEntry) {
                                        Text(stringResource(R.string.action_open_sftp))
                                    }
                                }
                                HomeCredentialPrompt.REST_CREDENTIALS -> {
                                    Button(onClick = onRetryRestCredentialsEntry) {
                                        Text(stringResource(R.string.action_open_rest))
                                    }
                                }
                                HomeCredentialPrompt.S3_CREDENTIALS -> {
                                    Button(onClick = onRetryS3CredentialsEntry) {
                                        Text(stringResource(R.string.action_open_s3))
                                    }
                                }
                            }
                        }
                    }
                }
                uiState.snapshotsWithMetadata.isEmpty() -> {
                    item(key = "empty_snapshots") {
                        Text(
                            text = stringResource(R.string.no_snapshots_found_for_repository),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    val snapshots = uiState.snapshotsWithMetadata.sortedByDescending { it.snapshotInfo.time }
                    item(key = "snapshots_header") {
                        ExpressiveSectionHeader(
                            title = pluralStringResource(
                                R.plurals.snapshots_count,
                                snapshots.size,
                                snapshots.size
                            )
                        )
                    }
                    itemsIndexed(
                        items = snapshots,
                        key = { _, item -> item.snapshotInfo.id },
                        contentType = { _, _ -> "snapshot_item" }
                    ) { index, item ->
                        ExpressiveSegmentedSurface(
                            index = index,
                            count = snapshots.size,
                            modifier = Modifier.padding(bottom = if (index < snapshots.size - 1) 2.dp else 0.dp)
                        ) {
                            SnapshotItem(
                                snapshotWithMetadata = item,
                                apps = uiState.appInfoMap[item.snapshotInfo.id],
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SnapshotItem(
    snapshotWithMetadata: SnapshotWithMetadata,
    apps: List<AppInfo>?,
    onClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onForgetClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val snapshot = snapshotWithMetadata.snapshotInfo
    val context = androidx.compose.ui.platform.LocalContext.current

    val timeText = remember(snapshot.time, context) {
        try {
            val instant = java.time.Instant.parse(snapshot.time)
            val now = java.time.Instant.now()
            val duration = java.time.Duration.between(instant, now)

            val relativeTime = when {
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
            "$relativeTime (${snapshot.time.take(10)})"
        } catch (e: Exception) {
            snapshot.time.take(10)
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuExpanded = true }
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = snapshot.id.take(8),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            val appCount = snapshotWithMetadata.metadata?.apps?.size ?: 0
            val customDirCount = snapshotWithMetadata.metadata?.customDirectories?.size ?: 0
            val totalCount = appCount + customDirCount

            if (totalCount > 0) {
                Text(
                    pluralStringResource(R.plurals.backed_up_items_count, totalCount, totalCount),
                    style = MaterialTheme.typography.labelMedium
                )
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val iconSize = 32.dp
                    val spacing = 8.dp
                    val itemWidthWithSpacing = iconSize + spacing
                    val maxIconsPossible = ((maxWidth + spacing) / itemWidthWithSpacing).toInt().coerceAtLeast(1)

                    val customDirSlot = if (customDirCount > 0) 1 else 0
                    val availableSlotsForApps = (maxIconsPossible - customDirSlot).coerceAtLeast(0)
                    val showCounter = appCount > availableSlotsForApps
                    val limit = if (showCounter) (availableSlotsForApps - 1).coerceAtLeast(0) else availableSlotsForApps

                    Row(horizontalArrangement = Arrangement.spacedBy(spacing), verticalAlignment = Alignment.CenterVertically) {
                        if (apps != null && apps.isNotEmpty() && appCount > 0) {
                            apps.take(limit).forEach { app ->
                                Image(
                                    painter = rememberAsyncImagePainter(model = app.icon),
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
                    }
                }
            } else if (snapshotWithMetadata.metadata == null) {
                Text(
                    text = stringResource(R.string.metadata_missing_or_corrupted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    text = stringResource(R.string.no_app_info_for_snapshot),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
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
