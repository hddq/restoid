package io.github.hddq.restoid.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootRepositoryTest {

    @Test
    fun initialState_readsPersistedPreference() {
        val deniedRepo = RootRepository(loadPersistedHasRoot = { false })
        val grantedRepo = RootRepository(loadPersistedHasRoot = { true })

        assertFalse(deniedRepo.hasRoot.value)
        assertTrue(grantedRepo.hasRoot.value)
    }

    @Test
    fun checkRootAccess_updatesAndPersistsGrantedAndDenied() = runTest {
        var persisted = false
        var probeResult = true
        var closeCalls = 0
        val repo = RootRepository(
            loadPersistedHasRoot = { persisted },
            savePersistedHasRoot = { persisted = it },
            isShellBusy = { false },
            closeCachedShell = { closeCalls++ },
            probeRootShell = { probeResult }
        )

        repo.checkRootAccess()
        assertTrue(repo.hasRoot.value)
        assertTrue(persisted)
        assertEquals(1, closeCalls)

        probeResult = false
        repo.checkRootAccess()
        assertFalse(repo.hasRoot.value)
        assertFalse(persisted)
        assertEquals(2, closeCalls)
    }

    @Test
    fun checkRootAccess_whenShellBusy_doesNotCloseShellOrProbe() = runTest {
        var persisted = true
        var closeCalls = 0
        var probeCalls = 0
        var saveCalls = 0
        val repo = RootRepository(
            loadPersistedHasRoot = { persisted },
            savePersistedHasRoot = {
                saveCalls++
                persisted = it
            },
            isShellBusy = { true },
            cachedShellIsRoot = { true },
            closeCachedShell = { closeCalls++ },
            probeRootShell = {
                probeCalls++
                false
            }
        )

        repo.checkRootAccess()

        assertTrue(repo.hasRoot.value)
        assertTrue(persisted)
        assertEquals(0, closeCalls)
        assertEquals(0, probeCalls)
        assertEquals(0, saveCalls)
    }

    @Test
    fun checkRootAccess_whenShellBusyAndCachedDiffers_updatesWithoutClosingShell() = runTest {
        var persisted = false
        var closeCalls = 0
        var probeCalls = 0
        val repo = RootRepository(
            loadPersistedHasRoot = { persisted },
            savePersistedHasRoot = { persisted = it },
            isShellBusy = { true },
            cachedShellIsRoot = { true },
            closeCachedShell = { closeCalls++ },
            probeRootShell = {
                probeCalls++
                false
            }
        )

        repo.checkRootAccess()

        assertTrue(repo.hasRoot.value)
        assertTrue(persisted)
        assertEquals(0, closeCalls)
        assertEquals(0, probeCalls)
    }

    @Test
    fun checkRootAccess_whenProbeThrows_defaultsToFalse() = runTest {
        var persisted = true
        val repo = RootRepository(
            loadPersistedHasRoot = { persisted },
            savePersistedHasRoot = { persisted = it },
            isShellBusy = { false },
            closeCachedShell = {},
            probeRootShell = { error("probe failed") }
        )

        repo.checkRootAccess()

        assertFalse(repo.hasRoot.value)
        assertFalse(persisted)
    }
}
