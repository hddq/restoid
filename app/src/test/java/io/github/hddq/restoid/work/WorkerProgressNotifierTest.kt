package io.github.hddq.restoid.work

import io.github.hddq.restoid.ui.shared.OperationProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkerProgressNotifierTest {

    private data class RecordedState(
        val savedLogs: MutableList<Pair<OperationProgress, Long>> = mutableListOf(),
        val runtimeUpdates: MutableList<Pair<OperationProgress, Boolean>> = mutableListOf(),
        val foregroundUpdates: MutableList<OperationProgress> = mutableListOf(),
        val workProgressUpdates: MutableList<OperationProgress> = mutableListOf()
    )

    private fun createNotifier(
        startElapsedMs: Long = 0L,
        monotonicClock: () -> Long,
        wallClock: () -> Long = { 1_700_000_000_000L },
        checkpointIntervalMs: Long = 15_000L
    ): Pair<WorkerProgressNotifier, RecordedState> {
        val recorded = RecordedState()
        val notifier = WorkerProgressNotifier(
            startElapsedMs = startElapsedMs,
            initialProgress = OperationProgress(stageTitle = "Initializing"),
            monotonicNowMs = monotonicClock,
            wallNowMs = wallClock,
            checkpointIntervalMs = checkpointIntervalMs,
            onSaveInProgressLog = { progress, _, elapsedSeconds ->
                recorded.savedLogs += progress to elapsedSeconds
            },
            onMarkRuntimeProgress = { progress, persist ->
                recorded.runtimeUpdates += progress to persist
            },
            onUpdateForeground = { progress ->
                recorded.foregroundUpdates += progress
            },
            onSetWorkProgress = { progress ->
                recorded.workProgressUpdates += progress
            }
        )
        return notifier to recorded
    }

    @Test
    fun `interleaved timer ticks do not suppress small runner progress notification updates`() {
        var nowElapsedMs = 0L
        val (notifier, recorded) = createNotifier(
            startElapsedMs = 0L,
            monotonicClock = { nowElapsedMs }
        )

        // First runner emission at t=0 updates foreground immediately.
        notifier.onProgress(
            OperationProgress(
                stageTitle = "Backing up",
                overallPercentage = 0.00f,
                filesProcessed = 0
            )
        )
        assertEquals(1, recorded.foregroundUpdates.size)
        assertEquals(0.00f, recorded.foregroundUpdates.last().overallPercentage)

        // Small update (<2%) within 1s is throttled for notification, but stored as latestProgress.
        nowElapsedMs = 500L
        notifier.onProgress(
            OperationProgress(
                stageTitle = "Backing up",
                overallPercentage = 0.01f,
                filesProcessed = 10
            )
        )
        assertEquals(1, recorded.foregroundUpdates.size)

        // Timer tick at t=1000ms updates in-memory elapsedTime without touching notification thresholds.
        nowElapsedMs = 1000L
        notifier.onTimerTick()
        assertEquals(1, recorded.foregroundUpdates.size)
        assertEquals(1L, recorded.runtimeUpdates.last().first.elapsedTime)

        // Next small update (<2%) at t=1500ms must still refresh the notification since >=1s elapsed
        // since the last foreground notification at t=0ms.
        nowElapsedMs = 1500L
        notifier.onProgress(
            OperationProgress(
                stageTitle = "Backing up",
                overallPercentage = 0.015f,
                filesProcessed = 25
            )
        )
        assertEquals(2, recorded.foregroundUpdates.size)
        assertEquals(0.015f, recorded.foregroundUpdates.last().overallPercentage)
        assertEquals(25, recorded.foregroundUpdates.last().filesProcessed)

        // Interleave another tick at t=2000ms and another <2% update at t=2500ms.
        nowElapsedMs = 2000L
        notifier.onTimerTick()
        assertEquals(2, recorded.foregroundUpdates.size)

        nowElapsedMs = 2500L
        notifier.onProgress(
            OperationProgress(
                stageTitle = "Backing up",
                overallPercentage = 0.025f,
                filesProcessed = 40
            )
        )
        assertEquals(3, recorded.foregroundUpdates.size)
        assertEquals(0.025f, recorded.foregroundUpdates.last().overallPercentage)
        assertEquals(40, recorded.foregroundUpdates.last().filesProcessed)
    }

    @Test
    fun `quiet timer ticks update runtime state in memory every second and persist checkpoints periodically`() {
        var nowElapsedMs = 0L
        val (notifier, recorded) = createNotifier(
            startElapsedMs = 0L,
            monotonicClock = { nowElapsedMs },
            checkpointIntervalMs = 15_000L
        )

        for (second in 1L..35L) {
            nowElapsedMs = second * 1000L
            notifier.onTimerTick()
        }

        // Foreground notification is never updated by quiet timer ticks.
        assertTrue(recorded.foregroundUpdates.isEmpty())

        // In-memory runtime state updates once per second for all 35 seconds.
        assertEquals(35, recorded.runtimeUpdates.size)
        assertEquals((1L..35L).toList(), recorded.runtimeUpdates.map { it.first.elapsedTime })

        // Persistent checkpoints only happen at 15s and 30s.
        val persistedIndices = recorded.runtimeUpdates.mapIndexedNotNull { index, (_, persist) ->
            if (persist) index + 1 else null
        }
        assertEquals(listOf(15, 30), persistedIndices)
        assertEquals(listOf(15L, 30L), recorded.savedLogs.map { it.second })
        assertEquals(listOf(15L, 30L), recorded.workProgressUpdates.map { it.elapsedTime })
        assertFalse(recorded.runtimeUpdates[0].second)
    }

    @Test
    fun `elapsed time advances on monotonic clock even when wall clock jumps backward`() {
        var nowElapsedMs = 10_000L
        var wallClockMs = 1_700_000_100_000L
        val (notifier, recorded) = createNotifier(
            startElapsedMs = 10_000L,
            monotonicClock = { nowElapsedMs },
            wallClock = { wallClockMs },
            checkpointIntervalMs = 5_000L
        )

        nowElapsedMs = 11_000L
        notifier.onTimerTick()
        assertEquals(1L, recorded.runtimeUpdates.last().first.elapsedTime)

        // Wall clock jumps backward by 60 seconds; monotonic clock advances normally.
        wallClockMs -= 60_000L
        nowElapsedMs = 12_000L
        notifier.onTimerTick()
        assertEquals(2L, recorded.runtimeUpdates.last().first.elapsedTime)

        // Runner emits progress with a backward-jumped wall-clock elapsedTime = 0.
        nowElapsedMs = 12_500L
        notifier.onProgress(
            OperationProgress(
                stageTitle = "Checking",
                elapsedTime = 0L,
                overallPercentage = 0.5f
            )
        )
        assertEquals(2L, recorded.foregroundUpdates.last().elapsedTime)

        // Subsequent timer ticks continue advancing and persist at the next checkpoint interval.
        nowElapsedMs = 18_000L
        notifier.onTimerTick()
        assertEquals(8L, recorded.runtimeUpdates.last().first.elapsedTime)
        assertEquals(8L, recorded.savedLogs.last().second)
    }

    @Test
    fun `forward-jumped runner elapsedTime does not poison monotonic ticks or checkpoints`() {
        var nowElapsedMs = 10_000L
        val (notifier, recorded) = createNotifier(
            startElapsedMs = 10_000L,
            monotonicClock = { nowElapsedMs },
            checkpointIntervalMs = 5_000L
        )

        // Runner emits an elapsedTime jumped 1 hour forward while only 1s elapsed monotonically.
        nowElapsedMs = 11_000L
        notifier.onProgress(
            OperationProgress(
                stageTitle = "Checking",
                elapsedTime = 3600L,
                overallPercentage = 0.25f
            )
        )
        assertEquals(1L, recorded.foregroundUpdates.last().elapsedTime)

        // Quiet ticks continue advancing 2s..6s and persisting checkpoints at 16_000ms.
        for (second in 2L..6L) {
            nowElapsedMs = 10_000L + second * 1000L
            notifier.onTimerTick()
            assertEquals(second, recorded.runtimeUpdates.last().first.elapsedTime)
        }
        assertEquals(6L, recorded.savedLogs.last().second)
    }
}
