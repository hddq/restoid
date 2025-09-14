package app.restoid

import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "Home")
    object Settings : Screen("settings", "Settings")
    object Backup : Screen("backup", "Backup")
    object SnapshotDetails : Screen("snapshot_details", "Snapshot Details")
}

