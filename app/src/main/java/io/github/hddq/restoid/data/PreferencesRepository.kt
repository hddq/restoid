package io.github.hddq.restoid.data

import android.content.Context
import android.content.SharedPreferences
import io.github.hddq.restoid.ui.shared.BackupTypes
import io.github.hddq.restoid.model.MaintenanceConfig
import io.github.hddq.restoid.ui.restore.RestoreTypes
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PreferencesRepository internal constructor(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE))

    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val KEY_REQUIRE_APP_UNLOCK = "require_app_unlock"
        const val KEY_VERIFIER_RECOVERY_PENDING = "restore_verifier_recovery_pending"
        const val KEY_VERIFIER_ORIG_PACKAGE = "restore_verifier_orig_package"
        const val KEY_VERIFIER_ORIG_ADB = "restore_verifier_orig_adb"
    }

    // Maintenance Preferences
    fun saveMaintenanceState(state: MaintenanceConfig) {
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

    fun loadMaintenanceState(): MaintenanceConfig {
        return MaintenanceConfig(
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

    // Run Tasks Backup Preferences
    fun saveRunTasksBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("runtasks_backup_enabled", enabled).apply()
    }

    fun loadRunTasksBackupEnabled(): Boolean {
        return prefs.getBoolean("runtasks_backup_enabled", true)
    }

    fun saveRunTasksSelectedApps(selectAll: Boolean, packageNames: Set<String>) {
        with(prefs.edit()) {
            putBoolean("runtasks_select_all_apps", selectAll)
            putStringSet("runtasks_selected_packages", packageNames)
            apply()
        }
    }

    fun loadRunTasksSelectAllApps(): Boolean {
        return prefs.getBoolean("runtasks_select_all_apps", true)
    }

    fun loadRunTasksSelectedPackages(): Set<String> {
        return prefs.getStringSet("runtasks_selected_packages", emptySet()) ?: emptySet()
    }

    fun saveRunTasksAppBackupTypes(types: Map<String, BackupTypes>) {
        prefs.edit()
            .putString("runtasks_app_backup_types", json.encodeToString(types))
            .apply()
    }

    fun loadRunTasksAppBackupTypes(): Map<String, BackupTypes> {
        val serialized = prefs.getString("runtasks_app_backup_types", null) ?: return emptyMap()
        return try {
            json.decodeFromString<Map<String, BackupTypes>>(serialized)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // Custom Directories Backup Preferences
    fun saveCustomDirectoriesBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("runtasks_custom_directories_enabled", enabled).apply()
    }

    fun loadCustomDirectoriesBackupEnabled(): Boolean {
        return prefs.getBoolean("runtasks_custom_directories_enabled", false)
    }

    fun saveCustomDirectories(allUris: Set<String>, selectedUris: Set<String>) {
        with(prefs.edit()) {
            putStringSet("runtasks_custom_directories_all", allUris)
            putStringSet("runtasks_custom_directories_selected", selectedUris)
            apply()
        }
    }

    fun loadCustomDirectoriesAll(): Set<String> {
        return prefs.getStringSet("runtasks_custom_directories_all", emptySet()) ?: emptySet()
    }

    fun loadCustomDirectoriesSelected(): Set<String> {
        return prefs.getStringSet("runtasks_custom_directories_selected", emptySet()) ?: emptySet()
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
            putBoolean("backup_permissions", types.permissions)
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
            media = prefs.getBoolean("backup_media", false),
            permissions = prefs.getBoolean("backup_permissions", true)
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
            putBoolean("restore_permissions", types.permissions)
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
            media = prefs.getBoolean("restore_media", false),
            permissions = prefs.getBoolean("restore_permissions", true)
        )
    }

    fun saveAllowDowngrade(allow: Boolean) {
        prefs.edit().putBoolean("restore_allowDowngrade", allow).apply()
    }

    fun loadAllowDowngrade(): Boolean {
        return prefs.getBoolean("restore_allowDowngrade", false)
    }

    fun saveBypassVerification(bypass: Boolean) {
        prefs.edit().putBoolean("restore_bypassVerification", bypass).apply()
    }

    fun loadBypassVerification(): Boolean {
        return prefs.getBoolean("restore_bypassVerification", true)
    }

    @Synchronized
    fun hasPendingVerifierRecovery(): Boolean {
        return prefs.getBoolean(KEY_VERIFIER_RECOVERY_PENDING, false)
    }

    @Synchronized
    fun savePendingVerifierRecoveryIfAbsent(packageVerifierEnable: String?, adbVerifierEnable: String?) {
        if (prefs.getBoolean(KEY_VERIFIER_RECOVERY_PENDING, false)) {
            return
        }
        prefs.edit()
            .putBoolean(KEY_VERIFIER_RECOVERY_PENDING, true)
            .putString(KEY_VERIFIER_ORIG_PACKAGE, packageVerifierEnable)
            .putString(KEY_VERIFIER_ORIG_ADB, adbVerifierEnable)
            .commit()
    }

    @Synchronized
    fun loadPendingVerifierRecovery(): VerifierSettingsBackup? {
        if (!prefs.getBoolean(KEY_VERIFIER_RECOVERY_PENDING, false)) {
            return null
        }
        return VerifierSettingsBackup(
            packageVerifierEnable = prefs.getString(KEY_VERIFIER_ORIG_PACKAGE, null),
            adbVerifierEnable = prefs.getString(KEY_VERIFIER_ORIG_ADB, null)
        )
    }

    @Synchronized
    fun clearPendingVerifierRecovery() {
        prefs.edit()
            .remove(KEY_VERIFIER_RECOVERY_PENDING)
            .remove(KEY_VERIFIER_ORIG_PACKAGE)
            .remove(KEY_VERIFIER_ORIG_ADB)
            .commit()
    }

    fun saveRequireAppUnlock(required: Boolean) {
        prefs.edit().putBoolean(KEY_REQUIRE_APP_UNLOCK, required).apply()
    }

    fun loadRequireAppUnlock(): Boolean {
        return prefs.getBoolean(KEY_REQUIRE_APP_UNLOCK, false)
    }
}

data class VerifierSettingsBackup(
    val packageVerifierEnable: String?,
    val adbVerifierEnable: String?
)
