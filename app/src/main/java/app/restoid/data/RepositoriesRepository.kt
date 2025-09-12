package app.restoid.data

import android.content.Context
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
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

class RepositoriesRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("repositories", Context.MODE_PRIVATE)
    private val REPO_PATHS_KEY = "repo_paths"
    private val SELECTED_REPO_PATH_KEY = "selected_repo_path"

    private val _repositories = MutableStateFlow<List<LocalRepository>>(emptyList())
    val repositories = _repositories.asStateFlow()

    private val _selectedRepository = MutableStateFlow<String?>(null)
    val selectedRepository = _selectedRepository.asStateFlow()

    // Loads the repository paths from SharedPreferences and updates the state flow.
    fun loadRepositories() {
        val paths = prefs.getStringSet(REPO_PATHS_KEY, emptySet()) ?: emptySet()
        _repositories.value = paths.map { path ->
            LocalRepository(path = path)
        }.sortedBy { it.name }
        _selectedRepository.value = prefs.getString(SELECTED_REPO_PATH_KEY, null)
    }

    // Saves the selected repository path to SharedPreferences.
    fun selectRepository(path: String) {
        prefs.edit().putString(SELECTED_REPO_PATH_KEY, path).apply()
        _selectedRepository.value = path
    }

    /**
     * Adds a new repository. Before initializing, it checks if a valid restic repository
     * already exists at the given path with the provided password.
     *
     * @param path The local file system path for the repository.
     * @param password The password for the new or existing repository.
     * @param resticBinaryPath The absolute path to the restic executable.
     * @return An [AddRepositoryState] indicating the result of the operation.
     */
    suspend fun addRepository(path: String, password: String, resticBinaryPath: String): AddRepositoryState {
        return withContext(Dispatchers.IO) {
            val repoDir = File(path)
            val configFile = File(repoDir, "config")

            val currentPaths = prefs.getStringSet(REPO_PATHS_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            if (currentPaths.contains(path)) {
                return@withContext AddRepositoryState.Error("Repository already exists.")
            }
            val wasEmpty = currentPaths.isEmpty()

            // Check if it's an existing repository by trying to list keys
            if (repoDir.exists() && configFile.exists()) {
                val checkCommand = "RESTIC_PASSWORD='$password' $resticBinaryPath -r '$path' list keys --no-lock"
                val checkResult = Shell.cmd(checkCommand).exec()
                if (checkResult.isSuccess) {
                    // Valid existing repo, add it
                    currentPaths.add(path)
                    prefs.edit().putStringSet(REPO_PATHS_KEY, currentPaths).apply()
                    loadRepositories()
                    if (wasEmpty) selectRepository(path)
                    return@withContext AddRepositoryState.Success
                } else {
                    val errorMsg = checkResult.err.joinToString("\n").ifEmpty { "Invalid password or corrupted repository." }
                    return@withContext AddRepositoryState.Error(errorMsg)
                }
            }

            // It's not an existing repo, so try to initialize it.
            if (!repoDir.exists()) {
                if (!repoDir.mkdirs()) {
                    return@withContext AddRepositoryState.Error("Failed to create directory at $path")
                }
            }

            val initCommand = "RESTIC_PASSWORD='$password' $resticBinaryPath -r '$path' init"
            val initResult = Shell.cmd(initCommand).exec()

            if (initResult.isSuccess) {
                currentPaths.add(path)
                prefs.edit().putStringSet(REPO_PATHS_KEY, currentPaths).apply()
                loadRepositories()
                if (wasEmpty) selectRepository(path)
                return@withContext AddRepositoryState.Success
            } else {
                repoDir.deleteRecursively() // Clean up failed init
                val errorMsg = initResult.err.joinToString("\n").ifEmpty { "Failed to initialize repository." }
                return@withContext AddRepositoryState.Error(errorMsg)
            }
        }
    }
}
