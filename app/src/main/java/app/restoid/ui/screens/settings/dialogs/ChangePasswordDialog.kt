package app.restoid.ui.screens.settings.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.restoid.ui.settings.ChangePasswordState
import app.restoid.ui.settings.SettingsViewModel

@Composable
fun ChangePasswordDialog(
    viewModel: SettingsViewModel,
    repoPath: String,
    onDismiss: () -> Unit
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val changePasswordState by viewModel.changePasswordState.collectAsStateWithLifecycle()

    LaunchedEffect(changePasswordState) {
        when (changePasswordState) {
            ChangePasswordState.Success -> {
                viewModel.resetChangePasswordState()
                onDismiss()
            }
            ChangePasswordState.Error -> {
                error = "Failed to change password."
                viewModel.resetChangePasswordState()
            }
            else -> {}
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (changePasswordState != ChangePasswordState.InProgress) {
                onDismiss()
            }
        },
        title = { Text("Change Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it },
                    label = { Text("Old Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = if (passwordVisible) "Hide" else "Show")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = changePasswordState != ChangePasswordState.InProgress
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = if (passwordVisible) "Hide" else "Show")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = changePasswordState != ChangePasswordState.InProgress
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm New Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = if (passwordVisible) "Hide" else "Show")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = changePasswordState != ChangePasswordState.InProgress,
                    isError = newPassword != confirmPassword
                )
                if (newPassword != confirmPassword) {
                    Text("Passwords do not match.", color = MaterialTheme.colorScheme.error)
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    error = null
                    if (newPassword == confirmPassword) {
                        viewModel.changePassword(repoPath, oldPassword, newPassword)
                    }
                },
                enabled = oldPassword.isNotEmpty() &&
                        newPassword.isNotEmpty() &&
                        confirmPassword.isNotEmpty() &&
                        newPassword == confirmPassword &&
                        changePasswordState != ChangePasswordState.InProgress
            ) {
                if (changePasswordState == ChangePasswordState.InProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text("Change")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = changePasswordState != ChangePasswordState.InProgress
            ) {
                Text("Cancel")
            }
        }
    )
}
