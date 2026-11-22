package io.github.hddq.restoid.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
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

    @Test
    fun checkRootAccess_withSharedAdmissionMutex_cannotCloseShellAfterAdmission() = runTest {
        val sharedMutex = Mutex()
        var isRunning = false
        var shellInUse = false
        var closedWhileInUse = false
        val events = mutableListOf<String>()
        val probeStarted = CompletableDeferred<Unit>()
        val allowProbeFinish = CompletableDeferred<Unit>()

        val repo = RootRepository(
            loadPersistedHasRoot = { false },
            savePersistedHasRoot = {},
            isShellBusy = { isRunning },
            cachedShellIsRoot = { true },
            closeCachedShell = {
                if (shellInUse) closedWhileInUse = true
                events.add("close")
            },
            probeRootShell = {
                probeStarted.complete(Unit)
                runBlocking { allowProbeFinish.await() }
                events.add("probe")
                true
            },
            checkMutex = sharedMutex
        )

        val checkJob = launch {
            repo.checkRootAccess()
        }
        probeStarted.await()

        val admitJob = launch {
            sharedMutex.withLock {
                isRunning = true
                shellInUse = true
                events.add("admit")
            }
        }
        yield()

        // Admission must wait while checkRootAccess holds the shared mutex.
        assertFalse(isRunning)

        allowProbeFinish.complete(Unit)
        checkJob.join()
        admitJob.join()

        assertFalse(closedWhileInUse)
        assertEquals(listOf("close", "probe", "admit"), events)

        // Once admitted and running, a subsequent root check skips closing the active shell.
        repo.checkRootAccess()
        assertEquals(listOf("close", "probe", "admit"), events)
    }
}
