package io.github.hddq.restoid.data

import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import io.github.hddq.restoid.R
import io.github.hddq.restoid.util.StorageUtils
import io.github.hddq.restoid.util.buildResticOptionFlags
import io.github.hddq.restoid.util.buildShellEnvironmentPrefix
import io.github.hddq.restoid.util.isValidResticOptionName
import io.github.hddq.restoid.util.shellQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.Base64

// Represents the state of the "Add Repository" operation
sealed class AddRepositoryState {
    object Idle : AddRepositoryState()
    object Initializing : AddRepositoryState()
    object Success : AddRepositoryState()
    data class Error(val message: String) : AddRepositoryState()
}

data class SftpServerTrustInfo(
    val endpoint: String,
    val fingerprints: List<String>,
    val knownHostEntries: List<String>
)

class RepositoriesRepository(
    private val context: Context,
    private val passwordManager: PasswordManager,
    private val binaryManager: ResticBinaryManager
) {
    private val prefs = context.getSharedPreferences("repositories", Context.MODE_PRIVATE)
    private val LEGACY_REPOS_KEY = "repositories_json_v1"
    private val REPOS_KEY = "repositories_json_v2"
    private val LEGACY_SELECTED_REPO_PATH_KEY = "selected_repo_path"
    private val SELECTED_REPO_KEY = "selected_repo_key"

    private val _repositories = MutableStateFlow<List<LocalRepository>>(emptyList())
    val repositories = _repositories.asStateFlow()

    private val _selectedRepository = MutableStateFlow<String?>(null)
    val selectedRepository = _selectedRepository.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    fun loadRepositories() {
        val reposV2 = prefs.getStringSet(REPOS_KEY, null)
        val reposV1 = prefs.getStringSet(LEGACY_REPOS_KEY, emptySet()) ?: emptySet()

        val repoJsonSet = mutableSetOf<String>().apply {
            if (reposV2 != null) addAll(reposV2)
            addAll(reposV1)
        }

        _repositories.value = repoJsonSet.mapNotNull { jsonString ->
            try {
                json.decodeFromString<LocalRepository>(jsonString)
            } catch (_: Exception) {
                null
            }
        }.sortedBy { it.name }

        if (reposV2 == null && repoJsonSet.isNotEmpty()) {
            prefs.edit().putStringSet(REPOS_KEY, repoJsonSet).apply()
        }

        val selectedKey = prefs.getString(SELECTED_REPO_KEY, null)
            ?: prefs.getString(LEGACY_SELECTED_REPO_PATH_KEY, null)

        if (selectedKey != null && _repositories.value.any { repositoryKey(it) == selectedKey }) {
            _selectedRepository.value = selectedKey
            prefs.edit().putString(SELECTED_REPO_KEY, selectedKey).remove(LEGACY_SELECTED_REPO_PATH_KEY).apply()
        } else if (selectedKey != null) {
            val legacySelectedRepository = _repositories.value.find { it.path == selectedKey }
            if (legacySelectedRepository != null) {
                val migratedKey = repositoryKey(legacySelectedRepository)
                _selectedRepository.value = migratedKey
                prefs.edit().putString(SELECTED_REPO_KEY, migratedKey).remove(LEGACY_SELECTED_REPO_PATH_KEY).apply()
            } else {
                _selectedRepository.value = null
                prefs.edit().remove(SELECTED_REPO_KEY).remove(LEGACY_SELECTED_REPO_PATH_KEY).apply()
            }
        } else {
            _selectedRepository.value = null
            prefs.edit().remove(SELECTED_REPO_KEY).apply()
        }
    }

    fun selectRepository(key: String) {
        if (_repositories.value.none { repositoryKey(it) == key }) {
            prefs.edit().remove(SELECTED_REPO_KEY).apply()
            _selectedRepository.value = null
            return
        }

        prefs.edit().putString(SELECTED_REPO_KEY, key).apply()
        _selectedRepository.value = key
    }

    fun getRepositoryPassword(key: String): String? = passwordManager.getPassword(key)
    fun hasRepositoryPassword(key: String): Boolean = passwordManager.hasPassword(key)
    fun hasStoredRepositoryPassword(key: String): Boolean = passwordManager.hasStoredPassword(key)
    fun saveRepositoryPasswordTemporary(key: String, password: String) = passwordManager.savePasswordTemporary(key, password)
    fun saveRepositoryPassword(key: String, password: String) = passwordManager.savePassword(key, password)
    fun forgetPassword(key: String) = passwordManager.removeStoredPassword(key)

    fun getSftpPassword(key: String): String? = passwordManager.getSftpPassword(key)
    fun hasSftpPassword(key: String): Boolean = passwordManager.hasSftpPassword(key)
    fun saveSftpPasswordTemporary(key: String, password: String) = passwordManager.saveSftpPasswordTemporary(key, password)
    fun saveSftpPassword(key: String, password: String) = passwordManager.saveSftpPassword(key, password)
    fun forgetSftpPassword(key: String) = passwordManager.removeStoredSftpPassword(key)

    fun getExecutionEnvironmentVariables(key: String): Map<String, String> {
        val repository = getRepositoryByKey(key) ?: return emptyMap()
        val sftpPassword = if (repository.backendType == RepositoryBackendType.SFTP) {
            getSftpPassword(key)
        } else {
            null
        }

        val environmentWithDefaults = if (repository.backendType == RepositoryBackendType.SFTP) {
            applySftpClientEnvironmentDefaults(repository.environmentVariables)
        } else {
            repository.environmentVariables
        }

        return if (sftpPassword.isNullOrBlank()) {
            environmentWithDefaults
        } else {
            environmentWithDefaults + mapOf("SSHPASS" to sftpPassword)
        }
    }

    fun getExecutionResticOptions(key: String): Map<String, String> {
        val repository = getRepositoryByKey(key) ?: return emptyMap()
        val knownHostsFile = if (repository.backendType == RepositoryBackendType.SFTP) {
            ensureSftpKnownHostsFile().getOrNull()
        } else {
            null
        }

        return applySftpResticOptionDefaults(
            backendType = repository.backendType,
            options = repository.resticOptions,
            knownHostsFile = knownHostsFile,
            strictHostKeyCheckingValue = "yes"
        )
    }

    fun getRepositoryByKey(key: String): LocalRepository? {
        return repositories.value.find { repositoryKey(it) == key }
    }

    fun repositoryKey(repository: LocalRepository): String {
        return repository.path
    }

    fun deleteRepository(key: String) {
        val currentJsonSet = prefs.getStringSet(REPOS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        val repoToRemoveJson = currentJsonSet.find {
            try {
                val decoded = json.decodeFromString<LocalRepository>(it)
                repositoryKey(decoded) == key
            } catch (_: Exception) {
                false
            }
        }

        if (repoToRemoveJson != null && currentJsonSet.remove(repoToRemoveJson)) {
            prefs.edit().putStringSet(REPOS_KEY, currentJsonSet).apply()
            passwordManager.removePassword(key)
            passwordManager.removeSftpPassword(key)

            if (_selectedRepository.value == key) {
                prefs.edit().remove(SELECTED_REPO_KEY).apply()
                _selectedRepository.value = null
            }
            loadRepositories()
        }
    }

    suspend fun probeSftpServerTrust(
        path: String,
        password: String,
        environmentVariables: Map<String, String>,
        resticOptions: Map<String, String>,
        sftpPassword: String
    ): Result<SftpServerTrustInfo> {
        return withContext(Dispatchers.IO) {
            val normalizedPath = normalizeRepositoryPath(path, RepositoryBackendType.SFTP)
            val binaryState = binaryManager.resticState.value
            if (binaryState !is ResticState.Installed) {
                return@withContext Result.failure(IllegalStateException(context.getString(R.string.repo_error_binary_not_ready)))
            }

            val tempKnownHostsFile = File.createTempFile("sftp-known-hosts", ".tmp", context.cacheDir)
            val passwordFile = File.createTempFile("restic-pass", ".tmp", context.cacheDir)

            try {
                passwordFile.writeText(password)

                val persistedEnvironmentVariables = when {
                    sftpPassword.isNotBlank() -> {
                        val envResult = prepareSftpPasswordEnvironment(environmentVariables)
                        if (envResult.isFailure) {
                            return@withContext Result.failure(
                                IllegalStateException(
                                    envResult.exceptionOrNull()?.message
                                        ?: context.getString(R.string.repo_error_sftp_password_setup_failed)
                                )
                            )
                        }
                        envResult.getOrThrow()
                    }
                    else -> applySftpClientEnvironmentDefaults(environmentVariables)
                }

                val executionEnvironmentVariables = if (sftpPassword.isNotBlank()) {
                    persistedEnvironmentVariables + mapOf("SSHPASS" to sftpPassword)
                } else {
                    persistedEnvironmentVariables
                }

                val probeResticOptions = applySftpResticOptionDefaults(
                    backendType = RepositoryBackendType.SFTP,
                    options = resticOptions,
                    knownHostsFile = tempKnownHostsFile,
                    strictHostKeyCheckingValue = "accept-new"
                )

                if (probeResticOptions.keys.any { !isValidResticOptionName(it) }) {
                    return@withContext Result.failure(IllegalStateException(context.getString(R.string.settings_error_invalid_restic_options)))
                }

                val envPrefix = buildShellEnvironmentPrefix(executionEnvironmentVariables)
                val resticOptionFlags = buildResticOptionFlags(probeResticOptions)

                val probeResult = Shell.cmd(buildString {
                    if (envPrefix.isNotEmpty()) append(envPrefix).append(' ')
                    append("RESTIC_PASSWORD_FILE=").append(shellQuote(passwordFile.absolutePath)).append(' ')
                    append(shellQuote(binaryState.path)).append(' ')
                    if (resticOptionFlags.isNotEmpty()) append(resticOptionFlags).append(' ')
                    append("-r ").append(shellQuote(normalizedPath)).append(" snapshots --last 1 --json")
                }).exec()

                val knownHostEntries = readKnownHostEntries(tempKnownHostsFile)
                if (knownHostEntries.isEmpty()) {
                    val stderr = probeResult.err.joinToString("\n").trim()
                    val message = if (stderr.isBlank()) {
                        context.getString(R.string.repo_error_sftp_fingerprint_probe_failed)
                    } else {
                        stderr
                    }
                    return@withContext Result.failure(IllegalStateException(message))
                }

                val fingerprints = knownHostEntries
                    .mapNotNull(::parseFingerprintFromKnownHostEntry)
                    .distinct()

                if (fingerprints.isEmpty()) {
                    return@withContext Result.failure(IllegalStateException(context.getString(R.string.repo_error_sftp_fingerprint_probe_failed)))
                }

                Result.success(
                    SftpServerTrustInfo(
                        endpoint = buildSftpEndpointLabel(normalizedPath),
                        fingerprints = fingerprints,
                        knownHostEntries = knownHostEntries
                    )
                )
            } finally {
                passwordFile.delete()
                tempKnownHostsFile.delete()
            }
        }
    }

    fun trustSftpServer(knownHostEntries: List<String>): Result<Unit> {
        val normalizedEntries = knownHostEntries
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .distinct()

        if (normalizedEntries.isEmpty()) {
            return Result.failure(IllegalStateException(context.getString(R.string.repo_error_sftp_fingerprint_probe_failed)))
        }

        val knownHostsResult = ensureSftpKnownHostsFile()
        if (knownHostsResult.isFailure) {
            return Result.failure(
                IllegalStateException(
                    knownHostsResult.exceptionOrNull()?.message
                        ?: context.getString(R.string.repo_error_sftp_known_hosts_setup_failed)
                )
            )
        }

        val knownHostsFile = knownHostsResult.getOrThrow()
        return runCatching {
            val existingEntries = readKnownHostEntries(knownHostsFile).toMutableSet()
            val newEntries = normalizedEntries.filter { !existingEntries.contains(it) }

            if (newEntries.isNotEmpty()) {
                val separator = if (knownHostsFile.length() > 0L) "\n" else ""
                knownHostsFile.appendText(separator + newEntries.joinToString("\n") + "\n")
            }
        }
    }

    suspend fun addRepository(
        path: String,
        backendType: RepositoryBackendType,
        password: String,
        environmentVariables: Map<String, String>,
        resticOptions: Map<String, String>,
        sftpPassword: String,
        resticRepository: ResticRepository,
        savePassword: Boolean
    ): AddRepositoryState {
        return withContext(Dispatchers.IO) {
            val normalizedPath = normalizeRepositoryPath(path, backendType)
            val resolvedPath = if (backendType == RepositoryBackendType.LOCAL) {
                StorageUtils.resolvePathForShell(normalizedPath)
            } else {
                normalizedPath
            }

            val knownHostsFile = if (backendType == RepositoryBackendType.SFTP) {
                val knownHostsResult = ensureSftpKnownHostsFile()
                if (knownHostsResult.isFailure) {
                    return@withContext AddRepositoryState.Error(
                        knownHostsResult.exceptionOrNull()?.message
                            ?: context.getString(R.string.repo_error_sftp_known_hosts_setup_failed)
                    )
                }
                knownHostsResult.getOrThrow()
            } else {
                null
            }

            val persistedResticOptions = applySftpResticOptionDefaults(
                backendType = backendType,
                options = resticOptions,
                knownHostsFile = knownHostsFile,
                strictHostKeyCheckingValue = "yes"
            )

            val persistedEnvironmentVariables = when {
                backendType == RepositoryBackendType.SFTP && sftpPassword.isNotBlank() -> {
                    val envResult = prepareSftpPasswordEnvironment(environmentVariables)
                    if (envResult.isFailure) {
                        return@withContext AddRepositoryState.Error(
                            envResult.exceptionOrNull()?.message
                                ?: context.getString(R.string.repo_error_sftp_password_setup_failed)
                        )
                    }
                    envResult.getOrThrow()
                }
                backendType == RepositoryBackendType.SFTP -> applySftpClientEnvironmentDefaults(environmentVariables)
                else -> environmentVariables
            }

            val executionEnvironmentVariables = if (backendType == RepositoryBackendType.SFTP && sftpPassword.isNotBlank()) {
                persistedEnvironmentVariables + mapOf("SSHPASS" to sftpPassword)
            } else {
                persistedEnvironmentVariables
            }

            val draftRepository = LocalRepository(
                path = resolvedPath,
                backendType = backendType,
                environmentVariables = persistedEnvironmentVariables,
                resticOptions = persistedResticOptions
            )
            val newRepositoryKey = repositoryKey(draftRepository)

            if (persistedResticOptions.keys.any { !isValidResticOptionName(it) }) {
                return@withContext AddRepositoryState.Error(context.getString(R.string.settings_error_invalid_restic_options))
            }

            if (repositories.value.any { repositoryKey(it) == newRepositoryKey }) {
                return@withContext AddRepositoryState.Error(context.getString(R.string.repo_error_exists))
            }

            val binaryState = binaryManager.resticState.value
            if (binaryState !is ResticState.Installed) {
                return@withContext AddRepositoryState.Error(context.getString(R.string.repo_error_binary_not_ready))
            }

            val resticPath = binaryState.path
            val wasEmpty = repositories.value.isEmpty()
            val passwordFile = File.createTempFile("restic-pass", ".tmp", context.cacheDir)
            val envPrefix = buildShellEnvironmentPrefix(executionEnvironmentVariables)
            val resticOptionFlags = buildResticOptionFlags(persistedResticOptions)

            try {
                passwordFile.writeText(password)

                if (backendType == RepositoryBackendType.LOCAL) {
                    val repoExists = Shell.cmd("[ -f ${shellQuote(resolvedPath)}/config ]").exec().isSuccess
                    val directoryExists = Shell.cmd("[ -d ${shellQuote(resolvedPath)} ]").exec().isSuccess

                    if (repoExists) {
                        val checkResult = Shell.cmd(buildString {
                            if (envPrefix.isNotEmpty()) append(envPrefix).append(' ')
                            append("RESTIC_PASSWORD_FILE=").append(shellQuote(passwordFile.absolutePath)).append(' ')
                            append(shellQuote(resticPath)).append(' ')
                            if (resticOptionFlags.isNotEmpty()) append(resticOptionFlags).append(' ')
                            append("-r ").append(shellQuote(resolvedPath)).append(" list keys --no-lock")
                        }).exec()
                        if (checkResult.isSuccess) {
                            handleSuccessfulRepoAdd(
                                draftRepository,
                                password,
                                resticRepository,
                                executionEnvironmentVariables,
                                savePassword,
                                wasEmpty,
                                sftpPassword
                            )
                        } else {
                            AddRepositoryState.Error(context.getString(R.string.repo_error_invalid_password_or_corrupted))
                        }
                    } else {
                        if (directoryExists) {
                            val directoryHasEntries = Shell.cmd("find ${shellQuote(resolvedPath)} -mindepth 1 -print -quit | grep -q .").exec().isSuccess
                            if (directoryHasEntries) {
                                return@withContext AddRepositoryState.Error(context.getString(R.string.repo_error_directory_not_repository))
                            }
                        } else {
                            val mkdirResult = Shell.cmd("mkdir -p ${shellQuote(resolvedPath)}").exec()
                            if (!mkdirResult.isSuccess) {
                                return@withContext AddRepositoryState.Error(context.getString(R.string.repo_error_failed_create_directory))
                            }
                        }

                        val initResult = Shell.cmd(buildString {
                            if (envPrefix.isNotEmpty()) append(envPrefix).append(' ')
                            append("RESTIC_PASSWORD_FILE=").append(shellQuote(passwordFile.absolutePath)).append(' ')
                            append(shellQuote(resticPath)).append(' ')
                            if (resticOptionFlags.isNotEmpty()) append(resticOptionFlags).append(' ')
                            append("-r ").append(shellQuote(resolvedPath)).append(" init")
                        }).exec()
                        if (initResult.isSuccess) {
                            handleSuccessfulRepoAdd(
                                draftRepository,
                                password,
                                resticRepository,
                                executionEnvironmentVariables,
                                savePassword,
                                wasEmpty,
                                sftpPassword
                            )
                        } else {
                            if (!directoryExists) {
                                Shell.cmd("rm -rf ${shellQuote(resolvedPath)}").exec()
                            }
                            AddRepositoryState.Error(context.getString(R.string.repo_error_failed_initialize))
                        }
                    }
                } else {
                    val checkResult = Shell.cmd(buildString {
                        if (envPrefix.isNotEmpty()) append(envPrefix).append(' ')
                        append("RESTIC_PASSWORD_FILE=").append(shellQuote(passwordFile.absolutePath)).append(' ')
                        append(shellQuote(resticPath)).append(' ')
                        if (resticOptionFlags.isNotEmpty()) append(resticOptionFlags).append(' ')
                        append("-r ").append(shellQuote(resolvedPath)).append(" list keys --no-lock")
                    }).exec()

                    if (checkResult.isSuccess) {
                        handleSuccessfulRepoAdd(
                            draftRepository,
                            password,
                            resticRepository,
                            executionEnvironmentVariables,
                            savePassword,
                            wasEmpty,
                            sftpPassword
                        )
                    } else {
                        val initResult = Shell.cmd(buildString {
                            if (envPrefix.isNotEmpty()) append(envPrefix).append(' ')
                            append("RESTIC_PASSWORD_FILE=").append(shellQuote(passwordFile.absolutePath)).append(' ')
                            append(shellQuote(resticPath)).append(' ')
                            if (resticOptionFlags.isNotEmpty()) append(resticOptionFlags).append(' ')
                            append("-r ").append(shellQuote(resolvedPath)).append(" init")
                        }).exec()

                        if (initResult.isSuccess) {
                            handleSuccessfulRepoAdd(
                                draftRepository,
                                password,
                                resticRepository,
                                executionEnvironmentVariables,
                                savePassword,
                                wasEmpty,
                                sftpPassword
                            )
                        } else {
                            AddRepositoryState.Error(context.getString(R.string.repo_error_failed_initialize))
                        }
                    }
                }
            } finally {
                passwordFile.delete()
            }
        }
    }

    private suspend fun handleSuccessfulRepoAdd(
        repo: LocalRepository,
        password: String,
        resticRepository: ResticRepository,
        executionEnvironmentVariables: Map<String, String>,
        savePassword: Boolean,
        wasEmpty: Boolean,
        sftpPassword: String
    ): AddRepositoryState {
        val configResult = resticRepository.getConfig(
            repo.path,
            password,
            executionEnvironmentVariables,
            repo.resticOptions
        )
        if (configResult.isSuccess) {
            val repoId = configResult.getOrNull()?.id
            if (repoId != null) {
                restoreMetadataForRepo(
                    repoId,
                    repo.path,
                    password,
                    resticRepository,
                    executionEnvironmentVariables,
                    repo.resticOptions
                )
            }
            resticRepository.clearSnapshots()
            saveNewRepository(repo.copy(id = repoId), password, sftpPassword, savePassword, wasEmpty)
            return AddRepositoryState.Success
        }
        return AddRepositoryState.Error(context.getString(R.string.repo_error_failed_get_id))
    }

    private suspend fun restoreMetadataForRepo(
        repoId: String,
        repoPath: String,
        password: String,
        resticRepository: ResticRepository,
        environmentVariables: Map<String, String>,
        resticOptions: Map<String, String>
    ) {
        val tempRestoreDir = File(context.cacheDir, "metadata_restore_${System.currentTimeMillis()}").also { it.mkdirs() }
        try {
            val snapshots = resticRepository.getSnapshots(repoPath, password, environmentVariables, resticOptions).getOrNull()
            val metadataSnapshot = snapshots
                ?.filter { it.tags.contains("restoid") && it.tags.contains("metadata") }
                ?.maxByOrNull { it.time }

            if (metadataSnapshot != null) {
                val restoreResult = resticRepository.restore(
                    repoPath = repoPath,
                    password = password,
                    snapshotId = metadataSnapshot.id,
                    targetPath = tempRestoreDir.absolutePath,
                    pathsToRestore = emptyList(),
                    environmentVariables = environmentVariables,
                    resticOptions = resticOptions
                )

                if (restoreResult.isSuccess) {
                    val finalMetadataParentDir = File(context.filesDir, "metadata")
                    if (!finalMetadataParentDir.exists()) finalMetadataParentDir.mkdirs()

                    val ownerResult = Shell.cmd("stat -c '%u:%g' ${context.filesDir.absolutePath}").exec()
                    val owner = if (ownerResult.isSuccess) ownerResult.out.firstOrNull()?.trim() else null

                    if (owner != null) {
                        Shell.cmd("chown -R $owner ${shellQuote(tempRestoreDir.absolutePath)}").exec()
                        Shell.cmd("cp -a ${shellQuote(tempRestoreDir.absolutePath + "/.")} ${shellQuote(finalMetadataParentDir.absolutePath + "/")}").exec()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RepoRepo", "Error during metadata restore", e)
        } finally {
            Shell.cmd("rm -rf ${shellQuote(tempRestoreDir.absolutePath)}").exec()
        }
    }

    private fun saveNewRepository(
        repo: LocalRepository,
        password: String,
        sftpPassword: String,
        save: Boolean,
        wasEmpty: Boolean
    ) {
        saveRepository(repo)
        val key = repositoryKey(repo)
        if (save) passwordManager.savePassword(key, password)
        else passwordManager.savePasswordTemporary(key, password)

        if (repo.backendType == RepositoryBackendType.SFTP && sftpPassword.isNotBlank()) {
            if (save) passwordManager.saveSftpPassword(key, sftpPassword)
            else passwordManager.saveSftpPasswordTemporary(key, sftpPassword)
        }

        loadRepositories()
        if (wasEmpty) selectRepository(key)
    }

    private fun saveRepository(repository: LocalRepository) {
        val currentJsonSet = prefs.getStringSet(REPOS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        val repoJson = json.encodeToString(repository)
        currentJsonSet.add(repoJson)
        prefs.edit().putStringSet(REPOS_KEY, currentJsonSet).apply()
    }

    private fun normalizeRepositoryPath(path: String, backendType: RepositoryBackendType): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return trimmed

        return when (backendType) {
            RepositoryBackendType.LOCAL -> trimmed
            RepositoryBackendType.SFTP -> if (trimmed.startsWith("sftp:")) trimmed else "sftp:$trimmed"
            RepositoryBackendType.REST -> if (trimmed.startsWith("rest:")) trimmed else "rest:$trimmed"
            RepositoryBackendType.S3 -> if (trimmed.startsWith("s3:")) trimmed else "s3:$trimmed"
            RepositoryBackendType.SWIFT -> if (trimmed.startsWith("swift:")) trimmed else "swift:$trimmed"
            RepositoryBackendType.B2 -> if (trimmed.startsWith("b2:")) trimmed else "b2:$trimmed"
            RepositoryBackendType.AZURE -> if (trimmed.startsWith("azure:")) trimmed else "azure:$trimmed"
            RepositoryBackendType.GOOGLE_CLOUD_STORAGE -> if (trimmed.startsWith("gs:")) trimmed else "gs:$trimmed"
        }
    }

    private fun prepareSftpPasswordEnvironment(baseEnvironmentVariables: Map<String, String>): Result<Map<String, String>> {
        val sshPathResult = Shell.cmd("command -v ssh").exec()
        val sshPath = sshPathResult.out.firstOrNull()?.trim().orEmpty()
        if (!sshPathResult.isSuccess || sshPath.isBlank()) {
            return Result.failure(IllegalStateException(context.getString(R.string.repo_error_sftp_ssh_binary_not_found)))
        }

        val askpassDir = File(context.filesDir, "sftp-askpass")
        if (!askpassDir.exists() && !askpassDir.mkdirs()) {
            return Result.failure(IllegalStateException(context.getString(R.string.repo_error_sftp_askpass_create_failed)))
        }

        val askpassScript = File(askpassDir, "ssh-askpass.sh")
        runCatching {
            askpassScript.writeText(
                "#!/system/bin/sh\n" +
                    "if [ -n \"\$SSHPASS\" ]; then\n" +
                    "  printf '%s\\n' \"\$SSHPASS\"\n" +
                    "  exit 0\n" +
                    "fi\n" +
                    "exit 1\n"
            )
            askpassScript.setExecutable(true, true)
            askpassDir.setExecutable(true, true)
        }.onFailure {
            return Result.failure(IllegalStateException(context.getString(R.string.repo_error_sftp_askpass_create_failed)))
        }

        val environmentWithDefaults = applySftpClientEnvironmentDefaults(baseEnvironmentVariables)
        val displayValue = environmentWithDefaults["DISPLAY"] ?: "restoid:0"

        return Result.success(
            environmentWithDefaults + mapOf(
                "SSH_ASKPASS" to askpassScript.absolutePath,
                "SSH_ASKPASS_REQUIRE" to "force",
                "DISPLAY" to displayValue
            )
        )
    }

    private fun applySftpClientEnvironmentDefaults(baseEnvironmentVariables: Map<String, String>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        result.putAll(baseEnvironmentVariables)

        if (result["HOME"].isNullOrBlank()) {
            result["HOME"] = context.filesDir.absolutePath
        }
        if (result["TMPDIR"].isNullOrBlank()) {
            result["TMPDIR"] = context.cacheDir.absolutePath
        }

        return result
    }

    private fun ensureSftpKnownHostsFile(): Result<File> {
        val sshDir = File(context.filesDir, "ssh")
        val knownHostsFile = File(sshDir, "known_hosts")

        return runCatching {
            if (!sshDir.exists() && !sshDir.mkdirs()) {
                throw IllegalStateException(context.getString(R.string.repo_error_sftp_known_hosts_setup_failed))
            }
            if (!knownHostsFile.exists() && !knownHostsFile.createNewFile()) {
                throw IllegalStateException(context.getString(R.string.repo_error_sftp_known_hosts_setup_failed))
            }

            sshDir.setExecutable(true, true)
            sshDir.setReadable(true, true)
            sshDir.setWritable(true, true)
            knownHostsFile.setReadable(true, true)
            knownHostsFile.setWritable(true, true)

            knownHostsFile
        }
    }

    private fun buildDefaultSftpSshArgs(
        knownHostsFile: File,
        strictHostKeyCheckingValue: String
    ): String {
        return "-o BatchMode=no " +
            "-o StrictHostKeyChecking=$strictHostKeyCheckingValue " +
            "-o UserKnownHostsFile=${knownHostsFile.absolutePath} " +
            "-o GlobalKnownHostsFile=/dev/null " +
            "-o ConnectTimeout=15 " +
            "-o PreferredAuthentications=password,keyboard-interactive " +
            "-o NumberOfPasswordPrompts=1"
    }

    private fun readKnownHostEntries(file: File): List<String> {
        if (!file.exists()) return emptyList()

        return runCatching {
            file.readLines()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
        }.getOrDefault(emptyList())
    }

    private fun parseFingerprintFromKnownHostEntry(entry: String): String? {
        val parts = entry.split(Regex("\\s+"))
        if (parts.size < 3) return null

        val keyTypeIndex = if (parts.firstOrNull()?.startsWith("@") == true) 2 else 1
        val keyIndex = keyTypeIndex + 1
        if (parts.size <= keyIndex) return null

        val algorithm = parts[keyTypeIndex]
        val keyBase64 = parts[keyIndex]
        val decoded = runCatching { Base64.getDecoder().decode(keyBase64) }.getOrNull() ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(decoded)
        val fingerprint = Base64.getEncoder().withoutPadding().encodeToString(digest)

        return "$algorithm SHA256:$fingerprint"
    }

    private fun buildSftpEndpointLabel(path: String): String {
        val withoutScheme = path.removePrefix("sftp:")
        val authority = withoutScheme.substringBefore(":/")
        val hostPort = authority.substringAfterLast('@', authority)

        if (hostPort.startsWith("[")) {
            val closingBracket = hostPort.indexOf(']')
            if (closingBracket > 0) {
                return hostPort.substring(0, closingBracket + 1)
            }
        }

        return hostPort.substringBefore(':')
    }

    private fun applySftpResticOptionDefaults(
        backendType: RepositoryBackendType,
        options: Map<String, String>,
        knownHostsFile: File?,
        strictHostKeyCheckingValue: String
    ): Map<String, String> {
        if (backendType != RepositoryBackendType.SFTP) {
            return options
        }

        if (!options["sftp.command"].isNullOrBlank()) {
            return options
        }

        if (!options["sftp.args"].isNullOrBlank()) {
            return options
        }

        if (knownHostsFile == null) {
            return options
        }

        return options + mapOf(
            "sftp.args" to buildDefaultSftpSshArgs(
                knownHostsFile = knownHostsFile,
                strictHostKeyCheckingValue = strictHostKeyCheckingValue
            )
        )
    }
}
