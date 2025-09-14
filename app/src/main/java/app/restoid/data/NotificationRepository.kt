package app.restoid.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.restoid.R
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

    companion object {
        const val BACKUP_CHANNEL_ID = "backup_channel"
        private const val BACKUP_CHANNEL_NAME = "Backups"
        private const val BACKUP_PROGRESS_NOTIFICATION_ID = 1
        private const val BACKUP_FINISHED_NOTIFICATION_ID = 2
    }

    fun createNotificationChannels() {
        val channel = NotificationChannel(
            BACKUP_CHANNEL_ID,
            BACKUP_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications for backup status"
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    fun showBackupStartingNotification() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Can't post notification, permission not granted.
            // The UI should prevent this from being called if permission is denied.
            return
        }
        val builder = NotificationCompat.Builder(context, BACKUP_CHANNEL_ID)
            .setContentTitle("Backup in progress")
            .setContentText("Preparing to back up your apps...")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // You should have a proper backup icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(0, 0, true) // Indeterminate progress

        NotificationManagerCompat.from(context).notify(BACKUP_PROGRESS_NOTIFICATION_ID, builder.build())
    }

    fun showBackupFinishedNotification(success: Boolean, summary: String) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        // Cancel the ongoing progress notification
        NotificationManagerCompat.from(context).cancel(BACKUP_PROGRESS_NOTIFICATION_ID)

        val title = if (success) "Backup finished successfully" else "Backup failed"

        val builder = NotificationCompat.Builder(context, BACKUP_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(summary)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Different icon for success/fail?
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context).notify(BACKUP_FINISHED_NOTIFICATION_ID, builder.build())
    }


    fun checkPermissionStatus() {
        // Condition (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) is always true for minSdk 35
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
    }
}
