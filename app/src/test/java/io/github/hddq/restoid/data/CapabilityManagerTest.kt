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
            liveRootChecker = { rootGranted },
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
            liveRootChecker = { rootGranted },
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
            liveRootChecker = { rootGranted },
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

    @Test
    fun liveShellRootRevocation_immediatelyDeniesAppBackupAndRestore() = runTest {
        val testScope = TestScope()
        val rootRepo = RootRepository()
        val storageRepo = StoragePermissionRepository(FakeContext())

        var liveRoot = true

        val capabilityManager = CapabilityManager(
            context = FakeContext(),
            rootRepository = rootRepo,
            storagePermissionRepository = storageRepo,
            coroutineScope = testScope.backgroundScope,
            liveRootChecker = { liveRoot },
            allFilesAccessChecker = { true }
        )

        assertTrue(capabilityManager.canBackupApps())
        assertTrue(capabilityManager.canRestoreApps())

        // Revoke root on live shell
        liveRoot = false
        assertFalse(capabilityManager.canBackupApps())
        assertFalse(capabilityManager.canRestoreApps())
    }

    @Test
    fun initialState_doesNotCallLiveRootChecker() = runTest {
        val testScope = TestScope()
        val rootRepo = RootRepository() // defaults to RootState.Checking
        val storageRepo = StoragePermissionRepository(FakeContext())

        var rootCheckerCalled = false

        val capabilityManager = CapabilityManager(
            context = FakeContext(),
            rootRepository = rootRepo,
            storagePermissionRepository = storageRepo,
            coroutineScope = testScope.backgroundScope,
            liveRootChecker = {
                rootCheckerCalled = true
                false
            },
            allFilesAccessChecker = { false }
        )

        assertFalse(rootCheckerCalled)
        assertFalse(capabilityManager.hasStorageAccess.value)
    }

    @Test
    fun hasCapability_withRoot_returnsTrueForAnyPermission() = runTest {
        val testScope = TestScope()
        val rootRepo = RootRepository()
        val storageRepo = StoragePermissionRepository(FakeContext())

        val capabilityManager = CapabilityManager(
            context = FakeContext(),
            rootRepository = rootRepo,
            storagePermissionRepository = storageRepo,
            coroutineScope = testScope.backgroundScope,
            liveRootChecker = { true },
            allFilesAccessChecker = { false }
        )

        assertTrue(capabilityManager.hasCapability(android.Manifest.permission.READ_SMS))
    }

    @Test
    fun canAccessRepository_localBackendRequiresStorageOrLiveRoot() = runTest {
        val testScope = TestScope()
        val rootRepo = RootRepository()
        val storageRepo = StoragePermissionRepository(FakeContext())

        var liveRoot = false
        var allFiles = false

        val capabilityManager = CapabilityManager(
            context = FakeContext(),
            rootRepository = rootRepo,
            storagePermissionRepository = storageRepo,
            coroutineScope = testScope.backgroundScope,
            liveRootChecker = { liveRoot },
            allFilesAccessChecker = { allFiles }
        )

        // Neither storage nor root
        assertFalse(capabilityManager.canAccessRepository(RepositoryBackendType.LOCAL))
        assertTrue(capabilityManager.canAccessRepository(RepositoryBackendType.SFTP))
        assertTrue(capabilityManager.canAccessRepository(RepositoryBackendType.REST))
        assertTrue(capabilityManager.canAccessRepository(RepositoryBackendType.S3))

        // Storage permission granted
        allFiles = true
        assertTrue(capabilityManager.canAccessRepository(RepositoryBackendType.LOCAL))

        // Storage revoked, root granted
        allFiles = false
        liveRoot = true
        assertTrue(capabilityManager.canAccessRepository(RepositoryBackendType.LOCAL))
    }
}
