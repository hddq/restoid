package app.restoid.ui.screens.settings

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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.restoid.data.AddRepositoryState
import app.restoid.ui.settings.AddRepoUiState

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
        title = { Text("Add Local Repository") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.path,
                    onValueChange = {},
                    label = { Text("Path") },
                    placeholder = { Text("Select a directory...") },
                    singleLine = true,
                    readOnly = true,
                    isError = uiState.state is AddRepositoryState.Error,
                    trailingIcon = {
                        IconButton(onClick = onSelectPath) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Select Folder")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                        val description = if (passwordVisible) "Hide password" else "Show password"
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
                        text = "Save password",
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
                    Text("Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
