package app.restoid.ui.backup

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restoid.model.AppInfo
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Data class to hold the state of backup types
data class BackupTypes(
    val apk: Boolean = true,
    val data: Boolean = true,
    val deviceProtectedData: Boolean = true,
    val externalData: Boolean = false,
    val obb: Boolean = false,
    val media: Boolean = false
)

class BackupViewModel(private val application: Application) : ViewModel() {

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps = _apps.asStateFlow()

    private val _backupTypes = MutableStateFlow(BackupTypes())
    val backupTypes = _backupTypes.asStateFlow()


    init {
        loadInstalledAppsWithRoot()
    }

    private fun loadInstalledAppsWithRoot() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = application.packageManager

            // Step 1: Get all third-party package names using root.
            val packageNamesResult = Shell.su("pm list packages -3").exec()

            if (packageNamesResult.isSuccess) {
                val installedApps = packageNamesResult.out
                    .map { it.removePrefix("package:").trim() }
                    .mapNotNull { packageName ->
                        // Step 2: For each package, get its APK path using root.
                        val pathResult = Shell.su("pm path $packageName").exec()
                        if (pathResult.isSuccess && pathResult.out.isNotEmpty()) {
                            val apkPath = pathResult.out.first().removePrefix("package:").trim()

                            // Step 3: Get info directly from the APK file. This bypasses visibility restrictions.
                            val packageInfo: PackageInfo? = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_META_DATA)

                            packageInfo?.applicationInfo?.let { appInfo ->
                                // We need to manually set these source dirs for the resources (label, icon) to be loaded correctly.
                                appInfo.sourceDir = apkPath
                                appInfo.publicSourceDir = apkPath

                                AppInfo(
                                    name = appInfo.loadLabel(pm).toString(),
                                    packageName = appInfo.packageName,
                                    icon = appInfo.loadIcon(pm)
                                )
                            }
                        } else {
                            null
                        }
                    }
                    .sortedBy { it.name.lowercase() }

                _apps.value = installedApps
            } else {
                // Handle the case where the root command fails.
                _apps.value = emptyList()
            }
        }
    }


    fun toggleAppSelection(packageName: String) {
        _apps.update { currentApps ->
            currentApps.map { app ->
                if (app.packageName == packageName) {
                    app.copy(isSelected = !app.isSelected)
                } else {
                    app
                }
            }
        }
    }

    fun toggleAll() {
        _apps.update { currentApps ->
            // If any app is not selected, select all. Otherwise, deselect all.
            val shouldSelectAll = currentApps.any { !it.isSelected }
            currentApps.map { it.copy(isSelected = shouldSelectAll) }
        }
    }

    fun setBackupApk(value: Boolean) {
        _backupTypes.update { it.copy(apk = value) }
    }
    fun setBackupData(value: Boolean) {
        _backupTypes.update { it.copy(data = value) }
    }
    fun setBackupDeviceProtectedData(value: Boolean) {
        _backupTypes.update { it.copy(deviceProtectedData = value) }
    }
    fun setBackupExternalData(value: Boolean) {
        _backupTypes.update { it.copy(externalData = value) }
    }
    fun setBackupObb(value: Boolean) {
        _backupTypes.update { it.copy(obb = value) }
    }
    fun setBackupMedia(value: Boolean) {
        _backupTypes.update { it.copy(media = value) }
    }
}
