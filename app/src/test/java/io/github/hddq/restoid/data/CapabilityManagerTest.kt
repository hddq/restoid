package io.github.hddq.restoid.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CapabilityManagerTest {

    private class FakeContext : android.content.ContextWrapper(null)

    @Test
    fun rootGranted_storageDenied_hasStorageAndAppAccess() = runTest {
        val testScope = TestScope()
        val rootRepo = RootRepository()
        val storageRepo = StoragePermissionRepository(FakeContext())

        var rootGranted = true
        var allFilesGranted = false

        val capabilityManager = CapabilityManager(
            context = FakeContext(),
            rootRepository = rootRepo,
            storagePermissionRepository = storageRepo,
            coroutineScope = testScope.backgroundScope,
            rootGrantedChecker = { rootGranted },
            allFilesAccessChecker = { allFilesGranted }
        )

        assertTrue(capabilityManager.hasStorageAccessSync())
        assertTrue(capabilityManager.canAccessRepository(RepositoryBackendType.LOCAL))
        assertTrue(capabilityManager.canAccessRepository(RepositoryBackendType.SFTP))
        assertTrue(capabilityManager.canBackupCustomDirectories())
        assertTrue(capabilityManager.canRestoreCustomDirectories())
        assertTrue(capabilityManager.canBackupApps())
        assertTrue(capabilityManager.canRestoreApps())
    }

    @Test
    fun rootDenied_storageGranted_hasStorageAccess_butNoAppAccess() = runTest {
        val testScope = TestScope()
        val rootRepo = RootRepository()
        val storageRepo = StoragePermissionRepository(FakeContext())

        var rootGranted = false
        var allFilesGranted = true

        val capabilityManager = CapabilityManager(
            context = FakeContext(),
            rootRepository = rootRepo,
            storagePermissionRepository = storageRepo,
            coroutineScope = testScope.backgroundScope,
            rootGrantedChecker = { rootGranted },
            allFilesAccessChecker = { allFilesGranted }
        )

        assertTrue(capabilityManager.hasStorageAccessSync())
        assertTrue(capabilityManager.canAccessRepository(RepositoryBackendType.LOCAL))
        assertTrue(capabilityManager.canBackupCustomDirectories())
        assertTrue(capabilityManager.canRestoreCustomDirectories())
        assertFalse(capabilityManager.canBackupApps())
        assertFalse(capabilityManager.canRestoreApps())
    }

    @Test
    fun bothDenied_noStorageAccess_remoteRepoStillAllowed() = runTest {
        val testScope = TestScope()
        val rootRepo = RootRepository()
        val storageRepo = StoragePermissionRepository(FakeContext())

        var rootGranted = false
        var allFilesGranted = false

        val capabilityManager = CapabilityManager(
            context = FakeContext(),
            rootRepository = rootRepo,
            storagePermissionRepository = storageRepo,
            coroutineScope = testScope.backgroundScope,
            rootGrantedChecker = { rootGranted },
            allFilesAccessChecker = { allFilesGranted }
        )

        assertFalse(capabilityManager.hasStorageAccessSync())
        assertFalse(capabilityManager.canAccessRepository(RepositoryBackendType.LOCAL))
        assertTrue(capabilityManager.canAccessRepository(RepositoryBackendType.SFTP))
        assertTrue(capabilityManager.canAccessRepository(RepositoryBackendType.REST))
        assertTrue(capabilityManager.canAccessRepository(RepositoryBackendType.S3))
        assertFalse(capabilityManager.canBackupCustomDirectories())
        assertFalse(capabilityManager.canRestoreCustomDirectories())
        assertFalse(capabilityManager.canBackupApps())
        assertFalse(capabilityManager.canRestoreApps())
    }
}
