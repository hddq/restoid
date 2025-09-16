package app.restoid.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import app.restoid.RestoidApplication
import app.restoid.model.AppInfo
import app.restoid.model.BackupDetail
import app.restoid.ui.restore.RestoreProgress
import app.restoid.ui.restore.RestoreTypes
import app.restoid.ui.restore.RestoreViewModel
import app.restoid.ui.restore.RestoreViewModelFactory
import coil.compose.rememberAsyncImagePainter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreScreen(navController: NavController, snapshotId: String?) {
    val application = LocalContext.current.applicationContext as RestoidApplication
    val viewModel: RestoreViewModel = viewModel(
        factory = RestoreViewModelFactory(
            application,
            application.repositoriesRepository,
            application.resticRepository,
            application.appInfoRepository,
            application.notificationRepository,
            snapshotId ?: ""
        )
    )

    val backupDetails by viewModel.backupDetails.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val restoreTypes by viewModel.restoreTypes.collectAsState()
    val isRestoring by viewModel.isRestoring.collectAsState()
    val restoreProgress by viewModel.restoreProgress.collectAsState()

    val showProgressScreen = isRestoring || restoreProgress.isFinished

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showProgressScreen) "Restore Progress" else "Restore Snapshot") },
                navigationIcon = {
                    if (!isRestoring) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!showProgressScreen) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.startRestore() },
                    icon = { Icon(Icons.Default.Restore, contentDescription = "Start Restore") },
                    text = { Text("Start Restore") }
                )
            }
        }
    ) { paddingValues ->
        Crossfade(targetState = showProgressScreen, label = "RestoreScreenCrossfade") { showProgress ->
            if (showProgress) {
                RestoreProgressContent(
                    progress = restoreProgress,
                    onDone = {
                        viewModel.onDone()
                        navController.popBackStack()
                    },
                    modifier = Modifier.padding(paddingValues)
                )
            } else {
                RestoreSelectionContent(
                    paddingValues = paddingValues,
                    backupDetails = backupDetails,
                    isLoading = isLoading,
                    restoreTypes = restoreTypes,
                    onToggleApp = viewModel::toggleRestoreAppSelection,
                    onToggleAll = viewModel::toggleAllRestoreSelection,
                    onToggleRestoreType = { type, value ->
                        when (type) {
                            "APK" -> viewModel.setRestoreApk(value)
                            "Data" -> viewModel.setRestoreData(value)
                            "Device Protected Data" -> viewModel.setRestoreDeviceProtectedData(value)
                            "External Data" -> viewModel.setRestoreExternalData(value)
                            "OBB Data" -> viewModel.setRestoreObb(value)
                            "Media Data" -> viewModel.setRestoreMedia(value)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun RestoreProgressContent(progress: RestoreProgress, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (progress.isFinished) {
            val icon = if (progress.error == null) Icons.Default.CheckCircle else Icons.Default.Error
            val iconColor = if (progress.error == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = iconColor
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (progress.error == null) "Restore Complete" else "Restore Failed",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = progress.finalSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onDone) {
                Text("Done")
            }
        } else {
            // In-Progress State is simpler for restore for now
            Text(text = progress.currentAction, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(modifier = Modifier.size(64.dp))
        }
    }
}


@Composable
fun RestoreSelectionContent(
    paddingValues: PaddingValues,
    backupDetails: List<BackupDetail>,
    isLoading: Boolean,
    restoreTypes: RestoreTypes,
    onToggleApp: (String) -> Unit,
    onToggleAll: () -> Unit,
    onToggleRestoreType: (String, Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceBright
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Restore Types",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    RestoreTypeToggle("APK", checked = restoreTypes.apk) { onToggleRestoreType("APK", it) }
                    RestoreTypeToggle("Data", checked = restoreTypes.data) { onToggleRestoreType("Data", it) }
                    RestoreTypeToggle("Device Protected Data", checked = restoreTypes.deviceProtectedData) { onToggleRestoreType("Device Protected Data", it) }
                    RestoreTypeToggle("External Data", checked = restoreTypes.externalData) { onToggleRestoreType("External Data", it) }
                    RestoreTypeToggle("OBB Data", checked = restoreTypes.obb) { onToggleRestoreType("OBB Data", it) }
                    RestoreTypeToggle("Media Data", checked = restoreTypes.media) { onToggleRestoreType("Media Data", it) }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Apps to Restore",
                    style = MaterialTheme.typography.titleMedium
                )
                FilledTonalButton(onClick = onToggleAll) {
                    Text("Toggle All")
                }
            }
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (backupDetails.isEmpty() && !isLoading) {
            item {
                Text(
                    "No app data found in this snapshot.",
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(backupDetails, key = { it.appInfo.packageName }) { detail ->
                RestoreAppListItem(app = detail.appInfo) { onToggleApp(detail.appInfo.packageName) }
            }
        }
    }
}

@Composable
fun RestoreTypeToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun RestoreAppListItem(app: AppInfo, onToggle: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = rememberAsyncImagePainter(model = app.icon),
                contentDescription = app.name,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = app.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = app.isSelected,
                onCheckedChange = { onToggle() }
            )
        }
    }
}
