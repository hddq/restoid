package io.github.hddq.restoid.data

import io.github.hddq.restoid.ui.shared.OperationProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationRepositoryTest {

    private fun formatContent(progress: OperationProgress): String {
        return NotificationRepository.formatProgressContentText(
            progress = progress,
            formatFileSize = { bytes -> "$bytes B" },
            formatFilesProgress = { processed, total ->
                if (total == 1) "$processed/$total file" else "$processed/$total files"
            },
            formatActivityItemsProgress = { activity, processed, total ->
                "$activity ($processed / $total)"
            },
            inProgressText = "Running…",
            scanningFilesText = "Scanning files…"
        )
    }

    @Test
    fun formatProgressContentText_backupWithBytes_formatsFilesAndBytes() {
        val text = formatContent(
            OperationProgress(
                filesProcessed = 4,
                totalFiles = 10,
                bytesProcessed = 512L,
                totalBytes = 1024L,
                currentActivity = "Backing up"
            )
        )
        assertEquals("4/10 files | 512 B / 1024 B", text)
    }

    @Test
    fun formatProgressContentText_pruneWithoutBytesWithActivity_formatsActivityAndItems() {
        val text = formatContent(
            OperationProgress(
                filesProcessed = 4,
                totalFiles = 12,
                bytesProcessed = 0L,
                totalBytes = 0L,
                currentActivity = "Repacking packs"
            )
        )
        assertEquals("Repacking packs (4 / 12)", text)
    }

    @Test
    fun formatProgressContentText_restoreWithoutBytesOrActivity_omitsZeroBytes() {
        val text = formatContent(
            OperationProgress(
                filesProcessed = 150,
                totalFiles = 500,
                bytesProcessed = 0L,
                totalBytes = 0L,
                currentActivity = ""
            )
        )
        assertEquals("150/500 files", text)
    }

    @Test
    fun formatProgressContentText_indeterminateActivityOnly_returnsActivity() {
        val text = formatContent(
            OperationProgress(
                filesProcessed = 0,
                totalFiles = 0,
                isStageIndeterminate = true,
                currentActivity = "Loading indexes…"
            )
        )
        assertEquals("Loading indexes…", text)
    }

    @Test
    fun finishedNotificationChannelId_usesSuccessChannelForSuccess() {
        val channelId = NotificationRepository.finishedNotificationChannelId(success = true)
        assertEquals(NotificationRepository.SUCCESS_CHANNEL_ID, channelId)
    }

    @Test
    fun finishedNotificationChannelId_usesErrorChannelForFailure() {
        val channelId = NotificationRepository.finishedNotificationChannelId(success = false)
        assertEquals(NotificationRepository.ERROR_CHANNEL_ID, channelId)
    }

    @Test
    fun finishedNotificationId_usesSuccessIdForSuccess() {
        val notificationId = NotificationRepository.finishedNotificationId(success = true)
        assertEquals(NotificationRepository.SUCCESS_NOTIFICATION_ID, notificationId)
    }

    @Test
    fun finishedNotificationId_usesErrorIdForFailure() {
        val notificationId = NotificationRepository.finishedNotificationId(success = false)
        assertEquals(NotificationRepository.ERROR_NOTIFICATION_ID, notificationId)
    }

    @Test
    fun notificationChannels_areAllDistinct() {
        val channelIds = setOf(
            NotificationRepository.PROGRESS_CHANNEL_ID,
            NotificationRepository.SUCCESS_CHANNEL_ID,
            NotificationRepository.ERROR_CHANNEL_ID,
            NotificationRepository.LEGACY_FINISHED_CHANNEL_ID
        )
        assertEquals(4, channelIds.size)
    }

    @Test
    fun notificationIds_areAllDistinct() {
        val notificationIds = setOf(
            NotificationRepository.PROGRESS_NOTIFICATION_ID,
            NotificationRepository.SUCCESS_NOTIFICATION_ID,
            NotificationRepository.ERROR_NOTIFICATION_ID
        )
        assertEquals(3, notificationIds.size)
    }

    @Test
    fun extraConstants_areDistinct() {
        assertNotEquals(
            NotificationRepository.EXTRA_OPEN_OPERATION_PROGRESS,
            NotificationRepository.EXTRA_OPEN_OPERATION_LOG_ID
        )
    }
}
