package io.github.hddq.restoid.ui.screens.settings.dialogs

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hddq.restoid.R
import io.github.hddq.restoid.ui.settings.ChangePasswordState
import io.github.hddq.restoid.ui.settings.SettingsViewModel

@Composable
fun ChangePasswordDialog(
    viewModel: SettingsViewModel,
    repositoryKey: String,
    onDismiss: () -> Unit
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val failedChangePasswordMessage = stringResource(R.string.error_failed_change_password)

    val changePasswordState by viewModel.changePasswordState.collectAsStateWithLifecycle()

    LaunchedEffect(changePasswordState) {
        when (changePasswordState) {
            ChangePasswordState.Success -> {
                viewModel.resetChangePasswordState()
                onDismiss()
            }
            ChangePasswordState.Error -> {
                error = failedChangePasswordMessage
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
        title = { Text(stringResource(R.string.dialog_change_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it },
                    label = { Text(stringResource(R.string.label_old_password)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = if (passwordVisible) stringResource(R.string.cd_hide) else stringResource(R.string.cd_show))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = changePasswordState != ChangePasswordState.InProgress
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text(stringResource(R.string.label_new_password)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = if (passwordVisible) stringResource(R.string.cd_hide) else stringResource(R.string.cd_show))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = changePasswordState != ChangePasswordState.InProgress
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(stringResource(R.string.label_confirm_new_password)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = if (passwordVisible) stringResource(R.string.cd_hide) else stringResource(R.string.cd_show))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = changePasswordState != ChangePasswordState.InProgress,
                    isError = newPassword != confirmPassword
                )
                if (newPassword != confirmPassword) {
                    Text(stringResource(R.string.error_passwords_do_not_match), color = MaterialTheme.colorScheme.error)
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
                        viewModel.changePassword(repositoryKey, oldPassword, newPassword)
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
                    Text(stringResource(R.string.action_change))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = changePasswordState != ChangePasswordState.InProgress
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
