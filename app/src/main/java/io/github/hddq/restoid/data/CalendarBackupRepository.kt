package io.github.hddq.restoid.data

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import io.github.hddq.restoid.R
import io.github.hddq.restoid.model.BackedUpCalendar
import io.github.hddq.restoid.model.BackedUpCalendarAttendee
import io.github.hddq.restoid.model.BackedUpCalendarEvent
import io.github.hddq.restoid.model.BackedUpCalendarReminder
import io.github.hddq.restoid.model.CalendarBackupArchive
import io.github.hddq.restoid.model.CalendarEventDedupIndex
import io.github.hddq.restoid.model.CalendarInfo
import io.github.hddq.restoid.model.buildCalendarSelectionKey
import io.github.hddq.restoid.model.normalizeEventTiming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.TimeZone

data class ExportedCalendarResult(
    val calendarCount: Int,
    val eventCount: Int,
    val totalSizeBytes: Long,
    val outputDir: File
)

data class ImportedCalendarResult(
    val restoredCount: Int,
    val skippedCount: Int,
    val failedCount: Int
)

private data class DeviceCalendarRecord(
    val info: CalendarInfo,
    val ownerAccount: String?,
    val visible: Int?,
    val syncEvents: Int?,
    val accessLevel: Int
)

private data class EventInsertResult(
    val eventId: Long?,
    val childrenComplete: Boolean
)

class CalendarBackupRepository(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun hasReadCalendarPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun hasWriteCalendarPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun queryDeviceCalendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        if (!hasReadCalendarPermission()) return@withContext emptyList()
        loadDeviceCalendars().map { it.info }
    }

    suspend fun exportCalendars(
        outputDir: File,
        selectAllCalendars: Boolean,
        selectedCalendarKeys: Set<String>,
        shouldStop: () -> Boolean = { false }
    ): ExportedCalendarResult = withContext(Dispatchers.IO) {
        outputDir.deleteRecursively()
        outputDir.mkdirs()

        val allCalendars = loadDeviceCalendars(shouldStop)
        val calendarsToExport = if (selectAllCalendars) {
            allCalendars
        } else {
            allCalendars.filter { it.info.selectionKey in selectedCalendarKeys }
        }
        if (!selectAllCalendars && calendarsToExport.isEmpty()) {
            throw IllegalStateException(context.getString(R.string.error_calendar_none_selected))
        }
        val selectedIds = calendarsToExport.mapTo(HashSet()) { it.info.id }

        val rawEventsByCalendar = linkedMapOf<Long, MutableList<BackedUpCalendarEvent>>()
        val exportedEventIds = HashSet<Long>()

        if (selectedIds.isNotEmpty() && !shouldStop()) {
            val resolver = context.contentResolver
            resolver.query(
                CalendarContract.Events.CONTENT_URI,
                EVENT_PROJECTION,
                "${CalendarContract.Events.DELETED} = 0",
                null,
                "${CalendarContract.Events.DTSTART} ASC, ${CalendarContract.Events._ID} ASC"
            )?.use { c ->
                val idIdx = c.getColumnIndex(CalendarContract.Events._ID)
                val calIdIdx = c.getColumnIndex(CalendarContract.Events.CALENDAR_ID)
                val titleIdx = c.getColumnIndex(CalendarContract.Events.TITLE)
                val descIdx = c.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                val locIdx = c.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                val dtStartIdx = c.getColumnIndex(CalendarContract.Events.DTSTART)
                val dtEndIdx = c.getColumnIndex(CalendarContract.Events.DTEND)
                val durationIdx = c.getColumnIndex(CalendarContract.Events.DURATION)
                val tzIdx = c.getColumnIndex(CalendarContract.Events.EVENT_TIMEZONE)
                val endTzIdx = c.getColumnIndex(CalendarContract.Events.EVENT_END_TIMEZONE)
                val allDayIdx = c.getColumnIndex(CalendarContract.Events.ALL_DAY)
                val rruleIdx = c.getColumnIndex(CalendarContract.Events.RRULE)
                val rdateIdx = c.getColumnIndex(CalendarContract.Events.RDATE)
                val exruleIdx = c.getColumnIndex(CalendarContract.Events.EXRULE)
                val exdateIdx = c.getColumnIndex(CalendarContract.Events.EXDATE)
                val origIdIdx = c.getColumnIndex(CalendarContract.Events.ORIGINAL_ID)
                val origSyncIdIdx = c.getColumnIndex(CalendarContract.Events.ORIGINAL_SYNC_ID)
                val origTimeIdx = c.getColumnIndex(CalendarContract.Events.ORIGINAL_INSTANCE_TIME)
                val origAllDayIdx = c.getColumnIndex(CalendarContract.Events.ORIGINAL_ALL_DAY)
                val accessIdx = c.getColumnIndex(CalendarContract.Events.ACCESS_LEVEL)
                val availIdx = c.getColumnIndex(CalendarContract.Events.AVAILABILITY)
                val statusIdx = c.getColumnIndex(CalendarContract.Events.STATUS)
                val uidIdx = c.getColumnIndex(CalendarContract.Events.UID_2445)
                val organizerIdx = c.getColumnIndex(CalendarContract.Events.ORGANIZER)
                val guestsModifyIdx = c.getColumnIndex(CalendarContract.Events.GUESTS_CAN_MODIFY)
                val guestsInviteIdx = c.getColumnIndex(CalendarContract.Events.GUESTS_CAN_INVITE_OTHERS)
                val guestsSeeIdx = c.getColumnIndex(CalendarContract.Events.GUESTS_CAN_SEE_GUESTS)
                val customPkgIdx = c.getColumnIndex(CalendarContract.Events.CUSTOM_APP_PACKAGE)
                val customUriIdx = c.getColumnIndex(CalendarContract.Events.CUSTOM_APP_URI)
                val colorIdx = c.getColumnIndex(CalendarContract.Events.EVENT_COLOR)

                while (c.moveToNext() && !shouldStop()) {
                    val eventId = c.getLongOrNull(idIdx) ?: continue
                    val calendarId = c.getLongOrNull(calIdIdx) ?: continue
                    if (calendarId !in selectedIds) continue
                    val dtStart = c.getLongOrNull(dtStartIdx) ?: continue

                    exportedEventIds.add(eventId)
                    rawEventsByCalendar.getOrPut(calendarId) { mutableListOf() }.add(
                        BackedUpCalendarEvent(
                            sourceEventId = eventId,
                            title = c.getStringOrNull(titleIdx),
                            description = c.getStringOrNull(descIdx),
                            eventLocation = c.getStringOrNull(locIdx),
                            dtStart = dtStart,
                            dtEnd = c.getLongOrNull(dtEndIdx),
                            duration = c.getStringOrNull(durationIdx),
                            eventTimezone = c.getStringOrNull(tzIdx),
                            eventEndTimezone = c.getStringOrNull(endTzIdx),
                            allDay = (c.getIntOrNull(allDayIdx) ?: 0) == 1,
                            rrule = c.getStringOrNull(rruleIdx),
                            rdate = c.getStringOrNull(rdateIdx),
                            exrule = c.getStringOrNull(exruleIdx),
                            exdate = c.getStringOrNull(exdateIdx),
                            originalSourceEventId = c.getLongOrNull(origIdIdx),
                            originalSyncId = c.getStringOrNull(origSyncIdIdx),
                            originalInstanceTime = c.getLongOrNull(origTimeIdx),
                            originalAllDay = c.getIntOrNull(origAllDayIdx)?.let { it == 1 },
                            accessLevel = c.getIntOrNull(accessIdx),
                            availability = c.getIntOrNull(availIdx),
                            status = c.getIntOrNull(statusIdx),
                            uid2445 = c.getStringOrNull(uidIdx),
                            organizer = c.getStringOrNull(organizerIdx),
                            guestsCanModify = c.getIntOrNull(guestsModifyIdx)?.let { it == 1 },
                            guestsCanInviteOthers = c.getIntOrNull(guestsInviteIdx)?.let { it == 1 },
                            guestsCanSeeGuests = c.getIntOrNull(guestsSeeIdx)?.let { it == 1 },
                            customAppPackage = c.getStringOrNull(customPkgIdx),
                            customAppUri = c.getStringOrNull(customUriIdx),
                            eventColor = c.getIntOrNull(colorIdx)
                        )
                    )
                }
            } ?: throw IllegalStateException("Calendar events query returned null cursor")
        }

        val remindersByEventId = if (exportedEventIds.isNotEmpty() && !shouldStop()) {
            loadRemindersForEvents(exportedEventIds, shouldStop)
        } else {
            emptyMap()
        }
        val attendeesByEventId = if (exportedEventIds.isNotEmpty() && !shouldStop()) {
            loadAttendeesForEvents(exportedEventIds, shouldStop)
        } else {
            emptyMap()
        }

        var totalEventCount = 0
        val backedUpCalendars = calendarsToExport.map { record ->
            val events = rawEventsByCalendar[record.info.id].orEmpty().map { event ->
                val eid = event.sourceEventId
                event.copy(
                    reminders = eid?.let { remindersByEventId[it] }.orEmpty(),
                    attendees = eid?.let { attendeesByEventId[it] }.orEmpty()
                )
            }
            totalEventCount += events.size
            BackedUpCalendar(
                selectionKey = record.info.selectionKey,
                name = record.info.name.takeIf { it.isNotBlank() },
                displayName = record.info.displayName,
                accountName = record.info.accountName.takeIf { it.isNotBlank() },
                accountType = record.info.accountType.takeIf { it.isNotBlank() },
                ownerAccount = record.ownerAccount,
                color = record.info.color,
                timeZone = record.info.timeZone,
                visible = record.visible,
                syncEvents = record.syncEvents,
                accessLevel = record.accessLevel,
                events = events
            )
        }

        val archive = CalendarBackupArchive(
            version = 1,
            calendars = backedUpCalendars
        )
        val archiveFile = File(outputDir, CALENDAR_FILE_NAME)
        archiveFile.writeText(json.encodeToString(archive))

        val totalSizeBytes = outputDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        ExportedCalendarResult(
            calendarCount = backedUpCalendars.size,
            eventCount = totalEventCount,
            totalSizeBytes = totalSizeBytes,
            outputDir = outputDir
        )
    }

    suspend fun importCalendars(
        inputDir: File,
        shouldStop: () -> Boolean = { false },
        onEventProcessed: (processed: Int, total: Int, title: String?) -> Unit = { _, _, _ -> }
    ): ImportedCalendarResult = withContext(Dispatchers.IO) {
        val archiveFile = File(inputDir, CALENDAR_FILE_NAME)
        require(archiveFile.exists()) { "Calendar backup file not found: ${archiveFile.absolutePath}" }
        check(hasReadCalendarPermission()) { "READ_CALENDAR permission is required to restore calendars." }
        check(hasWriteCalendarPermission()) { "WRITE_CALENDAR permission is required to restore calendars." }

        val archive = json.decodeFromString<CalendarBackupArchive>(archiveFile.readText())
        val existingLocalCalendars = loadLocalCalendars()

        var restoredCount = 0
        var skippedCount = 0
        var failedCount = 0
        var processedEvents = 0
        val totalEvents = archive.calendars.sumOf { it.events.size }

        for (calendar in archive.calendars) {
            if (shouldStop()) break

            val targetCalendarId = ensureLocalCalendar(calendar, existingLocalCalendars)
            if (targetCalendarId == null) {
                android.util.Log.w(TAG, "Failed to resolve or create local calendar '${calendar.displayName}'")
                failedCount += calendar.events.size
                processedEvents += calendar.events.size
                onEventProcessed(processedEvents.coerceAtMost(totalEvents), totalEvents, calendar.displayName)
                continue
            }

            val dedupIndex = loadExistingEventDedupIndex(targetCalendarId)
            val sourceIdToRestoredId = HashMap<Long, Long>()

            // Restore master/standalone events before recurrence exceptions so ORIGINAL_ID can be remapped.
            val (exceptions, baseEvents) = calendar.events.partition {
                it.originalSourceEventId != null || it.originalInstanceTime != null
            }
            val orderedEvents = baseEvents + exceptions

            for (event in orderedEvents) {
                if (shouldStop()) {
                    return@withContext ImportedCalendarResult(
                        restoredCount = restoredCount,
                        skippedCount = skippedCount,
                        failedCount = failedCount
                    )
                }

                processedEvents++
                val label = event.title?.takeIf { it.isNotBlank() } ?: calendar.displayName
                onEventProcessed(processedEvents, totalEvents, label)

                val existingMatchId = dedupIndex.findDuplicateEventId(event)
                if (existingMatchId != null) {
                    event.sourceEventId?.let { sourceIdToRestoredId[it] = existingMatchId }
                    val childrenComplete = ensureEventChildren(
                        eventId = existingMatchId,
                        reminders = event.reminders,
                        attendees = event.attendees
                    )
                    if (childrenComplete) {
                        skippedCount++
                    } else {
                        failedCount++
                    }
                    continue
                }

                val isException = event.originalSourceEventId != null || event.originalInstanceTime != null
                val resolvedOriginalId = event.originalSourceEventId?.let { sourceIdToRestoredId[it] }
                if (isException && resolvedOriginalId == null) {
                    android.util.Log.w(
                        TAG,
                        "Skipping recurrence exception '${event.title}' because its parent event could not be resolved"
                    )
                    failedCount++
                    continue
                }

                val insertResult = insertEventWithChildren(
                    calendarId = targetCalendarId,
                    calendarTimezone = calendar.timeZone,
                    event = event,
                    resolvedOriginalId = resolvedOriginalId
                )
                val insertedId = insertResult.eventId
                if (insertedId != null && insertedId > 0L) {
                    event.sourceEventId?.let { sourceIdToRestoredId[it] = insertedId }
                    dedupIndex.register(insertedId, event)
                    if (insertResult.childrenComplete) {
                        restoredCount++
                    } else {
                        failedCount++
                    }
                } else {
                    failedCount++
                }
            }
        }

        ImportedCalendarResult(
            restoredCount = restoredCount,
            skippedCount = skippedCount,
            failedCount = failedCount
        )
    }

    private fun loadDeviceCalendars(
        shouldStop: () -> Boolean = { false }
    ): List<DeviceCalendarRecord> {
        val records = mutableListOf<DeviceCalendarRecord>()
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            CALENDAR_PROJECTION,
            "${CalendarContract.Calendars.DELETED} = 0",
            null,
            "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} COLLATE NOCASE ASC, ${CalendarContract.Calendars._ID} ASC"
        ) ?: throw IllegalStateException("Calendar query returned null cursor")

        cursor.use { c ->
            val idIdx = c.getColumnIndex(CalendarContract.Calendars._ID)
            val nameIdx = c.getColumnIndex(CalendarContract.Calendars.NAME)
            val displayNameIdx = c.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accountNameIdx = c.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
            val accountTypeIdx = c.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
            val ownerIdx = c.getColumnIndex(CalendarContract.Calendars.OWNER_ACCOUNT)
            val colorIdx = c.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR)
            val tzIdx = c.getColumnIndex(CalendarContract.Calendars.CALENDAR_TIME_ZONE)
            val accessIdx = c.getColumnIndex(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
            val visibleIdx = c.getColumnIndex(CalendarContract.Calendars.VISIBLE)
            val syncIdx = c.getColumnIndex(CalendarContract.Calendars.SYNC_EVENTS)

            while (c.moveToNext() && !shouldStop()) {
                val id = c.getLongOrNull(idIdx) ?: continue
                val rawName = c.getStringOrNull(nameIdx).orEmpty()
                val rawDisplayName = c.getStringOrNull(displayNameIdx).orEmpty()
                val accountName = c.getStringOrNull(accountNameIdx).orEmpty()
                val accountType = c.getStringOrNull(accountTypeIdx).orEmpty()
                val displayName = rawDisplayName.ifBlank {
                    rawName.ifBlank { accountName.ifBlank { "Calendar $id" } }
                }
                val accessLevel = c.getIntOrNull(accessIdx) ?: CalendarContract.Calendars.CAL_ACCESS_NONE
                val isReadOnly = accessLevel < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                val selectionKey = buildCalendarSelectionKey(
                    accountType = accountType,
                    accountName = accountName,
                    name = rawName,
                    displayName = displayName
                )
                records.add(
                    DeviceCalendarRecord(
                        info = CalendarInfo(
                            id = id,
                            selectionKey = selectionKey,
                            name = rawName,
                            displayName = displayName,
                            accountName = accountName,
                            accountType = accountType,
                            color = c.getIntOrNull(colorIdx) ?: DEFAULT_CALENDAR_COLOR,
                            timeZone = c.getStringOrNull(tzIdx),
                            isReadOnly = isReadOnly
                        ),
                        ownerAccount = c.getStringOrNull(ownerIdx),
                        visible = c.getIntOrNull(visibleIdx),
                        syncEvents = c.getIntOrNull(syncIdx),
                        accessLevel = accessLevel
                    )
                )
            }
        }
        return records
    }

    private fun loadRemindersForEvents(
        eventIds: Set<Long>,
        shouldStop: () -> Boolean
    ): Map<Long, List<BackedUpCalendarReminder>> {
        val map = HashMap<Long, MutableList<BackedUpCalendarReminder>>()
        val cursor = context.contentResolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(
                CalendarContract.Reminders.EVENT_ID,
                CalendarContract.Reminders.MINUTES,
                CalendarContract.Reminders.METHOD
            ),
            null,
            null,
            null
        ) ?: throw IllegalStateException("Calendar reminders query returned null cursor")

        cursor.use { c ->
            while (c.moveToNext() && !shouldStop()) {
                val eventId = c.getLongOrNull(0) ?: continue
                if (eventId !in eventIds) continue
                val minutes = c.getIntOrNull(1) ?: continue
                val method = c.getIntOrNull(2) ?: CalendarContract.Reminders.METHOD_DEFAULT
                map.getOrPut(eventId) { mutableListOf() }.add(
                    BackedUpCalendarReminder(minutes = minutes, method = method)
                )
            }
        }
        return map
    }

    private fun loadAttendeesForEvents(
        eventIds: Set<Long>,
        shouldStop: () -> Boolean
    ): Map<Long, List<BackedUpCalendarAttendee>> {
        val map = HashMap<Long, MutableList<BackedUpCalendarAttendee>>()
        val cursor = context.contentResolver.query(
            CalendarContract.Attendees.CONTENT_URI,
            arrayOf(
                CalendarContract.Attendees.EVENT_ID,
                CalendarContract.Attendees.ATTENDEE_NAME,
                CalendarContract.Attendees.ATTENDEE_EMAIL,
                CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
                CalendarContract.Attendees.ATTENDEE_TYPE,
                CalendarContract.Attendees.ATTENDEE_STATUS
            ),
            null,
            null,
            null
        ) ?: throw IllegalStateException("Calendar attendees query returned null cursor")

        cursor.use { c ->
            while (c.moveToNext() && !shouldStop()) {
                val eventId = c.getLongOrNull(0) ?: continue
                if (eventId !in eventIds) continue
                val name = c.getStringOrNull(1)
                val email = c.getStringOrNull(2)
                if (name.isNullOrBlank() && email.isNullOrBlank()) continue
                map.getOrPut(eventId) { mutableListOf() }.add(
                    BackedUpCalendarAttendee(
                        name = name,
                        email = email,
                        relationship = c.getIntOrNull(3),
                        type = c.getIntOrNull(4),
                        status = c.getIntOrNull(5)
                    )
                )
            }
        }
        return map
    }

    private data class LocalCalendarEntry(
        val id: Long,
        val name: String,
        val displayName: String
    )

    private fun loadLocalCalendars(): MutableList<LocalCalendarEntry> {
        val list = mutableListOf<LocalCalendarEntry>()
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.NAME,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
            ),
            "${CalendarContract.Calendars.DELETED} = 0 AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ?",
            arrayOf(CalendarContract.ACCOUNT_TYPE_LOCAL),
            "${CalendarContract.Calendars._ID} ASC"
        ) ?: throw IllegalStateException("Local calendars query returned null cursor")

        cursor.use { c ->
            while (c.moveToNext()) {
                val id = c.getLongOrNull(0) ?: continue
                val name = c.getStringOrNull(1).orEmpty().trim()
                val displayName = c.getStringOrNull(2).orEmpty().trim()
                list.add(LocalCalendarEntry(id = id, name = name, displayName = displayName))
            }
        }
        return list
    }

    private fun ensureLocalCalendar(
        backedUp: BackedUpCalendar,
        existingLocalCalendars: MutableList<LocalCalendarEntry>
    ): Long? {
        val targetDisplayName = backedUp.displayName.trim().ifEmpty {
            backedUp.name?.trim().orEmpty().ifEmpty { "Calendar" }
        }
        val targetName = backedUp.name?.trim().orEmpty().ifEmpty { targetDisplayName }

        val exactMatch = existingLocalCalendars.firstOrNull { local ->
            (targetName.isNotEmpty() && local.name == targetName) ||
                local.displayName == targetDisplayName
        }
        if (exactMatch != null) return exactMatch.id

        val ignoreCaseMatch = existingLocalCalendars.firstOrNull { local ->
            (targetName.isNotEmpty() && local.name.equals(targetName, ignoreCase = true)) ||
                local.displayName.equals(targetDisplayName, ignoreCase = true)
        }
        if (ignoreCaseMatch != null) return ignoreCaseMatch.id

        val syncUri = localSyncAdapterUri(CalendarContract.Calendars.CONTENT_URI)
        val op = ContentProviderOperation.newInsert(syncUri)
            .withValue(CalendarContract.Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT_NAME)
            .withValue(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .withValue(CalendarContract.Calendars.NAME, targetName)
            .withValue(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, targetDisplayName)
            .withValue(CalendarContract.Calendars.OWNER_ACCOUNT, LOCAL_ACCOUNT_NAME)
            .withValue(CalendarContract.Calendars.CALENDAR_COLOR, backedUp.color ?: DEFAULT_CALENDAR_COLOR)
            .withValue(
                CalendarContract.Calendars.CALENDAR_TIME_ZONE,
                backedUp.timeZone?.takeIf { it.isNotBlank() } ?: TimeZone.getDefault().id
            )
            .withValue(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            .withValue(CalendarContract.Calendars.VISIBLE, backedUp.visible ?: 1)
            .withValue(CalendarContract.Calendars.SYNC_EVENTS, 1)
            .build()

        return try {
            val insertedUri = context.contentResolver.applyBatch(
                CalendarContract.AUTHORITY,
                arrayListOf(op)
            ).firstOrNull()?.uri
            val calendarId = insertedUri?.let { ContentUris.parseId(it) } ?: -1L
            if (calendarId > 0L) {
                existingLocalCalendars.add(
                    LocalCalendarEntry(
                        id = calendarId,
                        name = targetName,
                        displayName = targetDisplayName
                    )
                )
                calendarId
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to create local calendar '$targetDisplayName'", e)
            null
        }
    }

    private fun loadExistingEventDedupIndex(calendarId: Long): CalendarEventDedupIndex {
        val index = CalendarEventDedupIndex()
        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.UID_2445,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.DURATION,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.RRULE,
                CalendarContract.Events.RDATE,
                CalendarContract.Events.EXRULE,
                CalendarContract.Events.EXDATE,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.ORIGINAL_INSTANCE_TIME
            ),
            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DELETED} = 0",
            arrayOf(calendarId.toString()),
            null
        ) ?: throw IllegalStateException("Existing calendar events query returned null cursor")

        cursor.use { c ->
            while (c.moveToNext()) {
                val eventId = c.getLongOrNull(0) ?: continue
                val uid2445 = c.getStringOrNull(1)
                val title = c.getStringOrNull(2)
                val dtStart = c.getLongOrNull(3) ?: continue
                val dtEnd = c.getLongOrNull(4)
                val duration = c.getStringOrNull(5)
                val allDay = (c.getIntOrNull(6) ?: 0) == 1
                val rrule = c.getStringOrNull(7)
                val rdate = c.getStringOrNull(8)
                val exrule = c.getStringOrNull(9)
                val exdate = c.getStringOrNull(10)
                val location = c.getStringOrNull(11)
                val originalInstanceTime = c.getLongOrNull(12)

                index.register(
                    eventId = eventId,
                    uid2445 = uid2445,
                    title = title,
                    dtStart = dtStart,
                    dtEnd = dtEnd,
                    duration = duration,
                    allDay = allDay,
                    rrule = rrule,
                    rdate = rdate,
                    exrule = exrule,
                    exdate = exdate,
                    eventLocation = location,
                    originalInstanceTime = originalInstanceTime
                )
            }
        }
        return index
    }

    private fun insertEventWithChildren(
        calendarId: Long,
        calendarTimezone: String?,
        event: BackedUpCalendarEvent,
        resolvedOriginalId: Long?
    ): EventInsertResult {
        val timing = normalizeEventTiming(
            dtStart = event.dtStart,
            dtEnd = event.dtEnd,
            duration = event.duration,
            allDay = event.allDay,
            eventTimezone = event.eventTimezone,
            calendarTimezone = calendarTimezone,
            rrule = event.rrule,
            rdate = event.rdate,
            exrule = event.exrule,
            exdate = event.exdate,
            fallbackTimezone = TimeZone.getDefault().id
        )

        val fullOps = buildEventInsertBatch(
            calendarId = calendarId,
            event = event,
            timing = timing,
            resolvedOriginalId = resolvedOriginalId,
            includeOptionalColumns = true,
            includeChildren = true
        )
        try {
            val results = context.contentResolver.applyBatch(CalendarContract.AUTHORITY, fullOps)
            val eventId = results.firstOrNull()?.uri?.let { ContentUris.parseId(it) } ?: -1L
            val allResultsValid = results.size == fullOps.size && results.all { it.uri != null }
            if (eventId > 0L) {
                return EventInsertResult(eventId = eventId, childrenComplete = allResultsValid)
            }
        } catch (batchEx: Exception) {
            android.util.Log.w(TAG, "Full event batch insert failed for '${event.title}', retrying minimal", batchEx)
        }

        // Fallback: insert core event first, then attach missing reminders/attendees individually.
        val minimalOps = buildEventInsertBatch(
            calendarId = calendarId,
            event = event,
            timing = timing,
            resolvedOriginalId = resolvedOriginalId,
            includeOptionalColumns = false,
            includeChildren = false
        )
        val insertedId = try {
            val results = context.contentResolver.applyBatch(CalendarContract.AUTHORITY, minimalOps)
            results.firstOrNull()?.uri?.let { ContentUris.parseId(it) } ?: -1L
        } catch (fallbackEx: Exception) {
            android.util.Log.w(TAG, "Minimal event insert failed for '${event.title}'", fallbackEx)
            return EventInsertResult(eventId = null, childrenComplete = false)
        }
        if (insertedId <= 0L) return EventInsertResult(eventId = null, childrenComplete = false)

        val childrenComplete = ensureEventChildren(
            eventId = insertedId,
            reminders = event.reminders,
            attendees = event.attendees
        )
        return EventInsertResult(eventId = insertedId, childrenComplete = childrenComplete)
    }

    private fun ensureEventChildren(
        eventId: Long,
        reminders: List<BackedUpCalendarReminder>,
        attendees: List<BackedUpCalendarAttendee>
    ): Boolean {
        if (reminders.isEmpty() && attendees.isEmpty()) return true
        var allSucceeded = true

        if (reminders.isNotEmpty()) {
            val existingReminders = try {
                loadExistingRemindersForEvent(eventId)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to query existing reminders for event $eventId", e)
                allSucceeded = false
                null
            }
            if (existingReminders != null) {
                val remainingExisting = existingReminders.toMutableList()
                for (reminder in reminders) {
                    val existingIdx = remainingExisting.indexOfFirst {
                        it.minutes == reminder.minutes && it.method == reminder.method
                    }
                    if (existingIdx >= 0) {
                        remainingExisting.removeAt(existingIdx)
                        continue
                    }
                    val inserted = try {
                        val results = context.contentResolver.applyBatch(
                            CalendarContract.AUTHORITY,
                            arrayListOf(buildReminderOperation(eventId, reminder))
                        )
                        results.firstOrNull()?.uri != null
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "Failed to insert reminder for event $eventId", e)
                        false
                    }
                    if (!inserted) {
                        allSucceeded = false
                    }
                }
            }
        }

        if (attendees.isNotEmpty()) {
            val existingAttendees = try {
                loadExistingAttendeesForEvent(eventId)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to query existing attendees for event $eventId", e)
                allSucceeded = false
                null
            }
            if (existingAttendees != null) {
                val remainingExisting = existingAttendees.toMutableList()
                for (attendee in attendees) {
                    val existingIdx = remainingExisting.indexOfFirst { existing ->
                        attendeesMatch(existing, attendee)
                    }
                    if (existingIdx >= 0) {
                        remainingExisting.removeAt(existingIdx)
                        continue
                    }
                    val inserted = try {
                        val results = context.contentResolver.applyBatch(
                            CalendarContract.AUTHORITY,
                            arrayListOf(buildAttendeeOperation(eventId, attendee))
                        )
                        results.firstOrNull()?.uri != null
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "Failed to insert attendee for event $eventId", e)
                        false
                    }
                    if (!inserted) {
                        allSucceeded = false
                    }
                }
            }
        }

        return allSucceeded
    }

    private fun loadExistingRemindersForEvent(eventId: Long): List<BackedUpCalendarReminder> {
        val list = mutableListOf<BackedUpCalendarReminder>()
        val cursor = context.contentResolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(
                CalendarContract.Reminders.MINUTES,
                CalendarContract.Reminders.METHOD
            ),
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            null
        ) ?: throw IllegalStateException("Event reminders query returned null cursor")

        cursor.use { c ->
            while (c.moveToNext()) {
                val minutes = c.getIntOrNull(0) ?: continue
                val method = c.getIntOrNull(1) ?: CalendarContract.Reminders.METHOD_DEFAULT
                list.add(BackedUpCalendarReminder(minutes = minutes, method = method))
            }
        }
        return list
    }

    private fun loadExistingAttendeesForEvent(eventId: Long): List<BackedUpCalendarAttendee> {
        val list = mutableListOf<BackedUpCalendarAttendee>()
        val cursor = context.contentResolver.query(
            CalendarContract.Attendees.CONTENT_URI,
            arrayOf(
                CalendarContract.Attendees.ATTENDEE_NAME,
                CalendarContract.Attendees.ATTENDEE_EMAIL,
                CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
                CalendarContract.Attendees.ATTENDEE_TYPE,
                CalendarContract.Attendees.ATTENDEE_STATUS
            ),
            "${CalendarContract.Attendees.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            null
        ) ?: throw IllegalStateException("Event attendees query returned null cursor")

        cursor.use { c ->
            while (c.moveToNext()) {
                val name = c.getStringOrNull(0)
                val email = c.getStringOrNull(1)
                if (name.isNullOrBlank() && email.isNullOrBlank()) continue
                list.add(
                    BackedUpCalendarAttendee(
                        name = name,
                        email = email,
                        relationship = c.getIntOrNull(2),
                        type = c.getIntOrNull(3),
                        status = c.getIntOrNull(4)
                    )
                )
            }
        }
        return list
    }

    private fun attendeesMatch(
        existing: BackedUpCalendarAttendee,
        target: BackedUpCalendarAttendee
    ): Boolean {
        val existingEmail = existing.email?.trim().orEmpty().lowercase()
        val targetEmail = target.email?.trim().orEmpty().lowercase()
        val existingName = existing.name?.trim().orEmpty().lowercase()
        val targetName = target.name?.trim().orEmpty().lowercase()
        if (existingEmail != targetEmail || existingName != targetName) return false
        if (target.relationship != null && existing.relationship != null && existing.relationship != target.relationship) return false
        if (target.type != null && existing.type != null && existing.type != target.type) return false
        if (target.status != null && existing.status != null && existing.status != target.status) return false
        return true
    }

    private fun buildEventInsertBatch(
        calendarId: Long,
        event: BackedUpCalendarEvent,
        timing: io.github.hddq.restoid.model.NormalizedEventTiming,
        resolvedOriginalId: Long?,
        includeOptionalColumns: Boolean,
        includeChildren: Boolean
    ): ArrayList<ContentProviderOperation> {
        val ops = ArrayList<ContentProviderOperation>()
        val eventOp = ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
            .withValue(CalendarContract.Events.CALENDAR_ID, calendarId)
            .withValue(CalendarContract.Events.DTSTART, timing.dtStart)
            .withValue(CalendarContract.Events.EVENT_TIMEZONE, timing.eventTimezone)
            .withValue(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
            .withValue(CalendarContract.Events.HAS_ALARM, if (event.reminders.isNotEmpty()) 1 else 0)
            .withValue(CalendarContract.Events.HAS_ATTENDEE_DATA, if (event.attendees.isNotEmpty()) 1 else 0)
            .apply {
                timing.dtEnd?.let { withValue(CalendarContract.Events.DTEND, it) }
                timing.duration?.let { withValue(CalendarContract.Events.DURATION, it) }
                event.title?.let { withValue(CalendarContract.Events.TITLE, it) }
                event.description?.let { withValue(CalendarContract.Events.DESCRIPTION, it) }
                event.eventLocation?.let { withValue(CalendarContract.Events.EVENT_LOCATION, it) }
                if (!event.allDay) {
                    event.eventEndTimezone?.takeIf { it.isNotBlank() }?.let {
                        withValue(CalendarContract.Events.EVENT_END_TIMEZONE, it)
                    }
                }
                event.rrule?.takeIf { it.isNotBlank() }?.let { withValue(CalendarContract.Events.RRULE, it) }
                event.rdate?.takeIf { it.isNotBlank() }?.let { withValue(CalendarContract.Events.RDATE, it) }
                event.exrule?.takeIf { it.isNotBlank() }?.let { withValue(CalendarContract.Events.EXRULE, it) }
                event.exdate?.takeIf { it.isNotBlank() }?.let { withValue(CalendarContract.Events.EXDATE, it) }
                event.accessLevel?.let { withValue(CalendarContract.Events.ACCESS_LEVEL, it) }
                event.availability?.let { withValue(CalendarContract.Events.AVAILABILITY, it) }
                event.status?.let { withValue(CalendarContract.Events.STATUS, it) }
                if (resolvedOriginalId != null) {
                    withValue(CalendarContract.Events.ORIGINAL_ID, resolvedOriginalId)
                    event.originalInstanceTime?.let { withValue(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, it) }
                    event.originalAllDay?.let { withValue(CalendarContract.Events.ORIGINAL_ALL_DAY, if (it) 1 else 0) }
                }
                if (includeOptionalColumns) {
                    event.uid2445?.takeIf { it.isNotBlank() }?.let { withValue(CalendarContract.Events.UID_2445, it) }
                    event.organizer?.takeIf { it.isNotBlank() }?.let { withValue(CalendarContract.Events.ORGANIZER, it) }
                    if (resolvedOriginalId != null) {
                        event.originalSyncId?.takeIf { it.isNotBlank() }?.let {
                            withValue(CalendarContract.Events.ORIGINAL_SYNC_ID, it)
                        }
                    }
                    event.guestsCanModify?.let { withValue(CalendarContract.Events.GUESTS_CAN_MODIFY, if (it) 1 else 0) }
                    event.guestsCanInviteOthers?.let { withValue(CalendarContract.Events.GUESTS_CAN_INVITE_OTHERS, if (it) 1 else 0) }
                    event.guestsCanSeeGuests?.let { withValue(CalendarContract.Events.GUESTS_CAN_SEE_GUESTS, if (it) 1 else 0) }
                    event.customAppPackage?.takeIf { it.isNotBlank() }?.let {
                        withValue(CalendarContract.Events.CUSTOM_APP_PACKAGE, it)
                    }
                    event.customAppUri?.takeIf { it.isNotBlank() }?.let {
                        withValue(CalendarContract.Events.CUSTOM_APP_URI, it)
                    }
                    event.eventColor?.let { withValue(CalendarContract.Events.EVENT_COLOR, it) }
                }
            }
            .build()
        ops.add(eventOp)

        if (includeChildren) {
            event.reminders.forEach { reminder ->
                ops.add(
                    ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
                        .withValueBackReference(CalendarContract.Reminders.EVENT_ID, 0)
                        .withValue(CalendarContract.Reminders.MINUTES, reminder.minutes)
                        .withValue(CalendarContract.Reminders.METHOD, reminder.method)
                        .build()
                )
            }
            event.attendees.forEach { attendee ->
                ops.add(
                    ContentProviderOperation.newInsert(CalendarContract.Attendees.CONTENT_URI)
                        .withValueBackReference(CalendarContract.Attendees.EVENT_ID, 0)
                        .apply {
                            attendee.name?.let { withValue(CalendarContract.Attendees.ATTENDEE_NAME, it) }
                            attendee.email?.let { withValue(CalendarContract.Attendees.ATTENDEE_EMAIL, it) }
                            attendee.relationship?.let { withValue(CalendarContract.Attendees.ATTENDEE_RELATIONSHIP, it) }
                            attendee.type?.let { withValue(CalendarContract.Attendees.ATTENDEE_TYPE, it) }
                            attendee.status?.let { withValue(CalendarContract.Attendees.ATTENDEE_STATUS, it) }
                        }
                        .build()
                )
            }
        }
        return ops
    }

    private fun buildReminderOperation(
        eventId: Long,
        reminder: BackedUpCalendarReminder
    ): ContentProviderOperation {
        return ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
            .withValue(CalendarContract.Reminders.EVENT_ID, eventId)
            .withValue(CalendarContract.Reminders.MINUTES, reminder.minutes)
            .withValue(CalendarContract.Reminders.METHOD, reminder.method)
            .build()
    }

    private fun buildAttendeeOperation(
        eventId: Long,
        attendee: BackedUpCalendarAttendee
    ): ContentProviderOperation {
        return ContentProviderOperation.newInsert(CalendarContract.Attendees.CONTENT_URI)
            .withValue(CalendarContract.Attendees.EVENT_ID, eventId)
            .apply {
                attendee.name?.let { withValue(CalendarContract.Attendees.ATTENDEE_NAME, it) }
                attendee.email?.let { withValue(CalendarContract.Attendees.ATTENDEE_EMAIL, it) }
                attendee.relationship?.let { withValue(CalendarContract.Attendees.ATTENDEE_RELATIONSHIP, it) }
                attendee.type?.let { withValue(CalendarContract.Attendees.ATTENDEE_TYPE, it) }
                attendee.status?.let { withValue(CalendarContract.Attendees.ATTENDEE_STATUS, it) }
            }
            .build()
    }

    private fun localSyncAdapterUri(uri: Uri): Uri {
        return uri.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
    }

    private fun Cursor.getStringOrNull(index: Int): String? =
        if (index < 0 || isNull(index)) null else getString(index)

    private fun Cursor.getIntOrNull(index: Int): Int? =
        if (index < 0 || isNull(index)) null else getInt(index)

    private fun Cursor.getLongOrNull(index: Int): Long? =
        if (index < 0 || isNull(index)) null else getLong(index)

    companion object {
        const val CALENDAR_FILE_NAME = "calendar.json"
        const val CALENDAR_RELATIVE_STAGING_PATH = "device-data/calendar"
        private const val LOCAL_ACCOUNT_NAME = "Restoid"
        private const val DEFAULT_CALENDAR_COLOR = 0xFF1E88E5.toInt()
        private const val TAG = "CalendarBackupRepo"

        private val CALENDAR_PROJECTION = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.NAME,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_TIME_ZONE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.SYNC_EVENTS
        )

        private val EVENT_PROJECTION = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.EVENT_END_TIMEZONE,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.RDATE,
            CalendarContract.Events.EXRULE,
            CalendarContract.Events.EXDATE,
            CalendarContract.Events.ORIGINAL_ID,
            CalendarContract.Events.ORIGINAL_SYNC_ID,
            CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
            CalendarContract.Events.ORIGINAL_ALL_DAY,
            CalendarContract.Events.ACCESS_LEVEL,
            CalendarContract.Events.AVAILABILITY,
            CalendarContract.Events.STATUS,
            CalendarContract.Events.UID_2445,
            CalendarContract.Events.ORGANIZER,
            CalendarContract.Events.GUESTS_CAN_MODIFY,
            CalendarContract.Events.GUESTS_CAN_INVITE_OTHERS,
            CalendarContract.Events.GUESTS_CAN_SEE_GUESTS,
            CalendarContract.Events.CUSTOM_APP_PACKAGE,
            CalendarContract.Events.CUSTOM_APP_URI,
            CalendarContract.Events.EVENT_COLOR
        )
    }
}
