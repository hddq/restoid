package io.github.hddq.restoid.data

import android.content.Context
import io.github.hddq.restoid.model.RestoidMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class MetadataRepository(private val context: Context) {

    private val metadataRoot = File(context.filesDir, "metadata")
    private val json = Json { ignoreUnknownKeys = true }

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

