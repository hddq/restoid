package io.github.hddq.restoid.model

import kotlinx.serialization.Serializable

data class ContactAccountInfo(
    val selectionKey: String,
    val accountName: String,
    val accountType: String,
    val displayName: String,
    val accountTypeLabel: String?,
    val isLocal: Boolean,
    val isReadOnly: Boolean
)

@Serializable
data class ContactsBackupArchive(
    val version: Int = 1,
    val groups: List<BackedUpContactGroup> = emptyList(),
    val contacts: List<BackedUpRawContact> = emptyList()
)

@Serializable
data class BackedUpContactGroup(
    val title: String,
    val notes: String? = null,
    val accountName: String? = null,
    val accountType: String? = null
)

@Serializable
data class BackedUpRawContact(
    val accountName: String? = null,
    val accountType: String? = null,
    val readOnly: Boolean = false,
    val starred: Boolean = false,
    val customRingtone: String? = null,
    val sendToVoicemail: Boolean = false,
    val displayName: String? = null,
    val photoFileName: String? = null,
    val groupTitles: List<String> = emptyList(),
    val dataRows: List<BackedUpContactDataRow> = emptyList()
)

@Serializable
data class BackedUpContactDataRow(
    val mimeType: String,
    val isPrimary: Boolean = false,
    val isSuperPrimary: Boolean = false,
    val data: Map<String, String> = emptyMap()
)

internal fun buildContactAccountSelectionKey(
    accountType: String?,
    accountName: String?
): String {
    return listOf(
        accountType?.trim().orEmpty(),
        accountName?.trim().orEmpty()
    ).joinToString("\u001F")
}

internal fun normalizePhoneForDedup(
    raw: String,
    defaultCountryIso: String? = null,
    e164Formatter: ((String, String) -> String?)? = null
): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val digits = trimmed.filter { it.isDigit() }
    if (digits.isEmpty()) return ""

    val country = defaultCountryIso?.trim()?.uppercase()?.takeIf { it.length == 2 }

    if (trimmed.startsWith("+")) {
        val formatted = if (country != null && e164Formatter != null) {
            e164Formatter(trimmed, country)?.takeIf { it.startsWith("+") }
        } else {
            null
        }
        return formatted ?: "+$digits"
    }

    if (trimmed.startsWith("00") && digits.length > 6) {
        val intl = "+${digits.drop(2)}"
        val formatted = if (country != null && e164Formatter != null) {
            (e164Formatter(trimmed, country) ?: e164Formatter(intl, country))
                ?.takeIf { it.startsWith("+") }
        } else {
            null
        }
        return formatted ?: intl
    }

    if (country != null) {
        e164Formatter?.invoke(trimmed, country)
            ?.takeIf { it.startsWith("+") }
            ?.let { return it }

        if (country in NANP_COUNTRIES) {
            if (digits.startsWith("011") && digits.length > 5) return "+${digits.drop(3)}"
            if (digits.length == 10) return "+1$digits"
            if (digits.length == 11 && digits.startsWith("1")) return "+$digits"
        } else {
            val callingCode = TRUNK_ZERO_CALLING_CODES[country]
            if (callingCode != null && digits.startsWith("0") && digits.length > 6) {
                return "+$callingCode${digits.drop(1)}"
            }
        }
    }

    return digits
}

internal fun buildContactDedupKeys(
    displayName: String?,
    phones: List<String>,
    emails: List<String>,
    defaultCountryIso: String? = null,
    e164Formatter: ((String, String) -> String?)? = null
): Set<String> {
    val keys = LinkedHashSet<String>()
    phones.forEach { phone ->
        val normalized = normalizePhoneForDedup(phone, defaultCountryIso, e164Formatter)
        if (normalized.isNotEmpty()) {
            keys.add("p:$normalized")
        }
    }
    emails.forEach { email ->
        val normalized = email.trim().lowercase()
        if (normalized.isNotEmpty()) {
            keys.add("e:$normalized")
        }
    }
    if (keys.isEmpty()) {
        val normalizedName = displayName?.trim()?.lowercase().orEmpty()
        if (normalizedName.isNotEmpty()) {
            keys.add("n:$normalizedName")
        }
    }
    return keys
}

internal fun BackedUpRawContact.extractDedupKeys(
    defaultCountryIso: String? = null,
    e164Formatter: ((String, String) -> String?)? = null
): Set<String> {
    val phones = dataRows
        .filter { it.mimeType == PHONE_MIME_TYPE }
        .mapNotNull { it.data["data1"] }
    val emails = dataRows
        .filter { it.mimeType == EMAIL_MIME_TYPE }
        .mapNotNull { it.data["data1"] }
    return buildContactDedupKeys(displayName, phones, emails, defaultCountryIso, e164Formatter)
}

internal fun coalesceContactsForImport(
    contacts: List<BackedUpRawContact>,
    defaultCountryIso: String? = null,
    e164Formatter: ((String, String) -> String?)? = null
): List<BackedUpRawContact> {
    if (contacts.size <= 1) return contacts

    val parent = IntArray(contacts.size) { it }
    fun find(x: Int): Int {
        var cur = x
        while (parent[cur] != cur) {
            parent[cur] = parent[parent[cur]]
            cur = parent[cur]
        }
        return cur
    }
    fun union(a: Int, b: Int) {
        val ra = find(a)
        val rb = find(b)
        if (ra != rb) parent[rb] = ra
    }

    val ownerByKey = HashMap<String, Int>()
    contacts.forEachIndexed { index, contact ->
        val keys = contact.extractDedupKeys(defaultCountryIso, e164Formatter)
        keys.forEach { key ->
            val existingOwner = ownerByKey.putIfAbsent(key, index)
            if (existingOwner != null) {
                union(existingOwner, index)
            }
        }
    }

    val components = LinkedHashMap<Int, MutableList<BackedUpRawContact>>()
    contacts.forEachIndexed { index, contact ->
        components.getOrPut(find(index)) { mutableListOf() }.add(contact)
    }

    return components.values.map { group ->
        if (group.size == 1) {
            group.first()
        } else {
            mergeRawContacts(group, defaultCountryIso, e164Formatter)
        }
    }
}

private fun mergeRawContacts(
    group: List<BackedUpRawContact>,
    defaultCountryIso: String?,
    e164Formatter: ((String, String) -> String?)?
): BackedUpRawContact {
    val first = group.first()
    val mergedRows = LinkedHashMap<String, BackedUpContactDataRow>()

    group.forEach { contact ->
        contact.dataRows.forEach { row ->
            val dedupKey = when (row.mimeType) {
                PHONE_MIME_TYPE -> {
                    val norm = row.data["data1"]
                        ?.let { normalizePhoneForDedup(it, defaultCountryIso, e164Formatter) }
                        .orEmpty()
                    if (norm.isNotEmpty()) "phone:$norm" else "raw:${row.mimeType}:${row.data}"
                }
                EMAIL_MIME_TYPE -> {
                    val norm = row.data["data1"]?.trim()?.lowercase().orEmpty()
                    if (norm.isNotEmpty()) "email:$norm" else "raw:${row.mimeType}:${row.data}"
                }
                STRUCTURED_NAME_MIME_TYPE -> {
                    val norm = row.data["data1"]?.trim()?.lowercase().orEmpty()
                    if (norm.isNotEmpty()) "name:$norm" else "raw:${row.mimeType}:${row.data}"
                }
                else -> "raw:${row.mimeType}:${row.data}"
            }

            val existing = mergedRows[dedupKey]
            if (existing == null) {
                mergedRows[dedupKey] = row
            } else {
                val combinedData = LinkedHashMap(row.data).apply { putAll(existing.data) }
                mergedRows[dedupKey] = existing.copy(
                    isPrimary = existing.isPrimary || row.isPrimary,
                    isSuperPrimary = existing.isSuperPrimary || row.isSuperPrimary,
                    data = combinedData.toSortedMap()
                )
            }
        }
    }

    return first.copy(
        starred = group.any { it.starred },
        sendToVoicemail = group.any { it.sendToVoicemail },
        customRingtone = group.firstNotNullOfOrNull { it.customRingtone?.takeIf(String::isNotBlank) },
        displayName = group.firstNotNullOfOrNull { it.displayName?.takeIf(String::isNotBlank) },
        photoFileName = group.firstNotNullOfOrNull { it.photoFileName?.takeIf(String::isNotBlank) },
        groupTitles = group.flatMapTo(LinkedHashSet()) { it.groupTitles }.toList(),
        dataRows = mergedRows.values.toList()
    )
}

private const val PHONE_MIME_TYPE = "vnd.android.cursor.item/phone_v2"
private const val EMAIL_MIME_TYPE = "vnd.android.cursor.item/email_v2"
private const val STRUCTURED_NAME_MIME_TYPE = "vnd.android.cursor.item/name"

private val NANP_COUNTRIES = setOf("US", "CA")

private val TRUNK_ZERO_CALLING_CODES = mapOf(
    "AU" to "61",
    "AT" to "43",
    "BE" to "32",
    "BR" to "55",
    "CH" to "41",
    "CN" to "86",
    "CZ" to "420",
    "DE" to "49",
    "ES" to "34",
    "FI" to "358",
    "FR" to "33",
    "GB" to "44",
    "GR" to "30",
    "HU" to "36",
    "ID" to "62",
    "IE" to "353",
    "IL" to "972",
    "IN" to "91",
    "IT" to "39",
    "JP" to "81",
    "KR" to "82",
    "MX" to "52",
    "NL" to "31",
    "NZ" to "64",
    "PL" to "48",
    "PT" to "351",
    "RO" to "40",
    "RU" to "7",
    "SE" to "46",
    "TR" to "90",
    "UA" to "380",
    "ZA" to "27"
)
