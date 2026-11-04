package io.github.hddq.restoid.work

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Exercises the monitor's timer/polling behavior with virtual time: the
 * production code paths that decide *when* a run is stopped, as opposed
 * to the pure abort policy covered by [NetworkLossMonitorTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NetworkLossMonitorTimingTest {

    private fun healthy() = true to true

    private fun down() = false to false

    private fun monitor(
        state: () -> Pair<Boolean, Boolean>,
        onViolation: () -> Unit,
        scope: kotlinx.coroutines.CoroutineScope,
        gracePeriodMs: Long = 90_000L,
        pollIntervalMs: Long = 30_000L,
    ) = NetworkLossMonitor(
        stateProvider = state,
        gracePeriodMs = gracePeriodMs,
        pollIntervalMs = pollIntervalMs,
        scope = scope,
        onSustainedViolation = onViolation
    ).apply {
        configure(backendIsRemote = true, requireUnmetered = false)
    }

    @Test
    fun `repeated polls do not restart the grace countdown`() = runTest {
        var state = healthy()
        var fires = 0
        val monitor = monitor(
            state = { state },
            onViolation = { fires++ },
            scope = backgroundScope
        )
        monitor.start()

        // Outage begins; leave detection to the poller ticks (production path).
        state = down()
        advanceTimeBy(60_000)
        assertEquals(0, fires)
        // First detection happened at the t=30s tick, so the stop lands at t=120s.
        // (Overshoot slightly: continuations due exactly at the target time
        // need one more scheduler pump to run.)
        advanceTimeBy(65_000)
        assertEquals(1, fires)
        // The outage persists but the stop must not re-fire.
        advanceTimeBy(295_000)
        assertEquals(1, fires)
    }

    @Test
    fun `recovery grants the next outage a full grace period`() = runTest {
        var state = healthy()
        var fires = 0
        val monitor = monitor(
            state = { state },
            onViolation = { fires++ },
            scope = backgroundScope
        )
        monitor.start()

        // Outage #1 starts at t=0 (callback path), recovers at t=60s.
        state = down()
        monitor.evaluate()
        advanceTimeBy(60_000)
        assertEquals(0, fires)
        state = healthy()
        monitor.evaluate()
        advanceTimeBy(60_000)
        assertEquals(0, fires)

        // Outage #2 starts at t=120s and must earn its own full 90s.
        state = down()
        monitor.evaluate()
        advanceTimeBy(89_000)
        assertEquals(0, fires)
        // Overshoot slightly: continuations due exactly at the target time
        // need one more scheduler pump to run.
        advanceTimeBy(5_000)
        assertEquals(1, fires)
    }

    @Test
    fun `stop prevents later notifications`() = runTest {
        var state = down()
        var fires = 0
        val monitor = monitor(
            state = { state },
            onViolation = { fires++ },
            scope = backgroundScope
        )
        monitor.start()
        monitor.stop()

        advanceTimeBy(300_000)
        assertEquals(0, fires)
    }

    @Test
    fun `healthy network never notifies`() = runTest {
        var fires = 0
        val monitor = monitor(
            state = { healthy() },
            onViolation = { fires++ },
            scope = backgroundScope
        )
        monitor.start()

        advanceTimeBy(300_000)
        assertEquals(0, fires)
    }
}
