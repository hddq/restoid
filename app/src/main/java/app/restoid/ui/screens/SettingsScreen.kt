package app.restoid.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.restoid.RestoidApplication
import app.restoid.data.AddRepositoryState
import app.restoid.data.LocalRepository
import app.restoid.data.ResticState
import app.restoid.data.RootState
import app.restoid.ui.settings.AddRepoUiState
import app.restoid.ui.settings.SettingsViewModel
import app.restoid.ui.settings.SettingsViewModelFactory

@Composable
fun SettingsScreen() {
    val application = LocalContext.current.applicationContext as RestoidApplication
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(
            application.rootRepository,
            application.resticRepository,
            application.repositoriesRepository
        )
    )

    val rootState by settingsViewModel.rootState.collectAsStateWithLifecycle()
    val resticState by settingsViewModel.resticState.collectAsStateWithLifecycle()
    val repositories by settingsViewModel.repositories.collectAsStateWithLifecycle()
    val addRepoUiState by settingsViewModel.addRepoUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            uri?.let {
                try {
                    // Persist access permissions for the selected directory.
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(it, takeFlags)

                    getPathFromTreeUri(it)?.let { path ->
                        settingsViewModel.onNewRepoPathChanged(path)
                    }
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    // Handle cases where permission couldn't be persisted.
                }
            }
        }
    )


    // Show dialog for adding a new repo when requested by the state
    if (addRepoUiState.showDialog) {
        AddRepositoryDialog(
            uiState = addRepoUiState,
            onDismiss = { settingsViewModel.onNewRepoDialogDismiss() },
            onPasswordChange = { settingsViewModel.onNewRepoPasswordChanged(it) },
            onConfirm = { settingsViewModel.addRepository() },
            onSelectPath = { directoryPickerLauncher.launch(null) }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card for Root Access status
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("System", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    AnimatedContent(targetState = rootState, label = "RootStatusAnimation") { state ->
                        when (state) {
                            RootState.Denied -> RootRequestRow(
                                text = "Root access denied",
                                buttonText = "Try Again",
                                icon = Icons.Default.Error,
                                onClick = { settingsViewModel.requestRootAccess() }
                            )
                            RootState.Checking -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Checking for root...")
                                }
                            }
                            RootState.Granted -> RootStatusRow(
                                text = "Root access granted",
                                icon = Icons.Default.CheckCircle
                            )
                        }
                    }
                }
            }
        }

        // Card for Restic Dependency management
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Dependencies", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    ResticDependencyRow(
                        state = resticState,
                        onDownloadClick = { settingsViewModel.downloadRestic() }
                    )
                }
            }
        }

        // Card for Repositories
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    // Title row is always visible, but the add button is conditional
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Backup Repositories", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        // Show "Add" button only if restic is installed
                        if (resticState is ResticState.Installed) {
                            IconButton(onClick = { settingsViewModel.onShowAddRepoDialog() }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Repository")
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))

                    // Content depends on whether restic is installed
                    if (resticState is ResticState.Installed) {
                        if (repositories.isEmpty()) {
                            Text(
                                "No repositories configured. Add one to get started.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Column {
                                repositories.forEach { repo ->
                                    RepositoryRow(repo = repo)
                                }
                            }
                        }
                    } else {
                        // Show a message asking the user to install restic first
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp),
                                tint = LocalContentColor.current.copy(alpha = 0.6f)
                            )
                            Text(
                                "Install restic to manage repositories.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RepositoryRow(repo: LocalRepository) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Storage,
            contentDescription = "Repository",
            modifier = Modifier.padding(end = 16.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Column {
            Text(repo.name, style = MaterialTheme.typography.bodyLarge)
            Text(repo.path, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun AddRepositoryDialog(
    uiState: AddRepoUiState,
    onDismiss: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onSelectPath: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Local Repository") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Path Selector Field (read-only)
                OutlinedTextField(
                    value = uiState.path,
                    onValueChange = {}, // Path is selected via picker, not typed
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

                // Password Field
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

// ResticDependencyRow, RootRequestRow, RootStatusRow are unchanged from the previous version.

@Composable
fun ResticDependencyRow(
    state: ResticState,
    onDownloadClick: () -> Unit
) {
    AnimatedContent(targetState = state, label = "ResticStatusAnimation") { targetState ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val icon = when (targetState) {
                        is ResticState.Installed -> Icons.Default.CheckCircle
                        is ResticState.Error -> Icons.Default.Error
                        else -> Icons.Default.CloudDownload
                    }
                    val iconColor = when (targetState) {
                        is ResticState.Installed -> MaterialTheme.colorScheme.primary
                        is ResticState.Error -> MaterialTheme.colorScheme.error
                        else -> LocalContentColor.current
                    }
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.padding(end = 16.dp), tint = iconColor)

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Restic Binary", style = MaterialTheme.typography.bodyLarge)
                        val supportingText = when (targetState) {
                            ResticState.Idle -> "Checking status..."
                            ResticState.NotInstalled -> "Required for backups"
                            is ResticState.Downloading -> "Downloading..."
                            ResticState.Extracting -> "Extracting binary..."
                            is ResticState.Installed -> targetState.version
                            is ResticState.Error -> "Error: ${targetState.message}"
                        }
                        Text(
                            text = supportingText,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                when (targetState) {
                    ResticState.NotInstalled, is ResticState.Error -> {
                        Button(onClick = onDownloadClick) {
                            Text(if (targetState is ResticState.Error) "Retry" else "Download")
                        }
                    }
                    ResticState.Extracting -> CircularProgressIndicator()
                    is ResticState.Downloading -> {
                        Text(
                            text = "${(targetState.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is ResticState.Installed, ResticState.Idle -> {
                        // Nothing to show here for these states
                    }
                }
            }

            if (targetState is ResticState.Downloading) {
                LinearProgressIndicator(
                    progress = { targetState.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun RootRequestRow(
    text: String,
    buttonText: String,
    icon: ImageVector?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Text(text = text)
        }
        Button(onClick = onClick) {
            Text(buttonText)
        }
    }
}

@Composable
fun RootStatusRow(text: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * A helper function to convert a tree URI from ACTION_OPEN_DOCUMENT_TREE
 * into a file path. This is a simplified implementation and mainly works for
 * primary external storage. More complex cases like SD cards may require
 * additional logic.
 */
private fun getPathFromTreeUri(treeUri: Uri): String? {
    // Check if the authority is the External Storage provider.
    if (treeUri.authority != "com.android.externalstorage.documents") {
        return null
    }

    val docId = DocumentsContract.getTreeDocumentId(treeUri)
    val split = docId.split(":")
    if (split.size > 1) {
        val type = split[0]
        val path = split[1]
        return when (type) {
            // "primary" refers to the primary shared/external storage volume.
            "primary" -> "${Environment.getExternalStorageDirectory()}/$path"
            // TODO: Handle other storage volumes (e.g., SD cards) if needed.
            // This would require iterating through storage volumes to match the type.
            else -> null
        }
    }
    return null
}
