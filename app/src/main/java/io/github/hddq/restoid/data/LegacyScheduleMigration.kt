package io.github.hddq.restoid.data

// ============================================================================
// LEGACY_SCHEDULE_MIGRATION
// Isolated migration from legacy schedules.json (inline RunTasksConfig) to
// presets.json (TaskPreset) + schedules.json (presetId reference).
//
// To remove in a future release, search for "LEGACY_SCHEDULE_MIGRATION" across
// the codebase, delete this file, remove the single hook in MetadataRepository,
// and delete LegacyScheduleMigrationTest.
// ============================================================================

import io.github.hddq.restoid.model.Schedule
import io.github.hddq.restoid.model.TaskPreset
import io.github.hddq.restoid.model.TriggerConditions
import io.github.hddq.restoid.work.RunTasksConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.File
import java.util.UUID

internal object LegacyScheduleMigration {

    @Serializable
    private data class LegacyScheduleRecord(
        val id: String,
        val name: String,
        val intervalHours: Int,
        val config: RunTasksConfig,
        val triggerConditions: TriggerConditions = TriggerConditions(),
        val lastRunTimestamp: Long? = null,
        val isEnabled: Boolean = true
    )

    internal fun deterministicPresetId(legacyScheduleId: String): String {
        return UUID.nameUUIDFromBytes(
            "legacy_schedule_$legacyScheduleId".toByteArray(Charsets.UTF_8)
        ).toString()
    }

    /**
     * Inspects [repoDir]/schedules.json and, if any entry uses the legacy inline `config`
     * format (missing `presetId`), extracts deterministic-ID [TaskPreset]s into
     * [repoDir]/presets.json first, then rewrites [repoDir]/schedules.json with `presetId`
     * references while preserving original schedule IDs.
     *
     * Idempotent and safe against partial failures or restored legacy metadata snapshots.
     *
     * @return `true` if migration wrote updated files, `false` if no legacy entries were found.
     */
    fun migrateIfNeeded(
        repoDir: File,
        json: Json,
        writeTextAtomically: (File, String) -> Unit
    ): Boolean {
        val schedulesFile = File(repoDir, MetadataRepository.SCHEDULES_FILE_NAME)
        if (!schedulesFile.exists() || !schedulesFile.isFile) return false

        val rawText = schedulesFile.readText()
        if (rawText.isBlank()) return false

        val rootElement = runCatching { json.parseToJsonElement(rawText) }.getOrNull()
            ?: return false
        val scheduleElements = rootElement as? JsonArray ?: return false
        if (scheduleElements.isEmpty()) return false

        val hasLegacyEntry = scheduleElements.any { element ->
            element is JsonObject &&
                !element.containsKey("presetId") &&
                element.containsKey("config")
        }
        if (!hasLegacyEntry) return false

        val presetsFile = File(repoDir, MetadataRepository.PRESETS_FILE_NAME)
        val existingPresets = if (presetsFile.exists() && presetsFile.isFile) {
            runCatching {
                json.decodeFromString<List<TaskPreset>>(presetsFile.readText())
            }.getOrElse {
                return false
            }
        } else {
            emptyList()
        }

        val mergedPresets = existingPresets.toMutableList()
        val existingPresetIds = mergedPresets.mapTo(mutableSetOf()) { it.id }
        val migratedSchedules = mutableListOf<Schedule>()

        for (element in scheduleElements) {
            val isLegacy = element is JsonObject &&
                !element.containsKey("presetId") &&
                element.containsKey("config")

            if (isLegacy) {
                val legacy = json.decodeFromJsonElement<LegacyScheduleRecord>(element)
                val presetId = deterministicPresetId(legacy.id)
                if (presetId !in existingPresetIds) {
                    mergedPresets.add(
                        TaskPreset(
                            id = presetId,
                            name = legacy.name,
                            config = legacy.config,
                            lastRunTimestamp = legacy.lastRunTimestamp
                        )
                    )
                    existingPresetIds.add(presetId)
                }
                migratedSchedules.add(
                    Schedule(
                        id = legacy.id,
                        presetId = presetId,
                        name = null,
                        intervalHours = legacy.intervalHours,
                        triggerConditions = legacy.triggerConditions,
                        lastRunTimestamp = legacy.lastRunTimestamp,
                        isEnabled = legacy.isEnabled
                    )
                )
            } else {
                migratedSchedules.add(json.decodeFromJsonElement<Schedule>(element))
            }
        }

        // Write presets.json FIRST so a crash between writes never loses task configurations.
        writeTextAtomically(presetsFile, json.encodeToString(mergedPresets))
        writeTextAtomically(schedulesFile, json.encodeToString(migratedSchedules))
        return true
    }
}
