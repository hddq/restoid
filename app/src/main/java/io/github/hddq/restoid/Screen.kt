package io.github.hddq.restoid

sealed class Screen(val route: String, val titleRes: Int) {
    object Home : Screen("home", R.string.screen_home)
    object Settings : Screen("settings", R.string.screen_settings)
    object RunTasks : Screen("run_tasks", R.string.screen_run_tasks)
    object SnapshotDetails : Screen("snapshot_details", R.string.screen_snapshot_details)
    object Restore : Screen("restore", R.string.screen_restore)
    object Licenses : Screen("licenses", R.string.screen_licenses)
    object OperationProgress : Screen("operation_progress", R.string.screen_operation_progress)
}
