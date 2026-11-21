package io.github.hddq.restoid.data

import io.github.hddq.restoid.model.AppMetadata
import io.github.hddq.restoid.model.BackedUpCalendar
import io.github.hddq.restoid.model.BackedUpCalendarAttendee
import io.github.hddq.restoid.model.BackedUpCalendarEvent
import io.github.hddq.restoid.model.BackedUpCalendarReminder
import io.github.hddq.restoid.model.CalendarBackupArchive
import io.github.hddq.restoid.model.CalendarBackupMetadata
import io.github.hddq.restoid.model.CalendarEventDedupIndex
import io.github.hddq.restoid.model.CallLogsBackupMetadata
import io.github.hddq.restoid.model.ContactsBackupMetadata
import io.github.hddq.restoid.model.DeviceDataMetadata
import io.github.hddq.restoid.model.RestoidMetadata
import io.github.hddq.restoid.model.buildCalendarEventDedupKeys
import io.github.hddq.restoid.model.buildCalendarSelectionKey
import io.github.hddq.restoid.model.isRecurringEvent
import io.github.hddq.restoid.model.normalizeEventTiming
import io.github.hddq.restoid.model.toDedupKeys
import io.github.hddq.restoid.work.DeviceDataBackupSelection
import io.github.hddq.restoid.work.DeviceDataRestoreSelection
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarSerializationTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `CalendarBackupArchive round-trips calendars events reminders and attendees`() {
        val archive = CalendarBackupArchive(
            version = 1,
            calendars = listOf(
                BackedUpCalendar(
                    selectionKey = buildCalendarSelectionKey(
                        accountType = "com.google",
                        accountName = "alice@example.com",
                        name = "personal",
                        displayName = "Personal"
                    ),
                    name = "personal",
                    displayName = "Personal",
                    accountName = "alice@example.com",
                    accountType = "com.google",
                    ownerAccount = "alice@example.com",
                    color = -16738680,
                    timeZone = "America/New_York",
                    visible = 1,
                    syncEvents = 1,
                    accessLevel = 700,
                    events = listOf(
                        BackedUpCalendarEvent(
                            sourceEventId = 101L,
                            title = "Weekly Sync",
                            description = "Project review",
                            eventLocation = "Room 4B",
                            dtStart = 1_700_000_000_000L,
                            dtEnd = null,
                            duration = "PT1H",
                            eventTimezone = "America/New_York",
                            eventEndTimezone = null,
                            allDay = false,
                            rrule = "FREQ=WEEKLY;BYDAY=MO",
                            uid2445 = "weekly-sync-101@example.com",
                            organizer = "alice@example.com",
                            guestsCanModify = false,
                            guestsCanInviteOthers = true,
                            guestsCanSeeGuests = true,
                            reminders = listOf(
                                BackedUpCalendarReminder(minutes = 15, method = 1),
                                BackedUpCalendarReminder(minutes = 60, method = 4)
                            ),
                            attendees = listOf(
                                BackedUpCalendarAttendee(
                                    name = "Bob",
                                    email = "bob@example.com",
                                    relationship = 1,
                                    type = 1,
                                    status = 1
                                )
                            )
                        ),
                        BackedUpCalendarEvent(
                            sourceEventId = 102L,
                            title = "Company Holiday",
                            dtStart = 1_700_092_800_000L,
                            dtEnd = 1_700_179_200_000L,
                            eventTimezone = "UTC",
                            allDay = true
                        ),
                        BackedUpCalendarEvent(
                            sourceEventId = 103L,
                            title = "Weekly Sync (Moved)",
                            dtStart = 1_700_608_400_000L,
                            dtEnd = 1_700_612_000_000L,
                            eventTimezone = "America/New_York",
                            originalSourceEventId = 101L,
                            originalInstanceTime = 1_700_604_800_000L,
                            originalAllDay = false
                        )
                    )
                ),
                BackedUpCalendar(
                    selectionKey = buildCalendarSelectionKey(
                        accountType = "LOCAL",
                        accountName = "Restoid",
                        name = null,
                        displayName = "Empty Calendar"
                    ),
                    displayName = "Empty Calendar"
                )
            )
        )

        val encoded = json.encodeToString(archive)
        val decoded = json.decodeFromString<CalendarBackupArchive>(encoded)

        assertEquals(archive, decoded)
    }

    @Test
    fun `RestoidMetadata deserializes legacy deviceData without calendar and round-trips with calendar`() {
        val legacyJson = """
            {
              "apps": {
                "com.example.app": {
                  "size": 1024,
                  "types": ["apk"],
                  "versionCode": 1,
                  "versionName": "1.0"
                }
              },
              "customDirectories": {},
              "deviceData": {
                "contacts": {
                  "count": 42,
                  "size": 16384,
                  "path": "/staging/contacts"
                },
                "callLogs": {
                  "count": 150,
                  "size": 32768,
                  "path": "/staging/call_logs"
                }
              }
            }
        """.trimIndent()

        val legacyDecoded = json.decodeFromString<RestoidMetadata>(legacyJson)
        assertNull(legacyDecoded.deviceData?.calendar)
        assertEquals(42, legacyDecoded.deviceData?.contacts?.count)
        assertEquals(150, legacyDecoded.deviceData?.callLogs?.count)

        val withCalendar = RestoidMetadata(
            apps = mapOf(
                "com.example.app" to AppMetadata(
                    size = 1024L,
                    types = listOf("apk"),
                    versionCode = 1L,
                    versionName = "1.0"
                )
            ),
            customDirectories = emptyMap(),
            deviceData = DeviceDataMetadata(
                contacts = ContactsBackupMetadata(count = 42, size = 16_384L, path = "/staging/contacts"),
                callLogs = CallLogsBackupMetadata(count = 150, size = 32_768L, path = "/staging/call_logs"),
                calendar = CalendarBackupMetadata(
                    count = 275,
                    size = 65_536L,
                    path = "/staging/calendar",
                    calendarCount = 3
                )
            )
        )

        val encoded = json.encodeToString(withCalendar)
        val decoded = json.decodeFromString<RestoidMetadata>(encoded)
        assertEquals(withCalendar, decoded)

        val metadataWithoutCalendarCount = json.decodeFromString<CalendarBackupMetadata>(
            """{"count":10,"size":2048,"path":"/staging/calendar"}"""
        )
        assertNull(metadataWithoutCalendarCount.calendarCount)
    }

    @Test
    fun `normalizeEventTiming enforces Android CalendarContract invariants`() {
        // All-day non-recurring event forces UTC timezone and defaults dtEnd to +1 day
        val allDayDefaultEnd = normalizeEventTiming(
            dtStart = 1_700_000_000_000L,
            dtEnd = null,
            duration = null,
            allDay = true,
            eventTimezone = "America/Los_Angeles",
            calendarTimezone = "Europe/Berlin",
            rrule = null,
            rdate = null,
            exrule = null,
            exdate = null
        )
        assertEquals("UTC", allDayDefaultEnd.eventTimezone)
        assertEquals(1_700_000_000_000L + 86_400_000L, allDayDefaultEnd.dtEnd)
        assertNull(allDayDefaultEnd.duration)

        // Timed non-recurring event resolves timezone fallback and clamps dtEnd >= dtStart
        val timedClamped = normalizeEventTiming(
            dtStart = 1_700_000_000_000L,
            dtEnd = 1_699_999_000_000L,
            duration = "PT30M",
            allDay = false,
            eventTimezone = "  ",
            calendarTimezone = "Europe/Berlin",
            rrule = null,
            rdate = null,
            exrule = null,
            exdate = null,
            fallbackTimezone = "America/Chicago"
        )
        assertEquals("Europe/Berlin", timedClamped.eventTimezone)
        assertEquals(1_700_000_000_000L, timedClamped.dtEnd)
        assertNull(timedClamped.duration)

        // Recurring event clears dtEnd and computes duration from bounds if duration is absent
        assertTrue(isRecurringEvent(rrule = "FREQ=DAILY", rdate = null, exrule = null, exdate = null))
        assertTrue(isRecurringEvent(rrule = null, rdate = "20240101T090000Z", exrule = null, exdate = null))
        assertFalse(isRecurringEvent(rrule = " ", rdate = null, exrule = "", exdate = null))

        val recurringTimed = normalizeEventTiming(
            dtStart = 1_700_000_000_000L,
            dtEnd = 1_700_005_400_000L,
            duration = null,
            allDay = false,
            eventTimezone = null,
            calendarTimezone = null,
            rrule = "FREQ=WEEKLY",
            rdate = null,
            exrule = null,
            exdate = null,
            fallbackTimezone = "America/New_York"
        )
        assertNull(recurringTimed.dtEnd)
        assertEquals("PT5400S", recurringTimed.duration)
        assertEquals("America/New_York", recurringTimed.eventTimezone)

        val recurringAllDayNoEnd = normalizeEventTiming(
            dtStart = 1_700_000_000_000L,
            dtEnd = null,
            duration = null,
            allDay = true,
            eventTimezone = "Asia/Tokyo",
            calendarTimezone = null,
            rrule = "FREQ=YEARLY",
            rdate = null,
            exrule = null,
            exdate = null
        )
        assertNull(recurringAllDayNoEnd.dtEnd)
        assertEquals("P1D", recurringAllDayNoEnd.duration)
        assertEquals("UTC", recurringAllDayNoEnd.eventTimezone)
    }

    @Test
    fun `buildCalendarSelectionKey and event dedup keys normalize fields accurately`() {
        val key = buildCalendarSelectionKey(
            accountType = " com.google ",
            accountName = " user@example.com ",
            name = null,
            displayName = " Work "
        )
        assertEquals("com.google\u001Fuser@example.com\u001F\u001FWork", key)

        val event = BackedUpCalendarEvent(
            title = " Standup ",
            eventLocation = " Room A ",
            dtStart = 1_700_000_000_000L,
            allDay = false,
            rrule = " FREQ=DAILY ",
            uid2445 = " ABC-123@example.com "
        )
        val matchingKeys = buildCalendarEventDedupKeys(
            uid2445 = "ABC-123@example.com",
            title = "standup",
            dtStart = 1_700_000_000_000L,
            allDay = false,
            rrule = "FREQ=DAILY",
            eventLocation = "room a",
            originalInstanceTime = null
        )
        assertEquals(2, matchingKeys.size)
        assertEquals(event.toDedupKeys(), matchingKeys)

        val differentCaseUidKeys = event.copy(uid2445 = "abc-123@example.com").toDedupKeys()
        assertNotEquals(event.toDedupKeys(), differentCaseUidKeys)

        val noUidKeys = event.copy(uid2445 = "   ").toDedupKeys()
        assertEquals(1, noUidKeys.size)
        assertEquals(matchingKeys[1], noUidKeys[0])

        assertNotEquals(
            event.toDedupKeys(),
            event.copy(originalInstanceTime = 1_700_086_400_000L).toDedupKeys()
        )
        assertNotEquals(
            event.toDedupKeys(),
            event.copy(allDay = true).toDedupKeys()
        )
        assertNotEquals(
            event.copy(rrule = null, dtEnd = 1_700_003_600_000L).toDedupKeys(),
            event.copy(rrule = null, dtEnd = 1_700_007_200_000L).toDedupKeys()
        )
    }

    @Test
    fun `CalendarEventDedupIndex handles case-sensitive UIDs, UID-less promotion, recurring timezones, and exception parents`() {
        val index = CalendarEventDedupIndex()
        val firstEvent = BackedUpCalendarEvent(
            title = "1:1 Sync",
            eventLocation = "Zoom",
            dtStart = 1_700_000_000_000L,
            dtEnd = 1_700_001_800_000L,
            allDay = false,
            uid2445 = "uid-First@example.com"
        )
        index.register(10L, firstEvent)

        // Exact UID match (trimmed) resolves to 10L
        assertEquals(
            10L,
            index.findDuplicateEventId(firstEvent.copy(uid2445 = " uid-First@example.com "))
        )

        // Differently cased UID is distinct per RFC 5545 and must NOT match 10L
        val differentCaseUidEvent = firstEvent.copy(uid2445 = "uid-first@example.com")
        assertNull(index.findDuplicateEventId(differentCaseUidEvent))

        // Identical content + timing, but a different known UID -> must NOT match 10L
        val secondDistinctUidEvent = firstEvent.copy(uid2445 = "uid-second@example.com")
        assertNull(index.findDuplicateEventId(secondDistinctUidEvent))

        // After registering the second event, both can be looked up by their respective UIDs
        index.register(20L, secondDistinctUidEvent)
        assertEquals(10L, index.findDuplicateEventId(firstEvent))
        assertEquals(20L, index.findDuplicateEventId(secondDistinctUidEvent))

        // An event without a UID still falls back to content deduplication (e.g. when provider stripped UID_2445)
        val noUidCopy = firstEvent.copy(uid2445 = null)
        assertEquals(10L, index.findDuplicateEventId(noUidCopy))

        // An existing event stored without a UID matches the first incoming event by content,
        // and registering its incoming UID prevents a later event with a different UID from also matching row 30L
        val indexWithUidlessExisting = CalendarEventDedupIndex()
        indexWithUidlessExisting.register(30L, noUidCopy)
        assertEquals(30L, indexWithUidlessExisting.findDuplicateEventId(firstEvent))
        indexWithUidlessExisting.register(30L, firstEvent)
        assertNull(indexWithUidlessExisting.findDuplicateEventId(secondDistinctUidEvent))

        // Recurring UID-less events with identical start, duration, title, location, and rrule remain distinct when effective timezones differ
        val recurringNy = BackedUpCalendarEvent(
            title = "Daily Standup",
            dtStart = 1_700_000_000_000L,
            duration = "PT30M",
            allDay = false,
            eventTimezone = "America/New_York",
            rrule = "FREQ=DAILY"
        )
        val recurringUtc = recurringNy.copy(eventTimezone = "UTC")
        val recurringIndex = CalendarEventDedupIndex(defaultCalendarTimezone = "UTC")
        recurringIndex.register(40L, recurringNy)
        assertEquals(40L, recurringIndex.findDuplicateEventId(recurringNy))
        assertNull(recurringIndex.findDuplicateEventId(recurringUtc))

        // All-day recurring events normalize to UTC regardless of eventTimezone
        val allDayRecurringNy = recurringNy.copy(allDay = true, duration = "P1D")
        val allDayRecurringUtc = allDayRecurringNy.copy(eventTimezone = "UTC")
        recurringIndex.register(41L, allDayRecurringNy)
        assertEquals(41L, recurringIndex.findDuplicateEventId(allDayRecurringUtc))

        // UID-less exceptions from different parent series stay distinct even with identical content and originalInstanceTime
        val exceptionSeriesA = BackedUpCalendarEvent(
            title = "Moved Occurrence",
            dtStart = 1_700_100_000_000L,
            dtEnd = 1_700_103_600_000L,
            originalSourceEventId = 100L,
            originalInstanceTime = 1_700_092_800_000L
        )
        val exceptionSeriesB = exceptionSeriesA.copy(originalSourceEventId = 200L)
        recurringIndex.register(50L, exceptionSeriesA, resolvedOriginalId = 1000L)
        assertEquals(50L, recurringIndex.findDuplicateEventId(exceptionSeriesA, resolvedOriginalId = 1000L))
        assertNull(recurringIndex.findDuplicateEventId(exceptionSeriesB, resolvedOriginalId = 2000L))
    }

    @Test
    fun `DeviceData selection defaults leave calendar disabled and deserialize legacy payloads`() {
        val defaultBackup = DeviceDataBackupSelection()
        assertFalse(defaultBackup.contacts)
        assertFalse(defaultBackup.callLogs)
        assertFalse(defaultBackup.calendar)
        assertTrue(defaultBackup.selectAllCalendars)
        assertTrue(defaultBackup.selectedCalendarKeys.isEmpty())
        assertFalse(defaultBackup.anyEnabled())
        assertTrue(DeviceDataBackupSelection(calendar = true).anyEnabled())

        val defaultRestore = DeviceDataRestoreSelection()
        assertFalse(defaultRestore.contacts)
        assertFalse(defaultRestore.callLogs)
        assertFalse(defaultRestore.calendar)
        assertFalse(defaultRestore.anyEnabled())
        assertTrue(DeviceDataRestoreSelection(calendar = true).anyEnabled())

        val legacyBackupSelection = json.decodeFromString<DeviceDataBackupSelection>("""{"contacts":true}""")
        assertTrue(legacyBackupSelection.contacts)
        assertFalse(legacyBackupSelection.callLogs)
        assertFalse(legacyBackupSelection.calendar)
        assertTrue(legacyBackupSelection.selectAllCalendars)
        assertTrue(legacyBackupSelection.selectedCalendarKeys.isEmpty())

        val legacyRestoreSelection = json.decodeFromString<DeviceDataRestoreSelection>("""{"contacts":true}""")
        assertTrue(legacyRestoreSelection.contacts)
        assertFalse(legacyRestoreSelection.callLogs)
        assertFalse(legacyRestoreSelection.calendar)
    }
}
