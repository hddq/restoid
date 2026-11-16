package io.github.hddq.restoid.model

import kotlinx.serialization.Serializable

enum class DeviceDataType(val key: String, val fileName: String) {
    CONTACTS("contacts", "contacts.json"),
    CALL_HISTORY("call_history", "call_history.json"),
    CALENDAR("calendar", "calendar.json");

    val relativePath: String get() = "$SNAPSHOT_DIR/$fileName"
    val snapshotPath: String get() = "/$SNAPSHOT_DIR/$fileName"

    companion object {
        const val SNAPSHOT_DIR = "device-data"

        fun fromKey(key: String): DeviceDataType? = entries.firstOrNull { it.key == key }

        fun fromSnapshotPath(path: String): DeviceDataType? = path.trim().let { norm ->
            entries.firstOrNull {
                norm == it.snapshotPath || norm == it.relativePath || norm.endsWith("/${it.relativePath}")
            }
        }
    }
}

@Serializable
data class DeviceDataSelection(
    val contacts: Boolean = false,
    val callHistory: Boolean = false,
    val calendar: Boolean = false
) {
    fun isEnabled(type: DeviceDataType): Boolean = when (type) {
        DeviceDataType.CONTACTS -> contacts
        DeviceDataType.CALL_HISTORY -> callHistory
        DeviceDataType.CALENDAR -> calendar
    }

    fun withType(type: DeviceDataType, enabled: Boolean): DeviceDataSelection = when (type) {
        DeviceDataType.CONTACTS -> copy(contacts = enabled)
        DeviceDataType.CALL_HISTORY -> copy(callHistory = enabled)
        DeviceDataType.CALENDAR -> copy(calendar = enabled)
    }

    fun anyEnabled(): Boolean = contacts || callHistory || calendar
    fun selectedTypes(): List<DeviceDataType> = DeviceDataType.entries.filter(::isEnabled)
    fun enabledKeys(): Set<String> = selectedTypes().mapTo(mutableSetOf()) { it.key }

    companion object {
        fun fromTypes(types: Set<DeviceDataType>): DeviceDataSelection = DeviceDataSelection(
            contacts = DeviceDataType.CONTACTS in types,
            callHistory = DeviceDataType.CALL_HISTORY in types,
            calendar = DeviceDataType.CALENDAR in types
        )
    }
}

@Serializable
data class ContactsBackupPayload(
    val schemaVersion: Int = 1,
    val groups: List<ContactGroupBackup> = emptyList(),
    val contacts: List<ContactEntryBackup> = emptyList()
)

@Serializable
data class ContactGroupBackup(
    val title: String,
    val notes: String? = null,
    val groupVisible: Int? = null,
    val isReadOnly: Int? = null,
    val favorites: Int? = null,
    val accountName: String? = null,
    val accountType: String? = null
)

@Serializable
data class ContactEntryBackup(
    val displayName: String? = null,
    val starred: Boolean = false,
    val customRingtone: String? = null,
    val sendToVoicemail: Boolean = false,
    val rawContacts: List<RawContactBackup> = emptyList()
)

@Serializable
data class RawContactBackup(
    val accountName: String? = null,
    val accountType: String? = null,
    val sourceId: String? = null,
    val dataItems: List<ContactDataItemBackup> = emptyList(),
    val groupTitles: List<String> = emptyList()
)

@Serializable
data class ContactDataItemBackup(
    val mimeType: String,
    val isPrimary: Int = 0,
    val isSuperPrimary: Int = 0,
    val dataColumns: Map<String, String> = emptyMap(),
    val data15Base64: String? = null
)

@Serializable
data class CallHistoryBackupPayload(
    val schemaVersion: Int = 1,
    val calls: List<CallLogEntryBackup> = emptyList()
)

@Serializable
data class CallLogEntryBackup(
    val number: String = "",
    val numberPresentation: Int? = null,
    val type: Int,
    val date: Long,
    val duration: Long,
    val cachedName: String? = null,
    val cachedNumberType: Int? = null,
    val cachedNumberLabel: String? = null,
    val countryIso: String? = null,
    val geocodedLocation: String? = null,
    val features: Int? = null,
    val isNew: Int? = null,
    val isRead: Int? = null,
    val phoneAccountComponentName: String? = null,
    val phoneAccountId: String? = null
)

@Serializable
data class CalendarBackupPayload(
    val schemaVersion: Int = 1,
    val calendars: List<CalendarEntryBackup> = emptyList()
)

@Serializable
data class CalendarEntryBackup(
    val accountName: String? = null,
    val accountType: String? = null,
    val calendarDisplayName: String? = null,
    val calendarColor: Int? = null,
    val calendarTimeZone: String? = null,
    val calendarAccessLevel: Int? = null,
    val ownerAccount: String? = null,
    val visible: Int = 1,
    val syncEvents: Int = 1,
    val events: List<CalendarEventBackup> = emptyList()
)

@Serializable
data class CalendarEventBackup(
    val title: String? = null,
    val description: String? = null,
    val eventLocation: String? = null,
    val dtstart: Long,
    val dtend: Long? = null,
    val duration: String? = null,
    val allDay: Int = 0,
    val eventTimeZone: String? = null,
    val eventEndTimeZone: String? = null,
    val rrule: String? = null,
    val rdate: String? = null,
    val exrule: String? = null,
    val exdate: String? = null,
    val availability: Int? = null,
    val status: Int? = null,
    val hasAlarm: Int = 0,
    val organizer: String? = null,
    val uid2445: String? = null,
    val reminders: List<CalendarReminderBackup> = emptyList()
)

@Serializable
data class CalendarReminderBackup(val minutes: Int, val method: Int)

data class ContactDedupSignature(
    val normalizedDisplayName: String,
    val normalizedPhones: List<String>,
    val normalizedEmails: List<String>,
    val fallbackKey: String = ""
)

data class CallDedupKey(
    val normalizedNumber: String,
    val date: Long,
    val type: Int,
    val duration: Long
)

data class CalendarEventDedupKey(
    val title: String,
    val dtstart: Long,
    val rrule: String,
    val allDay: Int
)

data class NormalizedEventTiming(val dtstart: Long, val dtend: Long?, val duration: String?)

object DeviceDataNormalization {
    private const val MIME_PHONE = "vnd.android.cursor.item/phone_v2"
    private const val MIME_EMAIL = "vnd.android.cursor.item/email_v2"

    fun normalizePhoneNumber(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        val digitsOnly = trimmed.filterIndexed { i, c -> c.isDigit() || (c == '+' && i == 0) }
        return if (digitsOnly.any(Char::isDigit)) digitsOnly else trimmed.lowercase()
    }

    fun normalizeEmail(raw: String?): String = raw?.trim()?.lowercase().orEmpty()

    fun contactSignature(
        displayName: String?,
        phones: Iterable<String>,
        emails: Iterable<String>,
        fallbackParts: Iterable<String> = emptyList()
    ): ContactDedupSignature {
        val normName = displayName?.trim()?.lowercase().orEmpty()
        val normPhones = phones.map(::normalizePhoneNumber).filter { it.isNotEmpty() }.distinct().sorted()
        val normEmails = emails.map(::normalizeEmail).filter { it.isNotEmpty() }.distinct().sorted()
        val fallback = if (normName.isEmpty() && normPhones.isEmpty() && normEmails.isEmpty()) {
            fallbackParts.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.sorted().joinToString("|")
        } else {
            ""
        }
        return ContactDedupSignature(normName, normPhones, normEmails, fallback)
    }

    fun contactSignature(contact: ContactEntryBackup): ContactDedupSignature {
        val phones = mutableListOf<String>()
        val emails = mutableListOf<String>()
        val fallbackParts = mutableListOf<String>()
        for (raw in contact.rawContacts) {
            for (item in raw.dataItems) {
                val data1 = item.dataColumns["data1"].orEmpty()
                if (data1.isBlank()) continue
                when (item.mimeType) {
                    MIME_PHONE -> phones.add(data1)
                    MIME_EMAIL -> emails.add(data1)
                    else -> fallbackParts.add("${item.mimeType}:$data1")
                }
            }
        }
        return contactSignature(contact.displayName, phones, emails, fallbackParts)
    }

    fun callDedupKey(call: CallLogEntryBackup) = CallDedupKey(
        normalizedNumber = normalizePhoneNumber(call.number).ifEmpty { call.number.trim() },
        date = call.date,
        type = call.type,
        duration = call.duration
    )

    fun eventDedupKey(event: CalendarEventBackup) = CalendarEventDedupKey(
        title = event.title.orEmpty().trim(),
        dtstart = event.dtstart,
        rrule = event.rrule.orEmpty().trim(),
        allDay = event.allDay
    )

    /**
     * CalendarProvider requires recurring events (non-blank rrule/rdate) to set DURATION with null DTEND,
     * and non-recurring events to set DTEND >= dtstart with null DURATION.
     */
    fun normalizeEventTiming(
        dtstart: Long,
        dtend: Long?,
        duration: String?,
        rrule: String?,
        rdate: String?
    ): NormalizedEventTiming {
        return if (!rrule.isNullOrBlank() || !rdate.isNullOrBlank()) {
            val normalizedDuration = duration?.trim()?.takeIf { it.isNotEmpty() } ?: run {
                val seconds = if (dtend != null && dtend > dtstart) ((dtend - dtstart) / 1000L).coerceAtLeast(1L) else 0L
                "PT${seconds}S"
            }
            NormalizedEventTiming(dtstart, dtend = null, duration = normalizedDuration)
        } else {
            NormalizedEventTiming(dtstart, dtend = dtend?.coerceAtLeast(dtstart) ?: dtstart, duration = null)
        }
    }

    fun sortContactsPayload(payload: ContactsBackupPayload): ContactsBackupPayload {
        val sortedGroups = payload.groups
            .distinctBy { Triple(it.title, it.accountType, it.accountName) }
            .sortedWith(compareBy({ it.title.lowercase() }, { it.accountType.orEmpty() }, { it.accountName.orEmpty() }))
        val sortedContacts = payload.contacts
            .map { contact ->
                contact.copy(
                    rawContacts = contact.rawContacts
                        .map { raw ->
                            raw.copy(
                                groupTitles = raw.groupTitles.distinct().sorted(),
                                dataItems = raw.dataItems
                                    .map { it.copy(dataColumns = it.dataColumns.toSortedMap()) }
                                    .sortedWith(
                                        compareBy(
                                            { it.mimeType },
                                            { it.dataColumns["data1"].orEmpty() },
                                            { it.dataColumns["data2"].orEmpty() },
                                            { it.dataColumns["data3"].orEmpty() },
                                            { it.data15Base64.orEmpty() }
                                        )
                                    )
                            )
                        }
                        .sortedWith(compareBy({ it.accountType.orEmpty() }, { it.accountName.orEmpty() }, { it.sourceId.orEmpty() }))
                )
            }
            .sortedWith(
                compareBy<ContactEntryBackup>(
                    { it.displayName.orEmpty().lowercase() },
                    { contactSignature(it).normalizedPhones.firstOrNull().orEmpty() },
                    { contactSignature(it).normalizedEmails.firstOrNull().orEmpty() }
                )
            )
        return payload.copy(groups = sortedGroups, contacts = sortedContacts)
    }

    fun sortCallHistoryPayload(payload: CallHistoryBackupPayload) = payload.copy(
        calls = payload.calls.sortedWith(compareBy({ it.date }, { it.type }, { normalizePhoneNumber(it.number) }, { it.duration }))
    )

    fun sortCalendarPayload(payload: CalendarBackupPayload) = payload.copy(
        calendars = payload.calendars
            .map { cal ->
                cal.copy(
                    events = cal.events
                        .map { ev -> ev.copy(reminders = ev.reminders.distinct().sortedWith(compareBy({ it.minutes }, { it.method }))) }
                        .sortedWith(compareBy({ it.dtstart }, { it.title.orEmpty().lowercase() }, { it.rrule.orEmpty() }, { it.allDay }))
                )
            }
            .sortedWith(compareBy({ it.calendarDisplayName.orEmpty().lowercase() }, { it.accountType.orEmpty() }, { it.accountName.orEmpty() }))
    )
}
