package io.github.hddq.restoid.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.topjohnwu.superuser.Shell
import io.github.hddq.restoid.R
import io.github.hddq.restoid.util.StorageUtils
import io.github.hddq.restoid.util.StorageVolumeItem
import io.github.hddq.restoid.util.shellQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class FolderBrowserViewState(
    val atRoots: Boolean,
    val path: String,
    val entries: List<String>?,
    val isLoading: Boolean,
    val depth: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(
    initialPath: String? = null,
    onSelectPath: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var storageVolumes by remember { mutableStateOf<List<StorageVolumeItem>>(emptyList()) }
    val normalizedInitial = remember(initialPath) {
        initialPath
            ?.let { StorageUtils.resolveCustomDirectoryPath(it) }
            ?.takeIf { File(it).isDirectory }
    }

    var targetPath by remember { mutableStateOf(normalizedInitial ?: "/storage/emulated/0") }
    var targetAtRoots by remember { mutableStateOf(normalizedInitial == null) }
    var showHidden by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    var viewState by remember {
        mutableStateOf(
            FolderBrowserViewState(
                atRoots = normalizedInitial == null,
                path = normalizedInitial ?: "/storage/emulated/0",
                entries = emptyList(),
                isLoading = true,
                depth = if (normalizedInitial == null) 0 else normalizedInitial.count { it == '/' }
            )
        )
    }

    var showManualPathField by remember { mutableStateOf(false) }
    var manualPathInput by remember { mutableStateOf(targetPath) }
    var manualPathError by remember { mutableStateOf<String?>(null) }

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var newFolderError by remember { mutableStateOf<String?>(null) }

    val invalidPathMsg = stringResource(R.string.custom_directories_typed_path_invalid)
    val notFoundTemplate = stringResource(R.string.custom_directories_typed_path_not_found)
    val createFailedMsg = stringResource(R.string.folder_browser_create_failed)

    LaunchedEffect(Unit) {
        val loadedVolumes = withContext(Dispatchers.IO) {
            StorageUtils.listStorageVolumes()
        }
        storageVolumes = loadedVolumes
        if (normalizedInitial == null) {
            if (loadedVolumes.size == 1) {
                targetPath = loadedVolumes.first().path
                targetAtRoots = false
            } else if (loadedVolumes.isNotEmpty()) {
                targetPath = loadedVolumes.first().path
                targetAtRoots = true
            }
        }
    }

    LaunchedEffect(targetPath, showHidden, targetAtRoots, refreshTrigger, storageVolumes) {
        if (targetAtRoots) {
            viewState = FolderBrowserViewState(
                atRoots = true,
                path = targetPath,
                entries = emptyList(),
                isLoading = storageVolumes.isEmpty(),
                depth = 0
            )
            return@LaunchedEffect
        }

        val loadingJob = launch {
            delay(180)
            viewState = viewState.copy(
                atRoots = false,
                path = targetPath,
                isLoading = true,
                depth = targetPath.count { it == '/' }
            )
        }

        val loadedEntries = withContext(Dispatchers.IO) {
            StorageUtils.listChildDirectories(targetPath, showHidden)
        }
        loadingJob.cancel()

        viewState = FolderBrowserViewState(
            atRoots = false,
            path = targetPath,
            entries = loadedEntries,
            isLoading = false,
            depth = targetPath.count { it == '/' }
        )
    }

    val currentPath = viewState.path
    val atRoots = viewState.atRoots

    val navigateToManualPath = {
        val normalized = StorageUtils.normalizeDirectoryPath(manualPathInput)
        if (normalized == null) {
            manualPathError = invalidPathMsg
        } else {
            scope.launch {
                val exists = withContext(Dispatchers.IO) {
                    File(normalized).isDirectory ||
                        runCatching {
                            Shell.getShell().isRoot &&
                                Shell.cmd("[ -d ${shellQuote(normalized)} ]").exec().isSuccess
                        }.getOrDefault(false)
                }
                if (!exists) {
                    manualPathError = notFoundTemplate.format(normalized)
                } else {
                    manualPathError = null
                    targetPath = normalized
                    targetAtRoots = false
                    showManualPathField = false
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                )
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (atRoots) {
                            stringResource(R.string.folder_browser_roots_title)
                        } else {
                            currentPath.substringAfterLast('/').ifEmpty { currentPath }
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    AnimatedVisibility(
                        visible = !atRoots,
                        enter = fadeIn(tween(200)) + expandVertically(tween(220)),
                        exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
                    ) {
                        Text(
                            text = currentPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedVisibility(
                        visible = !atRoots,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(150))
                    ) {
                        IconButton(
                            onClick = {
                                newFolderName = ""
                                newFolderError = null
                                showNewFolderDialog = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = stringResource(R.string.folder_browser_new_folder),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            if (!showManualPathField) {
                                manualPathInput = if (atRoots) {
                                    storageVolumes.firstOrNull()?.path ?: "/storage/emulated/0"
                                } else {
                                    currentPath
                                }
                                manualPathError = null
                            }
                            showManualPathField = !showManualPathField
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.folder_browser_edit_path),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showManualPathField,
                enter = fadeIn(tween(200)) + expandVertically(tween(220)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = manualPathInput,
                            onValueChange = {
                                manualPathInput = it
                                manualPathError = null
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.custom_directories_typed_path_label)) },
                            placeholder = { Text(stringResource(R.string.custom_directories_typed_path_hint)) },
                            supportingText = manualPathError?.let { err -> { Text(err) } },
                            isError = manualPathError != null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { navigateToManualPath() })
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { navigateToManualPath() }) {
                            Text(stringResource(R.string.action_go))
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !atRoots,
                enter = fadeIn(tween(200)) + expandVertically(tween(220)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showHidden = !showHidden }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val showHiddenLabel = stringResource(R.string.folder_browser_show_hidden)
                        Text(
                            text = showHiddenLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = showHidden,
                            onCheckedChange = { showHidden = it },
                            modifier = Modifier.semantics {
                                contentDescription = showHiddenLabel
                            }
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            if (atRoots) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            val isAtStorageRoot = storageVolumes.any { it.path == currentPath }
            val parentPath = remember(currentPath) { File(currentPath).parent }
            val canNavigateUp = !atRoots && (isAtStorageRoot || parentPath != null || storageVolumes.isNotEmpty())

            AnimatedVisibility(
                visible = canNavigateUp,
                enter = fadeIn(tween(200)) + expandVertically(tween(220)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isAtStorageRoot && storageVolumes.size > 1) {
                                    targetAtRoots = true
                                } else if (parentPath != null) {
                                    scope.launch {
                                        val parentListable = withContext(Dispatchers.IO) {
                                            StorageUtils.listChildDirectories(parentPath, showHidden) != null
                                        }
                                        if (parentListable) {
                                            targetPath = parentPath
                                        } else if (storageVolumes.isNotEmpty()) {
                                            targetAtRoots = true
                                        }
                                    }
                                } else if (storageVolumes.isNotEmpty()) {
                                    targetAtRoots = true
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = if (isAtStorageRoot && storageVolumes.size > 1) {
                                stringResource(R.string.folder_browser_roots_title)
                            } else {
                                stringResource(R.string.folder_browser_up)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            AnimatedContent(
                targetState = viewState,
                transitionSpec = {
                    val goingDeeper = targetState.depth > initialState.depth
                    val enterSlide = slideInHorizontally(
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                        initialOffsetX = { fullWidth -> if (goingDeeper) fullWidth / 5 else -fullWidth / 5 }
                    ) + fadeIn(tween(220))
                    val exitSlide = slideOutHorizontally(
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        targetOffsetX = { fullWidth -> if (goingDeeper) -fullWidth / 5 else fullWidth / 5 }
                    ) + fadeOut(tween(160))

                    (enterSlide togetherWith exitSlide).using(
                        SizeTransform(
                            clip = true,
                            sizeAnimationSpec = { _, _ ->
                                tween(durationMillis = 260, easing = FastOutSlowInEasing)
                            }
                        )
                    )
                },
                label = "FolderBrowserListTransition"
            ) { state ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    when {
                        state.isLoading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        state.atRoots -> {
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                itemsIndexed(storageVolumes, key = { _, item -> item.path }) { index, volume ->
                                    val title = if (volume.isPrimary) {
                                        stringResource(R.string.folder_browser_internal_storage)
                                    } else {
                                        stringResource(R.string.folder_browser_external_storage, volume.name)
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                targetPath = volume.path
                                                targetAtRoots = false
                                            }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (volume.isPrimary) {
                                                Icons.Default.Smartphone
                                            } else {
                                                Icons.Default.SdStorage
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = title,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = volume.path,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (index < storageVolumes.size - 1) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }

                        state.entries == null -> {
                            Text(
                                text = stringResource(R.string.folder_browser_cannot_list),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
                        }

                        state.entries.isEmpty() -> {
                            Text(
                                text = stringResource(R.string.folder_browser_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
                        }

                        else -> {
                            val currentEntries = state.entries
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                itemsIndexed(currentEntries, key = { _, name -> name }) { index, name ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                targetPath = File(state.path, name).absolutePath
                                            }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (index < currentEntries.size - 1) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !atRoots,
                enter = fadeIn(tween(200)) + expandVertically(tween(220)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onSelectPath(currentPath) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.folder_browser_select_this_folder))
                    }
                }
            }
        }
    }

    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text(stringResource(R.string.folder_browser_new_folder)) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = {
                        newFolderName = it
                        newFolderError = null
                    },
                    label = { Text(stringResource(R.string.folder_browser_new_folder_name)) },
                    supportingText = newFolderError?.let { err -> { Text(err) } },
                    isError = newFolderError != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = newFolderName.trim().isNotEmpty() && !newFolderName.contains('/'),
                    onClick = {
                        val trimmed = newFolderName.trim()
                        val targetDir = File(currentPath, trimmed)
                        scope.launch {
                            val created = withContext(Dispatchers.IO) {
                                runCatching { targetDir.mkdirs() || targetDir.isDirectory }.getOrDefault(false) ||
                                    runCatching {
                                        Shell.getShell().isRoot &&
                                            Shell.cmd("mkdir -p ${shellQuote(targetDir.absolutePath)}").exec().isSuccess
                                    }.getOrDefault(false)
                            }
                            if (created) {
                                showNewFolderDialog = false
                                targetPath = targetDir.absolutePath
                                refreshTrigger++
                            } else {
                                newFolderError = createFailedMsg
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
