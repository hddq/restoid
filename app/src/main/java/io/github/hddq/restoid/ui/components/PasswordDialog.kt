package io.github.hddq.restoid.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.hddq.restoid.R

@Composable
fun PasswordDialog(
    title: String,
    message: String,
    onPasswordEntered: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var savePassword by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val submitPassword = {
        if (password.isNotBlank()) {
            onPasswordEntered(password, savePassword)
        } else {
            isError = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column {
                Text(text = message)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        isError = false
                    },
                    label = { Text(stringResource(R.string.label_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            submitPassword()
                        }
                    ),
                    isError = isError,
                    supportingText = if (isError) {
                        { Text(stringResource(R.string.error_password_cannot_be_empty)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = savePassword,
                        onCheckedChange = { savePassword = it }
                    )
                    Text(text = stringResource(R.string.save_password))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    keyboardController?.hide()
                    submitPassword()
                }
            ) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
