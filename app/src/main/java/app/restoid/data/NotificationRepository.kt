package app.restoid.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class NotificationPermissionState {
    object Granted : NotificationPermissionState()
    object Denied : NotificationPermissionState()
    object NotRequested : NotificationPermissionState()
}

class NotificationRepository(private val context: Context) {

    private val _permissionState = MutableStateFlow<NotificationPermissionState>(NotificationPermissionState.NotRequested)
    val permissionState = _permissionState.asStateFlow()

    fun checkPermissionStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    _permissionState.value = NotificationPermissionState.Granted
                }
                else -> {
                    // We can't know for sure if it was denied or just not requested yet
                    // A better approach would be to check a flag in shared preferences
                    // For now, let's assume if not granted, it's denied for simplicity in UI
                    _permissionState.value = NotificationPermissionState.Denied
                }
            }
        } else {
            // Permissions are granted by default on older versions
            _permissionState.value = NotificationPermissionState.Granted
        }
    }
}
