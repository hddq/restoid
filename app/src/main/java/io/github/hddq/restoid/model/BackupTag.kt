package io.github.hddq.restoid.model

import kotlinx.serialization.Serializable

@Serializable
data class BackupTag(
    val name: String,
    val colorArgb: Int
)

object BackupTagRules {
    val RESERVED_TAGS = setOf("restoid", "backup", "metadata")

    val SUGGESTED_COLOR_ARGBS: List<Int> = listOf(
        0xFFE53935.toInt(), // Red
        0xFFFB8C00.toInt(), // Orange
        0xFFFFB300.toInt(), // Amber
        0xFF43A047.toInt(), // Green
        0xFF00897B.toInt(), // Teal
        0xFF00ACC1.toInt(), // Cyan
        0xFF1E88E5.toInt(), // Blue
        0xFF3949AB.toInt(), // Indigo
        0xFF8E24AA.toInt(), // Purple
        0xFFD81B60.toInt()  // Pink
    )

    enum class TagValidationError {
        EMPTY,
        CONTAINS_COMMA,
        RESERVED
    }

    fun normalizeTagName(raw: String): String = raw.trim()

    fun validateTagName(raw: String): TagValidationError? {
        val trimmed = normalizeTagName(raw)
        if (trimmed.isEmpty()) return TagValidationError.EMPTY
        if (',' in trimmed) return TagValidationError.CONTAINS_COMMA
        if (trimmed.lowercase() in RESERVED_TAGS) return TagValidationError.RESERVED
        return null
    }

    fun filterUserTags(tags: List<String>): List<String> {
        return tags
            .map(::normalizeTagName)
            .filter { it.isNotEmpty() && it.lowercase() !in RESERVED_TAGS }
            .distinct()
    }

    fun sanitizeBackupTags(tags: List<String>): List<String> {
        return tags
            .map(::normalizeTagName)
            .filter { validateTagName(it) == null }
            .distinct()
    }
}
