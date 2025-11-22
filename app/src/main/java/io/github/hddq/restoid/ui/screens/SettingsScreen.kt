package io.github.hddq.restoid.ui.screens

import android.Manifest
import android.content.Intent
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hddq.restoid.ui.screens.settings.*
import io.github.hddq.restoid.ui.settings.SettingsViewModel
import io.github.hddq.restoid.util.StorageUtils

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel, // Passed directly
    onNavigateToLicenses: () -> Unit,
    modifier: Modifier = Modifier
) {
    val addRepoUiState by viewModel.addRepoUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkNotificationPermission()
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
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(it, takeFlags)
                    StorageUtils.getPathFromTreeUri(it)?.let { path ->
                        viewModel.onNewRepoPathChanged(path)
                    }
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }
            }
        }
    )

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { viewModel.checkNotificationPermission() }
    )

    if (addRepoUiState.showDialog) {
        AddRepositoryDialog(
            uiState = addRepoUiState,
            onDismiss = { viewModel.onNewRepoDialogDismiss() },
            onPasswordChange = { viewModel.onNewRepoPasswordChanged(it) },
            onSavePasswordChange = { viewModel.onSavePasswordChanged(it) },
            onConfirm = { viewModel.addRepository() },
            onSelectPath = { directoryPickerLauncher.launch(null) }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SystemSettings(
                viewModel = viewModel,
                notificationPermissionLauncher = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                onOpenSettings = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                }
            )
        }
        item { DependencySettings(viewModel = viewModel) }
        item { RepositorySettings(viewModel = viewModel) }
        item { AboutSettings(onNavigateToLicenses = onNavigateToLicenses) }
    }
}