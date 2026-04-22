package io.github.hddq.restoid.ui.screens.settings.dialogs

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
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
import io.github.hddq.restoid.R
import io.github.hddq.restoid.ui.settings.SettingsViewModel

@Composable
fun SaveS3CredentialsDialog(
    viewModel: SettingsViewModel,
    repositoryKey: String,
    onDismiss: () -> Unit
) {
    var accessKeyId by remember { mutableStateOf("") }
    var secretAccessKey by remember { mutableStateOf("") }
    var secretVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_save_s3_credentials)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = accessKeyId,
                    onValueChange = { accessKeyId = it },
                    label = { Text(stringResource(R.string.label_s3_access_key_id)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = secretAccessKey,
                    onValueChange = { secretAccessKey = it },
                    label = { Text(stringResource(R.string.label_s3_secret_access_key)) },
                    singleLine = true,
                    visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (secretVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                        val description = if (secretVisible) stringResource(R.string.cd_hide_password) else stringResource(R.string.cd_show_password)
                        IconButton(onClick = { secretVisible = !secretVisible }) {
                            Icon(imageVector = image, contentDescription = description)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.s3_credentials_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.saveS3Credentials(repositoryKey, accessKeyId, secretAccessKey)
                    onDismiss()
                },
                enabled = accessKeyId.isNotBlank() && secretAccessKey.isNotBlank()
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
