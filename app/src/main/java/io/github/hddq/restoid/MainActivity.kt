package io.github.hddq.restoid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.hddq.restoid.ui.screens.BackupScreen
import io.github.hddq.restoid.ui.screens.HomeScreen
import io.github.hddq.restoid.ui.screens.LicensesScreen
import io.github.hddq.restoid.ui.screens.MaintenanceScreen
import io.github.hddq.restoid.ui.screens.RestoreScreen
import io.github.hddq.restoid.ui.screens.SettingsScreen
import io.github.hddq.restoid.ui.screens.SnapshotDetailsScreen
import io.github.hddq.restoid.ui.theme.RestoidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val application = applicationContext as RestoidApplication
        enableEdgeToEdge()
        setContent {
            RestoidTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                val selectedRepo by application.repositoriesRepository.selectedRepository.collectAsState()

                Scaffold(
                    bottomBar = {
                        // Do not show bottom bar on screens that take up the full view
                        if (currentDestination?.route != Screen.Backup.route &&
                            currentDestination?.route?.startsWith(Screen.SnapshotDetails.route) == false &&
                            currentDestination?.route?.startsWith(Screen.Restore.route) == false &&
                            currentDestination?.route != Screen.Licenses.route &&
                            currentDestination?.route != Screen.Maintenance.route
                        ) {
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
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
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
                        // Show FAB only on HomeScreen
                        if (currentDestination?.route == Screen.Home.route) {
                            val isRepoSelected = selectedRepo != null
                            ExtendedFloatingActionButton(
                                text = { Text("Backup") },
                                icon = { Icon(Icons.Filled.Add, contentDescription = "Backup") },
                                onClick = {
                                    if (isRepoSelected) {
                                        navController.navigate(Screen.Backup.route)
                                    }
                                },
                                // Visually disable the FAB when no repo is selected
                                containerColor = if (isRepoSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                contentColor = if (isRepoSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
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
                                onSnapshotClick = { snapshotId ->
                                    navController.navigate("${Screen.SnapshotDetails.route}/$snapshotId")
                                },
                                onMaintenanceClick = {
                                    navController.navigate(Screen.Maintenance.route)
                                }
                            )
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                onNavigateToLicenses = { navController.navigate(Screen.Licenses.route) }
                            )
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
