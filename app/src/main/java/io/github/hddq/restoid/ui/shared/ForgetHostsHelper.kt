package io.github.hddq.restoid.ui.shared

import android.content.Context
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.PreferencesRepository
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.RepositoryBackendType
import io.github.hddq.restoid.data.ResticBinaryManager
import io.github.hddq.restoid.data.ResticRepository
import io.github.hddq.restoid.data.SnapshotInfo
import io.github.hddq.restoid.model.MaintenanceConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

data class ForgetHostOption(
    val hostname: String,
    val isCurrentDevice: Boolean = false,
    val snapshotCount: Int? = null,
    val isRemovable: Boolean = false
)

data class ForgetHostsSlice(
    val maintenance: MaintenanceConfig = MaintenanceConfig(),
    val availableForgetHosts: List<ForgetHostOption> = emptyList(),
    val areRepoSnapshotsLoaded: Boolean = false,
    val isLoadingForgetHosts: Boolean = false,
    val forgetHostsError: String? = null
)

fun normalizeCustomForgetHostname(hostname: String): String {
    return hostname
        .replace("\\p{Cntrl}".toRegex(), "")
        .trim()
}

fun normalizeForgetHosts(hosts: List<String>): List<String> {
    return hosts
        .map { normalizeCustomForgetHostname(it) }
        .filter { it.isNotEmpty() }
        .distinct()
}

fun toggleForgetHostSelection(
    selectedHosts: List<String>,
    hostname: String
): List<String> {
    val cleanHost = normalizeCustomForgetHostname(hostname)
    if (cleanHost.isEmpty()) return normalizeForgetHosts(selectedHosts)

    val normalized = normalizeForgetHosts(selectedHosts)
    return if (cleanHost in normalized) {
        normalized.filterNot { it == cleanHost }
    } else {
        normalized + cleanHost
    }
}

fun toggleAllForgetHostsSelection(
    availableHostnames: List<String>,
    selectedHosts: List<String>,
    forgetAllHosts: Boolean
): List<String> {
    val available = normalizeForgetHosts(availableHostnames)
    val selected = normalizeForgetHosts(selectedHosts)
    val isAllSelected = forgetAllHosts || (available.isNotEmpty() && available.all { it in selected })
    return if (isAllSelected) emptyList() else available
}

fun buildAvailableForgetHosts(
    currentDeviceHostname: String,
    snapshots: List<SnapshotInfo>?,
    selectedHosts: List<String>,
    manualHosts: List<String> = emptyList()
): List<ForgetHostOption> {
    val cleanCurrentDevice = normalizeCustomForgetHostname(currentDeviceHostname)
    val backupSnapshots = snapshots?.filter {
        it.tags.contains("restoid") && it.tags.contains("backup")
    }
    val snapshotCountsByHost = backupSnapshots?.let { list ->
        list.mapNotNull { it.hostname?.let(::normalizeCustomForgetHostname)?.takeIf { h -> h.isNotEmpty() } }
            .groupingBy { it }
            .eachCount()
    }

    val result = mutableListOf<ForgetHostOption>()
    val seen = mutableSetOf<String>()

    if (cleanCurrentDevice.isNotEmpty()) {
        seen.add(cleanCurrentDevice)
        result.add(
            ForgetHostOption(
                hostname = cleanCurrentDevice,
                isCurrentDevice = true,
                snapshotCount = snapshotCountsByHost?.let { it[cleanCurrentDevice] ?: 0 },
                isRemovable = false
            )
        )
    }

    val repoHosts = snapshotCountsByHost?.keys
        ?.filter { it !in seen }
        ?.sortedBy { it.lowercase(Locale.ROOT) }
        .orEmpty()

    for (host in repoHosts) {
        seen.add(host)
        result.add(
            ForgetHostOption(
                hostname = host,
                isCurrentDevice = false,
                snapshotCount = snapshotCountsByHost?.get(host),
                isRemovable = false
            )
        )
    }

    val customHosts = (normalizeForgetHosts(selectedHosts) + normalizeForgetHosts(manualHosts))
        .distinct()
        .filter { it !in seen }
        .sortedBy { it.lowercase(Locale.ROOT) }

    for (host in customHosts) {
        seen.add(host)
        result.add(
            ForgetHostOption(
                hostname = host,
                isCurrentDevice = false,
                snapshotCount = if (snapshotCountsByHost != null) 0 else null,
                isRemovable = true
            )
        )
    }

    return result
}

class ForgetHostsController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticBinaryManager: ResticBinaryManager,
    private val resticRepository: ResticRepository,
    private val preferencesRepository: PreferencesRepository,
    initialManualHosts: List<String> = emptyList(),
    private val readSlice: () -> ForgetHostsSlice,
    private val updateSlice: ((ForgetHostsSlice) -> ForgetHostsSlice) -> Unit
) {
    private var manualForgetHosts: List<String> = normalizeForgetHosts(initialManualHosts)
    private var latestRepoSnapshots: List<SnapshotInfo>? = currentRepoSnapshots()

    init {
        refreshAvailableHosts()
        scope.launch {
            combine(
                repositoriesRepository.selectedRepository,
                resticRepository.snapshotsByRepoPath,
                resticRepository.snapshots,
                resticRepository.snapshotsRepoPath
            ) { repoKey, _, _, _ ->
                val repoPath = repoKey?.let { repositoriesRepository.getRepositoryByKey(it)?.path }
                resticRepository.getSnapshotsForRepo(repoPath)
            }.collect { snapshots ->
                latestRepoSnapshots = snapshots
                refreshAvailableHosts()
            }
        }
    }

    private fun currentRepoSnapshots(): List<SnapshotInfo>? {
        val repoKey = repositoriesRepository.selectedRepository.value
        val repoPath = repoKey?.let { repositoriesRepository.getRepositoryByKey(it)?.path }
        return resticRepository.getSnapshotsForRepo(repoPath)
    }

    fun setManualHosts(hosts: List<String>) {
        manualForgetHosts = normalizeForgetHosts(hosts)
        latestRepoSnapshots = currentRepoSnapshots()
        refreshAvailableHosts()
    }

    fun refreshAvailableHosts() {
        val currentHostname = preferencesRepository.getDeviceHostname()
        val snapshots = latestRepoSnapshots
        updateSlice { slice ->
            slice.copy(
                availableForgetHosts = buildAvailableForgetHosts(
                    currentDeviceHostname = currentHostname,
                    snapshots = snapshots,
                    selectedHosts = slice.maintenance.forgetHosts,
                    manualHosts = manualForgetHosts
                ),
                areRepoSnapshotsLoaded = snapshots != null
            )
        }
    }

    fun toggleAll() {
        updateSlice { slice ->
            val updatedHosts = toggleAllForgetHostsSelection(
                availableHostnames = slice.availableForgetHosts.map { it.hostname },
                selectedHosts = slice.maintenance.forgetHosts,
                forgetAllHosts = slice.maintenance.forgetAllHosts
            )
            slice.copy(
                maintenance = slice.maintenance.copy(
                    forgetAllHosts = false,
                    forgetHosts = updatedHosts
                )
            )
        }
        refreshAvailableHosts()
    }

    fun toggleHost(hostname: String) {
        val cleanHost = normalizeCustomForgetHostname(hostname)
        if (cleanHost.isEmpty()) return
        manualForgetHosts = (manualForgetHosts + cleanHost).distinct()
        updateSlice { slice ->
            val baseSelected = if (slice.maintenance.forgetAllHosts) {
                slice.availableForgetHosts.map { it.hostname }
            } else {
                slice.maintenance.forgetHosts
            }
            slice.copy(
                maintenance = slice.maintenance.copy(
                    forgetAllHosts = false,
                    forgetHosts = toggleForgetHostSelection(baseSelected, cleanHost)
                )
            )
        }
        refreshAvailableHosts()
    }

    fun addCustomHost(hostname: String) {
        val cleanHost = normalizeCustomForgetHostname(hostname)
        if (cleanHost.isEmpty()) return
        manualForgetHosts = (manualForgetHosts + cleanHost).distinct()
        updateSlice { slice ->
            val baseSelected = if (slice.maintenance.forgetAllHosts) {
                slice.availableForgetHosts.map { it.hostname }
            } else {
                slice.maintenance.forgetHosts
            }
            val updatedSelected = (normalizeForgetHosts(baseSelected) + cleanHost).distinct()
            slice.copy(
                maintenance = slice.maintenance.copy(
                    forgetAllHosts = false,
                    forgetHosts = updatedSelected
                ),
                forgetHostsError = null
            )
        }
        refreshAvailableHosts()
    }

    fun removeCustomHost(hostname: String) {
        val cleanHost = normalizeCustomForgetHostname(hostname)
        if (cleanHost.isEmpty()) return
        manualForgetHosts = manualForgetHosts.filterNot { normalizeCustomForgetHostname(it) == cleanHost }
        updateSlice { slice ->
            slice.copy(
                maintenance = slice.maintenance.copy(
                    forgetAllHosts = false,
                    forgetHosts = slice.maintenance.forgetHosts.filterNot { normalizeCustomForgetHostname(it) == cleanHost }
                )
            )
        }
        refreshAvailableHosts()
    }

    fun refreshHosts(selectedRepoKeyOverride: String? = null, userInitiated: Boolean = true) {
        val selectedRepoKey = selectedRepoKeyOverride ?: repositoriesRepository.selectedRepository.value
        val repository = selectedRepoKey?.let { repositoriesRepository.getRepositoryByKey(it) }
        val existingSnapshots = resticRepository.getSnapshotsForRepo(repository?.path)
        if (!userInitiated && existingSnapshots != null) {
            if (latestRepoSnapshots != existingSnapshots) {
                latestRepoSnapshots = existingSnapshots
                refreshAvailableHosts()
            }
            return
        }
        if (readSlice().isLoadingForgetHosts) return

        if (selectedRepoKey == null || repository == null) {
            if (userInitiated) {
                updateSlice { it.copy(forgetHostsError = context.getString(R.string.no_repository_selected)) }
            }
            return
        }
        if (!resticBinaryManager.isInstalled) {
            if (userInitiated) {
                updateSlice { it.copy(forgetHostsError = context.getString(R.string.restic_not_available_check_settings)) }
            }
            return
        }

        val password = repositoriesRepository.getRepositoryPassword(selectedRepoKey)
        val hasMissingBackendCreds = when (repository.backendType) {
            RepositoryBackendType.SFTP -> !repositoriesRepository.hasSftpCredentials(selectedRepoKey)
            RepositoryBackendType.REST -> repository.restAuthRequired && !repositoriesRepository.hasRestCredentials(selectedRepoKey)
            RepositoryBackendType.S3 -> repository.s3AuthRequired && !repositoriesRepository.hasS3Credentials(selectedRepoKey)
            else -> false
        }
        if (password == null || hasMissingBackendCreds) {
            if (userInitiated) {
                updateSlice { it.copy(forgetHostsError = context.getString(R.string.forget_hosts_credentials_required)) }
            }
            return
        }

        val requestedRepoPath = repository.path
        scope.launch {
            updateSlice { it.copy(isLoadingForgetHosts = true, forgetHostsError = null) }
            val env = repositoriesRepository.getExecutionEnvironmentVariables(selectedRepoKey)
            val resticOptions = repositoriesRepository.getExecutionResticOptions(selectedRepoKey)
            val result = resticRepository.getSnapshots(
                repoPath = requestedRepoPath,
                password = password,
                environmentVariables = env,
                resticOptions = resticOptions
            )
            result.fold(
                onSuccess = { snapshots ->
                    val activeKey = selectedRepoKeyOverride ?: repositoriesRepository.selectedRepository.value
                    val activePath = activeKey?.let { repositoriesRepository.getRepositoryByKey(it)?.path }
                    if (activePath == requestedRepoPath) {
                        latestRepoSnapshots = snapshots
                    }
                    updateSlice { it.copy(isLoadingForgetHosts = false, forgetHostsError = null) }
                    refreshAvailableHosts()
                },
                onFailure = { error ->
                    updateSlice {
                        it.copy(
                            isLoadingForgetHosts = false,
                            forgetHostsError = error.message ?: context.getString(R.string.restic_failure_load_snapshots)
                        )
                    }
                }
            )
        }
    }
}
