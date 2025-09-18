package app.restoid.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.text.format.Formatter
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.restoid.R
import app.restoid.ui.shared.OperationProgress
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
        const val OPERATION_CHANNEL_ID = "operation_channel"
        private const val OPERATION_CHANNEL_NAME = "Operations"
        private const val PROGRESS_NOTIFICATION_ID = 1
        private const val FINISHED_NOTIFICATION_ID = 2
    }

    fun createNotificationChannels() {
        val channel = NotificationChannel(
            OPERATION_CHANNEL_ID,
            OPERATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications for backup and restore status"
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    fun showOperationProgressNotification(operationName: String, progress: OperationProgress) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val percentage = (progress.stagePercentage * 100).toInt()
        val processedSize = Formatter.formatFileSize(context, progress.bytesProcessed)
        val totalSize = Formatter.formatFileSize(context, progress.totalBytes)
        val filesText = "${progress.filesProcessed}/${progress.totalFiles} files"

        val contentText = if (progress.totalFiles > 0) {
            "$filesText | $processedSize / $totalSize"
        } else {
            "Scanning files..."
        }

        val title = progress.stageTitle?.let { "$it ($percentage%)" } ?: "$operationName in progress ($percentage%)"

        val builder = NotificationCompat.Builder(context, OPERATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // TODO: Use a proper backup icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percentage, false)

        NotificationManagerCompat.from(context).notify(PROGRESS_NOTIFICATION_ID, builder.build())
    }


    fun showOperationFinishedNotification(operationName: String, success: Boolean, summary: String) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        // Cancel the ongoing progress notification
        NotificationManagerCompat.from(context).cancel(PROGRESS_NOTIFICATION_ID)

        val title = if (success) "$operationName finished successfully" else "$operationName failed"

        val builder = NotificationCompat.Builder(context, OPERATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setSmallIcon(R.drawable.ic_launcher_foreground) // TODO: Different icon for success/fail?
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context).notify(FINISHED_NOTIFICATION_ID, builder.build())
    }


    fun checkPermissionStatus() {
        when {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED -> {
                _permissionState.value = NotificationPermissionState.Granted
            }
            else -> {
                _permissionState.value = NotificationPermissionState.Denied
            }
        }
    }
}
