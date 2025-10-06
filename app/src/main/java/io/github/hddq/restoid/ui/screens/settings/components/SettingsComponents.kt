package io.github.hddq.restoid.ui.screens.settings.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hddq.restoid.BuildConfig
import io.github.hddq.restoid.data.LocalRepository
import io.github.hddq.restoid.data.NotificationPermissionState
import io.github.hddq.restoid.data.ResticState
import io.github.hddq.restoid.ui.screens.settings.dialogs.ChangePasswordDialog
import io.github.hddq.restoid.ui.screens.settings.dialogs.SavePasswordDialog
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
                    NotificationPermissionState.Granted -> "Notifications enabled"
                    NotificationPermissionState.Denied -> "Notifications disabled"
                    NotificationPermissionState.NotRequested -> "Notification permission"
                }
            )
        }
        if (state != NotificationPermissionState.Granted) {
            Button(onClick = if (state == NotificationPermissionState.NotRequested) onRequestPermission else onOpenSettings) {
                Text(if (state == NotificationPermissionState.Denied) "Settings" else "Grant")
            }
        }
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
            repo.id?.let {
                Text(
                    "ID: ${it.take(12)}...",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = LocalContentColor.current.copy(alpha = 0.7f)
                )
            }
        }
        var showChangePasswordDialog by remember { mutableStateOf(false) }
        var showSavePasswordDialog by remember { mutableStateOf(false) }

        if (showChangePasswordDialog) {
            ChangePasswordDialog(
                viewModel = viewModel,
                repoPath = repo.path,
                onDismiss = { showChangePasswordDialog = false }
            )
        }

        if (showSavePasswordDialog) {
            SavePasswordDialog(
                viewModel = viewModel,
                repoPath = repo.path,
                onDismiss = { showSavePasswordDialog = false }
            )
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        viewModel.deleteRepository(repo.path)
                        showMenu = false
                    }
                )
                if (viewModel.hasStoredRepositoryPassword(repo.path)) {
                    DropdownMenuItem(
                        text = { Text("Forget Password") },
                        onClick = {
                            viewModel.forgetPassword(repo.path)
                            showMenu = false
                        }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Save Password") },
                        onClick = {
                            showSavePasswordDialog = true
                            showMenu = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Change password") },
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
    Row(Modifier.height(IntrinsicSize.Min)) {
        Button(
            onClick = onClick,
            shape = MaterialTheme.shapes.medium.copy(
                topEnd = CornerSize(0.dp),
                bottomEnd = CornerSize(0.dp)
            ),
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
            shape = MaterialTheme.shapes.medium.copy(
                topStart = CornerSize(0.dp),
                bottomStart = CornerSize(0.dp)
            )
        ) {
            Icon(Icons.Default.ArrowDropDown, contentDescription = "More download options")
        }
    }
}


@Composable
fun ResticDependencyRow(
    state: ResticState,
    stableResticVersion: String,
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
                    val icon = when (targetState) {
                        is ResticState.Installed -> Icons.Default.CheckCircle
                        is ResticState.Error -> Icons.Default.Error
                        else -> Icons.Default.CloudDownload
                    }
                    val iconColor = when (targetState) {
                        is ResticState.Installed -> MaterialTheme.colorScheme.primary
                        is ResticState.Error -> MaterialTheme.colorScheme.error
                        else -> LocalContentColor.current
                    }
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.padding(end = 16.dp), tint = iconColor)

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Restic Binary", style = MaterialTheme.typography.bodyLarge)
                        val supportingText = when (targetState) {
                            ResticState.Idle -> "Checking status..."
                            ResticState.NotInstalled -> if (BuildConfig.IS_BUNDLED) "Bundled, will be extracted" else "Required for backups"
                            is ResticState.Downloading -> "Downloading..."
                            ResticState.Extracting -> "Extracting binary..."
                            is ResticState.Installed -> targetState.version
                            is ResticState.Error -> "Error: ${targetState.message}"
                        }
                        Text(
                            text = supportingText,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                when (targetState) {
                    ResticState.NotInstalled, is ResticState.Error -> {
                        if (!BuildConfig.IS_BUNDLED) {
                            var showMenu by remember { mutableStateOf(false) }
                            Box {
                                SplitButton(
                                    onClick = onDownloadClick,
                                    onSecondaryClick = { showMenu = true },
                                    text = if (targetState is ResticState.Error) "Retry" else "Download v$stableResticVersion"
                                )
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Download latest") },
                                        onClick = {
                                            onDownloadLatestClick()
                                            showMenu = false
                                        }
                                    )
                                }
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
                    is ResticState.Installed, ResticState.Idle -> {}
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

