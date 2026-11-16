package io.github.hddq.restoid.model

import io.github.hddq.restoid.work.BackupTypeSelection
import io.github.hddq.restoid.work.BackupWorkRequest
import io.github.hddq.restoid.work.RestoreTypeSelection
import io.github.hddq.restoid.work.RestoreWorkRequest
import io.github.hddq.restoid.work.RunTasksConfig
import io.github.hddq.restoid.work.RunTasksWorkRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDataModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun deviceDataType_fromSnapshotPath_andFromKey() {
        assertEquals(DeviceDataType.CONTACTS, DeviceDataType.fromSnapshotPath("/device-data/contacts.json"))
        assertEquals(DeviceDataType.CALL_HISTORY, DeviceDataType.fromSnapshotPath("device-data/call_history.json"))
        assertEquals(DeviceDataType.CALENDAR, DeviceDataType.fromSnapshotPath(" /tmp/stage/device-data/calendar.json "))
        assertNull(DeviceDataType.fromSnapshotPath("/device-data/restoid.json"))
        assertNull(DeviceDataType.fromSnapshotPath("/data/user/0/com.example/files/contacts.json"))

        assertEquals(DeviceDataType.CONTACTS, DeviceDataType.fromKey("contacts"))
        assertEquals(DeviceDataType.CALL_HISTORY, DeviceDataType.fromKey("call_history"))
        assertEquals(DeviceDataType.CALENDAR, DeviceDataType.fromKey("calendar"))
        assertNull(DeviceDataType.fromKey("sms"))
    }

    @Test
    fun deviceDataSelection_helpers_behaveAsExpected() {
        val empty = DeviceDataSelection()
        assertFalse(empty.anyEnabled())
        assertTrue(empty.selectedTypes().isEmpty())
        assertTrue(empty.enabledKeys().isEmpty())

        val contactsAndCalendar = empty
            .withType(DeviceDataType.CONTACTS, true)
            .withType(DeviceDataType.CALENDAR, true)

        assertTrue(contactsAndCalendar.anyEnabled())
        assertTrue(contactsAndCalendar.isEnabled(DeviceDataType.CONTACTS))
        assertFalse(contactsAndCalendar.isEnabled(DeviceDataType.CALL_HISTORY))
        assertTrue(contactsAndCalendar.isEnabled(DeviceDataType.CALENDAR))
        assertEquals(
            listOf(DeviceDataType.CONTACTS, DeviceDataType.CALENDAR),
            contactsAndCalendar.selectedTypes()
        )
        assertEquals(setOf("contacts", "calendar"), contactsAndCalendar.enabledKeys())

        assertEquals(
            contactsAndCalendar,
            DeviceDataSelection.fromTypes(setOf(DeviceDataType.CONTACTS, DeviceDataType.CALENDAR))
        )
    }

    @Test
    fun normalization_phoneEmailAndSignatures_areFormatAndOrderInvariant() {
        assertEquals("+15551234567", DeviceDataNormalization.normalizePhoneNumber(" +1 (555) 123-4567 "))
        assertEquals("00442079460958", DeviceDataNormalization.normalizePhoneNumber("0044 20-7946 0958"))
        assertEquals("", DeviceDataNormalization.normalizePhoneNumber("   "))
        assertEquals("custom-handle", DeviceDataNormalization.normalizePhoneNumber(" Custom-Handle "))
        assertEquals("alice@example.com", DeviceDataNormalization.normalizeEmail(" Alice@Example.COM "))

        val contactA = ContactEntryBackup(
            displayName = " Alice Smith ",
            rawContacts = listOf(
                RawContactBackup(
                    dataItems = listOf(
                        ContactDataItemBackup(
                            mimeType = "vnd.android.cursor.item/phone_v2",
                            dataColumns = mapOf("data1" to "+1 (555) 123-4567")
                        ),
                        ContactDataItemBackup(
                            mimeType = "vnd.android.cursor.item/email_v2",
                            dataColumns = mapOf("data1" to "Alice@Example.com")
                        )
                    )
                )
            )
        )
        val contactB = ContactEntryBackup(
            displayName = "alice smith",
            rawContacts = listOf(
                RawContactBackup(
                    dataItems = listOf(
                        ContactDataItemBackup(
                            mimeType = "vnd.android.cursor.item/email_v2",
                            dataColumns = mapOf("data1" to "alice@example.com")
                        ),
                        ContactDataItemBackup(
                            mimeType = "vnd.android.cursor.item/phone_v2",
                            dataColumns = mapOf("data1" to "+15551234567")
                        )
                    )
                )
            )
        )
        assertEquals(
            DeviceDataNormalization.contactSignature(contactA),
            DeviceDataNormalization.contactSignature(contactB)
        )

        val fallbackSig = DeviceDataNormalization.contactSignature(
            displayName = null,
            phones = emptyList(),
            emails = emptyList(),
            fallbackParts = listOf("vnd.android.cursor.item/organization:Acme", "vnd.android.cursor.item/note:VIP")
        )
        assertEquals(
            "vnd.android.cursor.item/note:vip|vnd.android.cursor.item/organization:acme",
            fallbackSig.fallbackKey
        )
    }

    @Test
    fun normalization_callAndEventDedupKeys_andEventTiming() {
        val callA = CallLogEntryBackup(number = "+1 (555) 000-1111", type = 2, date = 1_700_000_000_000L, duration = 42L)
        val callB = CallLogEntryBackup(number = "+15550001111", type = 2, date = 1_700_000_000_000L, duration = 42L)
        assertEquals(DeviceDataNormalization.callDedupKey(callA), DeviceDataNormalization.callDedupKey(callB))

        val eventA = CalendarEventBackup(title = " Standup ", dtstart = 1000L, rrule = "FREQ=DAILY", allDay = 0)
        val eventB = CalendarEventBackup(title = "Standup", dtstart = 1000L, rrule = "FREQ=DAILY ", allDay = 0)
        assertEquals(DeviceDataNormalization.eventDedupKey(eventA), DeviceDataNormalization.eventDedupKey(eventB))

        val recurringComputed = DeviceDataNormalization.normalizeEventTiming(
            dtstart = 10_000L,
            dtend = 3_610_000L,
            duration = null,
            rrule = "FREQ=WEEKLY",
            rdate = null
        )
        assertNull(recurringComputed.dtend)
        assertEquals("PT3600S", recurringComputed.duration)

        val recurringSubSecond = DeviceDataNormalization.normalizeEventTiming(
            dtstart = 10_000L,
            dtend = 10_500L,
            duration = null,
            rrule = null,
            rdate = "20261115T100000Z"
        )
        assertNull(recurringSubSecond.dtend)
        assertEquals("PT1S", recurringSubSecond.duration)

        val recurringExplicit = DeviceDataNormalization.normalizeEventTiming(
            dtstart = 10_000L,
            dtend = 20_000L,
            duration = "PT1H",
            rrule = "FREQ=MONTHLY",
            rdate = null
        )
        assertNull(recurringExplicit.dtend)
        assertEquals("PT1H", recurringExplicit.duration)

        val nonRecurringMissingEnd = DeviceDataNormalization.normalizeEventTiming(
            dtstart = 50_000L,
            dtend = null,
            duration = "PT1H",
            rrule = null,
            rdate = null
        )
        assertEquals(50_000L, nonRecurringMissingEnd.dtend)
        assertNull(nonRecurringMissingEnd.duration)

        val nonRecurringInvertedEnd = DeviceDataNormalization.normalizeEventTiming(
            dtstart = 50_000L,
            dtend = 40_000L,
            duration = null,
            rrule = " ",
            rdate = ""
        )
        assertEquals(50_000L, nonRecurringInvertedEnd.dtend)
        assertNull(nonRecurringInvertedEnd.duration)
    }

    @Test
    fun sortPayloads_producesDeterministicOutputRegardlessOfInputOrder() {
        val contactsUnsorted = ContactsBackupPayload(
            groups = listOf(
                ContactGroupBackup(title = "Work"),
                ContactGroupBackup(title = "Family"),
                ContactGroupBackup(title = "Work")
            ),
            contacts = listOf(
                ContactEntryBackup(
                    displayName = "Zoe",
                    rawContacts = listOf(
                        RawContactBackup(
                            groupTitles = listOf("Work", "Family", "Work"),
                            dataItems = listOf(
                                ContactDataItemBackup(
                                    mimeType = "vnd.android.cursor.item/phone_v2",
                                    dataColumns = mapOf("data2" to "2", "data1" to "222")
                                ),
                                ContactDataItemBackup(
                                    mimeType = "vnd.android.cursor.item/email_v2",
                                    dataColumns = mapOf("data1" to "zoe@example.com")
                                )
                            )
                        )
                    )
                ),
                ContactEntryBackup(displayName = "Alice")
            )
        )
        val contactsSorted = DeviceDataNormalization.sortContactsPayload(contactsUnsorted)
        assertEquals(listOf("Family", "Work"), contactsSorted.groups.map { it.title })
        assertEquals(listOf("Alice", "Zoe"), contactsSorted.contacts.map { it.displayName })
        val zoeRaw = contactsSorted.contacts[1].rawContacts.single()
        assertEquals(listOf("Family", "Work"), zoeRaw.groupTitles)
        assertEquals(
            listOf("vnd.android.cursor.item/email_v2", "vnd.android.cursor.item/phone_v2"),
            zoeRaw.dataItems.map { it.mimeType }
        )
        assertEquals(listOf("data1", "data2"), zoeRaw.dataItems[1].dataColumns.keys.toList())

        val callsSorted = DeviceDataNormalization.sortCallHistoryPayload(
            CallHistoryBackupPayload(
                calls = listOf(
                    CallLogEntryBackup(number = "222", type = 1, date = 200L, duration = 5L),
                    CallLogEntryBackup(number = "111", type = 2, date = 100L, duration = 10L)
                )
            )
        )
        assertEquals(listOf(100L, 200L), callsSorted.calls.map { it.date })

        val calendarSorted = DeviceDataNormalization.sortCalendarPayload(
            CalendarBackupPayload(
                calendars = listOf(
                    CalendarEntryBackup(
                        calendarDisplayName = "Work",
                        events = listOf(
                            CalendarEventBackup(
                                title = "Later",
                                dtstart = 2000L,
                                reminders = listOf(
                                    CalendarReminderBackup(30, 1),
                                    CalendarReminderBackup(10, 1),
                                    CalendarReminderBackup(10, 1)
                                )
                            ),
                            CalendarEventBackup(title = "Earlier", dtstart = 1000L)
                        )
                    ),
                    CalendarEntryBackup(calendarDisplayName = "Personal")
                )
            )
        )
        assertEquals(listOf("Personal", "Work"), calendarSorted.calendars.map { it.calendarDisplayName })
        val workEvents = calendarSorted.calendars[1].events
        assertEquals(listOf("Earlier", "Later"), workEvents.map { it.title })
        assertEquals(listOf(10, 30), workEvents[1].reminders.map { it.minutes })
    }

    @Test
    fun serialization_backwardCompatibility_defaultsDeviceDataFieldsWhenAbsent() {
        val legacyMetadataJson = """{"apps":{},"customDirectories":{"/sdcard/Notes":{"size":1024,"isDirectory":true}}}"""
        val decodedMetadata = json.decodeFromString<RestoidMetadata>(legacyMetadataJson)
        assertTrue(decodedMetadata.deviceData.isEmpty())
        assertEquals(1024L, decodedMetadata.customDirectories["/sdcard/Notes"]?.size)

        val backupReq = BackupWorkRequest(
            repositoryKey = "repo1",
            backupTypes = BackupTypeSelection(),
            selectedPackageNames = emptyList(),
            deviceData = DeviceDataSelection(contacts = true, calendar = true)
        )
        val encodedBackupReq = json.encodeToString(backupReq)
        val decodedBackupReq = json.decodeFromString<BackupWorkRequest>(encodedBackupReq)
        assertEquals(backupReq, decodedBackupReq)

        val legacyRunTasksConfigJson = """
            {
              "backupEnabled": true,
              "backupTypes": {},
              "selectedPackageNames": [],
              "unlockRepo": false,
              "forgetSnapshots": false,
              "pruneRepo": false,
              "checkRepo": false,
              "readData": false,
              "keepLast": 3,
              "keepDaily": 7,
              "keepWeekly": 4,
              "keepMonthly": 6
            }
        """.trimIndent()
        val decodedRunTasksConfig = json.decodeFromString<RunTasksConfig>(legacyRunTasksConfigJson)
        assertEquals(DeviceDataSelection(), decodedRunTasksConfig.deviceData)

        val restoreReq = RestoreWorkRequest(
            repositoryKey = "repo1",
            snapshotId = "abc123",
            restoreTypes = RestoreTypeSelection(),
            allowDowngrade = false,
            selectedApps = emptyList(),
            deviceData = DeviceDataSelection(callHistory = true)
        )
        val decodedRestoreReq = json.decodeFromString<RestoreWorkRequest>(json.encodeToString(restoreReq))
        assertEquals(DeviceDataSelection(callHistory = true), decodedRestoreReq.deviceData)

        val runTasksWorkReq = RunTasksWorkRequest(
            repositoryKey = "repo1",
            backupEnabled = true,
            backupTypes = BackupTypeSelection(),
            selectedPackageNames = emptyList(),
            deviceData = DeviceDataSelection(contacts = true),
            unlockRepo = false,
            forgetSnapshots = false,
            pruneRepo = false,
            checkRepo = false,
            readData = false,
            keepLast = 3,
            keepDaily = 7,
            keepWeekly = 4,
            keepMonthly = 6
        )
        val decodedRunTasksWorkReq = json.decodeFromString<RunTasksWorkRequest>(json.encodeToString(runTasksWorkReq))
        assertEquals(DeviceDataSelection(contacts = true), decodedRunTasksWorkReq.deviceData)
    }
}
