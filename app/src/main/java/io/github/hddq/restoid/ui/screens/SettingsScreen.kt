package io.github.hddq.restoid.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.hddq.restoid.RestoidApplication
import io.github.hddq.restoid.ui.screens.settings.AboutSettings
import io.github.hddq.restoid.ui.screens.settings.AddRepositoryDialog
import io.github.hddq.restoid.ui.screens.settings.DependencySettings
import io.github.hddq.restoid.ui.screens.settings.RepositorySettings
import io.github.hddq.restoid.ui.screens.settings.SystemSettings
import io.github.hddq.restoid.ui.settings.SettingsViewModel
import io.github.hddq.restoid.ui.settings.SettingsViewModelFactory

@Composable
fun SettingsScreen(onNavigateToLicenses: () -> Unit, modifier: Modifier = Modifier) {
    val application = LocalContext.current.applicationContext as RestoidApplication
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(
            application.rootRepository,
            application.resticRepository,
            application.repositoriesRepository,
            application.notificationRepository
        )
    )

    val addRepoUiState by settingsViewModel.addRepoUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                settingsViewModel.checkNotificationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            uri?.let {
                try {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(it, takeFlags)

                    getPathFromTreeUri(it)?.let { path ->
                        settingsViewModel.onNewRepoPathChanged(path)
                    }
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }
            }
        }
    )

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            settingsViewModel.checkNotificationPermission()
        }
    )

    if (addRepoUiState.showDialog) {
        AddRepositoryDialog(
            uiState = addRepoUiState,
            onDismiss = { settingsViewModel.onNewRepoDialogDismiss() },
            onPasswordChange = { settingsViewModel.onNewRepoPasswordChanged(it) },
            onSavePasswordChange = { settingsViewModel.onSavePasswordChanged(it) },
            onConfirm = { settingsViewModel.addRepository() },
            onSelectPath = { directoryPickerLauncher.launch(null) }
        )
    }

    // Apply the modifier passed from NavHost here
    LazyColumn(
        modifier = modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp), // Add padding to the content itself
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SystemSettings(
                viewModel = settingsViewModel,
                notificationPermissionLauncher = {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                onOpenSettings = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                }
            )
        }
        item {
            DependencySettings(viewModel = settingsViewModel)
        }
        item {
            RepositorySettings(viewModel = settingsViewModel)
        }
        item {
            AboutSettings(onNavigateToLicenses = onNavigateToLicenses)
        }
    }
}

private fun getPathFromTreeUri(treeUri: Uri): String? {
    if (treeUri.authority != "com.android.externalstorage.documents") {
        return null
    }

    val docId = DocumentsContract.getTreeDocumentId(treeUri)
    val split = docId.split(":")
    if (split.size > 1) {
        val type = split[0]
        val path = split[1]
        return when (type) {
            "primary" -> "${Environment.getExternalStorageDirectory()}/$path"
            else -> null
        }
    }
    return null
}
