package io.github.hddq.restoid.data

import android.content.Context
import io.github.hddq.restoid.model.RestoidMetadata
import io.github.hddq.restoid.model.Schedule
import io.github.hddq.restoid.model.TaskPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

class MetadataRepository internal constructor(
    private val metadataRoot: File
) {
    constructor(context: Context) : this(File(context.filesDir, "metadata"))

    private val json = Json { ignoreUnknownKeys = true }

    // LEGACY_SCHEDULE_MIGRATION: per-repo lock to serialize lazy migration checks.
    private val migrationMutexes = ConcurrentHashMap<String, Mutex>()

    internal fun writeTextAtomically(targetFile: File, content: String) {
        val parentDir = targetFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }
        val tempFile = File.createTempFile("${targetFile.name}.", ".tmp", parentDir)
        try {
            FileOutputStream(tempFile).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
                fos.flush()
                runCatching { fos.fd.sync() }
            }
            try {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    // LEGACY_SCHEDULE_MIGRATION: single lazy hook invoked before reading schedules or presets.
    private suspend fun ensureLegacySchedulesMigrated(repoId: String) {
        val repoDir = File(metadataRoot, repoId)
        val schedulesFile = File(repoDir, SCHEDULES_FILE_NAME)
        if (!schedulesFile.exists()) return
        val mutex = migrationMutexes.computeIfAbsent(repoId) { Mutex() }
        mutex.withLock {
            LegacyScheduleMigration.migrateIfNeeded(
                repoDir = repoDir,
                json = json,
                writeTextAtomically = ::writeTextAtomically
            )
        }
    }

    suspend fun getTaskPresets(repoId: String): List<TaskPreset> {
        return withContext(Dispatchers.IO) {
            try {
                ensureLegacySchedulesMigrated(repoId)
                val repoDir = File(metadataRoot, repoId)
                val presetsFile = File(repoDir, PRESETS_FILE_NAME)
                if (presetsFile.exists()) {
                    json.decodeFromString<List<TaskPreset>>(presetsFile.readText())
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun saveTaskPresets(repoId: String, presets: List<TaskPreset>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val repoDir = File(metadataRoot, repoId)
                if (!repoDir.exists()) repoDir.mkdirs()
                val presetsFile = File(repoDir, PRESETS_FILE_NAME)
                writeTextAtomically(presetsFile, json.encodeToString(presets))
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun getSchedules(repoId: String): List<Schedule> {
        return withContext(Dispatchers.IO) {
            try {
                ensureLegacySchedulesMigrated(repoId)
                val repoDir = File(metadataRoot, repoId)
                val schedulesFile = File(repoDir, SCHEDULES_FILE_NAME)
                if (schedulesFile.exists()) {
                    json.decodeFromString<List<Schedule>>(schedulesFile.readText())
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun saveSchedules(repoId: String, schedules: List<Schedule>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val repoDir = File(metadataRoot, repoId)
                if (!repoDir.exists()) repoDir.mkdirs()
                val schedulesFile = File(repoDir, SCHEDULES_FILE_NAME)
                writeTextAtomically(schedulesFile, json.encodeToString(schedules))
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun getMetadataForSnapshot(repoId: String, snapshotId: String): RestoidMetadata? {
        return withContext(Dispatchers.IO) {
            try {
                val repoDir = File(metadataRoot, repoId)
                if (!repoDir.exists() || !repoDir.isDirectory) return@withContext null

                // Look for an exact match using the full snapshot ID.
                val metadataFile = File(repoDir, "$snapshotId.json")

                if (metadataFile.exists()) {
                    val content = metadataFile.readText()
                    json.decodeFromString<RestoidMetadata>(content)
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun saveMetadataForSnapshot(repoId: String, snapshotId: String, metadata: RestoidMetadata): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val repoDir = File(metadataRoot, repoId)
                if (!repoDir.exists()) repoDir.mkdirs()
                val metadataFile = File(repoDir, "$snapshotId.json")
                writeTextAtomically(metadataFile, json.encodeToString(metadata))
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun getAllMetadata(repoId: String): Map<String, RestoidMetadata> {
        return withContext(Dispatchers.IO) {
            val repoDir = File(metadataRoot, repoId)
            if (!repoDir.exists() || !repoDir.isDirectory) return@withContext emptyMap()

            repoDir.listFiles { _, name ->
                name.endsWith(".json") && name !in NON_SNAPSHOT_METADATA_FILES
            }
                ?.associate { file ->
                    val snapshotId = file.nameWithoutExtension
                    val metadata = try {
                        json.decodeFromString<RestoidMetadata>(file.readText())
                    } catch (e: Exception) {
                        null
                    }
                    snapshotId to metadata
                }
                ?.filterValues { it != null }
                ?.mapValues { it.value!! } ?: emptyMap()
        }
    }

    suspend fun deleteMetadataForSnapshot(repoId: String, snapshotId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val repoDir = File(metadataRoot, repoId)
                if (!repoDir.exists()) return@withContext true // Nothing to delete

                val metadataFile = File(repoDir, "$snapshotId.json")
                if (metadataFile.exists()) {
                    metadataFile.delete()
                } else {
                    true // File doesn't exist, so it's already "deleted"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun pruneOrphanedMetadata(repoId: String, activeSnapshotIds: Set<String>): Int {
        return withContext(Dispatchers.IO) {
            try {
                val repoDir = File(metadataRoot, repoId)
                if (!repoDir.exists() || !repoDir.isDirectory) return@withContext 0

                val files = repoDir.listFiles { _, name ->
                    name.endsWith(".json") && name !in NON_SNAPSHOT_METADATA_FILES
                } ?: return@withContext 0

                var deletedCount = 0
                for (file in files) {
                    val snapshotId = file.nameWithoutExtension
                    if (!SNAPSHOT_ID_REGEX.matches(snapshotId)) continue
                    if (snapshotId !in activeSnapshotIds && file.delete()) {
                        deletedCount++
                    }
                }
                deletedCount
            } catch (e: Exception) {
                e.printStackTrace()
                0
            }
        }
    }

    internal companion object {
        const val SCHEDULES_FILE_NAME = "schedules.json"
        const val PRESETS_FILE_NAME = "presets.json"
        private val NON_SNAPSHOT_METADATA_FILES = setOf(SCHEDULES_FILE_NAME, PRESETS_FILE_NAME)
        private val SNAPSHOT_ID_REGEX = Regex("^[0-9a-fA-F]{8,64}$")
    }
}

