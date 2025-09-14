package app.restoid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.restoid.ui.screens.BackupScreen
import app.restoid.ui.screens.HomeScreen
import app.restoid.ui.screens.SettingsScreen
import app.restoid.ui.screens.SnapshotDetailsScreen
import app.restoid.ui.theme.RestoidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RestoidTheme {
                val navController = rememberNavController()

                Scaffold(
                    bottomBar = {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination

                        // Do not show bottom bar on the backup or snapshot details screen
                        if (currentDestination?.route != Screen.Backup.route && currentDestination?.route?.startsWith(Screen.SnapshotDetails.route) == false) {
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
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen(
                                onNavigateToBackup = { navController.navigate(Screen.Backup.route) },
                                onSnapshotClick = { snapshotId ->
                                    navController.navigate("${Screen.SnapshotDetails.route}/$snapshotId")
                                }
                            )
                        }
                        composable(Screen.Settings.route) { SettingsScreen() }
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
                    }
                }
            }
        }
    }
}

