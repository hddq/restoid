package io.github.hddq.restoid

import io.github.hddq.restoid.data.RepositoryBackendType
import io.github.hddq.restoid.work.NETWORK_LOSS_GRACE_PERIOD_MS
import io.github.hddq.restoid.work.isRemoteBackend
import io.github.hddq.restoid.work.shouldAbortNetworkRun
import org.junit.Assert.*
import org.junit.Test

class NetworkLossMonitorTest {

    @Test
    fun localBackendNeverAborts() {
        assertFalse(
            shouldAbortNetworkRun(
                backendIsRemote = false,
                hasInternet = false,
                isUnmetered = false,
                requireUnmetered = true,
                downDurationMs = NETWORK_LOSS_GRACE_PERIOD_MS * 10
            )
        )
    }

    @Test
    fun deadNetworkAbortsAfterGraceRegardlessOfMeteredRequirement() {
        assertFalse(
            shouldAbortNetworkRun(
                backendIsRemote = true,
                hasInternet = false,
                isUnmetered = false,
                requireUnmetered = false,
                downDurationMs = NETWORK_LOSS_GRACE_PERIOD_MS - 1
            )
        )
        assertTrue(
            shouldAbortNetworkRun(
                backendIsRemote = true,
                hasInternet = false,
                isUnmetered = false,
                requireUnmetered = false,
                downDurationMs = NETWORK_LOSS_GRACE_PERIOD_MS
            )
        )
    }

    @Test
    fun meteredNetworkAbortsOnlyWhenUnmeteredRequired() {
        // Brief handoff blip: tolerated.
        assertFalse(
            shouldAbortNetworkRun(
                backendIsRemote = true,
                hasInternet = true,
                isUnmetered = false,
                requireUnmetered = true,
                downDurationMs = 5_000L
            )
        )
        // Sustained metered use with unmetered required: abort (the reported incident).
        assertTrue(
            shouldAbortNetworkRun(
                backendIsRemote = true,
                hasInternet = true,
                isUnmetered = false,
                requireUnmetered = true,
                downDurationMs = NETWORK_LOSS_GRACE_PERIOD_MS
            )
        )
        // Metered use without the requirement: allowed (manual runs on mobile data).
        assertFalse(
            shouldAbortNetworkRun(
                backendIsRemote = true,
                hasInternet = true,
                isUnmetered = false,
                requireUnmetered = false,
                downDurationMs = NETWORK_LOSS_GRACE_PERIOD_MS * 10
            )
        )
    }

    @Test
    fun healthyNetworkNeverAborts() {
        assertFalse(
            shouldAbortNetworkRun(
                backendIsRemote = true,
                hasInternet = true,
                isUnmetered = true,
                requireUnmetered = true,
                downDurationMs = NETWORK_LOSS_GRACE_PERIOD_MS * 10
            )
        )
    }

    @Test
    fun isRemoteBackend_matchesOnlyNetworkBackends() {
        assertTrue(isRemoteBackend(RepositoryBackendType.SFTP))
        assertTrue(isRemoteBackend(RepositoryBackendType.REST))
        assertTrue(isRemoteBackend(RepositoryBackendType.S3))
        assertTrue(isRemoteBackend(RepositoryBackendType.RCLONE))
        assertFalse(isRemoteBackend(RepositoryBackendType.RCLONE, isRcloneLocal = true))
        assertFalse(isRemoteBackend(RepositoryBackendType.LOCAL))
        assertFalse(isRemoteBackend(null))
    }
}
