package io.github.hddq.restoid.data

import android.content.Context
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import io.github.hddq.restoid.R
import io.github.hddq.restoid.util.buildResticOptionFlags
import io.github.hddq.restoid.util.buildShellEnvironmentPrefix
import io.github.hddq.restoid.util.isValidEnvironmentVariableName
import io.github.hddq.restoid.util.isValidResticOptionName
import io.github.hddq.restoid.util.shellQuote
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

    fun binaryPath(): String? = binaryManager.getBinaryPath()

    /**
     * Executes a restic command.
     * @param repoPath Path to the repository.
     * @param password Repository password.
     * @param command The restic command arguments (e.g. "snapshots --json").
     * @param failureMessage Default message if the command fails with empty stderr.
     * @param environmentVariables Optional environment variables to prepend.
     * @param resticOptions Optional restic backend options passed via -o.
     * @param stdoutCallback Optional callback for streaming stdout (e.g., for progress).
     */
    suspend fun execute(
        repoPath: String,
        password: String,
        command: String,
        failureMessage: String = context.getString(R.string.restic_executor_failure_command),
        environmentVariables: Map<String, String> = emptyMap(),
        resticOptions: Map<String, String> = emptyMap(),
        stdoutCallback: CallbackList<String>? = null
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            val state = binaryManager.resticState.value
            if (state !is ResticState.Installed) {
                return@withContext Result.failure(Exception(context.getString(R.string.restic_executor_failure_not_ready)))
            }

            if (environmentVariables.keys.any { !isValidEnvironmentVariableName(it) }) {
                return@withContext Result.failure(Exception(context.getString(R.string.restic_executor_failure_invalid_env_name)))
            }

            if (resticOptions.keys.any { !isValidResticOptionName(it) }) {
                return@withContext Result.failure(Exception(context.getString(R.string.restic_executor_failure_invalid_option_name)))
            }

            val resticPath = state.path
            val passwordFile = File.createTempFile("restic-pass", ".tmp", context.cacheDir)

            try {
                passwordFile.writeText(password)

                val envPrefix = buildShellEnvironmentPrefix(environmentVariables)
                val optionFlags = buildResticOptionFlags(resticOptions)

                // Construct the full command
                // We use RESTIC_PASSWORD_FILE env var to avoid leaking password in process list
                val fullCommand = buildString {
                    if (envPrefix.isNotEmpty()) append(envPrefix).append(' ')
                    append("RESTIC_PASSWORD_FILE=").append(shellQuote(passwordFile.absolutePath)).append(' ')
                    append(shellQuote(resticPath)).append(' ')
                    append("--retry-lock 5s ")
                    if (optionFlags.isNotEmpty()) append(optionFlags).append(' ')
                    append("-r ").append(shellQuote(repoPath)).append(' ')
                    append(command)
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
