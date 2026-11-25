package io.github.hddq.restoid.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.LocalNetworkPermissionState
import io.github.hddq.restoid.data.LocalRepository
import io.github.hddq.restoid.data.ResticState
import io.github.hddq.restoid.data.StoragePermissionState
import io.github.hddq.restoid.ui.components.FolderPickerSheet
import io.github.hddq.restoid.ui.screens.settings.AccessModeCard
import io.github.hddq.restoid.ui.screens.settings.AddRepositoryDialog
import io.github.hddq.restoid.ui.screens.settings.PermissionsSectionsContent
import io.github.hddq.restoid.ui.screens.settings.SftpServerTrustDialog
import io.github.hddq.restoid.ui.screens.settings.dialogs.RcloneConfigDialog
import io.github.hddq.restoid.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

private const val WELCOME_STEP_COUNT = 3

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WelcomeScreen(
    viewModel: SettingsViewModel,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasRoot by viewModel.hasRoot.collectAsStateWithLifecycle()
    val storagePermissionState by viewModel.storagePermissionState.collectAsStateWithLifecycle()
    val notificationPermissionState by viewModel.notificationPermissionState.collectAsStateWithLifecycle()
    val localNetworkPermissionState by viewModel.localNetworkPermissionState.collectAsStateWithLifecycle()
    val batteryOptimizationDisabled by viewModel.isIgnoringBatteryOptimizations.collectAsStateWithLifecycle()
    val deviceDataPermissions by viewModel.deviceDataPermissions.collectAsStateWithLifecycle()
    val resticState by viewModel.resticState.collectAsStateWithLifecycle()
    val repositories by viewModel.repositories.collectAsStateWithLifecycle()
    val addRepoUiState by viewModel.addRepoUiState.collectAsStateWithLifecycle()
    val hasStorageAccess by viewModel.hasStorageAccess.collectAsStateWithLifecycle()
    val showMetadataWarning by viewModel.showMetadataWarning.collectAsStateWithLifecycle()
    val metadataRestoreSuccess by viewModel.metadataRestoreSuccess.collectAsStateWithLifecycle()
    val showRcloneConfigDialog by viewModel.showRcloneConfigDialog.collectAsStateWithLifecycle()
    val hasRcloneConfig by viewModel.hasRcloneConfig.collectAsStateWithLifecycle()
    val rcloneRemotes by viewModel.rcloneRemotes.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val pagerState = rememberPagerState(pageCount = { WELCOME_STEP_COUNT })
    val coroutineScope = rememberCoroutineScope()

    var showRepoFolderPicker by remember { mutableStateOf(false) }
    var pendingNotificationRationaleBefore by remember { mutableStateOf<Boolean?>(null) }
    var pendingDeviceDataPermissionCheck by remember { mutableStateOf<Pair<Array<String>, Boolean>?>(null) }

    BackHandler(enabled = pagerState.currentPage > 0) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(pagerState.currentPage - 1)
        }
    }

    LaunchedEffect(metadataRestoreSuccess) {
        val message = metadataRestoreSuccess ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.consumeMetadataRestoreSuccess()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSystemState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val openNotificationSettings = {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        runCatching { context.startActivity(intent) }
        Unit
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.checkNotificationPermission()
        val hadRationaleBefore = pendingNotificationRationaleBefore
        pendingNotificationRationaleBefore = null
        if (!granted && hadRationaleBefore != null && activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasRationaleAfter = ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (!hadRationaleBefore && !hasRationaleAfter) {
                openNotificationSettings()
            }
        }
    }

    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { viewModel.updateLocalNetworkPermissionStatus() }
    )

    val deviceDataPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        viewModel.refreshDeviceDataPermissions()
        val allGranted = results.isNotEmpty() && results.values.all { it }
        val pending = pendingDeviceDataPermissionCheck
        pendingDeviceDataPermissionCheck = null
        if (!allGranted && pending != null && activity != null) {
            val (requestedPermissions, hadRationaleBefore) = pending
            val hasRationaleAfter = requestedPermissions.any { permission ->
                ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            }
            if (!hadRationaleBefore && !hasRationaleAfter) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                runCatching { context.startActivity(intent) }
            }
        }
    }

    if (showMetadataWarning != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissMetadataWarning() },
            title = { Text(stringResource(R.string.repo_warning_metadata_restore_failed_title)) },
            text = { Text(showMetadataWarning ?: stringResource(R.string.repo_warning_metadata_restore_failed)) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissMetadataWarning() }) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        )
    }

    if (addRepoUiState.showDialog) {
        AddRepositoryDialog(
            uiState = addRepoUiState,
            hasRcloneConfig = hasRcloneConfig,
            rcloneRemotes = rcloneRemotes,
            onConfigureRclone = { viewModel.onShowRcloneConfigDialog() },
            onDismiss = { viewModel.onNewRepoDialogDismiss() },
            onBackendTypeChange = { viewModel.onNewRepoBackendTypeChanged(it) },
            onPathChange = { viewModel.onNewRepoPathChanged(it) },
            onSftpHostChange = { viewModel.onNewRepoSftpHostChanged(it) },
            onSftpPortChange = { viewModel.onNewRepoSftpPortChanged(it) },
            onSftpUsernameChange = { viewModel.onNewRepoSftpUsernameChanged(it) },
            onSftpPathChange = { viewModel.onNewRepoSftpPathChanged(it) },
            onPasswordChange = { viewModel.onNewRepoPasswordChanged(it) },
            onSftpPasswordChange = { viewModel.onNewRepoSftpPasswordChanged(it) },
            onSftpKeyChange = { viewModel.onNewRepoSftpKeyChanged(it) },
            onSftpKeyImportError = { viewModel.onNewRepoSftpKeyImportError(it) },
            onSftpKeyPassphraseChange = { viewModel.onNewRepoSftpKeyPassphraseChanged(it) },
            onSftpAuthMethodChange = { viewModel.onNewRepoSftpAuthMethodChanged(it) },
            onS3AccessKeyIdChange = { viewModel.onNewRepoS3AccessKeyIdChanged(it) },
            onS3SecretAccessKeyChange = { viewModel.onNewRepoS3SecretAccessKeyChanged(it) },
            onRestUsernameChange = { viewModel.onNewRepoRestUsernameChanged(it) },
            onRestPasswordChange = { viewModel.onNewRepoRestPasswordChanged(it) },
            onEnvironmentVariablesChange = { viewModel.onNewRepoEnvironmentVariablesChanged(it) },
            onSavePasswordChange = { viewModel.onSavePasswordChanged(it) },
            onConfirm = { viewModel.addRepository() },
            onSelectPath = { showRepoFolderPicker = true },
            hasStorageAccess = hasStorageAccess
        )
    }

    if (showRepoFolderPicker) {
        FolderPickerSheet(
            initialPath = addRepoUiState.path.takeIf { it.isNotBlank() },
            onSelectPath = { path ->
                showRepoFolderPicker = false
                viewModel.onNewRepoPathChanged(path)
            },
            onDismissRequest = { showRepoFolderPicker = false }
        )
    }

    addRepoUiState.sftpServerTrustInfo?.let { trustInfo ->
        SftpServerTrustDialog(
            trustInfo = trustInfo,
            onConfirm = { viewModel.onSftpTrustDialogConfirm() },
            onDismiss = { viewModel.onSftpTrustDialogDismiss() }
        )
    }

    if (showRcloneConfigDialog) {
        RcloneConfigDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.onDismissRcloneConfigDialog() }
        )
    }

    val showStorageRow = storagePermissionState != StoragePermissionState.NotRequested
    val showNotificationRow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val showLocalNetworkRow = Build.VERSION.SDK_INT >= 37 &&
        localNetworkPermissionState != LocalNetworkPermissionState.NotRequested
    val isLastPage = pagerState.currentPage == WELCOME_STEP_COUNT - 1

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            WelcomeBottomBar(
                currentPage = pagerState.currentPage,
                pageCount = WELCOME_STEP_COUNT,
                isLastPage = isLastPage,
                hasRepository = repositories.isNotEmpty(),
                onBack = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                    }
                },
                onNext = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(WELCOME_STEP_COUNT - 1))
                    }
                },
                onFinish = onFinish
            )
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { page ->
            when (page) {
                0 -> WelcomeIntroStep(
                    hasRoot = hasRoot,
                    onRefreshRoot = { viewModel.refreshRootAccess() }
                )
                1 -> WelcomePermissionsStep(
                    hasRoot = hasRoot,
                    storagePermissionState = storagePermissionState,
                    notificationPermissionState = notificationPermissionState,
                    localNetworkPermissionState = localNetworkPermissionState,
                    batteryOptimizationDisabled = batteryOptimizationDisabled,
                    deviceDataPermissions = deviceDataPermissions,
                    showStorageRow = showStorageRow,
                    showNotificationRow = showNotificationRow,
                    showLocalNetworkRow = showLocalNetworkRow,
                    onRequestStorage = {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        }
                        runCatching { context.startActivity(intent) }
                    },
                    onRequestNotification = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pendingNotificationRationaleBefore = activity?.let {
                                ActivityCompat.shouldShowRequestPermissionRationale(
                                    it,
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            }
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onOpenNotificationSettings = openNotificationSettings,
                    onRequestBatteryUnrestricted = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        runCatching { context.startActivity(intent) }
                            .onFailure {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                }
                            }
                    },
                    onRequestLocalNetwork = {
                        localNetworkPermissionLauncher.launch("android.permission.ACCESS_LOCAL_NETWORK")
                    },
                    onRequestDeviceDataPermissions = { permissions ->
                        val hadRationaleBefore = activity != null && permissions.any { permission ->
                            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
                        }
                        pendingDeviceDataPermissionCheck = permissions to hadRationaleBefore
                        deviceDataPermissionLauncher.launch(permissions)
                    }
                )
                else -> WelcomeRepositoryStep(
                    resticInstalled = resticState is ResticState.Installed,
                    repositories = repositories,
                    onAddRepository = { viewModel.onShowAddRepoDialog() }
                )
            }
        }
    }
}

@Composable
private fun WelcomeIntroStep(
    hasRoot: Boolean,
    onRefreshRoot: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(88.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(64.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        AccessModeCard(
            hasRoot = hasRoot,
            onRefreshRoot = onRefreshRoot
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.welcome_access_mode_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WelcomePermissionsStep(
    hasRoot: Boolean,
    storagePermissionState: StoragePermissionState,
    notificationPermissionState: io.github.hddq.restoid.data.NotificationPermissionState,
    localNetworkPermissionState: LocalNetworkPermissionState,
    batteryOptimizationDisabled: Boolean,
    deviceDataPermissions: io.github.hddq.restoid.ui.settings.DeviceDataPermissionsState,
    showStorageRow: Boolean,
    showNotificationRow: Boolean,
    showLocalNetworkRow: Boolean,
    onRequestStorage: () -> Unit,
    onRequestNotification: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRequestBatteryUnrestricted: () -> Unit,
    onRequestLocalNetwork: () -> Unit,
    onRequestDeviceDataPermissions: (Array<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.welcome_permissions_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.welcome_permissions_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        PermissionsSectionsContent(
            hasRoot = hasRoot,
            storagePermissionState = storagePermissionState,
            notificationPermissionState = notificationPermissionState,
            localNetworkPermissionState = localNetworkPermissionState,
            batteryOptimizationDisabled = batteryOptimizationDisabled,
            deviceDataPermissions = deviceDataPermissions,
            showStorageRow = showStorageRow,
            showNotificationRow = showNotificationRow,
            showLocalNetworkRow = showLocalNetworkRow,
            onRequestStorage = onRequestStorage,
            onRequestNotification = onRequestNotification,
            onOpenNotificationSettings = onOpenNotificationSettings,
            onRequestBatteryUnrestricted = onRequestBatteryUnrestricted,
            onRequestLocalNetwork = onRequestLocalNetwork,
            onRequestDeviceDataPermissions = onRequestDeviceDataPermissions,
            preferRuntimeNotificationRequest = true
        )
    }
}

@Composable
private fun WelcomeRepositoryStep(
    resticInstalled: Boolean,
    repositories: List<LocalRepository>,
    onAddRepository: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.welcome_repo_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.welcome_repo_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        if (repositories.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(end = 12.dp, top = 2.dp)
                            .size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.welcome_repo_ready_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = repositories.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.welcome_repo_ready_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        } else if (resticInstalled) {
            FilledTonalButton(
                onClick = onAddRepository,
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.cd_add_repository))
            }
        } else {
            Text(
                text = stringResource(R.string.restic_not_available_check_settings),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WelcomeBottomBar(
    currentPage: Int,
    pageCount: Int,
    isLastPage: Boolean,
    hasRepository: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motionScheme = MaterialTheme.motionScheme
    val indicatorWidthSpec = motionScheme.fastSpatialSpec<androidx.compose.ui.unit.Dp>()
    val indicatorColorSpec = motionScheme.fastEffectsSpec<androidx.compose.ui.graphics.Color>()

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pageCount) { index ->
                    val isSelected = index == currentPage
                    val width by animateDpAsState(
                        targetValue = if (isSelected) 24.dp else 8.dp,
                        animationSpec = indicatorWidthSpec,
                        label = "welcomeIndicatorWidth_$index"
                    )
                    val color by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        animationSpec = indicatorColorSpec,
                        label = "welcomeIndicatorColor_$index"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentPage > 0) {
                    TextButton(onClick = onBack) {
                        Text(text = stringResource(R.string.welcome_action_back))
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (isLastPage) {
                    if (hasRepository) {
                        Button(
                            onClick = onFinish,
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            Text(text = stringResource(R.string.welcome_action_finish))
                        }
                    } else {
                        TextButton(onClick = onFinish) {
                            Text(text = stringResource(R.string.welcome_action_skip))
                        }
                    }
                } else {
                    Button(
                        onClick = onNext,
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Text(text = stringResource(R.string.welcome_action_next))
                    }
                }
            }
        }
    }
}
