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

/**
 * Manages the list of configured restic repositories.
 * For now, it only supports local repositories and stores them in SharedPreferences.
 */

// Represents the state of the "Add Repository" operation
sealed class AddRepositoryState {
    object Idle : AddRepositoryState()
    object Initializing : AddRepositoryState()
    object Success : AddRepositoryState()
    data class Error(val message: String) : AddRepositoryState()
}

class RepositoriesRepository(
    private val context: Context,
    private val passwordManager: PasswordManager
) {
    private val prefs = context.getSharedPreferences("repositories", Context.MODE_PRIVATE)
    private val REPOS_KEY = "repositories_json_v1"
    private val SELECTED_REPO_PATH_KEY = "selected_repo_path"

    private val _repositories = MutableStateFlow<List<LocalRepository>>(emptyList())
    val repositories = _repositories.asStateFlow()

    private val _selectedRepository = MutableStateFlow<String?>(null)
    val selectedRepository = _selectedRepository.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    // Loads the repository paths from SharedPreferences and updates the state flow.
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

        // Also check if metadata folder exists for the selected repository
        val selectedRepoPath = _selectedRepository.value
        if (selectedRepoPath != null) {
            val repo = _repositories.value.find { it.path == selectedRepoPath }
            if (repo?.id != null) {
                val metadataDir = File(context.filesDir, "metadata/${repo.id}")
                if (!metadataDir.exists()) {
                    Log.w("RepoRepo", "Metadata directory for selected repo ${repo.id} is missing!")
                }
            }
        }
    }

    // Saves the selected repository path to SharedPreferences.
    fun selectRepository(path: String) {
        prefs.edit().putString(SELECTED_REPO_PATH_KEY, path).apply()
        _selectedRepository.value = path
    }

    // Gets the stored password for a repository
    fun getRepositoryPassword(path: String): String? {
        return passwordManager.getPassword(path)
    }

    // Checks if a password exists for a repository (either temporary or permanent)
    fun hasRepositoryPassword(path: String): Boolean {
        return passwordManager.hasPassword(path)
    }

    // Checks if a password exists for a repository (permanent only)
    fun hasStoredRepositoryPassword(path: String): Boolean {
        return passwordManager.hasStoredPassword(path)
    }

    // Saves a password temporarily for the session
    fun saveRepositoryPasswordTemporary(path: String, password: String) {
        passwordManager.savePasswordTemporary(path, password)
    }

    // Saves a password permanently
    fun saveRepositoryPassword(path: String, password: String) {
        passwordManager.savePassword(path, password)
    }

    fun forgetPassword(path: String) {
        passwordManager.removeStoredPassword(path)
    }

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

            loadRepositories() // Refresh the list
        }
    }

    /**
     * Adds a new repository. Before initializing, it checks if a valid restic repository
     * already exists at the given path with the provided password. After adding, it attempts
     * to restore the latest metadata backup from the repository.
     *
     * @param path The local file system path for the repository.
     * @param password The password for the new or existing repository.
     * @param resticRepository The repository to execute restic commands.
     * @param savePassword Whether to store the password securely on the device.
     * @return An [AddRepositoryState] indicating the result of the operation.
     */
    suspend fun addRepository(
        path: String,
        password: String,
        resticRepository: ResticRepository,
        savePassword: Boolean
    ): AddRepositoryState {
        return withContext(Dispatchers.IO) {
            // We do NOT use java.io.File here for existence checks or mkdirs.
            // Java runs as the app user and cannot access external storage roots (Scoped Storage).
            // We use Root Shell commands which bypass these restrictions.

            // Resolve the path for the root shell environment (Global Mount Namespace) using Utility
            val resolvedPath = StorageUtils.resolvePathForShell(path)
            Log.d("RepoRepo", "Adding repository at: $resolvedPath (Original: $path)")

            if (repositories.value.any { it.path == resolvedPath }) {
                return@withContext AddRepositoryState.Error("Repository already exists.")
            }
            val wasEmpty = repositories.value.isEmpty()
            val passwordFile = File.createTempFile("restic-pass", ".tmp", context.cacheDir)

            try {
                passwordFile.writeText(password)
                val resticPath = (resticRepository.resticState.value as ResticState.Installed).path

                // 1. Check if it's an existing repository using ROOT shell
                // [ -f path/config ] checks if the config file exists
                val checkConfigCmd = "[ -f '$resolvedPath/config' ]"
                val repoExists = Shell.cmd(checkConfigCmd).exec().isSuccess

                if (repoExists) {
                    val checkCommand = "RESTIC_PASSWORD_FILE='${passwordFile.absolutePath}' $resticPath -r '$resolvedPath' list keys --no-lock"
                    val checkResult = Shell.cmd(checkCommand).exec()
                    if (checkResult.isSuccess) {
                        handleSuccessfulRepoAdd(resolvedPath, password, resticRepository, savePassword, wasEmpty)
                    } else {
                        val errorOutput = checkResult.err.joinToString("\n")
                        AddRepositoryState.Error(if (errorOutput.isEmpty()) "Invalid password or corrupted repository." else errorOutput)
                    }
                } else {
                    // 2. It's a new repository. Create the directory using ROOT shell (mkdir -p)
                    val mkdirResult = Shell.cmd("mkdir -p '$resolvedPath'").exec()

                    if (!mkdirResult.isSuccess) {
                        return@withContext AddRepositoryState.Error("Failed to create directory at $resolvedPath. Check root permissions.")
                    }

                    // 3. Initialize using restic
                    val initCommand = "RESTIC_PASSWORD_FILE='${passwordFile.absolutePath}' $resticPath -r '$resolvedPath' init"
                    val initResult = Shell.cmd(initCommand).exec()

                    if (initResult.isSuccess) {
                        handleSuccessfulRepoAdd(resolvedPath, password, resticRepository, savePassword, wasEmpty)
                    } else {
                        // Clean up failed init using root
                        Shell.cmd("rm -rf '$resolvedPath'").exec()
                        val errorOutput = initResult.err.joinToString("\n")
                        AddRepositoryState.Error(if (errorOutput.isEmpty()) "Failed to initialize repository." else errorOutput)
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

            // 1. Attempt to restore metadata FIRST.
            // This ensures files are on disk before the UI knows about the repo.
            if (repoId != null) {
                restoreMetadataForRepo(repoId, path, password, resticRepository)
            }

            // 2. Clear the snapshot cache in ResticRepository.
            // This is critical! restoring metadata populates the cache with a list that
            // HomeViewModel sees too early. Clearing it forces HomeViewModel to reload
            // everything from scratch when the repo is selected, fixing the race condition.
            resticRepository.clearSnapshots()

            // 3. Save the repo to the app database.
            saveNewRepository(LocalRepository(path = path, id = repoId), password, savePassword, wasEmpty)

            return AddRepositoryState.Success
        } else {
            return AddRepositoryState.Error("Repo valid, but failed to get ID.")
        }
    }


    private suspend fun restoreMetadataForRepo(repoId: String, repoPath: String, password: String, resticRepository: ResticRepository) {
        // This whole operation is best-effort. It should not crash the main addRepository flow.
        val tempRestoreDir = File(context.cacheDir, "metadata_restore_${System.currentTimeMillis()}").also { it.mkdirs() }
        try {
            Log.d("RepoRepo", "Attempting to restore metadata for repoId: $repoId")
            val snapshots = resticRepository.getSnapshots(repoPath, password).getOrNull()
            val metadataSnapshot = snapshots
                ?.filter { it.tags.contains("restoid") && it.tags.contains("metadata") }
                ?.maxByOrNull { it.time }

            if (metadataSnapshot != null) {
                Log.d("RepoRepo", "Found metadata snapshot: ${metadataSnapshot.id}")

                // 1. Restore to a temporary directory.
                val restoreResult = resticRepository.restore(
                    repoPath = repoPath,
                    password = password,
                    snapshotId = metadataSnapshot.id,
                    targetPath = tempRestoreDir.absolutePath,
                    pathsToRestore = emptyList() // Restore everything from snapshot root
                )

                if (restoreResult.isSuccess) {
                    Log.d("RepoRepo", "Successfully restored snapshot to ${tempRestoreDir.absolutePath}")

                    val finalMetadataParentDir = File(context.filesDir, "metadata")
                    if (!finalMetadataParentDir.exists()) {
                        finalMetadataParentDir.mkdirs()
                    }

                    // 2. Determine the correct owner for the app's files ('u0_a123:u0_a123')
                    val ownerResult = Shell.cmd("stat -c '%u:%g' ${context.filesDir.absolutePath}").exec()
                    val owner = if (ownerResult.isSuccess) ownerResult.out.firstOrNull()?.trim() else null
                    Log.d("RepoRepo", "App data owner is: $owner")

                    if (owner != null) {
                        // 3. Fix ownership on the restored files inside the temporary directory FIRST.
                        val chownCmd = "chown -R $owner '${tempRestoreDir.absolutePath}'"
                        val chownResult = Shell.cmd(chownCmd).exec()
                        Log.d("RepoRepo", "Chown command on temp dir executed. Success: ${chownResult.isSuccess}")

                        // 4. Copy everything from the temp restore dir (now with correct owner) into the final parent dir.
                        // 'cp -a' will preserve the ownership we just set.
                        val copyCmd = "cp -a '${tempRestoreDir.absolutePath}/.' '${finalMetadataParentDir.absolutePath}/'"
                        val copyResult = Shell.cmd(copyCmd).exec()
                        Log.d("RepoRepo", "Copy command executed. Success: ${copyResult.isSuccess}")

                    } else {
                        Log.e("RepoRepo", "Could not determine file owner. Skipping copy and chown.")
                    }
                } else {
                    Log.e("RepoRepo", "Restic restore command failed: ${restoreResult.exceptionOrNull()?.message}")
                }
            } else {
                Log.d("RepoRepo", "No metadata snapshot found in the repository.")
            }
        } catch (e: Exception) {
            // Log the exception but don't fail the addRepository operation
            Log.e("RepoRepo", "Error during metadata restore", e)
        } finally {
            // 5. Clean up the temp directory using a root shell to avoid permission issues.
            val cleanupResult = Shell.cmd("rm -rf '${tempRestoreDir.absolutePath}'").exec()
            Log.d("RepoRepo", "Cleaned up temp directory. Success: ${cleanupResult.isSuccess}")
        }
    }

    private fun saveNewRepository(repo: LocalRepository, password: String, save: Boolean, wasEmpty: Boolean) {
        saveRepository(repo)
        if (save) {
            passwordManager.savePassword(repo.path, password)
        } else {
            passwordManager.savePasswordTemporary(repo.path, password)
        }
        loadRepositories()
        if (wasEmpty) {
            selectRepository(repo.path)
        }
    }

    private fun saveRepository(repository: LocalRepository) {
        val currentJsonSet = prefs.getStringSet(REPOS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        val repoJson = json.encodeToString(repository)
        currentJsonSet.add(repoJson)
        prefs.edit().putStringSet(REPOS_KEY, currentJsonSet).apply()
    }
}