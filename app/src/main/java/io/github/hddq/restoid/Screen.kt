package io.github.hddq.restoid

sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "Home")
    object Settings : Screen("settings", "Settings")
    object Backup : Screen("backup", "Backup")
    object SnapshotDetails : Screen("snapshot_details", "Snapshot Details")
    object Restore : Screen("restore", "Restore")
}
