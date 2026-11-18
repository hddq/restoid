package io.github.hddq.restoid.model

import kotlinx.serialization.Serializable

@Serializable
data class CallLogsBackupArchive(
    val version: Int = 1,
    val calls: List<BackedUpCallLogEntry> = emptyList()
)

@Serializable
data class BackedUpCallLogEntry(
    val number: String? = null,
    val presentation: Int? = null,
    val type: Int,
    val date: Long,
    val duration: Long,
    val isNew: Int? = null,
    val cachedName: String? = null,
    val cachedNumberType: Int? = null,
    val cachedNumberLabel: String? = null,
    val countryIso: String? = null,
    val geocodedLocation: String? = null,
    val features: Int? = null,
    val isRead: Int? = null,
    val phoneAccountComponentName: String? = null,
    val phoneAccountId: String? = null,
    val voicemailUri: String? = null,
    val postDialDigits: String? = null,
    val viaNumber: String? = null,
    val callScreeningAppName: String? = null,
    val callScreeningComponentName: String? = null,
    val blockReason: Int? = null
)

private const val PRESENTATION_RESTRICTED = 2
private const val PRESENTATION_UNKNOWN = 3
private const val PRESENTATION_PAYPHONE = 4
private const val PRESENTATION_UNAVAILABLE = 5

internal fun buildCallLogDedupKey(
    number: String?,
    presentation: Int?,
    type: Int,
    date: Long,
    duration: Long,
    defaultCountryIso: String? = null,
    e164Formatter: ((String, String) -> String?)? = null
): String {
    val trimmed = number?.trim().orEmpty()
    val isHiddenPresentation = presentation == PRESENTATION_RESTRICTED ||
        presentation == PRESENTATION_UNKNOWN ||
        presentation == PRESENTATION_UNAVAILABLE ||
        (presentation == PRESENTATION_PAYPHONE && !trimmed.any { it.isDigit() })
    val normalizedNumber = when {
        trimmed.isEmpty() || isHiddenPresentation -> "p:${presentation ?: -1}"
        trimmed.any { it.isLetter() } -> trimmed.lowercase()
        else -> normalizePhoneForDedup(trimmed, defaultCountryIso, e164Formatter).ifEmpty { trimmed }
    }
    return "$normalizedNumber|$date|$type|$duration"
}

internal fun BackedUpCallLogEntry.toDedupKey(
    defaultCountryIso: String? = null,
    e164Formatter: ((String, String) -> String?)? = null
): String = buildCallLogDedupKey(
    number = number,
    presentation = presentation,
    type = type,
    date = date,
    duration = duration,
    defaultCountryIso = countryIso ?: defaultCountryIso,
    e164Formatter = e164Formatter
)
