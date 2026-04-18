package io.github.hddq.restoid

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.hddq.restoid.ui.backup.BackupViewModel
import io.github.hddq.restoid.ui.backup.BackupViewModelFactory
import io.github.hddq.restoid.ui.home.HomeViewModel
import io.github.hddq.restoid.ui.home.HomeViewModelFactory
import io.github.hddq.restoid.ui.maintenance.MaintenanceViewModel
import io.github.hddq.restoid.ui.maintenance.MaintenanceViewModelFactory
import io.github.hddq.restoid.ui.restore.RestoreViewModel
import io.github.hddq.restoid.ui.restore.RestoreViewModelFactory
import io.github.hddq.restoid.ui.screens.*
import io.github.hddq.restoid.ui.settings.SettingsViewModel
import io.github.hddq.restoid.ui.settings.SettingsViewModelFactory
import io.github.hddq.restoid.ui.snapshot.SnapshotDetailsViewModel
import io.github.hddq.restoid.ui.snapshot.SnapshotDetailsViewModelFactory
import io.github.hddq.restoid.ui.theme.RestoidTheme

class MainActivity : FragmentActivity() {
    private companion object {
        const val STATE_APP_UNLOCKED = "state_app_unlocked"
    }

    private var isAppUnlocked = false
    private var isContentInitialized = false

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext as RestoidApplication
        isAppUnlocked = savedInstanceState?.getBoolean(STATE_APP_UNLOCKED) ?: false
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

    private fun authenticateAndLaunch(app: RestoidApplication) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val canAuthenticate = BiometricManager.from(this).canAuthenticate(authenticators)

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
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
                    isAppUnlocked = true
                    launchAppContent(app)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (!isFinishing && !isDestroyed) {
                        finish()
                    }
                }
            }
        )

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

                Scaffold(
                    topBar = {
                        if (!showBottomBar) {
                            TopAppBar(
                                title = {
                                    val titleRes = when {
                                        currentDestination?.route == Screen.Backup.route -> R.string.topbar_new_backup
                                        currentDestination?.route == Screen.Maintenance.route -> R.string.topbar_maintenance
                                        currentDestination?.route == Screen.Licenses.route -> R.string.topbar_open_source_licenses
                                        currentDestination?.route?.startsWith(Screen.SnapshotDetails.route) == true -> R.string.topbar_snapshot_details
                                        currentDestination?.route?.startsWith(Screen.Restore.route) == true -> R.string.topbar_restore_snapshot
                                        else -> null
                                    }
                                    titleRes?.let { Text(stringResource(it)) }
                                },
                                navigationIcon = {
                                    IconButton(onClick = { navController.navigateUp() }) {
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
                                val selectedRepo by app.repositoriesRepository.selectedRepository.collectAsState()
                                val isRepoSelected = selectedRepo != null
                                ExtendedFloatingActionButton(
                                    text = { Text(stringResource(R.string.fab_backup)) },
                                    icon = { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.fab_backup)) },
                                    onClick = { if (isRepoSelected) { navController.navigate(Screen.Backup.route) } },
                                    containerColor = if (isRepoSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                    contentColor = if (isRepoSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                            Screen.Backup.route -> {
                                val viewModel: BackupViewModel = viewModel(
                                    viewModelStoreOwner = navBackStackEntry!!,
                                    factory = BackupViewModelFactory(app, app.repositoriesRepository, app.resticBinaryManager, app.resticRepository, app.notificationRepository, app.appInfoRepository, app.preferencesRepository)
                                )
                                val isBackingUp by viewModel.isBackingUp.collectAsState()
                                val backupProgress by viewModel.backupProgress.collectAsState()
                                if (!isBackingUp && !backupProgress.isFinished) {
                                    ExtendedFloatingActionButton(
                                        onClick = { viewModel.startBackup() },
                                        icon = { Icon(Icons.Default.Backup, contentDescription = stringResource(R.string.fab_start_backup)) },
                                        text = { Text(stringResource(R.string.fab_start_backup)) }
                                    )
                                }
                            }
                            Screen.Maintenance.route -> {
                                val viewModel: MaintenanceViewModel = viewModel(
                                    viewModelStoreOwner = navBackStackEntry!!,
                                    factory = MaintenanceViewModelFactory(app, app.repositoriesRepository, app.resticBinaryManager, app.resticRepository, app.notificationRepository, app.preferencesRepository)
                                )
                                val uiState by viewModel.uiState.collectAsState()
                                if (!uiState.isRunning && !uiState.progress.isFinished) {
                                    ExtendedFloatingActionButton(
                                        onClick = { viewModel.runTasks() },
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
                                    factory = RestoreViewModelFactory(app, app.repositoriesRepository, app.resticBinaryManager, app.resticRepository, app.appInfoRepository, app.notificationRepository, app.metadataRepository, app.preferencesRepository, navBackStackEntry?.arguments?.getString("snapshotId") ?: "")
                                )
                                val isRestoring by viewModel.isRestoring.collectAsState()
                                val restoreProgress by viewModel.restoreProgress.collectAsState()
                                if (!isRestoring && !restoreProgress.isFinished) {
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
                            // Use Application for factory creation to avoid explicit passing
                            val vm: HomeViewModel = viewModel(
                                factory = HomeViewModelFactory(app, app.repositoriesRepository, app.resticBinaryManager, app.resticRepository, app.appInfoRepository, app.metadataRepository)
                            )
                            val uiState by vm.uiState.collectAsState()
                            HomeScreen(
                                onSnapshotClick = { snapshotId -> navController.navigate("${Screen.SnapshotDetails.route}/$snapshotId") },
                                onMaintenanceClick = { navController.navigate(Screen.Maintenance.route) },
                                uiState = uiState,
                                onRefresh = { vm.refreshSnapshots() },
                                onPasswordEntered = { p, s -> vm.onPasswordEntered(p, s) },
                                onSftpPasswordEntered = { p, s -> vm.onSftpPasswordEntered(p, s) },
                                onRetryRepositoryPasswordEntry = { vm.onRetryRepositoryPasswordEntry() },
                                onRetrySftpPasswordEntry = { vm.onRetrySftpPasswordEntry() },
                                onDismissPasswordDialog = { vm.onDismissPasswordDialog() },
                                onDismissSftpPasswordDialog = { vm.onDismissSftpPasswordDialog() }
                            )
                        }
                        composable(Screen.Settings.route) {
                            val vm: SettingsViewModel = viewModel(
                                factory = SettingsViewModelFactory(app, app.rootRepository, app.resticBinaryManager, app.resticRepository, app.repositoriesRepository, app.notificationRepository, app.preferencesRepository)
                            )
                            SettingsScreen(viewModel = vm, onNavigateToLicenses = { navController.navigate(Screen.Licenses.route) })
                        }
                        composable(Screen.Backup.route) { BackupScreen(onNavigateUp = { navController.navigateUp() }) }
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
                        composable(Screen.Maintenance.route) { MaintenanceScreen(onNavigateUp = { navController.navigateUp() }) }
                    }
                }
            }
        }
    }
}
