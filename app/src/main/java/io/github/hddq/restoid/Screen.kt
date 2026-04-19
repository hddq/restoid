package io.github.hddq.restoid

sealed class Screen(val route: String, val titleRes: Int) {
    object Home : Screen("home", R.string.screen_home)
    object Settings : Screen("settings", R.string.screen_settings)
    object Backup : Screen("backup", R.string.screen_backup)
    object SnapshotDetails : Screen("snapshot_details", R.string.screen_snapshot_details)
    object Restore : Screen("restore", R.string.screen_restore)
    object Licenses : Screen("licenses", R.string.screen_licenses)
    object Maintenance : Screen("maintenance", R.string.screen_maintenance)
    object OperationProgress : Screen("operation_progress", R.string.screen_operation_progress)
}
