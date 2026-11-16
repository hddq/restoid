package io.github.hddq.restoid.ui.shared

import io.github.hddq.restoid.data.SnapshotInfo
import java.util.Locale

data class ForgetHostOption(
    val hostname: String,
    val isCurrentDevice: Boolean = false,
    val snapshotCount: Int? = null,
    val isRemovable: Boolean = false
)

fun normalizeForgetHosts(hosts: List<String>): List<String> {
    return hosts
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
}

fun toggleForgetHostSelection(
    selectedHosts: List<String>,
    hostname: String
): List<String> {
    val cleanHost = hostname.trim()
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
    val cleanCurrentDevice = currentDeviceHostname.trim()
    val backupSnapshots = snapshots?.filter {
        it.tags.contains("restoid") && it.tags.contains("backup")
    }
    val snapshotCountsByHost = backupSnapshots?.let { list ->
        list.mapNotNull { it.hostname?.trim()?.takeIf { h -> h.isNotEmpty() } }
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
