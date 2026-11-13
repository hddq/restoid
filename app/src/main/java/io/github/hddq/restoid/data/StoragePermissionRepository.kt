package io.github.hddq.restoid.data

import android.content.Context
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class StoragePermissionState {
    object Granted : StoragePermissionState()
    object Denied : StoragePermissionState()
    object NotRequested : StoragePermissionState()
}

class StoragePermissionRepository(private val context: Context) {
    private val _permissionState = MutableStateFlow<StoragePermissionState>(StoragePermissionState.NotRequested)
    val permissionState = _permissionState.asStateFlow()

    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // Fallback for older versions if minSdk is lowered later
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    fun checkPermissionStatus() {
        _permissionState.value = if (hasStoragePermission()) {
            StoragePermissionState.Granted
        } else {
            StoragePermissionState.Denied
        }
    }
}
