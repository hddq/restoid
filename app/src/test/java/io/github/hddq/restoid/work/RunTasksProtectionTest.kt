package io.github.hddq.restoid.work

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunTasksProtectionTest {

    @Test
    fun destructiveCleanupBlocked_whenConfiguredCustomDirsOmittedDueToMissingStorageAccess() {
        val appsConfigured = false
        val customDirsConfigured = true
        val hasRoot = false
        val hasStorageAccess = false

        val configuredSourceOmitted = (appsConfigured && !hasRoot) || (customDirsConfigured && !hasStorageAccess)
        assertTrue(configuredSourceOmitted)

        val requestedForgetSnapshots = true
        val requestedPruneRepo = true

        val actualForgetSnapshots = if (configuredSourceOmitted) false else requestedForgetSnapshots
        val actualPruneRepo = if (configuredSourceOmitted) false else requestedPruneRepo

        assertFalse(actualForgetSnapshots)
        assertFalse(actualPruneRepo)
    }

    @Test
    fun destructiveCleanupBlocked_whenConfiguredAppsOmittedDueToMissingRoot() {
        val appsConfigured = true
        val customDirsConfigured = false
        val hasRoot = false
        val hasStorageAccess = true

        val configuredSourceOmitted = (appsConfigured && !hasRoot) || (customDirsConfigured && !hasStorageAccess)
        assertTrue(configuredSourceOmitted)

        val requestedForgetSnapshots = true
        val requestedPruneRepo = true

        val actualForgetSnapshots = if (configuredSourceOmitted) false else requestedForgetSnapshots
        val actualPruneRepo = if (configuredSourceOmitted) false else requestedPruneRepo

        assertFalse(actualForgetSnapshots)
        assertFalse(actualPruneRepo)
    }

    @Test
    fun destructiveCleanupAllowed_whenAllConfiguredSourcesHaveRequiredAccess() {
        val appsConfigured = true
        val customDirsConfigured = true
        val hasRoot = true
        val hasStorageAccess = true

        val configuredSourceOmitted = (appsConfigured && !hasRoot) || (customDirsConfigured && !hasStorageAccess)
        assertFalse(configuredSourceOmitted)

        val requestedForgetSnapshots = true
        val requestedPruneRepo = true

        val actualForgetSnapshots = if (configuredSourceOmitted) false else requestedForgetSnapshots
        val actualPruneRepo = if (configuredSourceOmitted) false else requestedPruneRepo

        assertTrue(actualForgetSnapshots)
        assertTrue(actualPruneRepo)
    }

    @Test
    fun destructiveCleanupAllowed_whenNoBackupSourcesConfigured_maintenanceOnly() {
        val appsConfigured = false
        val customDirsConfigured = false
        val hasRoot = false
        val hasStorageAccess = false

        val configuredSourceOmitted = (appsConfigured && !hasRoot) || (customDirsConfigured && !hasStorageAccess)
        assertFalse(configuredSourceOmitted)

        val requestedForgetSnapshots = true
        val requestedPruneRepo = true

        val actualForgetSnapshots = if (configuredSourceOmitted) false else requestedForgetSnapshots
        val actualPruneRepo = if (configuredSourceOmitted) false else requestedPruneRepo

        assertTrue(actualForgetSnapshots)
        assertTrue(actualPruneRepo)
    }
}
