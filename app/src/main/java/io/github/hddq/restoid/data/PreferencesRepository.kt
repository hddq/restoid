package io.github.hddq.restoid.data

import android.content.Context
import io.github.hddq.restoid.ui.backup.BackupTypes
import io.github.hddq.restoid.ui.maintenance.MaintenanceUiState
import io.github.hddq.restoid.ui.restore.RestoreTypes

class PreferencesRepository(context: Context) {
    private val prefs = context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE)

    // Maintenance Preferences
    fun saveMaintenanceState(state: MaintenanceUiState) {
        with(prefs.edit()) {
            putBoolean("maintenance_checkRepo", state.checkRepo)
            putBoolean("maintenance_pruneRepo", state.pruneRepo)
            putBoolean("maintenance_unlockRepo", state.unlockRepo)
            putBoolean("maintenance_readData", state.readData)
            putBoolean("maintenance_forgetSnapshots", state.forgetSnapshots)
            putInt("maintenance_keepLast", state.keepLast)
            putInt("maintenance_keepDaily", state.keepDaily)
            putInt("maintenance_keepWeekly", state.keepWeekly)
            putInt("maintenance_keepMonthly", state.keepMonthly)
            apply()
        }
    }

    fun loadMaintenanceState(): MaintenanceUiState {
        return MaintenanceUiState(
            checkRepo = prefs.getBoolean("maintenance_checkRepo", true),
            pruneRepo = prefs.getBoolean("maintenance_pruneRepo", false),
            unlockRepo = prefs.getBoolean("maintenance_unlockRepo", false),
            readData = prefs.getBoolean("maintenance_readData", false),
            forgetSnapshots = prefs.getBoolean("maintenance_forgetSnapshots", false),
            keepLast = prefs.getInt("maintenance_keepLast", 5),
            keepDaily = prefs.getInt("maintenance_keepDaily", 7),
            keepWeekly = prefs.getInt("maintenance_keepWeekly", 4),
            keepMonthly = prefs.getInt("maintenance_keepMonthly", 6)
        )
    }

    // Backup Preferences
    fun saveBackupTypes(types: BackupTypes) {
        with(prefs.edit()) {
            putBoolean("backup_apk", types.apk)
            putBoolean("backup_data", types.data)
            putBoolean("backup_deviceProtectedData", types.deviceProtectedData)
            putBoolean("backup_externalData", types.externalData)
            putBoolean("backup_obb", types.obb)
            putBoolean("backup_media", types.media)
            apply()
        }
    }

    fun loadBackupTypes(): BackupTypes {
        return BackupTypes(
            apk = prefs.getBoolean("backup_apk", true),
            data = prefs.getBoolean("backup_data", true),
            deviceProtectedData = prefs.getBoolean("backup_deviceProtectedData", true),
            externalData = prefs.getBoolean("backup_externalData", false),
            obb = prefs.getBoolean("backup_obb", false),
            media = prefs.getBoolean("backup_media", false)
        )
    }

    // Restore Preferences
    fun saveRestoreTypes(types: RestoreTypes) {
        with(prefs.edit()) {
            putBoolean("restore_apk", types.apk)
            putBoolean("restore_data", types.data)
            putBoolean("restore_deviceProtectedData", types.deviceProtectedData)
            putBoolean("restore_externalData", types.externalData)
            putBoolean("restore_obb", types.obb)
            putBoolean("restore_media", types.media)
            apply()
        }
    }

    fun loadRestoreTypes(): RestoreTypes {
        return RestoreTypes(
            apk = prefs.getBoolean("restore_apk", true),
            data = prefs.getBoolean("restore_data", true),
            deviceProtectedData = prefs.getBoolean("restore_deviceProtectedData", true),
            externalData = prefs.getBoolean("restore_externalData", false),
            obb = prefs.getBoolean("restore_obb", false),
            media = prefs.getBoolean("restore_media", false)
        )
    }

    fun saveAllowDowngrade(allow: Boolean) {
        prefs.edit().putBoolean("restore_allowDowngrade", allow).apply()
    }

    fun loadAllowDowngrade(): Boolean {
        return prefs.getBoolean("restore_allowDowngrade", false)
    }
}
