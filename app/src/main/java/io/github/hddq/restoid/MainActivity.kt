package io.github.hddq.restoid

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.BackEventCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import io.github.hddq.restoid.data.NotificationRepository
import io.github.hddq.restoid.ui.home.HomeViewModel
import io.github.hddq.restoid.ui.home.HomeViewModelFactory
import io.github.hddq.restoid.ui.restore.RestoreViewModel
import io.github.hddq.restoid.ui.restore.RestoreViewModelFactory
import io.github.hddq.restoid.ui.runtasks.BackupConfigScreen
import io.github.hddq.restoid.ui.runtasks.CustomDirectoriesConfigScreen
import io.github.hddq.restoid.ui.runtasks.CheckConfigScreen
import io.github.hddq.restoid.ui.runtasks.ForgetConfigScreen
import io.github.hddq.restoid.ui.runtasks.RunTasksRoutes
import io.github.hddq.restoid.ui.runtasks.RunTasksScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import io.github.hddq.restoid.ui.runtasks.RunTasksViewModel
import io.github.hddq.restoid.ui.runtasks.RunTasksViewModelFactory
import io.github.hddq.restoid.ui.schedules.AddEditScheduleScreen
import io.github.hddq.restoid.ui.schedules.ScheduleBackupConfigScreen
import io.github.hddq.restoid.ui.schedules.ScheduleCustomDirectoriesConfigScreen
import io.github.hddq.restoid.ui.schedules.ScheduleCheckConfigScreen
import io.github.hddq.restoid.ui.schedules.ScheduleForgetConfigScreen
import io.github.hddq.restoid.ui.schedules.SchedulesRoutes
import io.github.hddq.restoid.ui.schedules.SchedulesScreen
import io.github.hddq.restoid.ui.schedules.SchedulesViewModel
import io.github.hddq.restoid.ui.schedules.SchedulesViewModelFactory
import io.github.hddq.restoid.ui.operation.OperationLogsViewModel
import io.github.hddq.restoid.ui.operation.OperationLogsViewModelFactory
import io.github.hddq.restoid.ui.screens.HomeScreen
import io.github.hddq.restoid.ui.screens.LicensesScreen
import io.github.hddq.restoid.ui.screens.OperationLogsScreen
import io.github.hddq.restoid.ui.screens.OperationProgressScreen
import io.github.hddq.restoid.ui.screens.RestoreScreen
import io.github.hddq.restoid.ui.screens.SettingsScreen
import io.github.hddq.restoid.ui.screens.SnapshotDetailsScreen
import io.github.hddq.restoid.ui.settings.SettingsViewModel
import io.github.hddq.restoid.ui.settings.SettingsViewModelFactory
import io.github.hddq.restoid.ui.theme.RestoidTheme

val LocalAppBarActions = compositionLocalOf<MutableState<(@Composable RowScope.() -> Unit)?>> {
    error("LocalAppBarActions not provided")
}

class MainActivity : FragmentActivity() {
    private companion object {
        const val STATE_APP_UNLOCKED = "state_app_unlocked"
        const val STATE_OPEN_OPERATION_PROGRESS = "state_open_operation_progress"
        const val STATE_OPEN_OPERATION_LOG_ID = "state_open_operation_log_id"
        const val APP_UNLOCK_BACKGROUND_TIMEOUT_MS = 60_000L
    }

    private var isAppUnlocked by mutableStateOf(false)
    private var isContentInitialized = false
    private var backgroundedAtElapsedRealtimeMs: Long? = null
    private var isAuthenticationInProgress = false
    private var openOperationProgressOnLaunch by mutableStateOf(false)
    private var openOperationLogIdOnLaunch by mutableStateOf<String?>(null)

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
        openOperationProgressOnLaunch = savedInstanceState?.getBoolean(STATE_OPEN_OPERATION_PROGRESS)
            ?: consumeOpenOperationProgressFlag(intent)
        openOperationLogIdOnLaunch = savedInstanceState?.getString(STATE_OPEN_OPERATION_LOG_ID)
            ?: consumeOpenOperationLogIdFlag(intent)
        if (openOperationLogIdOnLaunch != null) {
            openOperationProgressOnLaunch = false
        }
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        enableEdgeToEdge()

        launchAppContent(app)

        if (app.preferencesRepository.loadRequireAppUnlock() && !isAppUnlocked) {
            authenticateAndLaunch(app)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_APP_UNLOCKED, isAppUnlocked)
        outState.putBoolean(STATE_OPEN_OPERATION_PROGRESS, openOperationProgressOnLaunch)
        outState.putString(STATE_OPEN_OPERATION_LOG_ID, openOperationLogIdOnLaunch)
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
        } else {
            val backgroundedAt = backgroundedAtElapsedRealtimeMs
            backgroundedAtElapsedRealtimeMs = null
            if (backgroundedAt != null) {
                val timeInBackgroundMs = SystemClock.elapsedRealtime() - backgroundedAt
                if (timeInBackgroundMs >= APP_UNLOCK_BACKGROUND_TIMEOUT_MS) {
                    isAppUnlocked = false
                    authenticateAndLaunch(app)
                }
            }
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
        val shouldOpenProgress = consumeOpenOperationProgressFlag(intent)
        val logId = consumeOpenOperationLogIdFlag(intent)
        if (logId != null) {
            openOperationLogIdOnLaunch = logId
            openOperationProgressOnLaunch = false
        } else if (shouldOpenProgress) {
            openOperationProgressOnLaunch = true
            openOperationLogIdOnLaunch = null
        }
    }

    private fun consumeOpenOperationLogIdFlag(sourceIntent: Intent?): String? {
        if (sourceIntent == null) return null
        val logId = sourceIntent.getStringExtra(NotificationRepository.EXTRA_OPEN_OPERATION_LOG_ID)
        if (logId != null) {
            sourceIntent.removeExtra(NotificationRepository.EXTRA_OPEN_OPERATION_LOG_ID)
        }
        setIntent(sourceIntent)
        return logId
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
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    if (!isAppUnlocked && app.preferencesRepository.loadRequireAppUnlock()) {
                        androidx.compose.foundation.layout.Box(
                            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            androidx.compose.material3.Button(onClick = { authenticateAndLaunch(app) }) {
                                androidx.compose.material3.Text(getString(R.string.app_unlock_prompt_title))
                            }
                        }
                        return@Surface
                    }

                    val navController = rememberNavController()
                    val topLevelRoutes = remember {
                        setOf(Screen.Home.route, SchedulesRoutes.Main, Screen.Settings.route)
                    }
                    val homeViewModel: HomeViewModel = viewModel(
                        factory = HomeViewModelFactory(app, app.repositoriesRepository, app.resticBinaryManager, app.resticRepository, app.appInfoRepository, app.metadataRepository, app.operationWorkRepository)
                    )
                    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()

                    val isNavigationAllowed = isAppUnlocked || !app.preferencesRepository.loadRequireAppUnlock()

                    if (openOperationLogIdOnLaunch != null && isNavigationAllowed) {
                        val targetLogId = openOperationLogIdOnLaunch
                        androidx.compose.runtime.LaunchedEffect(targetLogId, isNavigationAllowed) {
                            navController.navigate("${Screen.OperationLogs.route}?logId=$targetLogId") {
                                launchSingleTop = true
                            }
                            openOperationLogIdOnLaunch = null
                        }
                    }

                    if (openOperationProgressOnLaunch && isNavigationAllowed) {
                        androidx.compose.runtime.LaunchedEffect(openOperationProgressOnLaunch, isNavigationAllowed) {
                            navController.navigate(Screen.OperationProgress.route) {
                                launchSingleTop = true
                            }
                            openOperationProgressOnLaunch = false
                        }
                    }

                    val standardEasing = remember { CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f) }
                    val predictiveEasing = remember { CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f) }
                    val fadeOutEasing = remember { CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f) }

                    val navigateToTopLevel: (Screen) -> Unit = { screen ->
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = {
                            val isTabSwitch = initialState.destination.route in topLevelRoutes &&
                                targetState.destination.route in topLevelRoutes
                            if (isTabSwitch) {
                                fadeIn(animationSpec = tween(200, easing = standardEasing))
                            } else {
                                slideIntoContainer(
                                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                    animationSpec = tween(320, easing = standardEasing),
                                    initialOffset = { it / 4 }
                                ) + scaleIn(
                                    initialScale = 0.95f,
                                    animationSpec = tween(320, easing = standardEasing)
                                ) + fadeIn(animationSpec = tween(200, easing = standardEasing))
                            }
                        },
                        exitTransition = {
                            val isTabSwitch = initialState.destination.route in topLevelRoutes &&
                                targetState.destination.route in topLevelRoutes
                            if (isTabSwitch) {
                                fadeOut(animationSpec = tween(1, delayMillis = 200))
                            } else {
                                slideOutOfContainer(
                                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                    animationSpec = tween(320, easing = standardEasing),
                                    targetOffset = { it / 6 }
                                ) + scaleOut(
                                    targetScale = 0.95f,
                                    animationSpec = tween(320, easing = standardEasing)
                                ) + fadeOut(animationSpec = tween(1, delayMillis = 320))
                            }
                        },
                        popEnterTransition = {
                            val isTabSwitch = initialState.destination.route in topLevelRoutes &&
                                targetState.destination.route in topLevelRoutes
                            if (isTabSwitch) {
                                fadeIn(animationSpec = tween(200, easing = standardEasing))
                            } else {
                                slideIntoContainer(
                                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                                    animationSpec = tween(320, easing = predictiveEasing),
                                    initialOffset = { it / 5 }
                                ) + scaleIn(
                                    initialScale = 0.94f,
                                    animationSpec = tween(320, easing = predictiveEasing)
                                ) + fadeIn(
                                    initialAlpha = 0.6f,
                                    animationSpec = tween(320, easing = predictiveEasing)
                                )
                            }
                        },
                        popExitTransition = {
                            val isTabSwitch = initialState.destination.route in topLevelRoutes &&
                                targetState.destination.route in topLevelRoutes
                            if (isTabSwitch) {
                                fadeOut(animationSpec = tween(1, delayMillis = 200))
                            } else {
                                scaleOut(
                                    targetScale = 0.92f,
                                    animationSpec = tween(320, easing = predictiveEasing)
                                ) + slideOutOfContainer(
                                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                                    animationSpec = tween(320, easing = predictiveEasing),
                                    targetOffset = { it / 4 }
                                ) + fadeOut(
                                    animationSpec = tween(190, delayMillis = 130, easing = fadeOutEasing)
                                )
                            }
                        },
                        predictivePopEnterTransition = { swipeEdge ->
                            val slideDirection = if (swipeEdge == BackEventCompat.EDGE_RIGHT) {
                                AnimatedContentTransitionScope.SlideDirection.Left
                            } else {
                                AnimatedContentTransitionScope.SlideDirection.Right
                            }
                            slideIntoContainer(
                                towards = slideDirection,
                                animationSpec = tween(350, easing = predictiveEasing),
                                initialOffset = { it / 5 }
                            ) + scaleIn(
                                initialScale = 0.94f,
                                animationSpec = tween(350, easing = predictiveEasing)
                            ) + fadeIn(
                                initialAlpha = 0.6f,
                                animationSpec = tween(350, easing = predictiveEasing)
                            )
                        },
                        predictivePopExitTransition = { swipeEdge ->
                            val slideDirection = if (swipeEdge == BackEventCompat.EDGE_RIGHT) {
                                AnimatedContentTransitionScope.SlideDirection.Left
                            } else {
                                AnimatedContentTransitionScope.SlideDirection.Right
                            }
                            val pivotX = if (swipeEdge == BackEventCompat.EDGE_RIGHT) 0.25f else 0.75f
                            scaleOut(
                                targetScale = 0.92f,
                                transformOrigin = TransformOrigin(pivotX, 0.5f),
                                animationSpec = tween(350, easing = predictiveEasing)
                            ) + slideOutOfContainer(
                                towards = slideDirection,
                                animationSpec = tween(350, easing = predictiveEasing),
                                targetOffset = { it / 4 }
                            ) + fadeOut(
                                animationSpec = tween(210, delayMillis = 140, easing = fadeOutEasing)
                            )
                        }
                    ) {
                        composable(Screen.Home.route) {
                            val isRepoReady = homeUiState.isRepoReady
                            DestinationScaffold(
                                titleRes = R.string.home_title,
                                isTopLevel = true,
                                isHome = true,
                                onBack = { this@MainActivity.onBackPressedDispatcher.onBackPressed() },
                                actions = {
                                    IconButton(onClick = { navController.navigate(Screen.OperationLogs.route) }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                                            contentDescription = stringResource(R.string.screen_operation_logs)
                                        )
                                    }
                                },
                                bottomBar = {
                                    RestoidBottomNavigationBar(
                                        selectedScreen = Screen.Home,
                                        onSelectScreen = navigateToTopLevel
                                    )
                                },
                                floatingActionButton = {
                                    ExtendedFloatingActionButton(
                                        text = { Text(stringResource(R.string.screen_tasks)) },
                                        icon = { Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(R.string.screen_tasks)) },
                                        onClick = { if (isRepoReady) { navController.navigate(Screen.RunTasks.route) } },
                                        shape = RoundedCornerShape(20.dp),
                                        containerColor = if (isRepoReady) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = if (isRepoReady) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    )
                                }
                            ) {
                                HomeScreen(
                                    onSnapshotClick = { snapshotId -> navController.navigate("${Screen.SnapshotDetails.route}/$snapshotId") },
                                    onRestoreSnapshotClick = { snapshotId -> navController.navigate("${Screen.Restore.route}/$snapshotId") },
                                    onRequestForgetSnapshot = { snapshotId -> homeViewModel.requestForgetSnapshot(snapshotId) },
                                    onConfirmForgetSnapshot = { homeViewModel.confirmForgetSnapshot() },
                                    onDismissForgetDialog = { homeViewModel.dismissForgetDialog() },
                                    onDismissForgetErrorDialog = { homeViewModel.dismissForgetErrorDialog() },
                                    uiState = homeUiState,
                                    onRefresh = { homeViewModel.refreshSnapshots() },
                                    onPasswordEntered = { p, s -> homeViewModel.onPasswordEntered(p, s) },
                                    onSftpPasswordEntered = { keyOrPassword, passphrase, save ->
                                        homeViewModel.onSftpPasswordEntered(keyOrPassword, passphrase, save)
                                    },
                                    onRestCredentialsEntered = { u, p, s -> homeViewModel.onRestCredentialsEntered(u, p, s) },
                                    onS3CredentialsEntered = { u, p, s -> homeViewModel.onS3CredentialsEntered(u, p, s) },
                                    onRetryRepositoryPasswordEntry = { homeViewModel.onRetryRepositoryPasswordEntry() },
                                    onRetrySftpPasswordEntry = { homeViewModel.onRetrySftpPasswordEntry() },
                                    onRetryRestCredentialsEntry = { homeViewModel.onRetryRestCredentialsEntry() },
                                    onRetryS3CredentialsEntry = { homeViewModel.onRetryS3CredentialsEntry() },
                                    onDismissPasswordDialog = { homeViewModel.onDismissPasswordDialog() },
                                    onDismissSftpPasswordDialog = { homeViewModel.onDismissSftpPasswordDialog() },
                                    onDismissRestCredentialsDialog = { homeViewModel.onDismissRestCredentialsDialog() },
                                    onDismissS3CredentialsDialog = { homeViewModel.onDismissS3CredentialsDialog() },
                                    onOpenOperationProgress = {
                                        navController.navigate(Screen.OperationProgress.route) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onForceStopOperation = { homeViewModel.forceStopOperation() }
                                )
                            }
                        }
                        composable(Screen.Settings.route) {
                            val vm: SettingsViewModel = viewModel(
                                factory = SettingsViewModelFactory(
                                    applicationContext,
                                    app.rootRepository,
                                    app.resticBinaryManager,
                                    app.resticRepository,
                                    app.repositoriesRepository,
                                    app.notificationRepository,
                                    app.preferencesRepository,
                                    app.storagePermissionRepository,
                                    app.localNetworkPermissionRepository
                                )
                            )
                            DestinationScaffold(
                                titleRes = R.string.screen_settings,
                                isTopLevel = true,
                                onBack = { this@MainActivity.onBackPressedDispatcher.onBackPressed() },
                                bottomBar = {
                                    RestoidBottomNavigationBar(
                                        selectedScreen = Screen.Settings,
                                        onSelectScreen = navigateToTopLevel
                                    )
                                }
                            ) {
                                SettingsScreen(viewModel = vm, onNavigateToLicenses = { navController.navigate(Screen.Licenses.route) })
                            }
                        }
                        navigation(
                            startDestination = SchedulesRoutes.Main,
                            route = Screen.Schedules.route
                        ) {
                            composable(SchedulesRoutes.Main) { backStackEntry ->
                                val parentEntry = remember(backStackEntry) {
                                    navController.getBackStackEntry(Screen.Schedules.route)
                                }
                                val vm: SchedulesViewModel = viewModel(
                                    viewModelStoreOwner = parentEntry,
                                    factory = SchedulesViewModelFactory(app, app.scheduleRepository, app.repositoriesRepository, app.appInfoRepository, app.rootRepository)
                                )
                                DestinationScaffold(
                                    titleRes = R.string.topbar_schedules,
                                    isTopLevel = true,
                                    onBack = { this@MainActivity.onBackPressedDispatcher.onBackPressed() },
                                    bottomBar = {
                                        RestoidBottomNavigationBar(
                                            selectedScreen = Screen.Schedules,
                                            onSelectScreen = navigateToTopLevel
                                        )
                                    }
                                ) {
                                    SchedulesScreen(
                                        viewModel = vm,
                                        onNavigateToAddEditSchedule = { navController.navigate(SchedulesRoutes.AddEdit) }
                                    )
                                }
                            }
                                composable(SchedulesRoutes.AddEdit) { backStackEntry ->
                                    val parentEntry = remember(backStackEntry) {
                                        navController.getBackStackEntry(Screen.Schedules.route)
                                    }
                                    val vm: SchedulesViewModel = viewModel(
                                        viewModelStoreOwner = parentEntry,
                                        factory = SchedulesViewModelFactory(app, app.scheduleRepository, app.repositoriesRepository, app.appInfoRepository, app.rootRepository)
                                    )
                                    val state by vm.addEditState.collectAsState()
                                    DestinationScaffold(
                                        titleRes = if (state.id == null) R.string.topbar_add_schedule else R.string.topbar_edit_schedule,
                                        isTopLevel = false,
                                        onBack = { vm.onBackPress() },
                                        floatingActionButton = {
                                            if (!state.isSaving) {
                                                ExtendedFloatingActionButton(
                                                    onClick = { vm.saveSchedule() },
                                                    shape = RoundedCornerShape(20.dp),
                                                    icon = { Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.action_save)) },
                                                    text = { Text(stringResource(R.string.action_save)) }
                                                )
                                            }
                                        }
                                    ) {
                                        AddEditScheduleScreen(
                                            viewModel = vm,
                                            onNavigateBack = { navController.navigateUp() },
                                            onNavigateToBackupConfig = { navController.navigate(SchedulesRoutes.BackupConfig) },
                                            onNavigateToCustomDirectoriesConfig = { navController.navigate(SchedulesRoutes.CustomDirectoriesConfig) },
                                            onNavigateToForgetConfig = { navController.navigate(SchedulesRoutes.ForgetConfig) },
                                            onNavigateToCheckConfig = { navController.navigate(SchedulesRoutes.CheckConfig) }
                                        )
                                    }
                                }
                                composable(SchedulesRoutes.BackupConfig) { backStackEntry ->
                                    val parentEntry = remember(backStackEntry) {
                                        navController.getBackStackEntry(Screen.Schedules.route)
                                    }
                                    val vm: SchedulesViewModel = viewModel(
                                        viewModelStoreOwner = parentEntry,
                                        factory = SchedulesViewModelFactory(app, app.scheduleRepository, app.repositoriesRepository, app.appInfoRepository, app.rootRepository)
                                    )
                                    DestinationScaffold(
                                        titleRes = R.string.topbar_backup_config,
                                        isTopLevel = false,
                                        onBack = { this@MainActivity.onBackPressedDispatcher.onBackPressed() }
                                    ) {
                                        ScheduleBackupConfigScreen(viewModel = vm)
                                    }
                                }
                                composable(SchedulesRoutes.ForgetConfig) { backStackEntry ->
                                    val parentEntry = remember(backStackEntry) {
                                        navController.getBackStackEntry(Screen.Schedules.route)
                                    }
                                    val vm: SchedulesViewModel = viewModel(
                                        viewModelStoreOwner = parentEntry,
                                        factory = SchedulesViewModelFactory(app, app.scheduleRepository, app.repositoriesRepository, app.appInfoRepository, app.rootRepository)
                                    )
                                    DestinationScaffold(
                                        titleRes = R.string.topbar_forget_config,
                                        isTopLevel = false,
                                        onBack = { this@MainActivity.onBackPressedDispatcher.onBackPressed() }
                                    ) {
                                        ScheduleForgetConfigScreen(viewModel = vm)
                                    }
                                }
                                composable(SchedulesRoutes.CheckConfig) { backStackEntry ->
                                    val parentEntry = remember(backStackEntry) {
                                        navController.getBackStackEntry(Screen.Schedules.route)
                                    }
                                    val vm: SchedulesViewModel = viewModel(
                                        viewModelStoreOwner = parentEntry,
                                        factory = SchedulesViewModelFactory(app, app.scheduleRepository, app.repositoriesRepository, app.appInfoRepository, app.rootRepository)
                                    )
                                    DestinationScaffold(
                                        titleRes = R.string.topbar_check_config,
                                        isTopLevel = false,
                                        onBack = { this@MainActivity.onBackPressedDispatcher.onBackPressed() }
                                    ) {
                                        ScheduleCheckConfigScreen(viewModel = vm)
                                    }
                                }
                                composable(SchedulesRoutes.CustomDirectoriesConfig) { backStackEntry ->
                                    val parentEntry = remember(backStackEntry) {
                                        navController.getBackStackEntry(Screen.Schedules.route)
                                    }
                                    val vm: SchedulesViewModel = viewModel(
                                        viewModelStoreOwner = parentEntry,
                                        factory = SchedulesViewModelFactory(app, app.scheduleRepository, app.repositoriesRepository, app.appInfoRepository, app.rootRepository)
                                    )
                                    DestinationScaffold(
                                        titleRes = R.string.title_custom_directories,
                                        isTopLevel = false,
                                        onBack = { this@MainActivity.onBackPressedDispatcher.onBackPressed() }
                                    ) {
                                        ScheduleCustomDirectoriesConfigScreen(viewModel = vm)
                                    }
                                }
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
                                            app.rootRepository,
                                            app.repositoriesRepository,
                                            app.resticBinaryManager,
                                            app.appInfoRepository,
                                            app.preferencesRepository,
                                            app.operationWorkRepository
                                        )
                                    )
                                    val uiState by vm.uiState.collectAsState()
                                    DestinationScaffold(
                                        titleRes = R.string.topbar_tasks,
                                        isTopLevel = false,
                                        onBack = { this@MainActivity.onBackPressedDispatcher.onBackPressed() },
                                        floatingActionButton = {
                                            if (!uiState.isRunning) {
                                                ExtendedFloatingActionButton(
                                                    onClick = { vm.run() },
                                                    shape = RoundedCornerShape(20.dp),
                                                    icon = { Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(R.string.fab_run_tasks)) },
                                                    text = { Text(stringResource(R.string.fab_run_tasks)) }
                                                )
                                            }
                                        }
                                    ) {
                                        RunTasksScreen(
                                            viewModel = vm,
                                            onNavigateToOperationProgress = {
                                                navController.navigate(Screen.OperationProgress.route) { launchSingleTop = true }
                                            },
                                            onNavigateToBackupConfig = { navController.navigate(RunTasksRoutes.BackupConfig) },
                                            onNavigateToCustomDirectoriesConfig = { navController.navigate(RunTasksRoutes.CustomDirectoriesConfig) },
                                            onNavigateToForgetConfig = { navController.navigate(RunTasksRoutes.ForgetConfig) },
                                            onNavigateToCheckConfig = { navController.navigate(RunTasksRoutes.CheckConfig) }
                                        )
                                    }
                                }
                                composable(RunTasksRoutes.BackupConfig) { backStackEntry ->
                                    val parentEntry = remember(backStackEntry) {
                                        navController.getBackStackEntry(Screen.RunTasks.route)
                                    }
                                    val vm: RunTasksViewModel = viewModel(
                                        viewModelStoreOwner = parentEntry,
                                        factory = RunTasksViewModelFactory(
                                            app,
                                            app.rootRepository,
                                            app.repositoriesRepository,
                                            app.resticBinaryManager,
                                            app.appInfoRepository,
                                            app.preferencesRepository,
                                            app.operationWorkRepository
                                        )
                                    )
                                    DestinationScaffold(
                                        titleRes = R.string.topbar_backup_config,
                                        isTopLevel = false,
                                        onBack = { this@MainActivity.onBackPressedDispatcher.onBackPressed() }
                                    ) {
                                        BackupConfigScreen(viewModel = vm)
                                    }
                                }
                                composable(RunTasksRoutes.CustomDirectoriesConfig) { backStackEntry ->
                                    val parentEntry = remember(backStackEntry) {
                                        navController.getBackStackEntry(Screen.RunTasks.route)
                                    }
                                    val vm: RunTasksViewModel = viewModel(
                                        viewModelStoreOwner = parentEntry,
                                        factory = RunTasksViewModelFactory(
                                            app,
                                            app.rootRepository,
                                            app.repositoriesRepository,
                                            app.resticBinaryManager,
                                            app.appInfoRepository,
                                            app.preferencesRepository,
                                            app.operationWorkRepository
                                        )
                                    )
                                    DestinationScaffold(
                                        titleRes = R.string.title_custom_directories,
                                        isTopLevel = false,
                                        onBack = { this@MainActivity.onBackPressedDispatcher.onBackPressed() }
                                    ) {
                                        CustomDirectoriesConfigScreen(viewModel = vm)
                                    }
                                }
                                composable(RunTasksRoutes.ForgetConfig) { backStackEntry ->
                                    val parentEntry = remember(backStackEntry) {
                                        navController.getBackStackEntry(Screen.RunTasks.route)
                                    }
                                    val vm: RunTasksViewModel = viewModel(
                                        viewModelStoreOwner = parentEntry,
                                        factory = RunTasksViewModelFactory(
                                            app,
                                            app.rootRepository,
                                            app.repositoriesRepository,
                                            app.resticBinaryManager,
                                            app.appInfoRepository,
                                            app.preferencesRepository,
                                            app.operationWorkRepository
                                        )
                                    )
                                    DestinationScaffold(
                                        titleRes = R.string.topbar_forget_config,
                                        isTopLevel = false,
                                        onBack = { this@MainActivity.onBackPressedDispatcher.onBackPressed() }
                                    ) {
                                        ForgetConfigScreen(viewModel = vm)
                                    }
                                }
                                composable(RunTasksRoutes.CheckConfig) { backStackEntry ->
                                    val parentEntry = remember(backStackEntry) {
                                        navController.getBackStackEntry(Screen.RunTasks.route)
                                    }
                                    val vm: RunTasksViewModel = viewModel(
                                        viewModelStoreOwner = parentEntry,
                                        factory = RunTasksViewModelFactory(
                                            app,
                                            app.rootRepository,
                                            app.repositoriesRepository,
                                            app.resticBinaryManager,
                                            app.appInfoRepository,
                                            app.preferencesRepository,
                                            app.operationWorkRepository
                                        )
                                    )
                                    DestinationScaffold(
                                        titleRes = R.string.topbar_check_config,
                                        isTopLevel = false,
                                        onBack = { this@MainActivity.onBackPressedDispatcher.onBackPressed() }
                                    ) {
                                        CheckConfigScreen(viewModel = vm)
                                    }
                                }
                            }
                            composable(
                                route = "${Screen.SnapshotDetails.route}/{snapshotId}",
                                arguments = listOf(navArgument("snapshotId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val snapshotId = backStackEntry.arguments?.getString("snapshotId")
                                DestinationScaffold(
                                    titleRes = R.string.topbar_snapshot_details,
                                    isTopLevel = false,
                                    onBack = { this@MainActivity.onBackPressedDispatcher.onBackPressed() },
                                    floatingActionButton = {
                                        ExtendedFloatingActionButton(
                                            text = { Text(stringResource(R.string.fab_restore)) },
                                            icon = { Icon(Icons.Outlined.Restore, contentDescription = stringResource(R.string.topbar_restore_snapshot)) },
                                            shape = RoundedCornerShape(20.dp),
                                            onClick = { navController.navigate("${Screen.Restore.route}/$snapshotId") }
                                        )
                                    }
                                ) {
                                    SnapshotDetailsScreen(
                                        navController = navController,
                                        snapshotId = snapshotId
                                    )
                                }
                            }
                            navigation(
                                startDestination = "${Screen.Restore.route}/{snapshotId}",
                                route = "restore_graph/{snapshotId}",
                                arguments = listOf(navArgument("snapshotId") { type = NavType.StringType })
                            ) {
                                composable(
                                    route = "${Screen.Restore.route}/{snapshotId}",
                                    arguments = listOf(navArgument("snapshotId") { type = NavType.StringType })
                                ) { backStackEntry ->
                                    val parentEntry = remember(backStackEntry) {
                                        navController.getBackStackEntry("restore_graph/{snapshotId}")
                                    }
                                    val snapshotId = parentEntry.arguments?.getString("snapshotId") ?: ""
                                    val viewModel: RestoreViewModel = viewModel(
                                        viewModelStoreOwner = parentEntry,
                                        factory = RestoreViewModelFactory(
                                            app,
                                            app.repositoriesRepository,
                                            app.rootRepository,
                                            app.resticBinaryManager,
                                            app.resticRepository,
                                            app.appInfoRepository,
                                            app.metadataRepository,
                                            app.preferencesRepository,
                                            app.operationWorkRepository,
                                            snapshotId
                                        )
                                    )
                                    val isRestoring by viewModel.isRestoring.collectAsState()
                                    DestinationScaffold(
                                        titleRes = R.string.topbar_restore_snapshot,
                                        isTopLevel = false,
                                        onBack = { this@MainActivity.onBackPressedDispatcher.onBackPressed() },
                                        floatingActionButton = {
                                            if (!isRestoring) {
                                                ExtendedFloatingActionButton(
                                                    onClick = { viewModel.startRestore() },
                                                    shape = RoundedCornerShape(20.dp),
                                                    icon = { Icon(Icons.Outlined.Restore, contentDescription = stringResource(R.string.fab_start_restore)) },
                                                    text = { Text(stringResource(R.string.fab_start_restore)) }
                                                )
                                            }
                                        }
                                    ) {
                                        RestoreScreen(
                                            viewModel = viewModel,
                                            onNavigateToOperationProgress = { navController.navigate(Screen.OperationProgress.route) { launchSingleTop = true } },
                                            onNavigateToAppsConfig = { navController.navigate(io.github.hddq.restoid.ui.restore.RestoreRoutes.AppsConfig) },
                                            onNavigateToCustomDirectoriesConfig = { navController.navigate(io.github.hddq.restoid.ui.restore.RestoreRoutes.CustomDirectoriesConfig) }
                                        )
                                    }
                                }
                                composable(io.github.hddq.restoid.ui.restore.RestoreRoutes.AppsConfig) { backStackEntry ->
                                    val parentEntry = remember(backStackEntry) {
                                        navController.getBackStackEntry("restore_graph/{snapshotId}")
                                    }
                                    val snapshotId = parentEntry.arguments?.getString("snapshotId") ?: ""
                                    val vm: RestoreViewModel = viewModel(
                                        viewModelStoreOwner = parentEntry,
                                        factory = RestoreViewModelFactory(
                                            app,
                                            app.repositoriesRepository,
                                            app.rootRepository,
                                            app.resticBinaryManager,
                                            app.resticRepository,
                                            app.appInfoRepository,
                                            app.metadataRepository,
                                            app.preferencesRepository,
                                            app.operationWorkRepository,
                                            snapshotId
                                        )
                                    )
                                    DestinationScaffold(
                                        titleRes = R.string.apps_to_restore_title,
                                        isTopLevel = false,
                                        onBack = { this@MainActivity.onBackPressedDispatcher.onBackPressed() }
                                    ) {
                                        io.github.hddq.restoid.ui.screens.RestoreAppsConfigScreen(viewModel = vm)
                                    }
                                }
                                composable(io.github.hddq.restoid.ui.restore.RestoreRoutes.CustomDirectoriesConfig) { backStackEntry ->
                                    val parentEntry = remember(backStackEntry) {
                                        navController.getBackStackEntry("restore_graph/{snapshotId}")
                                    }
                                    val snapshotId = parentEntry.arguments?.getString("snapshotId") ?: ""
                                    val vm: RestoreViewModel = viewModel(
                                        viewModelStoreOwner = parentEntry,
                                        factory = RestoreViewModelFactory(
                                            app,
                                            app.repositoriesRepository,
                                            app.rootRepository,
                                            app.resticBinaryManager,
                                            app.resticRepository,
                                            app.appInfoRepository,
                                            app.metadataRepository,
                                            app.preferencesRepository,
                                            app.operationWorkRepository,
                                            snapshotId
                                        )
                                    )
                                    DestinationScaffold(
                                        titleRes = R.string.title_custom_directories,
                                        isTopLevel = false,
                                        onBack = { this@MainActivity.onBackPressedDispatcher.onBackPressed() }
                                    ) {
                                        io.github.hddq.restoid.ui.screens.RestoreCustomDirectoriesConfigScreen(viewModel = vm)
                                    }
                                }
                            }
                            composable(Screen.Licenses.route) {
                                DestinationScaffold(
                                    titleRes = R.string.topbar_open_source_licenses,
                                    isTopLevel = false,
                                    onBack = { this@MainActivity.onBackPressedDispatcher.onBackPressed() }
                                ) {
                                    LicensesScreen(onNavigateUp = { navController.navigateUp() })
                                }
                            }
                            composable(Screen.OperationProgress.route) {
                                DestinationScaffold(
                                    titleRes = R.string.topbar_operation_progress,
                                    isTopLevel = false,
                                    onBack = { this@MainActivity.onBackPressedDispatcher.onBackPressed() }
                                ) {
                                    OperationProgressScreen(onNavigateUp = { navController.navigateUp() })
                                }
                            }
                            composable(
                                route = "${Screen.OperationLogs.route}?logId={logId}",
                                arguments = listOf(
                                    navArgument("logId") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    }
                                )
                            ) { backStackEntry ->
                                val initialLogId = backStackEntry.arguments?.getString("logId")
                                val viewModel: OperationLogsViewModel = viewModel(
                                    factory = OperationLogsViewModelFactory(app.operationLogRepository)
                                )
                                DestinationScaffold(
                                    titleRes = R.string.screen_operation_logs,
                                    isTopLevel = false,
                                    onBack = { this@MainActivity.onBackPressedDispatcher.onBackPressed() }
                                ) {
                                    OperationLogsScreen(
                                        viewModel = viewModel,
                                        initialSelectedLogId = initialLogId,
                                        onNavigateUp = { navController.navigateUp() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
}

@Composable
private fun RestoidBottomNavigationBar(
    selectedScreen: Screen,
    onSelectScreen: (Screen) -> Unit
) {
    val items = listOf(
        Triple(Screen.Home, Icons.Filled.Home, Icons.Outlined.Home),
        Triple(Screen.Schedules, Icons.Filled.Schedule, Icons.Outlined.Schedule),
        Triple(Screen.Settings, Icons.Filled.Settings, Icons.Outlined.Settings)
    )
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        items.forEach { (screen, selectedIcon, unselectedIcon) ->
            val selected = screen == selectedScreen
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = if (selected) selectedIcon else unselectedIcon,
                        contentDescription = null
                    )
                },
                label = {
                    Text(
                        text = stringResource(screen.titleRes),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                selected = selected,
                onClick = {
                    if (!selected) {
                        onSelectScreen(screen)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DestinationScaffold(
    titleRes: Int,
    isTopLevel: Boolean,
    isHome: Boolean = false,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    val appBarActions = remember { mutableStateOf<(@Composable RowScope.() -> Unit)?>(null) }
    CompositionLocalProvider(LocalAppBarActions provides appBarActions) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    title = {
                        Text(
                            text = stringResource(titleRes),
                            style = if (isTopLevel) {
                                MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                            } else {
                                MaterialTheme.typography.titleLarge
                            },
                            color = if (isHome) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    navigationIcon = {
                        if (!isTopLevel) {
                            FilledTonalIconButton(
                                onClick = onBack,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = stringResource(R.string.cd_back)
                                )
                            }
                        }
                    },
                    actions = {
                        actions()
                        if (!isTopLevel) {
                            appBarActions.value?.invoke(this)
                        }
                    }
                )
            },
            bottomBar = bottomBar,
            floatingActionButton = floatingActionButton
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                content()
            }
        }
    }
}
