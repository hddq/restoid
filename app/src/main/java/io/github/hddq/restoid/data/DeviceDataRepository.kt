package io.github.hddq.restoid.data

import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Base64
import io.github.hddq.restoid.model.CalendarBackupPayload
import io.github.hddq.restoid.model.CalendarEntryBackup
import io.github.hddq.restoid.model.CalendarEventBackup
import io.github.hddq.restoid.model.CalendarEventDedupKey
import io.github.hddq.restoid.model.CalendarReminderBackup
import io.github.hddq.restoid.model.CallDedupKey
import io.github.hddq.restoid.model.CallHistoryBackupPayload
import io.github.hddq.restoid.model.CallLogEntryBackup
import io.github.hddq.restoid.model.ContactDataItemBackup
import io.github.hddq.restoid.model.ContactDedupSignature
import io.github.hddq.restoid.model.ContactEntryBackup
import io.github.hddq.restoid.model.ContactGroupBackup
import io.github.hddq.restoid.model.ContactsBackupPayload
import io.github.hddq.restoid.model.DeviceDataMetadata
import io.github.hddq.restoid.model.DeviceDataNormalization
import io.github.hddq.restoid.model.DeviceDataSelection
import io.github.hddq.restoid.model.DeviceDataType
import io.github.hddq.restoid.model.RawContactBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.TimeZone

data class DeviceDataExportResult(
    val type: DeviceDataType,
    val file: File,
    val itemCount: Int,
    val sizeBytes: Long
) {
    fun toMetadata() = DeviceDataMetadata(size = sizeBytes, itemCount = itemCount)
}

data class DeviceDataRestoreResult(
    val type: DeviceDataType,
    val insertedCount: Int,
    val skippedDuplicatesCount: Int
)

class DeviceDataRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    suspend fun queryAvailableItemCount(type: DeviceDataType): Int? = withContext(Dispatchers.IO) {
        if (!DeviceDataPermissionHelper.hasBackupPermissions(context, type)) return@withContext null
        val (uri, idCol, selection) = when (type) {
            DeviceDataType.CONTACTS -> Triple(ContactsContract.Contacts.CONTENT_URI, ContactsContract.Contacts._ID, null)
            DeviceDataType.CALL_HISTORY -> Triple(CallLog.Calls.CONTENT_URI, CallLog.Calls._ID, null)
            DeviceDataType.CALENDAR -> Triple(
                CalendarContract.Events.CONTENT_URI,
                CalendarContract.Events._ID,
                "${CalendarContract.Events.DELETED} = 0"
            )
        }
        runCatching {
            context.contentResolver.query(uri, arrayOf(idCol), selection, null, null)?.use { it.count }
        }.getOrNull()
    }

    suspend fun exportSelectedDeviceData(
        selection: DeviceDataSelection,
        stagingRoot: File,
        shouldStop: () -> Boolean = { false }
    ): List<DeviceDataExportResult> = withContext(Dispatchers.IO) {
        val deviceDataDir = File(stagingRoot, DeviceDataType.SNAPSHOT_DIR).apply { mkdirs() }
        buildList {
            for (type in selection.selectedTypes()) {
                if (shouldStop()) break
                val out = File(deviceDataDir, type.fileName)
                add(
                    when (type) {
                        DeviceDataType.CONTACTS -> exportContacts(out, shouldStop)
                        DeviceDataType.CALL_HISTORY -> exportCallHistory(out, shouldStop)
                        DeviceDataType.CALENDAR -> exportCalendar(out, shouldStop)
                    }
                )
            }
        }
    }

    suspend fun exportContacts(outputFile: File, shouldStop: () -> Boolean = { false }): DeviceDataExportResult =
        withContext(Dispatchers.IO) {
            requirePermissions(DeviceDataType.CONTACTS, isRestore = false)

            val groupsById = mutableMapOf<Long, ContactGroupBackup>()
            queryRows(
                uri = ContactsContract.Groups.CONTENT_URI,
                projection = arrayOf(
                    ContactsContract.Groups._ID, ContactsContract.Groups.TITLE,
                    ContactsContract.Groups.NOTES, ContactsContract.Groups.GROUP_VISIBLE,
                    ContactsContract.Groups.GROUP_IS_READ_ONLY, ContactsContract.Groups.FAVORITES,
                    ContactsContract.Groups.ACCOUNT_NAME, ContactsContract.Groups.ACCOUNT_TYPE
                ),
                selection = "${ContactsContract.Groups.DELETED} = 0",
                shouldStop = shouldStop
            ) { r ->
                val id = r.lng(ContactsContract.Groups._ID) ?: return@queryRows
                val title = r.str(ContactsContract.Groups.TITLE)?.trim()?.takeIf { it.isNotEmpty() } ?: return@queryRows
                groupsById[id] = ContactGroupBackup(
                    title = title,
                    notes = r.str(ContactsContract.Groups.NOTES),
                    groupVisible = r.int(ContactsContract.Groups.GROUP_VISIBLE),
                    isReadOnly = r.int(ContactsContract.Groups.GROUP_IS_READ_ONLY),
                    favorites = r.int(ContactsContract.Groups.FAVORITES),
                    accountName = r.str(ContactsContract.Groups.ACCOUNT_NAME),
                    accountType = r.str(ContactsContract.Groups.ACCOUNT_TYPE)
                )
            }

            data class ContactHeader(val displayName: String?, val starred: Boolean, val ringtone: String?, val voicemail: Boolean)
            val contactHeaders = linkedMapOf<Long, ContactHeader>()
            queryRows(
                uri = ContactsContract.Contacts.CONTENT_URI,
                projection = arrayOf(
                    ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.STARRED, ContactsContract.Contacts.CUSTOM_RINGTONE,
                    ContactsContract.Contacts.SEND_TO_VOICEMAIL
                ),
                shouldStop = shouldStop
            ) { r ->
                val id = r.lng(ContactsContract.Contacts._ID) ?: return@queryRows
                contactHeaders[id] = ContactHeader(
                    displayName = r.str(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                    starred = r.int(ContactsContract.Contacts.STARRED) == 1,
                    ringtone = r.str(ContactsContract.Contacts.CUSTOM_RINGTONE),
                    voicemail = r.int(ContactsContract.Contacts.SEND_TO_VOICEMAIL) == 1
                )
            }

            data class RawHeader(val rawId: Long, val accountName: String?, val accountType: String?, val sourceId: String?)
            val rawHeadersByContact = mutableMapOf<Long, MutableList<RawHeader>>()
            queryRows(
                uri = ContactsContract.RawContacts.CONTENT_URI,
                projection = arrayOf(
                    ContactsContract.RawContacts._ID, ContactsContract.RawContacts.CONTACT_ID,
                    ContactsContract.RawContacts.ACCOUNT_NAME, ContactsContract.RawContacts.ACCOUNT_TYPE,
                    ContactsContract.RawContacts.SOURCE_ID
                ),
                selection = "${ContactsContract.RawContacts.DELETED} = 0",
                shouldStop = shouldStop
            ) { r ->
                val rawId = r.lng(ContactsContract.RawContacts._ID) ?: return@queryRows
                val contactId = r.lng(ContactsContract.RawContacts.CONTACT_ID) ?: return@queryRows
                rawHeadersByContact.getOrPut(contactId) { mutableListOf() }.add(
                    RawHeader(
                        rawId = rawId,
                        accountName = r.str(ContactsContract.RawContacts.ACCOUNT_NAME),
                        accountType = r.str(ContactsContract.RawContacts.ACCOUNT_TYPE),
                        sourceId = r.str(ContactsContract.RawContacts.SOURCE_ID)
                    )
                )
            }

            val dataCols = (1..14).map { "data$it" }
            val dataItemsByRawId = mutableMapOf<Long, MutableList<ContactDataItemBackup>>()
            val groupTitlesByRawId = mutableMapOf<Long, MutableList<String>>()
            queryRows(
                uri = ContactsContract.Data.CONTENT_URI,
                projection = (listOf(
                    ContactsContract.Data.RAW_CONTACT_ID, ContactsContract.Data.MIMETYPE,
                    ContactsContract.Data.IS_PRIMARY, ContactsContract.Data.IS_SUPER_PRIMARY
                ) + dataCols + ContactsContract.Data.DATA15).toTypedArray(),
                shouldStop = shouldStop
            ) { r ->
                val rawId = r.lng(ContactsContract.Data.RAW_CONTACT_ID) ?: return@queryRows
                val mime = r.str(ContactsContract.Data.MIMETYPE)?.takeIf { it.isNotBlank() } ?: return@queryRows
                if (mime == ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE) {
                    val title = r.str("data1")?.toLongOrNull()?.let { groupsById[it]?.title }
                    if (!title.isNullOrBlank()) groupTitlesByRawId.getOrPut(rawId) { mutableListOf() }.add(title)
                    return@queryRows
                }

                val isPhoto = mime == ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                val colMap = sortedMapOf<String, String>()
                for (col in dataCols) {
                    if (isPhoto && col == "data14") continue // Exclude local PHOTO_FILE_ID; inline bytes live in data15.
                    r.str(col)?.let { colMap[col] = it }
                }

                var data15Base64: String? = null
                when (r.typeOf(ContactsContract.Data.DATA15)) {
                    Cursor.FIELD_TYPE_BLOB -> r.blob(ContactsContract.Data.DATA15)
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { data15Base64 = Base64.encodeToString(it, Base64.NO_WRAP) }
                    Cursor.FIELD_TYPE_STRING, Cursor.FIELD_TYPE_INTEGER, Cursor.FIELD_TYPE_FLOAT ->
                        r.str(ContactsContract.Data.DATA15)?.let { colMap["data15"] = it }
                }
                if (isPhoto && data15Base64 == null) return@queryRows

                dataItemsByRawId.getOrPut(rawId) { mutableListOf() }.add(
                    ContactDataItemBackup(
                        mimeType = mime,
                        isPrimary = r.int(ContactsContract.Data.IS_PRIMARY) ?: 0,
                        isSuperPrimary = r.int(ContactsContract.Data.IS_SUPER_PRIMARY) ?: 0,
                        dataColumns = colMap,
                        data15Base64 = data15Base64
                    )
                )
            }

            val entries = contactHeaders.mapNotNull { (contactId, header) ->
                val raws = rawHeadersByContact[contactId].orEmpty().mapNotNull { raw ->
                    val items = dataItemsByRawId[raw.rawId].orEmpty()
                    val groups = groupTitlesByRawId[raw.rawId].orEmpty().distinct()
                    if (items.isEmpty() && groups.isEmpty()) null else RawContactBackup(
                        accountName = raw.accountName, accountType = raw.accountType,
                        sourceId = raw.sourceId, dataItems = items, groupTitles = groups
                    )
                }
                if (raws.isEmpty()) null else ContactEntryBackup(
                    displayName = header.displayName, starred = header.starred,
                    customRingtone = header.ringtone, sendToVoicemail = header.voicemail, rawContacts = raws
                )
            }

            val payload = DeviceDataNormalization.sortContactsPayload(
                ContactsBackupPayload(schemaVersion = 1, groups = groupsById.values.toList(), contacts = entries)
            )
            writePayload(outputFile, payload)
            DeviceDataExportResult(DeviceDataType.CONTACTS, outputFile, payload.contacts.size, outputFile.length())
        }

    suspend fun exportCallHistory(outputFile: File, shouldStop: () -> Boolean = { false }): DeviceDataExportResult =
        withContext(Dispatchers.IO) {
            requirePermissions(DeviceDataType.CALL_HISTORY, isRestore = false)
            val calls = mutableListOf<CallLogEntryBackup>()
            queryRows(
                uri = CallLog.Calls.CONTENT_URI,
                projection = arrayOf(
                    CallLog.Calls.NUMBER, CallLog.Calls.NUMBER_PRESENTATION, CallLog.Calls.TYPE,
                    CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.CACHED_NUMBER_TYPE, CallLog.Calls.CACHED_NUMBER_LABEL,
                    CallLog.Calls.COUNTRY_ISO, CallLog.Calls.GEOCODED_LOCATION, CallLog.Calls.FEATURES,
                    CallLog.Calls.NEW, CallLog.Calls.IS_READ,
                    CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME, CallLog.Calls.PHONE_ACCOUNT_ID
                ),
                sortOrder = "${CallLog.Calls.DATE} ASC",
                shouldStop = shouldStop
            ) { r ->
                val type = r.int(CallLog.Calls.TYPE) ?: return@queryRows
                val date = r.lng(CallLog.Calls.DATE) ?: return@queryRows
                calls.add(
                    CallLogEntryBackup(
                        number = r.str(CallLog.Calls.NUMBER).orEmpty(),
                        numberPresentation = r.int(CallLog.Calls.NUMBER_PRESENTATION),
                        type = type,
                        date = date,
                        duration = r.lng(CallLog.Calls.DURATION) ?: 0L,
                        cachedName = r.str(CallLog.Calls.CACHED_NAME),
                        cachedNumberType = r.int(CallLog.Calls.CACHED_NUMBER_TYPE),
                        cachedNumberLabel = r.str(CallLog.Calls.CACHED_NUMBER_LABEL),
                        countryIso = r.str(CallLog.Calls.COUNTRY_ISO),
                        geocodedLocation = r.str(CallLog.Calls.GEOCODED_LOCATION),
                        features = r.int(CallLog.Calls.FEATURES),
                        isNew = r.int(CallLog.Calls.NEW),
                        isRead = r.int(CallLog.Calls.IS_READ),
                        phoneAccountComponentName = r.str(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME),
                        phoneAccountId = r.str(CallLog.Calls.PHONE_ACCOUNT_ID)
                    )
                )
            }
            val payload = DeviceDataNormalization.sortCallHistoryPayload(CallHistoryBackupPayload(schemaVersion = 1, calls = calls))
            writePayload(outputFile, payload)
            DeviceDataExportResult(DeviceDataType.CALL_HISTORY, outputFile, payload.calls.size, outputFile.length())
        }

    suspend fun exportCalendar(outputFile: File, shouldStop: () -> Boolean = { false }): DeviceDataExportResult =
        withContext(Dispatchers.IO) {
            requirePermissions(DeviceDataType.CALENDAR, isRestore = false)

            val calendarsById = linkedMapOf<Long, CalendarEntryBackup>()
            queryRows(
                uri = CalendarContract.Calendars.CONTENT_URI,
                projection = arrayOf(
                    CalendarContract.Calendars._ID, CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.CALENDAR_COLOR, CalendarContract.Calendars.CALENDAR_TIME_ZONE,
                    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.OWNER_ACCOUNT,
                    CalendarContract.Calendars.VISIBLE, CalendarContract.Calendars.SYNC_EVENTS
                ),
                shouldStop = shouldStop
            ) { r ->
                val id = r.lng(CalendarContract.Calendars._ID) ?: return@queryRows
                calendarsById[id] = CalendarEntryBackup(
                    accountName = r.str(CalendarContract.Calendars.ACCOUNT_NAME),
                    accountType = r.str(CalendarContract.Calendars.ACCOUNT_TYPE),
                    calendarDisplayName = r.str(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
                    calendarColor = r.int(CalendarContract.Calendars.CALENDAR_COLOR),
                    calendarTimeZone = r.str(CalendarContract.Calendars.CALENDAR_TIME_ZONE),
                    calendarAccessLevel = r.int(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL),
                    ownerAccount = r.str(CalendarContract.Calendars.OWNER_ACCOUNT),
                    visible = r.int(CalendarContract.Calendars.VISIBLE) ?: 1,
                    syncEvents = r.int(CalendarContract.Calendars.SYNC_EVENTS) ?: 1
                )
            }

            val remindersByEventId = mutableMapOf<Long, MutableList<CalendarReminderBackup>>()
            queryRows(
                uri = CalendarContract.Reminders.CONTENT_URI,
                projection = arrayOf(
                    CalendarContract.Reminders.EVENT_ID,
                    CalendarContract.Reminders.MINUTES,
                    CalendarContract.Reminders.METHOD
                ),
                shouldStop = shouldStop
            ) { r ->
                val eventId = r.lng(CalendarContract.Reminders.EVENT_ID) ?: return@queryRows
                val minutes = r.int(CalendarContract.Reminders.MINUTES) ?: return@queryRows
                val method = r.int(CalendarContract.Reminders.METHOD) ?: CalendarContract.Reminders.METHOD_DEFAULT
                remindersByEventId.getOrPut(eventId) { mutableListOf() }.add(CalendarReminderBackup(minutes, method))
            }

            val eventsByCalId = mutableMapOf<Long, MutableList<CalendarEventBackup>>()
            queryRows(
                uri = CalendarContract.Events.CONTENT_URI,
                projection = arrayOf(
                    CalendarContract.Events._ID, CalendarContract.Events.CALENDAR_ID, CalendarContract.Events.TITLE,
                    CalendarContract.Events.DESCRIPTION, CalendarContract.Events.EVENT_LOCATION,
                    CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND, CalendarContract.Events.DURATION,
                    CalendarContract.Events.ALL_DAY, CalendarContract.Events.EVENT_TIMEZONE,
                    CalendarContract.Events.EVENT_END_TIMEZONE, CalendarContract.Events.RRULE,
                    CalendarContract.Events.RDATE, CalendarContract.Events.EXRULE, CalendarContract.Events.EXDATE,
                    CalendarContract.Events.AVAILABILITY, CalendarContract.Events.STATUS,
                    CalendarContract.Events.HAS_ALARM, CalendarContract.Events.ORGANIZER, CalendarContract.Events.UID_2445
                ),
                selection = "${CalendarContract.Events.DELETED} = 0",
                sortOrder = "${CalendarContract.Events.DTSTART} ASC",
                shouldStop = shouldStop
            ) { r ->
                val eventId = r.lng(CalendarContract.Events._ID) ?: return@queryRows
                val calId = r.lng(CalendarContract.Events.CALENDAR_ID) ?: return@queryRows
                val dtstart = r.lng(CalendarContract.Events.DTSTART) ?: return@queryRows
                val rrule = r.str(CalendarContract.Events.RRULE)
                val rdate = r.str(CalendarContract.Events.RDATE)
                val timing = DeviceDataNormalization.normalizeEventTiming(
                    dtstart = dtstart,
                    dtend = r.lng(CalendarContract.Events.DTEND),
                    duration = r.str(CalendarContract.Events.DURATION),
                    rrule = rrule,
                    rdate = rdate
                )
                eventsByCalId.getOrPut(calId) { mutableListOf() }.add(
                    CalendarEventBackup(
                        title = r.str(CalendarContract.Events.TITLE),
                        description = r.str(CalendarContract.Events.DESCRIPTION),
                        eventLocation = r.str(CalendarContract.Events.EVENT_LOCATION),
                        dtstart = timing.dtstart, dtend = timing.dtend, duration = timing.duration,
                        allDay = r.int(CalendarContract.Events.ALL_DAY) ?: 0,
                        eventTimeZone = r.str(CalendarContract.Events.EVENT_TIMEZONE),
                        eventEndTimeZone = r.str(CalendarContract.Events.EVENT_END_TIMEZONE),
                        rrule = rrule, rdate = rdate,
                        exrule = r.str(CalendarContract.Events.EXRULE), exdate = r.str(CalendarContract.Events.EXDATE),
                        availability = r.int(CalendarContract.Events.AVAILABILITY),
                        status = r.int(CalendarContract.Events.STATUS),
                        hasAlarm = r.int(CalendarContract.Events.HAS_ALARM) ?: 0,
                        organizer = r.str(CalendarContract.Events.ORGANIZER),
                        uid2445 = r.str(CalendarContract.Events.UID_2445),
                        reminders = remindersByEventId[eventId].orEmpty()
                    )
                )
            }

            val calendarEntries = calendarsById.mapNotNull { (id, cal) ->
                val events = eventsByCalId[id].orEmpty()
                if (events.isEmpty() && cal.accountType != CalendarContract.ACCOUNT_TYPE_LOCAL) null
                else cal.copy(events = events)
            }
            val payload = DeviceDataNormalization.sortCalendarPayload(CalendarBackupPayload(schemaVersion = 1, calendars = calendarEntries))
            writePayload(outputFile, payload)
            DeviceDataExportResult(DeviceDataType.CALENDAR, outputFile, calendarEntries.sumOf { it.events.size }, outputFile.length())
        }

    suspend fun restoreFromFile(
        type: DeviceDataType,
        inputFile: File,
        shouldStop: () -> Boolean = { false }
    ): DeviceDataRestoreResult = withContext(Dispatchers.IO) {
        when (type) {
            DeviceDataType.CONTACTS -> restoreContacts(inputFile, shouldStop)
            DeviceDataType.CALL_HISTORY -> restoreCallHistory(inputFile, shouldStop)
            DeviceDataType.CALENDAR -> restoreCalendar(inputFile, shouldStop)
        }
    }

    suspend fun restoreContacts(inputFile: File, shouldStop: () -> Boolean = { false }): DeviceDataRestoreResult =
        withContext(Dispatchers.IO) {
            requirePermissions(DeviceDataType.CONTACTS, isRestore = true)
            val payload = json.decodeFromString<ContactsBackupPayload>(inputFile.readText())
            val resolver = context.contentResolver
            val existingSignatures = loadExistingContactSignatures()
            val groupTitleToId = loadExistingContactGroups()
            val registeredAccounts = runCatching {
                AccountManager.get(context).accounts.map { it.type to it.name }.toSet()
            }.getOrDefault(emptySet())

            val groupsByTitle = payload.groups.associateBy { it.title }
            for (title in payload.contacts.flatMap { c -> c.rawContacts.flatMap { it.groupTitles } }.distinct()) {
                if (shouldStop()) break
                if (title in groupTitleToId) continue
                val meta = groupsByTitle[title]
                val values = ContentValues().apply {
                    put(ContactsContract.Groups.TITLE, title)
                    meta?.notes?.let { put(ContactsContract.Groups.NOTES, it) }
                    put(ContactsContract.Groups.GROUP_VISIBLE, meta?.groupVisible ?: 1)
                    if (meta?.accountType != null && meta.accountName != null && (meta.accountType to meta.accountName) in registeredAccounts) {
                        put(ContactsContract.Groups.ACCOUNT_TYPE, meta.accountType)
                        put(ContactsContract.Groups.ACCOUNT_NAME, meta.accountName)
                    }
                }
                resolver.insert(ContactsContract.Groups.CONTENT_URI, values)
                    ?.let { runCatching { ContentUris.parseId(it) }.getOrNull() }
                    ?.takeIf { it > 0L }
                    ?.let { groupTitleToId[title] = it }
            }

            val ops = ArrayList<ContentProviderOperation>()
            var inserted = 0
            var skipped = 0
            for (contact in payload.contacts) {
                if (shouldStop()) break
                if (!existingSignatures.add(DeviceDataNormalization.contactSignature(contact))) {
                    skipped++
                    continue
                }
                if (contact.rawContacts.isEmpty()) continue

                for (raw in contact.rawContacts) {
                    val rawIdx = ops.size
                    val keepAccount = raw.accountType != null && raw.accountName != null &&
                        (raw.accountType to raw.accountName) in registeredAccounts
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, if (keepAccount) raw.accountType else null)
                            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, if (keepAccount) raw.accountName else null)
                            .withValue(ContactsContract.RawContacts.STARRED, if (contact.starred) 1 else 0)
                            .withValue(ContactsContract.RawContacts.SEND_TO_VOICEMAIL, if (contact.sendToVoicemail) 1 else 0)
                            .withOpt(ContactsContract.RawContacts.CUSTOM_RINGTONE, contact.customRingtone)
                            .build()
                    )

                    for (item in raw.dataItems) {
                        if (item.mimeType == ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE && item.data15Base64 == null) continue
                        val builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIdx)
                            .withValue(ContactsContract.Data.MIMETYPE, item.mimeType)
                            .apply {
                                if (item.isPrimary != 0) withValue(ContactsContract.Data.IS_PRIMARY, item.isPrimary)
                                if (item.isSuperPrimary != 0) withValue(ContactsContract.Data.IS_SUPER_PRIMARY, item.isSuperPrimary)
                                item.dataColumns.forEach { (col, value) -> withValue(col, value) }
                                item.data15Base64?.let { encoded ->
                                    runCatching { Base64.decode(encoded, Base64.DEFAULT) }
                                        .getOrNull()
                                        ?.takeIf { it.isNotEmpty() }
                                        ?.let { withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, it) }
                                }
                            }
                        ops.add(builder.build())
                    }

                    for (groupTitle in raw.groupTitles) {
                        val groupId = groupTitleToId[groupTitle] ?: continue
                        ops.add(
                            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIdx)
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID, groupId)
                                .build()
                        )
                    }
                }
                inserted++
                flushBatch(ContactsContract.AUTHORITY, ops, CONTACT_BATCH_LIMIT)
            }
            if (!shouldStop()) flushBatch(ContactsContract.AUTHORITY, ops, force = true)
            DeviceDataRestoreResult(DeviceDataType.CONTACTS, inserted, skipped)
        }

    suspend fun restoreCallHistory(inputFile: File, shouldStop: () -> Boolean = { false }): DeviceDataRestoreResult =
        withContext(Dispatchers.IO) {
            requirePermissions(DeviceDataType.CALL_HISTORY, isRestore = true)
            val payload = json.decodeFromString<CallHistoryBackupPayload>(inputFile.readText())
            val existingKeys = loadExistingCallKeys()
            val ops = ArrayList<ContentProviderOperation>()
            var inserted = 0
            var skipped = 0

            for (call in payload.calls) {
                if (shouldStop()) break
                if (!existingKeys.add(DeviceDataNormalization.callDedupKey(call))) {
                    skipped++
                    continue
                }
                ops.add(
                    ContentProviderOperation.newInsert(CallLog.Calls.CONTENT_URI)
                        .withValue(CallLog.Calls.NUMBER, call.number)
                        .withValue(CallLog.Calls.TYPE, call.type)
                        .withValue(CallLog.Calls.DATE, call.date)
                        .withValue(CallLog.Calls.DURATION, call.duration)
                        .withOpt(CallLog.Calls.NUMBER_PRESENTATION, call.numberPresentation)
                        .withOpt(CallLog.Calls.CACHED_NAME, call.cachedName)
                        .withOpt(CallLog.Calls.CACHED_NUMBER_TYPE, call.cachedNumberType)
                        .withOpt(CallLog.Calls.CACHED_NUMBER_LABEL, call.cachedNumberLabel)
                        .withOpt(CallLog.Calls.COUNTRY_ISO, call.countryIso)
                        .withOpt(CallLog.Calls.GEOCODED_LOCATION, call.geocodedLocation)
                        .withOpt(CallLog.Calls.FEATURES, call.features)
                        .withOpt(CallLog.Calls.NEW, call.isNew)
                        .withOpt(CallLog.Calls.IS_READ, call.isRead)
                        .withOpt(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME, call.phoneAccountComponentName)
                        .withOpt(CallLog.Calls.PHONE_ACCOUNT_ID, call.phoneAccountId)
                        .build()
                )
                inserted++
                flushBatch(CallLog.AUTHORITY, ops, CALL_LOG_BATCH_LIMIT)
            }
            if (!shouldStop()) flushBatch(CallLog.AUTHORITY, ops, force = true)
            DeviceDataRestoreResult(DeviceDataType.CALL_HISTORY, inserted, skipped)
        }

    suspend fun restoreCalendar(inputFile: File, shouldStop: () -> Boolean = { false }): DeviceDataRestoreResult =
        withContext(Dispatchers.IO) {
            requirePermissions(DeviceDataType.CALENDAR, isRestore = true)
            val payload = json.decodeFromString<CalendarBackupPayload>(inputFile.readText())
            val existingCalendars = loadWritableCalendars()
            val dedupKeysByCal = mutableMapOf<Long, MutableSet<CalendarEventDedupKey>>()
            val ops = ArrayList<ContentProviderOperation>()
            var inserted = 0
            var skipped = 0

            for (backupCal in payload.calendars) {
                if (shouldStop()) break
                if (backupCal.events.isEmpty()) continue
                val targetCalId = findOrCreateTargetCalendar(backupCal, existingCalendars)
                    ?: throw IllegalStateException("Unable to find or create writable calendar for ${backupCal.calendarDisplayName ?: "Restoid"}")
                val existingKeys = dedupKeysByCal.getOrPut(targetCalId) { loadExistingEventKeys(targetCalId) }
                val fallbackTz = backupCal.calendarTimeZone ?: TimeZone.getDefault().id

                for (event in backupCal.events) {
                    if (shouldStop()) break
                    if (!existingKeys.add(DeviceDataNormalization.eventDedupKey(event))) {
                        skipped++
                        continue
                    }
                    val timing = DeviceDataNormalization.normalizeEventTiming(
                        event.dtstart, event.dtend, event.duration, event.rrule, event.rdate
                    )
                    val eventOpIdx = ops.size
                    ops.add(
                        ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                            .withValue(CalendarContract.Events.CALENDAR_ID, targetCalId)
                            .withValue(CalendarContract.Events.DTSTART, timing.dtstart)
                            .withValue(CalendarContract.Events.ALL_DAY, event.allDay)
                            .withValue(CalendarContract.Events.EVENT_TIMEZONE, event.eventTimeZone?.takeIf { it.isNotBlank() } ?: fallbackTz)
                            .withValue(CalendarContract.Events.HAS_ALARM, if (event.reminders.isNotEmpty()) 1 else event.hasAlarm)
                            .withOpt(CalendarContract.Events.DTEND, timing.dtend)
                            .withOpt(CalendarContract.Events.DURATION, timing.duration)
                            .withOpt(CalendarContract.Events.TITLE, event.title)
                            .withOpt(CalendarContract.Events.DESCRIPTION, event.description)
                            .withOpt(CalendarContract.Events.EVENT_LOCATION, event.eventLocation)
                            .withOpt(CalendarContract.Events.EVENT_END_TIMEZONE, event.eventEndTimeZone)
                            .withOpt(CalendarContract.Events.RRULE, event.rrule)
                            .withOpt(CalendarContract.Events.RDATE, event.rdate)
                            .withOpt(CalendarContract.Events.EXRULE, event.exrule)
                            .withOpt(CalendarContract.Events.EXDATE, event.exdate)
                            .withOpt(CalendarContract.Events.AVAILABILITY, event.availability)
                            .withOpt(CalendarContract.Events.STATUS, event.status)
                            .withOpt(CalendarContract.Events.ORGANIZER, event.organizer)
                            .withOpt(CalendarContract.Events.UID_2445, event.uid2445)
                            .build()
                    )
                    for (reminder in event.reminders) {
                        ops.add(
                            ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
                                .withValueBackReference(CalendarContract.Reminders.EVENT_ID, eventOpIdx)
                                .withValue(CalendarContract.Reminders.MINUTES, reminder.minutes)
                                .withValue(CalendarContract.Reminders.METHOD, reminder.method)
                                .build()
                        )
                    }
                    inserted++
                    flushBatch(CalendarContract.AUTHORITY, ops, CALENDAR_BATCH_LIMIT)
                }
            }
            if (!shouldStop()) flushBatch(CalendarContract.AUTHORITY, ops, force = true)
            DeviceDataRestoreResult(DeviceDataType.CALENDAR, inserted, skipped)
        }

    private fun loadExistingContactGroups(): MutableMap<String, Long> = mutableMapOf<String, Long>().also { result ->
        queryRows(
            uri = ContactsContract.Groups.CONTENT_URI,
            projection = arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.TITLE),
            selection = "${ContactsContract.Groups.DELETED} = 0"
        ) { r ->
            val id = r.lng(ContactsContract.Groups._ID) ?: return@queryRows
            val title = r.str(ContactsContract.Groups.TITLE)?.trim()?.takeIf { it.isNotEmpty() } ?: return@queryRows
            result.putIfAbsent(title, id)
        }
    }

    private fun loadExistingContactSignatures(): MutableSet<ContactDedupSignature> {
        val displayNames = mutableMapOf<Long, String?>()
        val phones = mutableMapOf<Long, MutableList<String>>()
        val emails = mutableMapOf<Long, MutableList<String>>()
        val fallback = mutableMapOf<Long, MutableList<String>>()

        queryRows(
            uri = ContactsContract.Data.CONTENT_URI,
            projection = arrayOf(
                ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DISPLAY_NAME_PRIMARY,
                ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1
            )
        ) { r ->
            val contactId = r.lng(ContactsContract.Data.CONTACT_ID) ?: return@queryRows
            displayNames.putIfAbsent(contactId, r.str(ContactsContract.Data.DISPLAY_NAME_PRIMARY))
            val mime = r.str(ContactsContract.Data.MIMETYPE).orEmpty()
            val data1 = r.str(ContactsContract.Data.DATA1).orEmpty().takeIf { it.isNotBlank() } ?: return@queryRows
            when (mime) {
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> phones.getOrPut(contactId) { mutableListOf() }.add(data1)
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> emails.getOrPut(contactId) { mutableListOf() }.add(data1)
                ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE -> Unit
                else -> fallback.getOrPut(contactId) { mutableListOf() }.add("$mime:$data1")
            }
        }
        return displayNames.entries.mapTo(mutableSetOf()) { (id, name) ->
            DeviceDataNormalization.contactSignature(name, phones[id].orEmpty(), emails[id].orEmpty(), fallback[id].orEmpty())
        }
    }

    private fun loadExistingCallKeys(): MutableSet<CallDedupKey> = mutableSetOf<CallDedupKey>().also { keys ->
        queryRows(
            uri = CallLog.Calls.CONTENT_URI,
            projection = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION)
        ) { r ->
            val type = r.int(CallLog.Calls.TYPE) ?: return@queryRows
            val date = r.lng(CallLog.Calls.DATE) ?: return@queryRows
            keys.add(
                DeviceDataNormalization.callDedupKey(
                    CallLogEntryBackup(
                        number = r.str(CallLog.Calls.NUMBER).orEmpty(),
                        type = type,
                        date = date,
                        duration = r.lng(CallLog.Calls.DURATION) ?: 0L
                    )
                )
            )
        }
    }

    private data class WritableCalendar(val id: Long, val displayName: String?, val accountName: String?, val accountType: String?)

    private fun loadWritableCalendars(): MutableList<WritableCalendar> = mutableListOf<WritableCalendar>().also { calendars ->
        queryRows(
            uri = CalendarContract.Calendars.CONTENT_URI,
            projection = arrayOf(
                CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
            )
        ) { r ->
            val id = r.lng(CalendarContract.Calendars._ID) ?: return@queryRows
            if ((r.int(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL) ?: 0) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                calendars.add(
                    WritableCalendar(
                        id = id,
                        displayName = r.str(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
                        accountName = r.str(CalendarContract.Calendars.ACCOUNT_NAME),
                        accountType = r.str(CalendarContract.Calendars.ACCOUNT_TYPE)
                    )
                )
            }
        }
    }

    private fun findOrCreateTargetCalendar(backupCal: CalendarEntryBackup, existing: MutableList<WritableCalendar>): Long? {
        val desiredName = backupCal.calendarDisplayName?.trim().takeIf { !it.isNullOrEmpty() } ?: "Restoid"
        existing.firstOrNull {
            it.displayName?.equals(desiredName, ignoreCase = true) == true &&
                (backupCal.accountType == null || it.accountType == backupCal.accountType) &&
                (backupCal.accountName == null || it.accountName == backupCal.accountName)
        }?.let { return it.id }

        existing.firstOrNull {
            it.accountType == CalendarContract.ACCOUNT_TYPE_LOCAL && it.displayName?.equals(desiredName, ignoreCase = true) == true
        }?.let { return it.id }

        val accountName = backupCal.accountName?.takeIf { it.isNotBlank() } ?: "Restoid"
        val insertUri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, desiredName)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, desiredName)
            put(CalendarContract.Calendars.CALENDAR_COLOR, backupCal.calendarColor ?: DEFAULT_CALENDAR_COLOR)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, backupCal.ownerAccount ?: accountName)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, backupCal.calendarTimeZone?.takeIf { it.isNotBlank() } ?: TimeZone.getDefault().id)
            put(CalendarContract.Calendars.VISIBLE, backupCal.visible)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }
        val createdId = context.contentResolver.insert(insertUri, values)
            ?.let { runCatching { ContentUris.parseId(it) }.getOrNull() }
            ?.takeIf { it > 0L }
        if (createdId != null) {
            existing.add(WritableCalendar(createdId, desiredName, accountName, CalendarContract.ACCOUNT_TYPE_LOCAL))
            return createdId
        }
        return existing.firstOrNull { it.accountType == CalendarContract.ACCOUNT_TYPE_LOCAL }?.id ?: existing.firstOrNull()?.id
    }

    private fun loadExistingEventKeys(calendarId: Long): MutableSet<CalendarEventDedupKey> = mutableSetOf<CalendarEventDedupKey>().also { keys ->
        queryRows(
            uri = CalendarContract.Events.CONTENT_URI,
            projection = arrayOf(
                CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART,
                CalendarContract.Events.RRULE, CalendarContract.Events.ALL_DAY
            ),
            selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DELETED} = 0",
            selectionArgs = arrayOf(calendarId.toString())
        ) { r ->
            val dtstart = r.lng(CalendarContract.Events.DTSTART) ?: return@queryRows
            keys.add(
                DeviceDataNormalization.eventDedupKey(
                    CalendarEventBackup(
                        title = r.str(CalendarContract.Events.TITLE),
                        dtstart = dtstart,
                        rrule = r.str(CalendarContract.Events.RRULE),
                        allDay = r.int(CalendarContract.Events.ALL_DAY) ?: 0
                    )
                )
            )
        }
    }

    private fun requirePermissions(type: DeviceDataType, isRestore: Boolean) {
        if (!DeviceDataPermissionHelper.hasPermissionsForType(context, type, isRestore)) {
            val missing = DeviceDataPermissionHelper.requiredPermissionsForType(type, isRestore)
                .filterNot { DeviceDataPermissionHelper.hasPermission(context, it) }
            throw SecurityException("Missing runtime permission(s) for ${type.key}: ${missing.joinToString()}")
        }
    }

    private inline fun <reified T> writePayload(outputFile: File, payload: T) {
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(json.encodeToString(payload))
    }

    private inline fun queryRows(
        uri: Uri,
        projection: Array<String>,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null,
        shouldStop: () -> Boolean = { false },
        onRow: (CursorRow) -> Unit
    ) {
        context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val row = CursorRow(cursor, projection.associateWith { cursor.getColumnIndex(it) })
            while (!shouldStop() && cursor.moveToNext()) onRow(row)
        }
    }

    private fun flushBatch(authority: String, ops: ArrayList<ContentProviderOperation>, limit: Int = 0, force: Boolean = false) {
        if (ops.isNotEmpty() && (force || ops.size >= limit)) {
            context.contentResolver.applyBatch(authority, ops)
            ops.clear()
        }
    }

    private fun ContentProviderOperation.Builder.withOpt(col: String, value: Any?) = apply {
        when (value) {
            is String -> withValue(col, value)
            is Int -> withValue(col, value)
            is Long -> withValue(col, value)
            is ByteArray -> withValue(col, value)
        }
    }

    private class CursorRow(private val cursor: Cursor, private val idx: Map<String, Int>) {
        fun str(col: String): String? = validIndex(col)?.let(cursor::getString)
        fun int(col: String): Int? = validIndex(col)?.let(cursor::getInt)
        fun lng(col: String): Long? = validIndex(col)?.let(cursor::getLong)
        fun blob(col: String): ByteArray? = validIndex(col)?.let(cursor::getBlob)
        fun typeOf(col: String): Int? = validIndex(col)?.let(cursor::getType)
        private fun validIndex(col: String): Int? = idx[col]?.takeIf { it >= 0 && !cursor.isNull(it) }
    }

    private companion object {
        const val CONTACT_BATCH_LIMIT = 200
        const val CALL_LOG_BATCH_LIMIT = 100
        const val CALENDAR_BATCH_LIMIT = 150
        const val DEFAULT_CALENDAR_COLOR = 0xFF1976D2.toInt()
    }
}
