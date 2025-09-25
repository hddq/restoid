package io.github.hddq.restoid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.platform.LocalContext
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
import io.github.hddq.restoid.ui.maintenance.MaintenanceViewModel
import io.github.hddq.restoid.ui.maintenance.MaintenanceViewModelFactory
import io.github.hddq.restoid.ui.restore.RestoreViewModel
import io.github.hddq.restoid.ui.restore.RestoreViewModelFactory
import io.github.hddq.restoid.ui.screens.BackupScreen
import io.github.hddq.restoid.ui.screens.HomeScreen
import io.github.hddq.restoid.ui.screens.LicensesScreen
import io.github.hddq.restoid.ui.screens.MaintenanceScreen
import io.github.hddq.restoid.ui.screens.RestoreScreen
import io.github.hddq.restoid.ui.screens.SettingsScreen
import io.github.hddq.restoid.ui.screens.SnapshotDetailsScreen
import io.github.hddq.restoid.ui.snapshot.SnapshotDetailsViewModel
import io.github.hddq.restoid.ui.snapshot.SnapshotDetailsViewModelFactory
import io.github.hddq.restoid.ui.theme.RestoidTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val application = applicationContext as RestoidApplication
        enableEdgeToEdge()
        setContent {
            RestoidTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                // Determine if the bottom bar should be shown
                val showBottomBar = currentDestination?.route in listOf(Screen.Home.route, Screen.Settings.route)

                Scaffold(
                    topBar = {
                        // Dynamic TopAppBar: Show only on screens that need it
                        if (!showBottomBar) { // A good proxy for "detail" screens
                            TopAppBar(
                                title = {
                                    val title = when {
                                        currentDestination?.route == Screen.Backup.route -> "New Backup"
                                        currentDestination?.route == Screen.Maintenance.route -> "Maintenance"
                                        currentDestination?.route == Screen.Licenses.route -> "Open Source Licenses"
                                        currentDestination?.route?.startsWith(Screen.SnapshotDetails.route) == true -> "Snapshot Details"
                                        currentDestination?.route?.startsWith(Screen.Restore.route) == true -> "Restore Snapshot"
                                        else -> ""
                                    }
                                    Text(title)
                                },
                                navigationIcon = {
                                    IconButton(onClick = { navController.navigateUp() }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                },
                                actions = {
                                    // Actions are specific to certain screens
                                    if (currentDestination?.route?.startsWith(Screen.SnapshotDetails.route) == true) {
                                        val viewModel: SnapshotDetailsViewModel = viewModel(
                                            viewModelStoreOwner = navBackStackEntry!!,
                                            factory = SnapshotDetailsViewModelFactory(application, application.repositoriesRepository, application.resticRepository, application.appInfoRepository, application.metadataRepository)
                                        )
                                        IconButton(onClick = { viewModel.onForgetSnapshot() }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Forget Snapshot",
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
                            val items = listOf(
                                Screen.Home to Icons.Default.Home,
                                Screen.Settings to Icons.Default.Settings
                            )
                            NavigationBar {
                                items.forEach { (screen, icon) ->
                                    NavigationBarItem(
                                        icon = { Icon(icon, contentDescription = null) },
                                        label = { Text(screen.title) },
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
                        // Dynamic FAB: Show only on screens that need it
                        when (currentDestination?.route) {
                            Screen.Home.route -> {
                                val selectedRepo by application.repositoriesRepository.selectedRepository.collectAsState()
                                val isRepoSelected = selectedRepo != null
                                ExtendedFloatingActionButton(
                                    text = { Text("Backup") },
                                    icon = { Icon(Icons.Filled.Add, contentDescription = "Backup") },
                                    onClick = { if (isRepoSelected) { navController.navigate(Screen.Backup.route) } },
                                    containerColor = if (isRepoSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                    contentColor = if (isRepoSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                            Screen.Backup.route -> {
                                // FIX: Scope the ViewModel to the NavBackStackEntry to ensure a single instance is shared with the screen.
                                val viewModel: BackupViewModel = viewModel(
                                    viewModelStoreOwner = navBackStackEntry!!,
                                    factory = BackupViewModelFactory(application, application.repositoriesRepository, application.resticRepository, application.notificationRepository, application.appInfoRepository, application.preferencesRepository)
                                )
                                val isBackingUp by viewModel.isBackingUp.collectAsState()
                                val backupProgress by viewModel.backupProgress.collectAsState()
                                if (!isBackingUp && !backupProgress.isFinished) {
                                    ExtendedFloatingActionButton(
                                        onClick = { viewModel.startBackup() },
                                        icon = { Icon(Icons.Default.Backup, contentDescription = "Start Backup") },
                                        text = { Text("Start Backup") }
                                    )
                                }
                            }
                            Screen.Maintenance.route -> {
                                // FIX: Scope the ViewModel to the NavBackStackEntry.
                                val viewModel: MaintenanceViewModel = viewModel(
                                    viewModelStoreOwner = navBackStackEntry!!,
                                    factory = MaintenanceViewModelFactory(application.repositoriesRepository, application.resticRepository, application.notificationRepository, application.preferencesRepository)
                                )
                                val uiState by viewModel.uiState.collectAsState()
                                if (!uiState.isRunning && !uiState.progress.isFinished) {
                                    ExtendedFloatingActionButton(
                                        onClick = { viewModel.runTasks() },
                                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Run Tasks") },
                                        text = { Text("Run Tasks") }
                                    )
                                }
                            }
                            Screen.SnapshotDetails.route + "/{snapshotId}" -> {
                                ExtendedFloatingActionButton(
                                    text = { Text("Restore") },
                                    icon = { Icon(Icons.Default.Restore, contentDescription = "Restore Snapshot") },
                                    onClick = { navController.navigate("${Screen.Restore.route}/${navBackStackEntry?.arguments?.getString("snapshotId")}") }
                                )
                            }
                            Screen.Restore.route + "/{snapshotId}" -> {
                                // FIX: Scope the ViewModel to the NavBackStackEntry.
                                val viewModel: RestoreViewModel = viewModel(
                                    viewModelStoreOwner = navBackStackEntry!!,
                                    factory = RestoreViewModelFactory(application, application.repositoriesRepository, application.resticRepository, application.appInfoRepository, application.notificationRepository, application.metadataRepository, application.preferencesRepository, navBackStackEntry?.arguments?.getString("snapshotId") ?: "")
                                )
                                val isRestoring by viewModel.isRestoring.collectAsState()
                                val restoreProgress by viewModel.restoreProgress.collectAsState()
                                if (!isRestoring && !restoreProgress.isFinished) {
                                    ExtendedFloatingActionButton(
                                        onClick = { viewModel.startRestore() },
                                        icon = { Icon(Icons.Default.Restore, contentDescription = "Start Restore") },
                                        text = { Text("Start Restore") }
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route,
                        // Apply the padding from the Scaffold to the NavHost.
                        // Each screen's content will now live within this padded area.
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen(
                                onSnapshotClick = { snapshotId -> navController.navigate("${Screen.SnapshotDetails.route}/$snapshotId") },
                                onMaintenanceClick = { navController.navigate(Screen.Maintenance.route) }
                            )
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreen(onNavigateToLicenses = { navController.navigate(Screen.Licenses.route) })
                        }
                        composable(Screen.Backup.route) {
                            BackupScreen(onNavigateUp = { navController.navigateUp() })
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
                        composable(Screen.Licenses.route) {
                            LicensesScreen(onNavigateUp = { navController.navigateUp() })
                        }
                        composable(Screen.Maintenance.route) {
                            MaintenanceScreen(onNavigateUp = { navController.navigateUp() })
                        }
                    }
                }
            }
        }
    }
}
