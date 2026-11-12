package io.github.hddq.restoid.data

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class CapabilityManager(
    private val context: Context,
    val rootRepository: RootRepository,
    val storagePermissionRepository: StoragePermissionRepository,
    coroutineScope: CoroutineScope,
    private val rootGrantedChecker: () -> Boolean = {
        runCatching { Shell.getShell().isRoot }.getOrDefault(false)
    },
    private val allFilesAccessChecker: () -> Boolean = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
) {
    val rootState: StateFlow<RootState> = rootRepository.rootState
    val storagePermissionState: StateFlow<StoragePermissionState> = storagePermissionRepository.permissionState

    /**
     * Reactive StateFlow indicating whether the app has storage access
     * (either through root access or granted storage permissions).
     */
    val hasStorageAccess: StateFlow<Boolean> = combine(rootState, storagePermissionState) { root, storage ->
        root == RootState.Granted || storage == StoragePermissionState.Granted
    }.stateIn(
        scope = coroutineScope,
        started = SharingStarted.Eagerly,
        initialValue = (rootRepository.rootState.value == RootState.Granted) || allFilesAccessChecker()
    )

    /**
     * Checks if root access is currently granted.
     */
    fun isRootGranted(): Boolean = rootGrantedChecker()

    /**
     * Checks if the app has external storage / all files access via Android permissions.
     */
    fun hasAllFilesAccessSync(): Boolean = allFilesAccessChecker()

    /**
     * Checks if the app has storage access either via root or storage permissions.
     */
    fun hasStorageAccessSync(): Boolean {
        return isRootGranted() || hasAllFilesAccessSync()
    }

    /**
     * Checks if the app can access a given repository backend.
     */
    fun canAccessRepository(backendType: RepositoryBackendType): Boolean {
        return backendType != RepositoryBackendType.LOCAL || hasStorageAccessSync()
    }

    /**
     * Checks if custom directories backup is allowed.
     */
    fun canBackupCustomDirectories(): Boolean = hasStorageAccessSync()

    /**
     * Checks if custom directories restore is allowed.
     */
    fun canRestoreCustomDirectories(): Boolean = hasStorageAccessSync()

    /**
     * Checks if app backups are allowed (requires root).
     */
    fun canBackupApps(): Boolean = isRootGranted()

    /**
     * Checks if app restores are allowed (requires root).
     */
    fun canRestoreApps(): Boolean = isRootGranted()

    /**
     * Refreshes the storage permission state from Android system.
     */
    fun refreshStoragePermission() {
        storagePermissionRepository.checkPermissionStatus()
    }
}
