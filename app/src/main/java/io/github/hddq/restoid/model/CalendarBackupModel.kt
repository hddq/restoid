package io.github.hddq.restoid.model

import kotlinx.serialization.Serializable

data class CalendarInfo(
    val id: Long,
    val selectionKey: String,
    val name: String,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    val timeZone: String?,
    val isReadOnly: Boolean
)

@Serializable
data class CalendarBackupArchive(
    val version: Int = 1,
    val calendars: List<BackedUpCalendar> = emptyList()
)

@Serializable
data class BackedUpCalendar(
    val selectionKey: String,
    val name: String? = null,
    val displayName: String,
    val accountName: String? = null,
    val accountType: String? = null,
    val ownerAccount: String? = null,
    val color: Int? = null,
    val timeZone: String? = null,
    val visible: Int? = null,
    val syncEvents: Int? = null,
    val accessLevel: Int? = null,
    val events: List<BackedUpCalendarEvent> = emptyList()
)

@Serializable
data class BackedUpCalendarEvent(
    val sourceEventId: Long? = null,
    val title: String? = null,
    val description: String? = null,
    val eventLocation: String? = null,
    val dtStart: Long,
    val dtEnd: Long? = null,
    val duration: String? = null,
    val eventTimezone: String? = null,
    val eventEndTimezone: String? = null,
    val allDay: Boolean = false,
    val rrule: String? = null,
    val rdate: String? = null,
    val exrule: String? = null,
    val exdate: String? = null,
    val originalSourceEventId: Long? = null,
    val originalSyncId: String? = null,
    val originalInstanceTime: Long? = null,
    val originalAllDay: Boolean? = null,
    val accessLevel: Int? = null,
    val availability: Int? = null,
    val status: Int? = null,
    val uid2445: String? = null,
    val organizer: String? = null,
    val guestsCanModify: Boolean? = null,
    val guestsCanInviteOthers: Boolean? = null,
    val guestsCanSeeGuests: Boolean? = null,
    val customAppPackage: String? = null,
    val customAppUri: String? = null,
    val eventColor: Int? = null,
    val reminders: List<BackedUpCalendarReminder> = emptyList(),
    val attendees: List<BackedUpCalendarAttendee> = emptyList()
)

@Serializable
data class BackedUpCalendarReminder(
    val minutes: Int,
    val method: Int
)

@Serializable
data class BackedUpCalendarAttendee(
    val name: String? = null,
    val email: String? = null,
    val relationship: Int? = null,
    val type: Int? = null,
    val status: Int? = null
)

internal data class NormalizedEventTiming(
    val dtStart: Long,
    val dtEnd: Long?,
    val duration: String?,
    val eventTimezone: String
)

private const val MILLIS_PER_SECOND = 1000L
private const val MILLIS_PER_DAY = 86_400_000L

internal fun buildCalendarSelectionKey(
    accountType: String?,
    accountName: String?,
    name: String?,
    displayName: String?
): String {
    return listOf(
        accountType?.trim().orEmpty(),
        accountName?.trim().orEmpty(),
        name?.trim().orEmpty(),
        displayName?.trim().orEmpty()
    ).joinToString("\u001F")
}

internal fun isRecurringEvent(
    rrule: String?,
    rdate: String?,
    exrule: String?,
    exdate: String?
): Boolean {
    return !rrule.isNullOrBlank() ||
        !rdate.isNullOrBlank() ||
        !exrule.isNullOrBlank() ||
        !exdate.isNullOrBlank()
}

internal fun normalizeEventTiming(
    dtStart: Long,
    dtEnd: Long?,
    duration: String?,
    allDay: Boolean,
    eventTimezone: String?,
    calendarTimezone: String?,
    rrule: String?,
    rdate: String?,
    exrule: String?,
    exdate: String?,
    fallbackTimezone: String = "UTC"
): NormalizedEventTiming {
    val resolvedTimezone = if (allDay) {
        "UTC"
    } else {
        eventTimezone?.trim()?.takeIf { it.isNotEmpty() }
            ?: calendarTimezone?.trim()?.takeIf { it.isNotEmpty() }
            ?: fallbackTimezone
    }

    val recurring = isRecurringEvent(rrule, rdate, exrule, exdate)
    if (recurring) {
        val resolvedDuration = duration?.trim()?.takeIf { it.isNotEmpty() }
            ?: computeDurationFromBounds(dtStart = dtStart, dtEnd = dtEnd, allDay = allDay)
        return NormalizedEventTiming(
            dtStart = dtStart,
            dtEnd = null,
            duration = resolvedDuration,
            eventTimezone = resolvedTimezone
        )
    }

    val trimmedDuration = duration?.trim()?.takeIf { it.isNotEmpty() }
    val resolvedDtEnd = when {
        dtEnd != null -> maxOf(dtEnd, dtStart)
        trimmedDuration != null -> null
        allDay -> dtStart + MILLIS_PER_DAY
        else -> dtStart
    }
    return NormalizedEventTiming(
        dtStart = dtStart,
        dtEnd = resolvedDtEnd,
        duration = if (resolvedDtEnd != null) null else trimmedDuration,
        eventTimezone = resolvedTimezone
    )
}

private fun computeDurationFromBounds(
    dtStart: Long,
    dtEnd: Long?,
    allDay: Boolean
): String {
    if (dtEnd == null) {
        return if (allDay) "P1D" else "PT1H"
    }
    val diffMillis = (dtEnd - dtStart).coerceAtLeast(0L)
    if (allDay) {
        val days = (diffMillis / MILLIS_PER_DAY).coerceAtLeast(1L)
        return "P${days}D"
    }
    val seconds = diffMillis / MILLIS_PER_SECOND
    return "PT${seconds}S"
}

internal fun normalizeCalendarEventUid(uid2445: String?): String? =
    uid2445?.trim()?.takeIf { it.isNotEmpty() }

internal fun buildCalendarEventUidDedupKey(
    uid2445: String?,
    dtStart: Long,
    originalInstanceTime: Long?
): String? {
    val normalizedUid = normalizeCalendarEventUid(uid2445) ?: return null
    return "uid|$normalizedUid|$dtStart|${originalInstanceTime ?: -1L}"
}

internal fun buildCalendarEventContentDedupKey(
    title: String?,
    dtStart: Long,
    dtEnd: Long? = null,
    duration: String? = null,
    allDay: Boolean,
    eventTimezone: String? = null,
    calendarTimezone: String? = null,
    rrule: String?,
    rdate: String? = null,
    exrule: String? = null,
    exdate: String? = null,
    eventLocation: String?,
    originalInstanceTime: Long?,
    resolvedOriginalId: Long? = null
): String {
    val normalizedTiming = normalizeEventTiming(
        dtStart = dtStart,
        dtEnd = dtEnd,
        duration = duration,
        allDay = allDay,
        eventTimezone = eventTimezone,
        calendarTimezone = calendarTimezone,
        rrule = rrule,
        rdate = rdate,
        exrule = exrule,
        exdate = exdate
    )
    val recurring = isRecurringEvent(rrule, rdate, exrule, exdate)
    val effectiveRecurringTimezone = if (recurring) normalizedTiming.eventTimezone else ""
    val normalizedTitle = title?.trim().orEmpty().lowercase()
    val normalizedRrule = rrule?.trim().orEmpty()
    val normalizedRdate = rdate?.trim().orEmpty()
    val normalizedExrule = exrule?.trim().orEmpty()
    val normalizedExdate = exdate?.trim().orEmpty()
    val normalizedLocation = eventLocation?.trim().orEmpty().lowercase()
    return "content|$normalizedTitle|${normalizedTiming.dtStart}|${normalizedTiming.dtEnd ?: -1L}|${normalizedTiming.duration.orEmpty()}|${if (allDay) 1 else 0}|$effectiveRecurringTimezone|$normalizedRrule|$normalizedRdate|$normalizedExrule|$normalizedExdate|$normalizedLocation|${originalInstanceTime ?: -1L}|${resolvedOriginalId ?: -1L}"
}

internal fun buildCalendarEventDedupKeys(
    uid2445: String?,
    title: String?,
    dtStart: Long,
    dtEnd: Long? = null,
    duration: String? = null,
    allDay: Boolean,
    eventTimezone: String? = null,
    calendarTimezone: String? = null,
    rrule: String?,
    rdate: String? = null,
    exrule: String? = null,
    exdate: String? = null,
    eventLocation: String?,
    originalInstanceTime: Long?,
    resolvedOriginalId: Long? = null
): List<String> {
    val keys = ArrayList<String>(2)
    buildCalendarEventUidDedupKey(
        uid2445 = uid2445,
        dtStart = dtStart,
        originalInstanceTime = originalInstanceTime
    )?.let { keys.add(it) }
    keys.add(
        buildCalendarEventContentDedupKey(
            title = title,
            dtStart = dtStart,
            dtEnd = dtEnd,
            duration = duration,
            allDay = allDay,
            eventTimezone = eventTimezone,
            calendarTimezone = calendarTimezone,
            rrule = rrule,
            rdate = rdate,
            exrule = exrule,
            exdate = exdate,
            eventLocation = eventLocation,
            originalInstanceTime = originalInstanceTime,
            resolvedOriginalId = resolvedOriginalId
        )
    )
    return keys
}

internal fun BackedUpCalendarEvent.toDedupKeys(
    calendarTimezone: String? = null,
    resolvedOriginalId: Long? = null
): List<String> = buildCalendarEventDedupKeys(
    uid2445 = uid2445,
    title = title,
    dtStart = dtStart,
    dtEnd = dtEnd,
    duration = duration,
    allDay = allDay,
    eventTimezone = eventTimezone,
    calendarTimezone = calendarTimezone,
    rrule = rrule,
    rdate = rdate,
    exrule = exrule,
    exdate = exdate,
    eventLocation = eventLocation,
    originalInstanceTime = originalInstanceTime,
    resolvedOriginalId = resolvedOriginalId
)

internal class CalendarEventDedupIndex(
    private val defaultCalendarTimezone: String? = null
) {
    private val uidKeyToEventId = HashMap<String, Long>()
    private val contentKeyToEventIds = HashMap<String, MutableList<Long>>()
    private val eventIdToNormalizedUid = HashMap<Long, String>()

    fun register(
        eventId: Long,
        uid2445: String?,
        title: String?,
        dtStart: Long,
        dtEnd: Long? = null,
        duration: String? = null,
        allDay: Boolean,
        eventTimezone: String? = null,
        calendarTimezone: String? = defaultCalendarTimezone,
        rrule: String?,
        rdate: String? = null,
        exrule: String? = null,
        exdate: String? = null,
        eventLocation: String?,
        originalInstanceTime: Long?,
        resolvedOriginalId: Long? = null
    ) {
        val normalizedUid = normalizeCalendarEventUid(uid2445)
        if (normalizedUid != null) {
            eventIdToNormalizedUid.putIfAbsent(eventId, normalizedUid)
            buildCalendarEventUidDedupKey(
                uid2445 = normalizedUid,
                dtStart = dtStart,
                originalInstanceTime = originalInstanceTime
            )?.let { uidKey ->
                uidKeyToEventId.putIfAbsent(uidKey, eventId)
            }
        }
        val contentKey = buildCalendarEventContentDedupKey(
            title = title,
            dtStart = dtStart,
            dtEnd = dtEnd,
            duration = duration,
            allDay = allDay,
            eventTimezone = eventTimezone,
            calendarTimezone = calendarTimezone,
            rrule = rrule,
            rdate = rdate,
            exrule = exrule,
            exdate = exdate,
            eventLocation = eventLocation,
            originalInstanceTime = originalInstanceTime,
            resolvedOriginalId = resolvedOriginalId
        )
        val candidates = contentKeyToEventIds.getOrPut(contentKey) { mutableListOf() }
        if (eventId !in candidates) {
            candidates.add(eventId)
        }
    }

    fun register(
        eventId: Long,
        event: BackedUpCalendarEvent,
        calendarTimezone: String? = defaultCalendarTimezone,
        resolvedOriginalId: Long? = null
    ) {
        register(
            eventId = eventId,
            uid2445 = event.uid2445,
            title = event.title,
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
            eventLocation = event.eventLocation,
            originalInstanceTime = event.originalInstanceTime,
            resolvedOriginalId = resolvedOriginalId
        )
    }

    fun findDuplicateEventId(
        event: BackedUpCalendarEvent,
        calendarTimezone: String? = defaultCalendarTimezone,
        resolvedOriginalId: Long? = null
    ): Long? {
        val normalizedUid = normalizeCalendarEventUid(event.uid2445)
        val uidKey = buildCalendarEventUidDedupKey(
            uid2445 = normalizedUid,
            dtStart = event.dtStart,
            originalInstanceTime = event.originalInstanceTime
        )
        if (uidKey != null) {
            uidKeyToEventId[uidKey]?.let { return it }
        }

        val contentKey = buildCalendarEventContentDedupKey(
            title = event.title,
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
            eventLocation = event.eventLocation,
            originalInstanceTime = event.originalInstanceTime,
            resolvedOriginalId = resolvedOriginalId
        )
        val candidates = contentKeyToEventIds[contentKey] ?: return null
        return candidates.firstOrNull { candidateId ->
            val existingUid = eventIdToNormalizedUid[candidateId]
            // Do not match on content if both events carry known, different UIDs.
            normalizedUid == null || existingUid == null || normalizedUid == existingUid
        }
    }
}

