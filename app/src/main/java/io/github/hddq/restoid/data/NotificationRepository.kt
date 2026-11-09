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
        const val EXTRA_OPEN_OPERATION_LOG_ID = "extra_open_operation_log_id"
        const val PROGRESS_CHANNEL_ID = "progress_channel"
        const val SUCCESS_CHANNEL_ID = "success_channel"
        const val ERROR_CHANNEL_ID = "error_channel"
        const val LEGACY_FINISHED_CHANNEL_ID = "finished_channel"

        @Deprecated("Use SUCCESS_CHANNEL_ID or ERROR_CHANNEL_ID instead")
        const val FINISHED_CHANNEL_ID = LEGACY_FINISHED_CHANNEL_ID

        const val PROGRESS_NOTIFICATION_ID = 1
        const val SUCCESS_NOTIFICATION_ID = 2
        const val ERROR_NOTIFICATION_ID = 3

        @Deprecated("Use SUCCESS_NOTIFICATION_ID or ERROR_NOTIFICATION_ID instead")
        const val FINISHED_NOTIFICATION_ID = SUCCESS_NOTIFICATION_ID

        fun finishedNotificationChannelId(success: Boolean): String =
            if (success) SUCCESS_CHANNEL_ID else ERROR_CHANNEL_ID

        fun finishedNotificationId(success: Boolean): Int =
            if (success) SUCCESS_NOTIFICATION_ID else ERROR_NOTIFICATION_ID
    }

    fun createNotificationChannels() {
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Clean up legacy channel
        notificationManager.deleteNotificationChannel(LEGACY_FINISHED_CHANNEL_ID)

        // Low importance channel for silent progress updates
        val progressChannel = NotificationChannel(
            PROGRESS_CHANNEL_ID,
            context.getString(R.string.channel_ongoing_operations),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_ongoing_description)
        }
        notificationManager.createNotificationChannel(progressChannel)

        // Default importance channel for finished notifications to alert in notification shade
        val successChannel = NotificationChannel(
            SUCCESS_CHANNEL_ID,
            context.getString(R.string.channel_successful_operations),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_successful_description)
        }
        notificationManager.createNotificationChannel(successChannel)

        // High importance channel for errors to make them pop up
        val errorChannel = NotificationChannel(
            ERROR_CHANNEL_ID,
            context.getString(R.string.channel_failed_operations),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_failed_description)
        }
        notificationManager.createNotificationChannel(errorChannel)
    }

    private fun createPendingIntent(logId: String? = null): PendingIntent {
        val requestCode = logId?.hashCode() ?: System.currentTimeMillis().toInt()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (logId != null) {
                putExtra(EXTRA_OPEN_OPERATION_LOG_ID, logId)
            } else {
                putExtra(EXTRA_OPEN_OPERATION_PROGRESS, true)
            }
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun buildOperationProgressNotification(operationName: String, progress: OperationProgress): Notification {
        val isOverallIndeterminate = progress.isOverallIndeterminate
        val overallPercent = (progress.overallPercentage.coerceIn(0f, 1f) * 100).toInt()
        val stagePercent = (progress.stagePercentage.coerceIn(0f, 1f) * 100).toInt()
        
        val processedSize = Formatter.formatFileSize(context, progress.bytesProcessed)
        val totalSize = Formatter.formatFileSize(context, progress.totalBytes)
        val filesText = context.resources.getQuantityString(
            R.plurals.notification_files_progress,
            progress.totalFiles,
            progress.filesProcessed,
            progress.totalFiles
        )

        val contentText = when {
            progress.totalFiles > 0 -> "$filesText | $processedSize / $totalSize"
            progress.currentActivity.isNotBlank() -> progress.currentActivity
            progress.currentFile.isNotBlank() -> progress.currentFile
            progress.isStageIndeterminate || progress.isOverallIndeterminate -> context.getString(R.string.progress_in_progress)
            else -> context.getString(R.string.notification_scanning_files)
        }

        val title = if (progress.isStageIndeterminate) {
            progress.stageTitle
        } else {
            "${progress.stageTitle} ($stagePercent%)"
        }

        val match = Regex("^\\[(\\d+)/(\\d+)\\]").find(progress.stageTitle)
        val currentStage = match?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val totalStages = match?.groupValues?.get(2)?.toIntOrNull() ?: 1

        val progressStyle = NotificationCompat.ProgressStyle()
        if (totalStages > 1 && !isOverallIndeterminate) {
            val segments = mutableListOf<NotificationCompat.ProgressStyle.Segment>()
            val points = mutableListOf<NotificationCompat.ProgressStyle.Point>()
            val weight = 100 / totalStages
            for (i in 0 until totalStages) {
                val segmentColor = if (i < currentStage - 1) android.graphics.Color.DKGRAY
                else if (i == currentStage - 1) android.graphics.Color.BLUE
                else android.graphics.Color.LTGRAY
                segments.add(NotificationCompat.ProgressStyle.Segment(weight).setColor(segmentColor))
                
                if (i < totalStages - 1) {
                    val pointPos = weight * (i + 1)
                    val pointColor = if (i < currentStage - 1) android.graphics.Color.GREEN else android.graphics.Color.GRAY
                    points.add(NotificationCompat.ProgressStyle.Point(pointPos).setColor(pointColor))
                }
            }
            progressStyle.setProgressSegments(segments)
            progressStyle.setProgressPoints(points)
        }
        
        if (!isOverallIndeterminate) {
            progressStyle.setProgress(overallPercent)
        }

        val tickerText = if (isOverallIndeterminate) {
            context.getString(R.string.progress_in_progress)
        } else {
            "$overallPercent%"
        }

        val builder = NotificationCompat.Builder(context, PROGRESS_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, overallPercent, isOverallIndeterminate)
            .setTicker(tickerText)
            .setShortCriticalText(tickerText)
            .addExtras(android.os.Bundle().apply {
                putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, true)
            })
            .setContentIntent(createPendingIntent())

        if (!isOverallIndeterminate) {
            builder.setStyle(progressStyle)
        }

        return builder.build()
    }

    fun buildOperationFinishedNotification(
        operationName: String,
        success: Boolean,
        summary: String,
        logId: String? = null
    ): Notification {
        val title = if (success) {
            context.getString(R.string.notification_operation_finished_success, operationName)
        } else {
            context.getString(R.string.notification_operation_finished_failure, operationName)
        }

        val channelId = finishedNotificationChannelId(success)
        val priority = if (success) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH

        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(createPendingIntent(logId))
            .build()
    }

    fun cancelProgressNotification() {
        NotificationManagerCompat.from(context).cancel(PROGRESS_NOTIFICATION_ID)
    }

    fun showOperationProgressNotification(operationName: String, progress: OperationProgress) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        NotificationManagerCompat.from(context)
            .notify(PROGRESS_NOTIFICATION_ID, buildOperationProgressNotification(operationName, progress))
    }


    fun showOperationFinishedNotification(
        operationName: String,
        success: Boolean,
        summary: String,
        logId: String? = null
    ) {
        cancelProgressNotification()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationId = finishedNotificationId(success)
        NotificationManagerCompat.from(context)
            .notify(notificationId, buildOperationFinishedNotification(operationName, success, summary, logId))
    }


    fun checkPermissionStatus() {
        when {
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU -> {
                _permissionState.value = NotificationPermissionState.Granted
            }
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
