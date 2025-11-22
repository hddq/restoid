package io.github.hddq.restoid.data

import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import io.github.hddq.restoid.util.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

// Represents the state of the "Add Repository" operation
sealed class AddRepositoryState {
    object Idle : AddRepositoryState()
    object Initializing : AddRepositoryState()
    object Success : AddRepositoryState()
    data class Error(val message: String) : AddRepositoryState()
}

class RepositoriesRepository(
    private val context: Context,
    private val passwordManager: PasswordManager,
    private val binaryManager: ResticBinaryManager
) {
    private val prefs = context.getSharedPreferences("repositories", Context.MODE_PRIVATE)
    private val REPOS_KEY = "repositories_json_v1"
    private val SELECTED_REPO_PATH_KEY = "selected_repo_path"

    private val _repositories = MutableStateFlow<List<LocalRepository>>(emptyList())
    val repositories = _repositories.asStateFlow()

    private val _selectedRepository = MutableStateFlow<String?>(null)
    val selectedRepository = _selectedRepository.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    fun loadRepositories() {
        val repoJsonSet = prefs.getStringSet(REPOS_KEY, emptySet()) ?: emptySet()
        _repositories.value = repoJsonSet.mapNotNull { jsonString ->
            try {
                json.decodeFromString<LocalRepository>(jsonString)
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.name }
        _selectedRepository.value = prefs.getString(SELECTED_REPO_PATH_KEY, null)
    }

    fun selectRepository(path: String) {
        prefs.edit().putString(SELECTED_REPO_PATH_KEY, path).apply()
        _selectedRepository.value = path
    }

    fun getRepositoryPassword(path: String): String? = passwordManager.getPassword(path)
    fun hasRepositoryPassword(path: String): Boolean = passwordManager.hasPassword(path)
    fun hasStoredRepositoryPassword(path: String): Boolean = passwordManager.hasStoredPassword(path)
    fun saveRepositoryPasswordTemporary(path: String, password: String) = passwordManager.savePasswordTemporary(path, password)
    fun saveRepositoryPassword(path: String, password: String) = passwordManager.savePassword(path, password)
    fun forgetPassword(path: String) = passwordManager.removeStoredPassword(path)

    fun deleteRepository(path: String) {
        val currentJsonSet = prefs.getStringSet(REPOS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        val repoToRemoveJson = currentJsonSet.find {
            try { json.decodeFromString<LocalRepository>(it).path == path } catch (e: Exception) { false }
        }

        if (repoToRemoveJson != null && currentJsonSet.remove(repoToRemoveJson)) {
            prefs.edit().putStringSet(REPOS_KEY, currentJsonSet).apply()
            passwordManager.removePassword(path)

            if (_selectedRepository.value == path) {
                prefs.edit().remove(SELECTED_REPO_PATH_KEY).apply()
                _selectedRepository.value = null
            }
            loadRepositories()
        }
    }

    suspend fun addRepository(
        path: String,
        password: String,
        resticRepository: ResticRepository, // Still needed for metadata restore
        savePassword: Boolean
    ): AddRepositoryState {
        return withContext(Dispatchers.IO) {
            val resolvedPath = StorageUtils.resolvePathForShell(path)

            if (repositories.value.any { it.path == resolvedPath }) {
                return@withContext AddRepositoryState.Error("Repository already exists.")
            }

            val binaryState = binaryManager.resticState.value
            if (binaryState !is ResticState.Installed) {
                return@withContext AddRepositoryState.Error("Restic binary not ready.")
            }

            val resticPath = binaryState.path
            val wasEmpty = repositories.value.isEmpty()
            val passwordFile = File.createTempFile("restic-pass", ".tmp", context.cacheDir)

            try {
                passwordFile.writeText(password)

                // Check if exists using shell (bypasses scoped storage limits for root)
                val repoExists = Shell.cmd("[ -f '$resolvedPath/config' ]").exec().isSuccess

                if (repoExists) {
                    // Verify password
                    val checkResult = Shell.cmd("RESTIC_PASSWORD_FILE='${passwordFile.absolutePath}' $resticPath -r '$resolvedPath' list keys --no-lock").exec()
                    if (checkResult.isSuccess) {
                        handleSuccessfulRepoAdd(resolvedPath, password, resticRepository, savePassword, wasEmpty)
                    } else {
                        AddRepositoryState.Error("Invalid password or corrupted repository.")
                    }
                } else {
                    // Create new
                    val mkdirResult = Shell.cmd("mkdir -p '$resolvedPath'").exec()
                    if (!mkdirResult.isSuccess) return@withContext AddRepositoryState.Error("Failed to create directory.")

                    val initResult = Shell.cmd("RESTIC_PASSWORD_FILE='${passwordFile.absolutePath}' $resticPath -r '$resolvedPath' init").exec()
                    if (initResult.isSuccess) {
                        handleSuccessfulRepoAdd(resolvedPath, password, resticRepository, savePassword, wasEmpty)
                    } else {
                        Shell.cmd("rm -rf '$resolvedPath'").exec()
                        AddRepositoryState.Error("Failed to initialize repository.")
                    }
                }
            } finally {
                passwordFile.delete()
            }
        }
    }

    private suspend fun handleSuccessfulRepoAdd(path: String, password: String, resticRepository: ResticRepository, savePassword: Boolean, wasEmpty: Boolean): AddRepositoryState {
        val configResult = resticRepository.getConfig(path, password)
        if (configResult.isSuccess) {
            val repoId = configResult.getOrNull()?.id
            // Attempt metadata restore (best effort)
            if (repoId != null) {
                restoreMetadataForRepo(repoId, path, password, resticRepository)
            }
            resticRepository.clearSnapshots()
            saveNewRepository(LocalRepository(path = path, id = repoId), password, savePassword, wasEmpty)
            return AddRepositoryState.Success
        } else {
            return AddRepositoryState.Error("Repo valid, but failed to get ID.")
        }
    }

    private suspend fun restoreMetadataForRepo(repoId: String, repoPath: String, password: String, resticRepository: ResticRepository) {
        val tempRestoreDir = File(context.cacheDir, "metadata_restore_${System.currentTimeMillis()}").also { it.mkdirs() }
        try {
            val snapshots = resticRepository.getSnapshots(repoPath, password).getOrNull()
            val metadataSnapshot = snapshots
                ?.filter { it.tags.contains("restoid") && it.tags.contains("metadata") }
                ?.maxByOrNull { it.time }

            if (metadataSnapshot != null) {
                val restoreResult = resticRepository.restore(
                    repoPath = repoPath,
                    password = password,
                    snapshotId = metadataSnapshot.id,
                    targetPath = tempRestoreDir.absolutePath,
                    pathsToRestore = emptyList()
                )

                if (restoreResult.isSuccess) {
                    val finalMetadataParentDir = File(context.filesDir, "metadata")
                    if (!finalMetadataParentDir.exists()) finalMetadataParentDir.mkdirs()

                    val ownerResult = Shell.cmd("stat -c '%u:%g' ${context.filesDir.absolutePath}").exec()
                    val owner = if (ownerResult.isSuccess) ownerResult.out.firstOrNull()?.trim() else null

                    if (owner != null) {
                        Shell.cmd("chown -R $owner '${tempRestoreDir.absolutePath}'").exec()
                        Shell.cmd("cp -a '${tempRestoreDir.absolutePath}/.' '${finalMetadataParentDir.absolutePath}/'").exec()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RepoRepo", "Error during metadata restore", e)
        } finally {
            Shell.cmd("rm -rf '${tempRestoreDir.absolutePath}'").exec()
        }
    }

    private fun saveNewRepository(repo: LocalRepository, password: String, save: Boolean, wasEmpty: Boolean) {
        saveRepository(repo)
        if (save) passwordManager.savePassword(repo.path, password)
        else passwordManager.savePasswordTemporary(repo.path, password)

        loadRepositories()
        if (wasEmpty) selectRepository(repo.path)
    }

    private fun saveRepository(repository: LocalRepository) {
        val currentJsonSet = prefs.getStringSet(REPOS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        val repoJson = json.encodeToString(repository)
        currentJsonSet.add(repoJson)
        prefs.edit().putStringSet(REPOS_KEY, currentJsonSet).apply()
    }
}