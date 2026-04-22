package io.github.hddq.restoid.data

import android.Manifest
import android.app.Notification
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
        const val EXTRA_OPEN_OPERATION_PROGRESS = "extra_open_operation_progress"
        const val PROGRESS_CHANNEL_ID = "progress_channel"
        const val FINISHED_CHANNEL_ID = "finished_channel"
        const val PROGRESS_NOTIFICATION_ID = 1
        const val FINISHED_NOTIFICATION_ID = 2
    }

    fun createNotificationChannels() {
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Low importance channel for silent progress updates
        val progressChannel = NotificationChannel(
            PROGRESS_CHANNEL_ID,
            context.getString(R.string.channel_ongoing_operations),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_ongoing_description)
        }
        notificationManager.createNotificationChannel(progressChannel)

        // High importance channel for finished notifications to make them pop up
        val finishedChannel = NotificationChannel(
            FINISHED_CHANNEL_ID,
            context.getString(R.string.channel_completed_operations),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_completed_description)
        }
        notificationManager.createNotificationChannel(finishedChannel)
    }

    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_OPERATION_PROGRESS, true)
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun buildOperationProgressNotification(operationName: String, progress: OperationProgress): Notification {
        val percentage = (progress.stagePercentage.coerceIn(0f, 1f) * 100).toInt()
        val processedSize = Formatter.formatFileSize(context, progress.bytesProcessed)
        val totalSize = Formatter.formatFileSize(context, progress.totalBytes)
        val filesText = context.resources.getQuantityString(
            R.plurals.notification_files_progress,
            progress.totalFiles,
            progress.filesProcessed,
            progress.totalFiles
        )

        val contentText = if (progress.totalFiles > 0) {
            "$filesText | $processedSize / $totalSize"
        } else {
            context.getString(R.string.notification_scanning_files)
        }

        val title = progress.stageTitle.let { "$it ($percentage%)" }

        return NotificationCompat.Builder(context, PROGRESS_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percentage, false)
            .setContentIntent(createPendingIntent())
            .build()
    }

    fun buildOperationFinishedNotification(operationName: String, success: Boolean, summary: String): Notification {
        val title = if (success) {
            context.getString(R.string.notification_operation_finished_success, operationName)
        } else {
            context.getString(R.string.notification_operation_finished_failure, operationName)
        }

        return NotificationCompat.Builder(context, FINISHED_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(createPendingIntent())
            .build()
    }

    fun cancelProgressNotification() {
        NotificationManagerCompat.from(context).cancel(PROGRESS_NOTIFICATION_ID)
    }

    fun showOperationProgressNotification(operationName: String, progress: OperationProgress) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        NotificationManagerCompat.from(context)
            .notify(PROGRESS_NOTIFICATION_ID, buildOperationProgressNotification(operationName, progress))
    }


    fun showOperationFinishedNotification(operationName: String, success: Boolean, summary: String) {
        cancelProgressNotification()

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        NotificationManagerCompat.from(context)
            .notify(FINISHED_NOTIFICATION_ID, buildOperationFinishedNotification(operationName, success, summary))
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
