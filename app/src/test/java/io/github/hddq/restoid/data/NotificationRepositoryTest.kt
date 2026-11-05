package io.github.hddq.restoid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationRepositoryTest {

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
}
