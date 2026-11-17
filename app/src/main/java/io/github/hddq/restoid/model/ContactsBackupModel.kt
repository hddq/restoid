package io.github.hddq.restoid.model

import kotlinx.serialization.Serializable

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

internal fun normalizePhoneForDedup(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val digitsOnly = trimmed.filter { it.isDigit() }
    return if (digitsOnly.length >= 7) {
        digitsOnly.takeLast(10)
    } else {
        digitsOnly
    }
}

internal fun buildContactDedupKeys(
    displayName: String?,
    phones: List<String>,
    emails: List<String>
): Set<String> {
    val keys = LinkedHashSet<String>()
    phones.forEach { phone ->
        val normalized = normalizePhoneForDedup(phone)
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
