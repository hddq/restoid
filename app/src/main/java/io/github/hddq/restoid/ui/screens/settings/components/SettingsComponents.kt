package io.github.hddq.restoid.ui.screens.settings.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hddq.restoid.BuildConfig
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.LocalRepository
import io.github.hddq.restoid.data.NotificationPermissionState
import io.github.hddq.restoid.data.RepositoryBackendType
import io.github.hddq.restoid.data.ResticState
import io.github.hddq.restoid.ui.screens.settings.dialogs.ChangePasswordDialog
import io.github.hddq.restoid.ui.screens.settings.dialogs.SavePasswordDialog
import io.github.hddq.restoid.ui.screens.settings.dialogs.SaveRestCredentialsDialog
import io.github.hddq.restoid.ui.screens.settings.dialogs.SaveSftpPasswordDialog
import io.github.hddq.restoid.ui.settings.SettingsViewModel

@Composable
fun NotificationPermissionRow(
    state: NotificationPermissionState,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            val icon = when (state) {
                NotificationPermissionState.Granted -> Icons.Default.CheckCircle
                NotificationPermissionState.Denied -> Icons.Default.Error
                NotificationPermissionState.NotRequested -> Icons.Default.Notifications
            }
            val iconColor = when (state) {
                NotificationPermissionState.Granted -> MaterialTheme.colorScheme.primary
                NotificationPermissionState.Denied -> MaterialTheme.colorScheme.error
                NotificationPermissionState.NotRequested -> LocalContentColor.current
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(end = 16.dp),
                tint = iconColor
            )
            Text(
                text = when (state) {
                    NotificationPermissionState.Granted -> stringResource(R.string.notifications_enabled)
                    NotificationPermissionState.Denied -> stringResource(R.string.notifications_disabled)
                    NotificationPermissionState.NotRequested -> stringResource(R.string.notification_permission)
                }
            )
        }
        if (state != NotificationPermissionState.Granted) {
            Button(onClick = if (state == NotificationPermissionState.NotRequested) onRequestPermission else onOpenSettings) {
                Text(if (state == NotificationPermissionState.Denied) stringResource(R.string.action_settings) else stringResource(R.string.action_grant))
            }
        }
    }
}

@Composable
fun AppUnlockOnStartRow(
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!enabled) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.padding(end = 16.dp)
            )
            Text(text = stringResource(R.string.require_app_unlock_on_start))
        }
        Switch(
            checked = enabled,
            onCheckedChange = onCheckedChange,
            thumbContent = if (enabled) {
                {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                }
            } else {
                null
            }
        )
    }
}

@Composable
fun SelectableRepositoryRow(
    repo: LocalRepository,
    isSelected: Boolean,
    onSelected: () -> Unit,
    viewModel: SettingsViewModel
) {
    var showMenu by remember { mutableStateOf(false) }
    val repoKey = viewModel.repositoryKey(repo)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = onSelected,
                role = Role.RadioButton
            )
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(repo.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                repo.path,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when (repo.backendType) {
                    RepositoryBackendType.LOCAL -> stringResource(R.string.repo_backend_local)
                    RepositoryBackendType.SFTP -> stringResource(R.string.repo_backend_sftp)
                    RepositoryBackendType.REST -> stringResource(R.string.repo_backend_rest)
                    RepositoryBackendType.S3 -> stringResource(R.string.repo_backend_s3)
                },
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.75f)
            )
            repo.id?.let {
                Text(
                    stringResource(R.string.repository_id_short, it.take(12)),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = LocalContentColor.current.copy(alpha = 0.7f)
                )
            }
        }
        var showChangePasswordDialog by remember { mutableStateOf(false) }
        var showSavePasswordDialog by remember { mutableStateOf(false) }
        var showSaveSftpPasswordDialog by remember { mutableStateOf(false) }
        var showSaveRestCredentialsDialog by remember { mutableStateOf(false) }

        if (showChangePasswordDialog) {
            ChangePasswordDialog(
                viewModel = viewModel,
                repositoryKey = repoKey,
                onDismiss = { showChangePasswordDialog = false }
            )
        }

        if (showSavePasswordDialog) {
            SavePasswordDialog(
                viewModel = viewModel,
                repositoryKey = repoKey,
                onDismiss = { showSavePasswordDialog = false }
            )
        }

        if (showSaveSftpPasswordDialog) {
            SaveSftpPasswordDialog(
                viewModel = viewModel,
                repositoryKey = repoKey,
                onDismiss = { showSaveSftpPasswordDialog = false }
            )
        }

        if (showSaveRestCredentialsDialog) {
            SaveRestCredentialsDialog(
                viewModel = viewModel,
                repositoryKey = repoKey,
                onDismiss = { showSaveRestCredentialsDialog = false }
            )
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    onClick = {
                        viewModel.deleteRepository(repoKey)
                        showMenu = false
                    }
                )
                if (repo.backendType == RepositoryBackendType.SFTP) {
                    if (viewModel.hasStoredSftpPassword(repoKey)) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_forget_sftp_password)) },
                            onClick = {
                                viewModel.forgetSftpPassword(repoKey)
                                showMenu = false
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_save_sftp_password)) },
                            onClick = {
                                showSaveSftpPasswordDialog = true
                                showMenu = false
                            }
                        )
                    }
                }
                if (repo.backendType == RepositoryBackendType.REST) {
                    if (viewModel.hasStoredRestCredentials(repoKey)) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_forget_rest_credentials)) },
                            onClick = {
                                viewModel.forgetRestCredentials(repoKey)
                                showMenu = false
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_save_rest_credentials)) },
                            onClick = {
                                showSaveRestCredentialsDialog = true
                                showMenu = false
                            }
                        )
                    }
                }
                if (viewModel.hasStoredRepositoryPassword(repoKey)) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_forget_password)) },
                        onClick = {
                            viewModel.forgetPassword(repoKey)
                            showMenu = false
                        }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_save_password)) },
                        onClick = {
                            showSavePasswordDialog = true
                            showMenu = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_change_password)) },
                    onClick = {
                        showChangePasswordDialog = true
                        showMenu = false
                    }
                )
            }
        }
    }
}

@Composable
fun SplitButton(
    onClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    text: String
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(Modifier.height(IntrinsicSize.Min)) {
        Button(
            onClick = onClick,
            shape = shape.copy(topEnd = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
            contentPadding = ButtonDefaults.ContentPadding
        ) {
            Text(text)
        }
        Divider(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp),
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
        )
        Button(
            onClick = onSecondaryClick,
            shape = shape.copy(topStart = CornerSize(0.dp), bottomStart = CornerSize(0.dp))
        ) {
            Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.cd_more_download_options))
        }
    }
}


@Composable
fun ResticDependencyRow(
    state: ResticState,
    stableResticVersion: String,
    latestResticVersion: String?,
    onDownloadClick: () -> Unit,
    onDownloadLatestClick: () -> Unit
) {
    AnimatedContent(targetState = state, label = "ResticStatusAnimation") { targetState ->
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val isUpdateAvailable = (targetState as? ResticState.Installed)?.version?.let {
                        // A simple string comparison is often sufficient for semantic versions like this
                        it.isNotEmpty() && it != "unknown" && it < stableResticVersion
                    } == true

                    val icon: ImageVector
                    val iconColor: Color

                    when {
                        isUpdateAvailable -> {
                            icon = Icons.Default.CloudDownload
                            iconColor = MaterialTheme.colorScheme.primary
                        }
                        targetState is ResticState.Installed -> {
                            icon = Icons.Default.CheckCircle
                            iconColor = MaterialTheme.colorScheme.primary
                        }
                        targetState is ResticState.Error -> {
                            icon = Icons.Default.Error
                            iconColor = MaterialTheme.colorScheme.error
                        }
                        else -> {
                            icon = Icons.Default.CloudDownload
                            iconColor = LocalContentColor.current
                        }
                    }

                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.padding(end = 16.dp), tint = iconColor)

                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.restic_binary), style = MaterialTheme.typography.bodyLarge)
                        val supportingText = when (targetState) {
                            ResticState.Idle -> stringResource(R.string.restic_checking_status)
                            ResticState.NotInstalled -> if (BuildConfig.IS_BUNDLED) stringResource(R.string.restic_bundled_will_extract) else stringResource(R.string.restic_required_for_backups)
                            is ResticState.Downloading -> stringResource(R.string.restic_downloading)
                            ResticState.Extracting -> stringResource(R.string.restic_extracting_binary)
                            is ResticState.Installed -> targetState.fullVersionOutput
                            is ResticState.Error -> stringResource(R.string.restic_error_with_message, targetState.message)
                        }
                        Text(
                            text = supportingText,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isUpdateAvailable) {
                            Text(
                                text = stringResource(R.string.restic_update_available, stableResticVersion),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                when (targetState) {
                    ResticState.NotInstalled, is ResticState.Error -> {
                        if (!BuildConfig.IS_BUNDLED) {
                            val isStableLatest = latestResticVersion != null && latestResticVersion == stableResticVersion
                            val showSplitButton = latestResticVersion != null && !isStableLatest

                            if (showSplitButton) {
                                var showMenu by remember { mutableStateOf(false) }
                                Box {
                                    SplitButton(
                                        onClick = onDownloadClick,
                                        onSecondaryClick = { showMenu = true },
                                        text = if (targetState is ResticState.Error) {
                                            stringResource(R.string.action_retry)
                                        } else {
                                            stringResource(R.string.restic_download_version, stableResticVersion)
                                        }
                                    )
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.restic_download_latest, latestResticVersion ?: "")) },
                                            onClick = {
                                                onDownloadLatestClick()
                                                showMenu = false
                                            }
                                        )
                                    }
                                }
                            } else {
                                val buttonText = when {
                                    targetState is ResticState.Error -> stringResource(R.string.action_retry)
                                    latestResticVersion != null -> stringResource(R.string.restic_download_latest, latestResticVersion)
                                    else -> stringResource(R.string.restic_download_version, stableResticVersion)
                                }
                                val clickAction = when {
                                    targetState is ResticState.Error -> onDownloadClick
                                    latestResticVersion != null -> onDownloadLatestClick
                                    else -> onDownloadClick
                                }
                                Button(onClick = clickAction) {
                                    Text(buttonText)
                                }
                            }
                        }
                    }
                    is ResticState.Installed -> {
                        val isUpdateAvailable = targetState.version.let {
                            it.isNotEmpty() && it != "unknown" && it < stableResticVersion
                        }
                        if (isUpdateAvailable && !BuildConfig.IS_BUNDLED) {
                            Button(onClick = onDownloadClick) {
                                Text(stringResource(R.string.action_update))
                            }
                        }
                    }
                    ResticState.Extracting -> CircularProgressIndicator()
                    is ResticState.Downloading -> {
                        Text(
                            text = "${(targetState.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    ResticState.Idle -> {}
                }
            }

            if (targetState is ResticState.Downloading) {
                LinearProgressIndicator(
                    progress = { targetState.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun RootRequestRow(
    text: String,
    buttonText: String,
    icon: ImageVector?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Text(text = text)
        }
        Button(onClick = onClick) {
            Text(buttonText)
        }
    }
}

@Composable
fun RootStatusRow(text: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 16.dp)
        )
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}
