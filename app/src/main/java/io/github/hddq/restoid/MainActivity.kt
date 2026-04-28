package io.github.hddq.restoid

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.hddq.restoid.ui.home.HomeViewModel
import io.github.hddq.restoid.ui.home.HomeViewModelFactory
import io.github.hddq.restoid.ui.restore.RestoreViewModel
import io.github.hddq.restoid.ui.restore.RestoreViewModelFactory
import io.github.hddq.restoid.ui.runtasks.*
import io.github.hddq.restoid.ui.screens.*
import io.github.hddq.restoid.ui.settings.SettingsViewModel
import io.github.hddq.restoid.ui.settings.SettingsViewModelFactory
import io.github.hddq.restoid.ui.snapshot.SnapshotDetailsViewModel
import io.github.hddq.restoid.ui.snapshot.SnapshotDetailsViewModelFactory
import io.github.hddq.restoid.ui.theme.RestoidTheme
import io.github.hddq.restoid.data.NotificationRepository

class MainActivity : FragmentActivity() {
    private companion object {
        const val STATE_APP_UNLOCKED = "state_app_unlocked"
        const val APP_UNLOCK_BACKGROUND_TIMEOUT_MS = 60_000L
    }

    private var isAppUnlocked = false
    private var isContentInitialized = false
    private var backgroundedAtElapsedRealtimeMs: Long? = null
    private var isAuthenticationInProgress = false
    private var openOperationProgressOnLaunch by mutableStateOf(false)

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                isAppUnlocked = false
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext as RestoidApplication
        isAppUnlocked = savedInstanceState?.getBoolean(STATE_APP_UNLOCKED) ?: false
        openOperationProgressOnLaunch = consumeOpenOperationProgressFlag(intent)
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        enableEdgeToEdge()

        if (app.preferencesRepository.loadRequireAppUnlock() && !isAppUnlocked) {
            authenticateAndLaunch(app)
            return
        }

        launchAppContent(app)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_APP_UNLOCKED, isAppUnlocked)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()

        val app = applicationContext as RestoidApplication
        if (!app.preferencesRepository.loadRequireAppUnlock()) {
            backgroundedAtElapsedRealtimeMs = null
            return
        }

        if (!isAppUnlocked) {
            authenticateAndLaunch(app)
            return
        }

        val backgroundedAt = backgroundedAtElapsedRealtimeMs
        backgroundedAtElapsedRealtimeMs = null
        if (backgroundedAt == null) {
            return
        }

        val timeInBackgroundMs = SystemClock.elapsedRealtime() - backgroundedAt
        if (timeInBackgroundMs >= APP_UNLOCK_BACKGROUND_TIMEOUT_MS) {
            isAppUnlocked = false
            authenticateAndLaunch(app)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(screenOffReceiver)
        super.onDestroy()
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            backgroundedAtElapsedRealtimeMs = SystemClock.elapsedRealtime()
        }
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (consumeOpenOperationProgressFlag(intent)) {
            openOperationProgressOnLaunch = true
        }
    }

    private fun consumeOpenOperationProgressFlag(sourceIntent: Intent?): Boolean {
        if (sourceIntent == null) return false
        val shouldOpen = sourceIntent.getBooleanExtra(NotificationRepository.EXTRA_OPEN_OPERATION_PROGRESS, false)
        if (shouldOpen) {
            sourceIntent.removeExtra(NotificationRepository.EXTRA_OPEN_OPERATION_PROGRESS)
        }
        setIntent(sourceIntent)
        return shouldOpen
    }

    private fun authenticateAndLaunch(app: RestoidApplication) {
        if (isAuthenticationInProgress) {
            return
        }

        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val canAuthenticate = BiometricManager.from(this).canAuthenticate(authenticators)

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            isAppUnlocked = true
            launchAppContent(app)
            return
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_unlock_prompt_title))
            .setSubtitle(getString(R.string.app_unlock_prompt_subtitle))
            .setAllowedAuthenticators(authenticators)
            .build()

        val biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isAuthenticationInProgress = false
                    isAppUnlocked = true
                    launchAppContent(app)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    isAuthenticationInProgress = false
                    if (!isFinishing && !isDestroyed) {
                        finish()
                    }
                }
            }
        )

        isAuthenticationInProgress = true
        biometricPrompt.authenticate(promptInfo)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun launchAppContent(app: RestoidApplication) {
        if (isContentInitialized) {
            return
        }
        isContentInitialized = true

        setContent {
            RestoidTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                val showBottomBar = currentDestination?.route in listOf(Screen.Home.route, Screen.Settings.route)
                val homeViewModel: HomeViewModel = viewModel(
                    factory = HomeViewModelFactory(app, app.repositoriesRepository, app.resticBinaryManager, app.resticRepository, app.appInfoRepository, app.metadataRepository)
                )
                val homeUiState by homeViewModel.uiState.collectAsState()

                if (openOperationProgressOnLaunch) {
                    androidx.compose.runtime.LaunchedEffect(openOperationProgressOnLaunch) {
                        navController.navigate(Screen.OperationProgress.route) {
                            launchSingleTop = true
                        }
                        openOperationProgressOnLaunch = false
                    }
                }

                Scaffold(
                    topBar = {
                        if (!showBottomBar) {
                            TopAppBar(
                                title = {
                                    val titleRes = when {
                                        currentDestination?.route == RunTasksRoutes.Main -> R.string.topbar_run_tasks
                                        currentDestination?.route == RunTasksRoutes.BackupConfig -> R.string.topbar_backup_config
                                        currentDestination?.route == RunTasksRoutes.ForgetConfig -> R.string.topbar_forget_config
                                        currentDestination?.route == RunTasksRoutes.CheckConfig -> R.string.topbar_check_config
                                        currentDestination?.route == Screen.OperationProgress.route -> R.string.topbar_operation_progress
                                        currentDestination?.route == Screen.Licenses.route -> R.string.topbar_open_source_licenses
                                        currentDestination?.route?.startsWith(Screen.SnapshotDetails.route) == true -> R.string.topbar_snapshot_details
                                        currentDestination?.route?.startsWith(Screen.Restore.route) == true -> R.string.topbar_restore_snapshot
                                        else -> null
                                    }
                                    titleRes?.let { Text(stringResource(it)) }
                                },
                                navigationIcon = {
                                    IconButton(onClick = {
                                        if (currentDestination?.route == Screen.OperationProgress.route) {
                                            this@MainActivity.onBackPressedDispatcher.onBackPressed()
                                        } else {
                                            navController.navigateUp()
                                        }
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                                    }
                                },
                                actions = {
                                    if (currentDestination?.route?.startsWith(Screen.SnapshotDetails.route) == true) {
                                        val viewModel: SnapshotDetailsViewModel = viewModel(
                                            viewModelStoreOwner = navBackStackEntry!!,
                                            factory = SnapshotDetailsViewModelFactory(app, app.repositoriesRepository, app.resticRepository, app.appInfoRepository, app.metadataRepository)
                                        )
                                        IconButton(onClick = { viewModel.onForgetSnapshot() }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.cd_forget_snapshot),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    },
                    bottomBar = {
                        if (showBottomBar) {
                            val items = listOf(Screen.Home to Icons.Default.Home, Screen.Settings to Icons.Default.Settings)
                            NavigationBar {
                                items.forEach { (screen, icon) ->
                                    NavigationBarItem(
                                        icon = { Icon(icon, contentDescription = null) },
                                        label = { Text(stringResource(screen.titleRes)) },
                                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                        onClick = {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    },
                    floatingActionButton = {
                        when (currentDestination?.route) {
                            Screen.Home.route -> {
                                val isRepoReady = homeUiState.isRepoReady
                                ExtendedFloatingActionButton(
                                    text = { Text(stringResource(R.string.fab_run_tasks)) },
                                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.fab_run_tasks)) },
                                    onClick = { if (isRepoReady) { navController.navigate(Screen.RunTasks.route) } },
                                    containerColor = if (isRepoReady) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                    contentColor = if (isRepoReady) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                            RunTasksRoutes.Main -> {
                                val currentEntry = navBackStackEntry ?: return@Scaffold
                                val parentEntry = remember(currentEntry) {
                                    navController.getBackStackEntry(Screen.RunTasks.route)
                                }
                                val viewModel: RunTasksViewModel = viewModel(
                                    viewModelStoreOwner = parentEntry,
                                    factory = RunTasksViewModelFactory(
                                        app,
                                        app.repositoriesRepository,
                                        app.resticBinaryManager,
                                        app.appInfoRepository,
                                        app.preferencesRepository,
                                        app.operationWorkRepository
                                    )
                                )
                                val uiState by viewModel.uiState.collectAsState()
                                if (!uiState.isRunning) {
                                    ExtendedFloatingActionButton(
                                        onClick = { viewModel.run() },
                                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.fab_run_tasks)) },
                                        text = { Text(stringResource(R.string.fab_run_tasks)) }
                                    )
                                }
                            }
                            Screen.SnapshotDetails.route + "/{snapshotId}" -> {
                                ExtendedFloatingActionButton(
                                    text = { Text(stringResource(R.string.fab_restore)) },
                                    icon = { Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.topbar_restore_snapshot)) },
                                    onClick = { navController.navigate("${Screen.Restore.route}/${navBackStackEntry?.arguments?.getString("snapshotId")}") }
                                )
                            }
                            Screen.Restore.route + "/{snapshotId}" -> {
                                val viewModel: RestoreViewModel = viewModel(
                                    viewModelStoreOwner = navBackStackEntry!!,
                                    factory = RestoreViewModelFactory(
                                        app,
                                        app.repositoriesRepository,
                                        app.resticBinaryManager,
                                        app.resticRepository,
                                        app.appInfoRepository,
                                        app.metadataRepository,
                                        app.preferencesRepository,
                                        app.operationWorkRepository,
                                        navBackStackEntry?.arguments?.getString("snapshotId") ?: ""
                                    )
                                )
                                val isRestoring by viewModel.isRestoring.collectAsState()
                                if (!isRestoring) {
                                    ExtendedFloatingActionButton(
                                        onClick = { viewModel.startRestore() },
                                        icon = { Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.fab_start_restore)) },
                                        text = { Text(stringResource(R.string.fab_start_restore)) }
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen(
                                onSnapshotClick = { snapshotId -> navController.navigate("${Screen.SnapshotDetails.route}/$snapshotId") },
                                uiState = homeUiState,
                                onRefresh = { homeViewModel.refreshSnapshots() },
                                onPasswordEntered = { p, s -> homeViewModel.onPasswordEntered(p, s) },
                                onSftpPasswordEntered = { p, s -> homeViewModel.onSftpPasswordEntered(p, s) },
                                onRestCredentialsEntered = { u, p, s -> homeViewModel.onRestCredentialsEntered(u, p, s) },
                                onS3CredentialsEntered = { u, p, s -> homeViewModel.onS3CredentialsEntered(u, p, s) },
                                onRetryRepositoryPasswordEntry = { homeViewModel.onRetryRepositoryPasswordEntry() },
                                onRetrySftpPasswordEntry = { homeViewModel.onRetrySftpPasswordEntry() },
                                onRetryRestCredentialsEntry = { homeViewModel.onRetryRestCredentialsEntry() },
                                onRetryS3CredentialsEntry = { homeViewModel.onRetryS3CredentialsEntry() },
                                onDismissPasswordDialog = { homeViewModel.onDismissPasswordDialog() },
                                onDismissSftpPasswordDialog = { homeViewModel.onDismissSftpPasswordDialog() },
                                onDismissRestCredentialsDialog = { homeViewModel.onDismissRestCredentialsDialog() },
                                onDismissS3CredentialsDialog = { homeViewModel.onDismissS3CredentialsDialog() }
                            )
                        }
                        composable(Screen.Settings.route) {
                            val vm: SettingsViewModel = viewModel(
                                factory = SettingsViewModelFactory(app, app.rootRepository, app.resticBinaryManager, app.resticRepository, app.repositoriesRepository, app.notificationRepository, app.preferencesRepository)
                            )
                            SettingsScreen(viewModel = vm, onNavigateToLicenses = { navController.navigate(Screen.Licenses.route) })
                        }
                        navigation(
                            startDestination = RunTasksRoutes.Main,
                            route = Screen.RunTasks.route
                        ) {
                            composable(RunTasksRoutes.Main) { backStackEntry ->
                                val parentEntry = remember(backStackEntry) {
                                    navController.getBackStackEntry(Screen.RunTasks.route)
                                }
                                val vm: RunTasksViewModel = viewModel(
                                    viewModelStoreOwner = parentEntry,
                                    factory = RunTasksViewModelFactory(
                                        app,
                                        app.repositoriesRepository,
                                        app.resticBinaryManager,
                                        app.appInfoRepository,
                                        app.preferencesRepository,
                                        app.operationWorkRepository
                                    )
                                )
                                RunTasksScreen(
                                    viewModel = vm,
                                    onNavigateToOperationProgress = {
                                        navController.navigate(Screen.OperationProgress.route) { launchSingleTop = true }
                                    },
                                    onNavigateToBackupConfig = { navController.navigate(RunTasksRoutes.BackupConfig) },
                                    onNavigateToForgetConfig = { navController.navigate(RunTasksRoutes.ForgetConfig) },
                                    onNavigateToCheckConfig = { navController.navigate(RunTasksRoutes.CheckConfig) }
                                )
                            }
                            composable(RunTasksRoutes.BackupConfig) { backStackEntry ->
                                val parentEntry = remember(backStackEntry) {
                                    navController.getBackStackEntry(Screen.RunTasks.route)
                                }
                                val vm: RunTasksViewModel = viewModel(
                                    viewModelStoreOwner = parentEntry,
                                    factory = RunTasksViewModelFactory(
                                        app,
                                        app.repositoriesRepository,
                                        app.resticBinaryManager,
                                        app.appInfoRepository,
                                        app.preferencesRepository,
                                        app.operationWorkRepository
                                    )
                                )
                                BackupConfigScreen(viewModel = vm)
                            }
                            composable(RunTasksRoutes.ForgetConfig) { backStackEntry ->
                                val parentEntry = remember(backStackEntry) {
                                    navController.getBackStackEntry(Screen.RunTasks.route)
                                }
                                val vm: RunTasksViewModel = viewModel(
                                    viewModelStoreOwner = parentEntry,
                                    factory = RunTasksViewModelFactory(
                                        app,
                                        app.repositoriesRepository,
                                        app.resticBinaryManager,
                                        app.appInfoRepository,
                                        app.preferencesRepository,
                                        app.operationWorkRepository
                                    )
                                )
                                ForgetConfigScreen(viewModel = vm)
                            }
                            composable(RunTasksRoutes.CheckConfig) { backStackEntry ->
                                val parentEntry = remember(backStackEntry) {
                                    navController.getBackStackEntry(Screen.RunTasks.route)
                                }
                                val vm: RunTasksViewModel = viewModel(
                                    viewModelStoreOwner = parentEntry,
                                    factory = RunTasksViewModelFactory(
                                        app,
                                        app.repositoriesRepository,
                                        app.resticBinaryManager,
                                        app.appInfoRepository,
                                        app.preferencesRepository,
                                        app.operationWorkRepository
                                    )
                                )
                                CheckConfigScreen(viewModel = vm)
                            }
                        }
                        composable(
                            route = "${Screen.SnapshotDetails.route}/{snapshotId}",
                            arguments = listOf(navArgument("snapshotId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            SnapshotDetailsScreen(
                                navController = navController,
                                snapshotId = backStackEntry.arguments?.getString("snapshotId")
                            )
                        }
                        composable(
                            route = "${Screen.Restore.route}/{snapshotId}",
                            arguments = listOf(navArgument("snapshotId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            RestoreScreen(
                                navController = navController,
                                snapshotId = backStackEntry.arguments?.getString("snapshotId")
                            )
                        }
                        composable(Screen.Licenses.route) { LicensesScreen(onNavigateUp = { navController.navigateUp() }) }
                        composable(Screen.OperationProgress.route) {
                            OperationProgressScreen(onNavigateUp = { navController.navigateUp() })
                        }
                    }
                }
            }
        }
    }
}
