package io.github.hddq.restoid.data

import android.content.Context
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Low-level execution engine for Restic commands.
 * Handles:
 * - Creating temporary password files
 * - Constructing the shell command with the binary path
 * - Executing via libsu
 * - Returning Result<T>
 */
class ResticExecutor(
    private val context: Context,
    private val binaryManager: ResticBinaryManager
) {

    /**
     * Executes a restic command.
     * @param repoPath Path to the repository.
     * @param password Repository password.
     * @param command The restic command arguments (e.g. "snapshots --json").
     * @param failureMessage Default message if the command fails with empty stderr.
     * @param env Optional environment variables to prepend.
     * @param stdoutCallback Optional callback for streaming stdout (e.g., for progress).
     */
    suspend fun execute(
        repoPath: String,
        password: String,
        command: String,
        failureMessage: String = "Restic command failed",
        env: String = "",
        stdoutCallback: CallbackList<String>? = null
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            val state = binaryManager.resticState.value
            if (state !is ResticState.Installed) {
                return@withContext Result.failure(Exception("Restic is not installed or ready."))
            }

            val resticPath = state.path
            // Create a secure temp file for the password
            val passwordFile = File.createTempFile("restic-pass", ".tmp", context.cacheDir)

            try {
                passwordFile.writeText(password)

                // Construct the full command
                // We use RESTIC_PASSWORD_FILE env var to avoid leaking password in process list
                val fullCommand = buildString {
                    if (env.isNotEmpty()) append("$env ")
                    append("RESTIC_PASSWORD_FILE='${passwordFile.absolutePath}' ")
                    append("$resticPath -r '$repoPath' $command")
                }

                val shellTask = Shell.cmd(fullCommand)

                // Attach callback if provided
                val stderr = mutableListOf<String>()
                if (stdoutCallback != null) {
                    shellTask.to(stdoutCallback, stderr)
                } else {
                    // Otherwise just capture stderr normally
                    shellTask.to(ArrayList(), stderr)
                }

                val result = shellTask.exec()

                if (result.isSuccess) {
                    Result.success(result.out.joinToString("\n"))
                } else {
                    val errorOutput = stderr.joinToString("\n")
                    val errorMsg = if (errorOutput.isEmpty()) failureMessage else errorOutput
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                // Always clean up the sensitive password file
                passwordFile.delete()
            }
        }
    }
}