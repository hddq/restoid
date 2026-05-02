package io.github.hddq.restoid.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.hddq.restoid.R

@Composable
fun SshPrivateKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    onImportError: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: (@Composable (() -> Unit))? = null
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult

            readPrivateKeyText(context, uri)
                .onSuccess(onValueChange)
                .onFailure { error ->
                    onImportError(
                        error.message ?: context.getString(R.string.error_sftp_key_import_failed)
                    )
                }
        }
    )

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) {
            { Text(placeholder) }
        } else {
            null
        },
        minLines = 3,
        maxLines = 6,
        trailingIcon = {
            IconButton(
                onClick = { launcher.launch(arrayOf("*/*")) },
                enabled = enabled
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = stringResource(R.string.cd_select_private_key_file)
                )
            }
        },
        isError = isError,
        supportingText = supportingText,
        modifier = modifier,
        enabled = enabled
    )
}

private fun readPrivateKeyText(context: Context, uri: Uri): Result<String> {
    return runCatching {
        val imported = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            reader.readText()
        } ?: throw IllegalStateException(context.getString(R.string.error_sftp_key_import_failed))

        imported.trimEnd().ifBlank {
            throw IllegalStateException(context.getString(R.string.error_sftp_key_file_empty))
        }
    }
}
