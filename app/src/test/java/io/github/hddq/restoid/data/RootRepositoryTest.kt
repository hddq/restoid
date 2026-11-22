package io.github.hddq.restoid.data

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class RootRepositoryTest {

    @Test
    fun checkRootAccess_whenNoCachedShell_probesAndUpdatesHasRoot() = runTest {
        var cachedIsRoot: Boolean? = null
        var probeCalls = 0
        var closeCalls = 0

        val repository = RootRepository(
            cachedShellIsRoot = { cachedIsRoot },
            closeCachedShell = {
                closeCalls++
                cachedIsRoot = null
            },
            probeRootShell = {
                probeCalls++
                cachedIsRoot = true
                true
            }
        )

        assertFalse(repository.hasRoot.value)

        val granted = repository.checkRootAccess()

        assertTrue(granted)
        assertTrue(repository.hasRoot.value)
        assertEquals(1, probeCalls)
        assertEquals(0, closeCalls)
    }

    @Test
    fun checkRootAccess_whenCachedRootShell_neverClosesOrReprobesEvenOnRecheck() = runTest {
        var probeCalls = 0
        var closeCalls = 0

        val repository = RootRepository(
            cachedShellIsRoot = { true },
            closeCachedShell = { closeCalls++ },
            probeRootShell = {
                probeCalls++
                true
            }
        )

        val granted = repository.checkRootAccess(recheckIfDenied = true)

        assertTrue(granted)
        assertTrue(repository.hasRoot.value)
        assertEquals(0, closeCalls)
        assertEquals(0, probeCalls)
    }

    @Test
    fun checkRootAccess_whenCachedNonRootShellAndNoRecheck_doesNotCloseOrReprobe() = runTest {
        var probeCalls = 0
        var closeCalls = 0

        val repository = RootRepository(
            cachedShellIsRoot = { false },
            closeCachedShell = { closeCalls++ },
            probeRootShell = {
                probeCalls++
                true
            }
        )

        val granted = repository.checkRootAccess(recheckIfDenied = false)

        assertFalse(granted)
        assertFalse(repository.hasRoot.value)
        assertEquals(0, closeCalls)
        assertEquals(0, probeCalls)
    }

    @Test
    fun checkRootAccess_whenCachedNonRootShellAndRecheckWhileIdle_closesAndReprobes() = runTest {
        var cachedIsRoot: Boolean? = false
        var probeCalls = 0
        var closeCalls = 0

        val repository = RootRepository(
            isShellBusy = { false },
            cachedShellIsRoot = { cachedIsRoot },
            closeCachedShell = {
                closeCalls++
                cachedIsRoot = null
            },
            probeRootShell = {
                probeCalls++
                cachedIsRoot = true
                true
            }
        )

        val granted = repository.checkRootAccess(recheckIfDenied = true)

        assertTrue(granted)
        assertTrue(repository.hasRoot.value)
        assertEquals(1, closeCalls)
        assertEquals(1, probeCalls)
    }

    @Test
    fun checkRootAccess_whenCachedNonRootShellAndBusy_doesNotCloseShell() = runTest {
        var probeCalls = 0
        var closeCalls = 0

        val repository = RootRepository(
            isShellBusy = { true },
            cachedShellIsRoot = { false },
            closeCachedShell = { closeCalls++ },
            probeRootShell = {
                probeCalls++
                true
            }
        )

        val granted = repository.checkRootAccess(recheckIfDenied = true)

        assertFalse(granted)
        assertFalse(repository.hasRoot.value)
        assertEquals(0, closeCalls)
        assertEquals(0, probeCalls)
    }

    @Test
    fun checkRootAccess_whenProbeThrows_defaultsToFalse() = runTest {
        val repository = RootRepository(
            cachedShellIsRoot = { null },
            closeCachedShell = {},
            probeRootShell = { throw IllegalStateException("shell failed") }
        )

        val granted = repository.checkRootAccess()

        assertFalse(granted)
        assertFalse(repository.hasRoot.value)
    }

    @Test
    fun checkRootAccess_concurrentCaller_waitsForInFlightProbeWithoutReprobing() = runTest {
        val cachedIsRoot = AtomicReference<Boolean?>(null)
        val probeCalls = AtomicInteger(0)
        val probeStarted = CountDownLatch(1)
        val releaseProbe = CountDownLatch(1)

        val repository = RootRepository(
            cachedShellIsRoot = { cachedIsRoot.get() },
            closeCachedShell = { cachedIsRoot.set(null) },
            probeRootShell = {
                probeCalls.incrementAndGet()
                probeStarted.countDown()
                assertTrue(releaseProbe.await(5, TimeUnit.SECONDS))
                cachedIsRoot.set(true)
                true
            }
        )

        val initialProbe = async(Dispatchers.Default) { repository.checkRootAccess() }
        assertTrue(probeStarted.await(5, TimeUnit.SECONDS))

        // Caller arrives while the initial probe is still in flight and hasRoot is still false.
        assertFalse(repository.hasRoot.value)
        val awaitingAction = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            repository.checkRootAccess()
        }
        assertFalse(awaitingAction.isCompleted)

        releaseProbe.countDown()

        assertTrue(initialProbe.await())
        assertTrue(awaitingAction.await())
        assertTrue(repository.hasRoot.value)
        assertEquals(1, probeCalls.get())
    }
}
