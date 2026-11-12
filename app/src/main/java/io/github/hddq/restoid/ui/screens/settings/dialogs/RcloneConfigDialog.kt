package io.github.hddq.restoid.ui.screens.settings.dialogs

import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hddq.restoid.R
import io.github.hddq.restoid.ui.settings.SettingsViewModel

@Composable
fun RcloneConfigDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var configText by remember { mutableStateOf(viewModel.getRcloneConfigContent().orEmpty()) }
    var isImporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val initialExists = remember { viewModel.hasRcloneConfig.value }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isImporting = true
                try {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            stream.reader().readText()
                        }
                    }
                    if (text != null) {
                        configText = text
                    }
                } catch (e: Throwable) {
                    Toast.makeText(context, context.getString(R.string.rclone_config_import_error), Toast.LENGTH_SHORT).show()
                } finally {
                    isImporting = false
                }
            }
        }
    }

    val detectedRemotes = remember(configText) {
        val regex = Regex("""^\s*\[([^\]]+)\]\s*$""", RegexOption.MULTILINE)
        regex.findAll(configText).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.toList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rclone_config_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.rclone_config_dialog_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    enabled = !isImporting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.rclone_config_import_file))
                }

                if (detectedRemotes.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.rclone_config_subtitle_configured, detectedRemotes.size, detectedRemotes.joinToString(", ")),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = configText,
                    onValueChange = { configText = it },
                    label = { Text("rclone.conf") },
                    placeholder = { Text(stringResource(R.string.rclone_config_paste_placeholder)) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 320.dp)
                )

                if (initialExists) {
                    TextButton(
                        onClick = {
                            viewModel.deleteRcloneConfig()
                            configText = ""
                            Toast.makeText(context, context.getString(R.string.rclone_config_deleted), Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.rclone_config_delete))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val result = viewModel.saveRcloneConfig(configText.trim())
                    if (result.isSuccess) {
                        Toast.makeText(context, context.getString(R.string.rclone_config_saved_success), Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } else {
                        Toast.makeText(context, context.getString(R.string.rclone_config_saved_error), Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !isImporting && configText.isNotBlank()
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
