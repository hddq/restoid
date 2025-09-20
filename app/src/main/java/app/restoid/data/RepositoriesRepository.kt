package app.restoid.data

import android.content.Context
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
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
     * already exists at the given path with the provided password.
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
            val repoDir = File(path)
            val configFile = File(repoDir, "config")

            if (repositories.value.any { it.path == path }) {
                return@withContext AddRepositoryState.Error("Repository already exists.")
            }
            val wasEmpty = repositories.value.isEmpty()
            val passwordFile = File.createTempFile("restic-pass", ".tmp", context.cacheDir)

            try {
                passwordFile.writeText(password)
                val resticPath = (resticRepository.resticState.value as ResticState.Installed).path

                // Check if it's an existing repository by trying to list keys
                if (repoDir.exists() && configFile.exists()) {
                    val checkCommand = "RESTIC_PASSWORD_FILE='${passwordFile.absolutePath}' $resticPath -r '$path' list keys --no-lock"
                    val checkResult = Shell.cmd(checkCommand).exec()
                    if (checkResult.isSuccess) {
                        val configResult = resticRepository.getConfig(path, password)
                        if (configResult.isSuccess) {
                            val repoId = configResult.getOrNull()?.id
                            saveNewRepository(LocalRepository(path = path, id = repoId), password, savePassword, wasEmpty)
                            return@withContext AddRepositoryState.Success
                        } else {
                            return@withContext AddRepositoryState.Error("Repo valid, but failed to get ID.")
                        }
                    } else {
                        val errorOutput = checkResult.err.joinToString("\n")
                        return@withContext AddRepositoryState.Error(if (errorOutput.isEmpty()) "Invalid password or corrupted repository." else errorOutput)
                    }
                }

                if (!repoDir.exists() && !repoDir.mkdirs()) {
                    return@withContext AddRepositoryState.Error("Failed to create directory at $path")
                }

                val initCommand = "RESTIC_PASSWORD_FILE='${passwordFile.absolutePath}' $resticPath -r '$path' init"
                val initResult = Shell.cmd(initCommand).exec()

                if (initResult.isSuccess) {
                    val configResult = resticRepository.getConfig(path, password)
                    if (configResult.isSuccess) {
                        val repoId = configResult.getOrNull()?.id
                        saveNewRepository(LocalRepository(path = path, id = repoId), password, savePassword, wasEmpty)
                        return@withContext AddRepositoryState.Success
                    } else {
                        repoDir.deleteRecursively()
                        return@withContext AddRepositoryState.Error("Initialized, but failed to get ID.")
                    }
                } else {
                    repoDir.deleteRecursively()
                    val errorOutput = initResult.err.joinToString("\n")
                    return@withContext AddRepositoryState.Error(if (errorOutput.isEmpty()) "Failed to initialize repository." else errorOutput)
                }
            } finally {
                passwordFile.delete()
            }
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
