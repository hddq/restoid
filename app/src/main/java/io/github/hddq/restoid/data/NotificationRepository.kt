package io.github.hddq.restoid.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.text.format.Formatter
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.hddq.restoid.MainActivity
import io.github.hddq.restoid.R
import io.github.hddq.restoid.ui.shared.OperationProgress
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
        const val PROGRESS_CHANNEL_ID = "progress_channel"
        const val FINISHED_CHANNEL_ID = "finished_channel"
        private const val PROGRESS_CHANNEL_NAME = "Ongoing Operations"
        private const val FINISHED_CHANNEL_NAME = "Completed Operations"
        private const val PROGRESS_NOTIFICATION_ID = 1
        private const val FINISHED_NOTIFICATION_ID = 2
    }

    fun createNotificationChannels() {
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Low importance channel for silent progress updates
        val progressChannel = NotificationChannel(
            PROGRESS_CHANNEL_ID,
            PROGRESS_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows ongoing backup and restore progress silently."
        }
        notificationManager.createNotificationChannel(progressChannel)

        // High importance channel for finished notifications to make them pop up
        val finishedChannel = NotificationChannel(
            FINISHED_CHANNEL_ID,
            FINISHED_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when a backup or restore has finished."
        }
        notificationManager.createNotificationChannel(finishedChannel)
    }

    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
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

        val title = progress.stageTitle.let { "$it ($percentage%)" }

        val builder = NotificationCompat.Builder(context, PROGRESS_CHANNEL_ID) // Use progress channel
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Keep progress low priority to avoid spam
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percentage, false)
            .setContentIntent(createPendingIntent()) // Make it clickable

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

        val builder = NotificationCompat.Builder(context, FINISHED_CHANNEL_ID) // Use finished channel
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Make it pop
            .setAutoCancel(true)
            .setContentIntent(createPendingIntent()) // Make it clickable

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

