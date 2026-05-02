package io.github.hddq.restoid.ui.screens.settings.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hddq.restoid.R
import io.github.hddq.restoid.ui.components.SshPrivateKeyField
import io.github.hddq.restoid.ui.settings.SettingsViewModel

@Composable
fun SaveSftpCredentialsDialog(
    viewModel: SettingsViewModel,
    repositoryKey: String,
    onDismiss: () -> Unit
) {
    val repositories by viewModel.repositories.collectAsStateWithLifecycle()
    val repository = repositories.find { viewModel.repositoryKey(it) == repositoryKey }

    if (repository == null) {
        onDismiss()
        return
    }

    val isKeyAuth = repository.sftpKeyAuthRequired
    val requiresPassphrase = repository.sftpKeyPassphraseRequired

    var credentials by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var passphraseVisible by remember { mutableStateOf(false) }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                if (isKeyAuth) {
                    stringResource(R.string.dialog_save_sftp_key_credentials)
                } else {
                    stringResource(R.string.dialog_save_sftp_password)
                }
            )
        },
        text = {
            Column {
                if (isKeyAuth) {
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

                    if (requiresPassphrase) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it },
                            label = { Text(stringResource(R.string.label_sftp_key_passphrase)) },
                            singleLine = true,
                            visualTransformation = if (passphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                val image = if (passphraseVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                                val description = if (passphraseVisible) stringResource(R.string.cd_hide_password) else stringResource(R.string.cd_show_password)
                                IconButton(onClick = { passphraseVisible = !passphraseVisible }) {
                                    Icon(imageVector = image, contentDescription = description)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = credentials,
                        onValueChange = { credentials = it },
                        label = { Text(stringResource(R.string.label_sftp_password)) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                            val description = if (passwordVisible) stringResource(R.string.cd_hide_password) else stringResource(R.string.cd_show_password)
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = description)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isKeyAuth) {
                        viewModel.saveSftpKey(repositoryKey, credentials)
                        if (requiresPassphrase) {
                            viewModel.saveSftpKeyPassphrase(repositoryKey, passphrase)
                        }
                    } else {
                        viewModel.saveSftpPassword(repositoryKey, credentials)
                    }
                    onDismiss()
                },
                enabled = credentials.isNotEmpty() && (!requiresPassphrase || passphrase.isNotEmpty())
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
