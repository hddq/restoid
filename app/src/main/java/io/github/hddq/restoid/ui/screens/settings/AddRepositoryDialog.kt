package io.github.hddq.restoid.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.AddRepositoryState
import io.github.hddq.restoid.ui.settings.AddRepoUiState

@Composable
fun AddRepositoryDialog(
    uiState: AddRepoUiState,
    onDismiss: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onSavePasswordChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onSelectPath: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_add_local_repository_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.path,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.label_path)) },
                    placeholder = { Text(stringResource(R.string.placeholder_select_directory)) },
                    singleLine = true,
                    readOnly = true,
                    isError = uiState.state is AddRepositoryState.Error,
                    trailingIcon = {
                        IconButton(onClick = onSelectPath) {
                            Icon(Icons.Default.FolderOpen, contentDescription = stringResource(R.string.cd_select_folder))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = onPasswordChange,
                    label = { Text(stringResource(R.string.label_password)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                        val description = if (passwordVisible) stringResource(R.string.cd_hide_password) else stringResource(R.string.cd_show_password)
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = description)
                        }
                    },
                    isError = uiState.state is AddRepositoryState.Error,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = uiState.savePassword,
                        onCheckedChange = onSavePasswordChange
                    )
                    Text(
                        text = stringResource(R.string.save_password),
                        modifier = Modifier.clickable(onClick = { onSavePasswordChange(!uiState.savePassword) })
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
                enabled = uiState.state !is AddRepositoryState.Initializing && uiState.path.isNotBlank()
            ) {
                if (uiState.state is AddRepositoryState.Initializing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.action_add))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
