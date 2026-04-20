package io.github.hddq.restoid.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.AddRepositoryState
import io.github.hddq.restoid.data.RepositoryBackendType
import io.github.hddq.restoid.ui.settings.AddRepoUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRepositoryDialog(
    uiState: AddRepoUiState,
    onDismiss: () -> Unit,
    onBackendTypeChange: (RepositoryBackendType) -> Unit,
    onPathChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSftpPasswordChange: (String) -> Unit,
    onRestUsernameChange: (String) -> Unit,
    onRestPasswordChange: (String) -> Unit,
    onEnvironmentVariablesChange: (String) -> Unit,
    onSavePasswordChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onSelectPath: () -> Unit
) {
    var repositoryPasswordVisible by remember { mutableStateOf(false) }
    var sftpPasswordVisible by remember { mutableStateOf(false) }
    var restPasswordVisible by remember { mutableStateOf(false) }
    var backendExpanded by remember { mutableStateOf(false) }
    val isBusy = uiState.state is AddRepositoryState.Initializing
    val hasIncompleteRestCredentials =
        uiState.backendType == RepositoryBackendType.REST &&
            (uiState.restUsername.isBlank() != uiState.restPassword.isBlank())
    val canConfirm = !isBusy &&
        uiState.path.isNotBlank() &&
        uiState.password.isNotBlank() &&
        !hasIncompleteRestCredentials

    val backendOptions = listOf(
        RepositoryBackendType.LOCAL,
        RepositoryBackendType.SFTP,
        RepositoryBackendType.REST,
        RepositoryBackendType.S3
    )

    val selectedBackendLabel = when (uiState.backendType) {
        RepositoryBackendType.LOCAL -> stringResource(R.string.repo_backend_local)
        RepositoryBackendType.SFTP -> stringResource(R.string.repo_backend_sftp)
        RepositoryBackendType.REST -> stringResource(R.string.repo_backend_rest)
        RepositoryBackendType.S3 -> stringResource(R.string.repo_backend_s3)
    }

    val pathPlaceholder = when (uiState.backendType) {
        RepositoryBackendType.LOCAL -> stringResource(R.string.placeholder_select_directory)
        RepositoryBackendType.SFTP -> stringResource(R.string.repo_hint_sftp)
        RepositoryBackendType.REST -> stringResource(R.string.repo_hint_rest)
        RepositoryBackendType.S3 -> stringResource(R.string.repo_hint_s3)
    }

    val savePasswordLabel = when (uiState.backendType) {
        RepositoryBackendType.SFTP,
        RepositoryBackendType.REST -> stringResource(R.string.action_save_passwords)
        else -> stringResource(R.string.action_save_password)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_add_repository_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = backendExpanded,
                    onExpandedChange = { if (!isBusy) backendExpanded = !backendExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedBackendLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.label_repository_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = backendExpanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        enabled = !isBusy
                    )

                    ExposedDropdownMenu(
                        expanded = backendExpanded,
                        onDismissRequest = { backendExpanded = false }
                    ) {
                        backendOptions.forEach { backend ->
                            val label = when (backend) {
                                RepositoryBackendType.LOCAL -> stringResource(R.string.repo_backend_local)
                                RepositoryBackendType.SFTP -> stringResource(R.string.repo_backend_sftp)
                                RepositoryBackendType.REST -> stringResource(R.string.repo_backend_rest)
                                RepositoryBackendType.S3 -> stringResource(R.string.repo_backend_s3)
                            }

                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onBackendTypeChange(backend)
                                    backendExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = uiState.path,
                    onValueChange = onPathChange,
                    label = {
                        Text(
                            if (uiState.backendType == RepositoryBackendType.LOCAL) {
                                stringResource(R.string.label_path)
                            } else {
                                stringResource(R.string.label_repository_specification)
                            }
                        )
                    },
                    placeholder = { Text(pathPlaceholder) },
                    singleLine = true,
                    readOnly = uiState.backendType == RepositoryBackendType.LOCAL,
                    isError = uiState.state is AddRepositoryState.Error,
                    trailingIcon = {
                        if (uiState.backendType == RepositoryBackendType.LOCAL) {
                            IconButton(onClick = onSelectPath, enabled = !isBusy) {
                                Icon(Icons.Default.FolderOpen, contentDescription = stringResource(R.string.cd_select_folder))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy
                )

                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = onPasswordChange,
                    label = { Text(stringResource(R.string.label_repository_password)) },
                    singleLine = true,
                    visualTransformation = if (repositoryPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (repositoryPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                        val description = if (repositoryPasswordVisible) stringResource(R.string.cd_hide_password) else stringResource(R.string.cd_show_password)
                        IconButton(onClick = { repositoryPasswordVisible = !repositoryPasswordVisible }, enabled = !isBusy) {
                            Icon(imageVector = image, contentDescription = description)
                        }
                    },
                    isError = uiState.state is AddRepositoryState.Error,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy
                )

                if (uiState.backendType == RepositoryBackendType.SFTP) {
                    OutlinedTextField(
                        value = uiState.sftpPassword,
                        onValueChange = onSftpPasswordChange,
                        label = { Text(stringResource(R.string.label_sftp_password)) },
                        singleLine = true,
                        visualTransformation = if (sftpPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (sftpPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                            val description = if (sftpPasswordVisible) stringResource(R.string.cd_hide_password) else stringResource(R.string.cd_show_password)
                            IconButton(onClick = { sftpPasswordVisible = !sftpPasswordVisible }, enabled = !isBusy) {
                                Icon(imageVector = image, contentDescription = description)
                            }
                        },
                        isError = uiState.state is AddRepositoryState.Error,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
                    )
                }

                if (uiState.backendType == RepositoryBackendType.REST) {
                    OutlinedTextField(
                        value = uiState.restUsername,
                        onValueChange = onRestUsernameChange,
                        label = { Text(stringResource(R.string.label_rest_username)) },
                        singleLine = true,
                        isError = uiState.state is AddRepositoryState.Error,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
                    )

                    OutlinedTextField(
                        value = uiState.restPassword,
                        onValueChange = onRestPasswordChange,
                        label = { Text(stringResource(R.string.label_rest_password)) },
                        singleLine = true,
                        visualTransformation = if (restPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (restPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                            val description = if (restPasswordVisible) stringResource(R.string.cd_hide_password) else stringResource(R.string.cd_show_password)
                            IconButton(onClick = { restPasswordVisible = !restPasswordVisible }, enabled = !isBusy) {
                                Icon(imageVector = image, contentDescription = description)
                            }
                        },
                        isError = uiState.state is AddRepositoryState.Error,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
                    )
                }

                if (uiState.backendType != RepositoryBackendType.LOCAL) {
                    OutlinedTextField(
                        value = uiState.environmentVariablesRaw,
                        onValueChange = onEnvironmentVariablesChange,
                        label = { Text(stringResource(R.string.label_environment_variables)) },
                        placeholder = { Text(stringResource(R.string.placeholder_environment_variables)) },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = uiState.savePassword,
                        onCheckedChange = onSavePasswordChange,
                        enabled = !isBusy
                    )
                    Text(
                        text = savePasswordLabel,
                        modifier = Modifier.clickable(enabled = !isBusy, onClick = { onSavePasswordChange(!uiState.savePassword) })
                    )
                }

                if (uiState.state is AddRepositoryState.Error) {
                    Text(
                        text = uiState.state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = canConfirm
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.action_add))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
