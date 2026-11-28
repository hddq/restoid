package io.github.hddq.restoid.ui.screens.settings.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.LocalRepository
import io.github.hddq.restoid.data.RepositoryBackendType
import io.github.hddq.restoid.data.ResticState
import io.github.hddq.restoid.ui.components.expressiveListItemColors
import io.github.hddq.restoid.ui.screens.settings.dialogs.ChangePasswordDialog
import io.github.hddq.restoid.ui.screens.settings.dialogs.SavePasswordDialog
import io.github.hddq.restoid.ui.screens.settings.dialogs.SaveRestCredentialsDialog
import io.github.hddq.restoid.ui.screens.settings.dialogs.SaveS3CredentialsDialog
import io.github.hddq.restoid.ui.screens.settings.dialogs.SaveSftpCredentialsDialog
import io.github.hddq.restoid.ui.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsToggleRow(
    title: String,
    icon: ImageVector,
    enabled: Boolean,
    shapes: ListItemShapes,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SegmentedListItem(
        onClick = { onCheckedChange(!enabled) },
        shapes = shapes,
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        colors = expressiveListItemColors(),
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
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
        },
        content = { Text(text = title) }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppUnlockOnStartRow(
    enabled: Boolean,
    shapes: ListItemShapes,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsToggleRow(
        title = stringResource(R.string.require_app_unlock_on_start),
        icon = Icons.Outlined.Lock,
        enabled = enabled,
        shapes = shapes,
        onCheckedChange = onCheckedChange,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeviceHostnameRow(
    hostname: String,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        colors = expressiveListItemColors(),
        leadingContent = {
            Icon(
                imageVector = Icons.Outlined.Dns,
                contentDescription = null
            )
        },
        supportingContent = {
            Text(
                text = hostname,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.action_change)
            )
        },
        content = {
            Text(text = stringResource(R.string.settings_device_hostname_title))
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SelectableRepositoryRowLayout(
    repo: LocalRepository,
    isSelected: Boolean,
    shapes: ListItemShapes,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable () -> Unit = {
        IconButton(onClick = {}) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
        }
    }
) {
    SegmentedListItem(
        selected = isSelected,
        onClick = onSelected,
        shapes = shapes.copy(selectedShape = shapes.shape),
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        colors = expressiveListItemColors(
            selectedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            selectedContentColor = MaterialTheme.colorScheme.onSurface
        ),
        leadingContent = {
            RadioButton(
                selected = isSelected,
                onClick = null
            )
        },
        trailingContent = trailingContent,
        content = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = repo.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        contentColor = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    ) {
                        Text(
                            text = when (repo.backendType) {
                                RepositoryBackendType.LOCAL -> stringResource(R.string.repo_backend_local)
                                RepositoryBackendType.SFTP -> stringResource(R.string.repo_backend_sftp)
                                RepositoryBackendType.REST -> stringResource(R.string.repo_backend_rest)
                                RepositoryBackendType.S3 -> stringResource(R.string.repo_backend_s3)
                                RepositoryBackendType.RCLONE -> stringResource(R.string.repo_backend_rclone)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = repo.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                repo.id?.let {
                    Text(
                        text = stringResource(R.string.repository_id_short, it.take(12)),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = LocalContentColor.current.copy(alpha = 0.7f)
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SelectableRepositoryRow(
    repo: LocalRepository,
    isSelected: Boolean,
    shapes: ListItemShapes,
    onSelected: () -> Unit,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val repoKey = viewModel.repositoryKey(repo)

    SelectableRepositoryRowLayout(
        repo = repo,
        isSelected = isSelected,
        shapes = shapes,
        onSelected = onSelected,
        modifier = modifier
    ) {
        var showChangePasswordDialog by remember { mutableStateOf(false) }
        var showSavePasswordDialog by remember { mutableStateOf(false) }
        var showSaveSftpCredentialsDialog by remember { mutableStateOf(false) }
        var showSaveRestCredentialsDialog by remember { mutableStateOf(false) }
        var showSaveS3CredentialsDialog by remember { mutableStateOf(false) }

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

        if (showSaveSftpCredentialsDialog) {
            SaveSftpCredentialsDialog(
                viewModel = viewModel,
                repositoryKey = repoKey,
                onDismiss = { showSaveSftpCredentialsDialog = false }
            )
        }

        if (showSaveRestCredentialsDialog) {
            SaveRestCredentialsDialog(
                viewModel = viewModel,
                repositoryKey = repoKey,
                onDismiss = { showSaveRestCredentialsDialog = false }
            )
        }

        if (showSaveS3CredentialsDialog) {
            SaveS3CredentialsDialog(
                viewModel = viewModel,
                repositoryKey = repoKey,
                onDismiss = { showSaveS3CredentialsDialog = false }
            )
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        viewModel.deleteRepository(repoKey)
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_restore_metadata)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Restore,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        viewModel.restoreMetadata(repoKey)
                        showMenu = false
                    }
                )
                if (repo.backendType == RepositoryBackendType.SFTP) {
                    val hasCredentials = viewModel.hasStoredSftpCredentialsEntry(repoKey)
                    if (hasCredentials) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (repo.sftpKeyAuthRequired) {
                                        stringResource(R.string.action_forget_sftp_key_credentials)
                                    } else {
                                        stringResource(R.string.action_forget_sftp_password)
                                    }
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Key,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                if (repo.sftpKeyAuthRequired) viewModel.forgetSftpKey(repoKey) else viewModel.forgetSftpPassword(repoKey)
                                showMenu = false
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (repo.sftpKeyAuthRequired) {
                                        stringResource(R.string.action_save_sftp_key_credentials)
                                    } else {
                                        stringResource(R.string.action_save_sftp_password)
                                    }
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Key,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showSaveSftpCredentialsDialog = true
                                showMenu = false
                            }
                        )
                    }
                }
                if (repo.backendType == RepositoryBackendType.REST) {
                    if (viewModel.hasStoredRestCredentialsEntry(repoKey)) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_forget_rest_credentials)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Key,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                viewModel.forgetRestCredentials(repoKey)
                                showMenu = false
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_save_rest_credentials)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Key,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showSaveRestCredentialsDialog = true
                                showMenu = false
                            }
                        )
                    }
                }
                if (repo.backendType == RepositoryBackendType.S3) {
                    if (viewModel.hasStoredS3CredentialsEntry(repoKey)) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_forget_s3_credentials)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Key,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                viewModel.forgetS3Credentials(repoKey)
                                showMenu = false
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_save_s3_credentials)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Key,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showSaveS3CredentialsDialog = true
                                showMenu = false
                            }
                        )
                    }
                }
                if (viewModel.hasStoredRepositoryPasswordEntry(repoKey)) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_forget_password)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Key,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            viewModel.forgetPassword(repoKey)
                            showMenu = false
                        }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_save_password)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Key,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showSavePasswordDialog = true
                            showMenu = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_change_password)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.LockReset,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        showChangePasswordDialog = true
                        showMenu = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ResticDependencyRow(
    state: ResticState,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier
) {
    SegmentedListItem(
        shapes = shapes,
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        colors = expressiveListItemColors(),
        leadingContent = {
            AnimatedContent(targetState = state, label = "ResticIconAnimation") { targetState ->
                val icon: ImageVector
                val iconColor: Color
                when (targetState) {
                    is ResticState.Installed -> {
                        icon = Icons.Outlined.CheckCircle
                        iconColor = MaterialTheme.colorScheme.primary
                    }
                    is ResticState.Error -> {
                        icon = Icons.Outlined.ErrorOutline
                        iconColor = MaterialTheme.colorScheme.error
                    }
                    ResticState.Idle -> {
                        icon = Icons.Outlined.CheckCircle
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    }
                }
                Icon(imageVector = icon, contentDescription = null, tint = iconColor)
            }
        },
        supportingContent = {
            AnimatedContent(targetState = state, label = "ResticStatusAnimation") { targetState ->
                val supportingText = when (targetState) {
                    ResticState.Idle -> stringResource(R.string.restic_checking_status)
                    is ResticState.Installed -> targetState.fullVersionOutput
                    is ResticState.Error -> stringResource(R.string.restic_error_with_message, targetState.message)
                }
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        content = { Text(stringResource(R.string.restic_binary)) }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RcloneDependencyRow(
    state: ResticState,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier
) {
    SegmentedListItem(
        shapes = shapes,
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        colors = expressiveListItemColors(),
        leadingContent = {
            AnimatedContent(targetState = state, label = "RcloneIconAnimation") { targetState ->
                val icon: ImageVector
                val iconColor: Color
                when (targetState) {
                    is ResticState.Installed -> {
                        icon = Icons.Outlined.CheckCircle
                        iconColor = MaterialTheme.colorScheme.primary
                    }
                    is ResticState.Error -> {
                        icon = Icons.Outlined.ErrorOutline
                        iconColor = MaterialTheme.colorScheme.error
                    }
                    ResticState.Idle -> {
                        icon = Icons.Outlined.CheckCircle
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    }
                }
                Icon(imageVector = icon, contentDescription = null, tint = iconColor)
            }
        },
        supportingContent = {
            AnimatedContent(targetState = state, label = "RcloneStatusAnimation") { targetState ->
                val supportingText = when (targetState) {
                    ResticState.Idle -> stringResource(R.string.restic_checking_status)
                    is ResticState.Installed -> targetState.fullVersionOutput.ifBlank { "rclone" }
                    is ResticState.Error -> targetState.message
                }
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        content = { Text(stringResource(R.string.rclone_binary)) }
    )
}
