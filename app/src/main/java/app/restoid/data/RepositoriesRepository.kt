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

    private val _repositories = MutableStateFlow<List<LocalRepository>>(emptyList())
    val repositories = _repositories.asStateFlow()

    // Loads the repository paths from SharedPreferences and updates the state flow.
    fun loadRepositories() {
        val paths = prefs.getStringSet(REPO_PATHS_KEY, emptySet()) ?: emptySet()
        _repositories.value = paths.map { path ->
            LocalRepository(path = path)
        }.sortedBy { it.name }
    }

    /**
     * Adds a new repository. If the directory doesn't exist or isn't a restic repository,
     * it attempts to initialize it using 'restic init'.
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

            // Check if it already looks like a restic repository.
            if (repoDir.exists() && repoDir.isDirectory && configFile.exists()) {
                // If it exists, we just add it without trying to init.
                // A more robust check might involve running a command like 'restic check'.
            } else {
                // Directory doesn't exist or doesn't look like a repo, so initialize it.
                if (!repoDir.exists() && !repoDir.mkdirs()) {
                    return@withContext AddRepositoryState.Error("Failed to create directory at $path")
                }

                // Execute 'restic init' with the provided password and path.
                val command = "RESTIC_PASSWORD='$password' $resticBinaryPath -r '$path' init"
                val result = Shell.cmd(command).exec()

                if (!result.isSuccess) {
                    val errorMsg = result.err.joinToString("\n").ifEmpty { "Failed to initialize repository." }
                    return@withContext AddRepositoryState.Error(errorMsg)
                }
            }

            // If initialization was successful or skipped, save the path.
            val currentPaths = prefs.getStringSet(REPO_PATHS_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            currentPaths.add(path)
            prefs.edit().putStringSet(REPO_PATHS_KEY, currentPaths).apply()

            // Reload the list to reflect the changes.
            loadRepositories()
            AddRepositoryState.Success
        }
    }
}
