package io.github.hddq.restoid.data

import android.content.Context
import io.github.hddq.restoid.model.RestoidMetadata
import io.github.hddq.restoid.model.Schedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class MetadataRepository(private val context: Context) {

    private val metadataRoot = File(context.filesDir, "metadata")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getSchedules(repoId: String): List<Schedule> {
        return withContext(Dispatchers.IO) {
            try {
                val repoDir = File(metadataRoot, repoId)
                val schedulesFile = File(repoDir, "schedules.json")
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
                val schedulesFile = File(repoDir, "schedules.json")
                schedulesFile.writeText(json.encodeToString(schedules))
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
                metadataFile.writeText(json.encodeToString(metadata))
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

            repoDir.listFiles { _, name -> name.endsWith(".json") }
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
}

