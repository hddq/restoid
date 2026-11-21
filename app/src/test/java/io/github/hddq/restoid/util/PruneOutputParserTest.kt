package io.github.hddq.restoid.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PruneOutputParserTest {

    @Test
    fun `full prune stream emits all phases, exact item counts, and strictly monotonic overallFraction`() {
        val tracker = PruneProgressTracker()
        val lines = listOf(
            "loading indexes...",
            "loading all snapshots...",
            "finding data that is still in use for 20 snapshots",
            "[0:01] 45.00%  9 / 20 snapshots",
            "[0:02] 100.00%  20 / 20 snapshots",
            "searching used packs...",
            "collecting packs for deletion and repacking",
            "[0:03] 50.00%  10 / 20 packs processed",
            "[0:04] 100.00%  20 / 20 packs processed",
            "to repack:         120 blobs / 12.500 MiB",
            "this removes:       40 blobs / 4.200 MiB",
            "to delete:          80 blobs / 8.300 MiB",
            "total prune:       120 blobs / 12.500 MiB",
            "not yet compressed: 10 blobs / 1.000 MiB",
            "remaining:         500 blobs / 100.000 MiB",
            "unused size after prune: 2.000 MiB (2.00% of remaining size)",
            "deleting unreferenced packs",
            "[0:05] 50.00%  5 / 10 files deleted",
            "[0:06] 100.00%  10 / 10 files deleted",
            "repacking packs",
            "[0:07] 33.33%  4 / 12 packs repacked",
            "[0:09] 100.00%  12 / 12 packs repacked",
            "rebuilding index",
            "[0:10] 50.00%  3 / 6 indexes processed",
            "[0:11] 100.00%  6 / 6 indexes processed",
            "[0:11] 50.00%  2 / 4 old indexes deleted",
            "[0:12] 100.00%  4 / 4 old indexes deleted",
            "removing 12 old packs",
            "[0:13] 25.00%  3 / 12 files deleted",
            "[0:14] 100.00%  12 / 12 files deleted",
            "done"
        )

        val updates = lines.map { line -> line to tracker.processLine(line) }
        val nonNullUpdates = updates.mapNotNull { it.second }

        // Ensure stats lines were absorbed silently (7 stats lines)
        val absorbedLines = updates.filter { it.second == null }.map { it.first }
        assertEquals(7, absorbedLines.size)

        // Verify monotonicity across every emitted update
        for (i in 1 until nonNullUpdates.size) {
            assertTrue(
                "overallFraction regressed at index $i: ${nonNullUpdates[i - 1].overallFraction} -> ${nonNullUpdates[i].overallFraction}",
                nonNullUpdates[i].overallFraction >= nonNullUpdates[i - 1].overallFraction
            )
        }

        // Spot check key transitions and exact values
        val byLine = updates.toMap()

        val loadingIdx = byLine.getValue("loading indexes...")!!
        assertEquals(PrunePhase.LOADING_INDEXES, loadingIdx.phase)
        assertTrue(loadingIdx.isStageIndeterminate)
        assertFalse(loadingIdx.hasDeterminateOverall)
        assertEquals(0.00f, loadingIdx.overallFraction, 0.0001f)

        val loadingSnaps = byLine.getValue("loading all snapshots...")!!
        assertEquals(PrunePhase.LOADING_SNAPSHOTS, loadingSnaps.phase)
        assertTrue(loadingSnaps.isStageIndeterminate)
        assertFalse(loadingSnaps.hasDeterminateOverall)
        assertEquals(0.02f, loadingSnaps.overallFraction, 0.0001f)

        val findingHdr = byLine.getValue("finding data that is still in use for 20 snapshots")!!
        assertEquals(PrunePhase.FINDING_USED_DATA, findingHdr.phase)
        assertTrue(findingHdr.isStageIndeterminate)
        assertEquals(0.05f, findingHdr.overallFraction, 0.0001f)

        val findingMid = byLine.getValue("[0:01] 45.00%  9 / 20 snapshots")!!
        assertEquals(PrunePhase.FINDING_USED_DATA, findingMid.phase)
        assertFalse(findingMid.isStageIndeterminate)
        assertTrue(findingMid.hasDeterminateOverall)
        assertEquals(0.45f, findingMid.stagePercentage, 0.0001f)
        assertEquals(9, findingMid.itemsProcessed)
        assertEquals(20, findingMid.totalItems)
        assertEquals(0.14f, findingMid.overallFraction, 0.0001f)

        val collectingMid = byLine.getValue("[0:03] 50.00%  10 / 20 packs processed")!!
        assertEquals(PrunePhase.COLLECTING_PACKS, collectingMid.phase)
        assertEquals(0.50f, collectingMid.stagePercentage, 0.0001f)
        assertEquals(10, collectingMid.itemsProcessed)
        assertEquals(20, collectingMid.totalItems)
        assertEquals(0.35f, collectingMid.overallFraction, 0.0001f)

        val unrefMid = byLine.getValue("[0:05] 50.00%  5 / 10 files deleted")!!
        assertEquals(PrunePhase.DELETING_UNREFERENCED, unrefMid.phase)
        assertEquals(0.50f, unrefMid.stagePercentage, 0.0001f)
        assertEquals(5, unrefMid.itemsProcessed)
        assertEquals(10, unrefMid.totalItems)
        assertEquals(0.475f, unrefMid.overallFraction, 0.0001f)

        val repackMid = byLine.getValue("[0:07] 33.33%  4 / 12 packs repacked")!!
        assertEquals(PrunePhase.REPACKING_PACKS, repackMid.phase)
        assertEquals(0.3333f, repackMid.stagePercentage, 0.0001f)
        assertEquals(4, repackMid.itemsProcessed)
        assertEquals(12, repackMid.totalItems)
        assertEquals(0.50f + 0.35f * 0.3333f, repackMid.overallFraction, 0.0001f)

        val rebuildHdr = byLine.getValue("rebuilding index")!!
        assertEquals(PrunePhase.REBUILDING_INDEX, rebuildHdr.phase)
        assertTrue(rebuildHdr.isStageIndeterminate)
        assertTrue(rebuildHdr.hasDeterminateOverall)
        assertEquals(0, rebuildHdr.itemsProcessed)
        assertEquals(0, rebuildHdr.totalItems)
        assertEquals(0.85f, rebuildHdr.overallFraction, 0.0001f)

        val idxProcMid = byLine.getValue("[0:10] 50.00%  3 / 6 indexes processed")!!
        assertEquals(PrunePhase.REBUILDING_INDEX, idxProcMid.phase)
        assertEquals(0.87f, idxProcMid.overallFraction, 0.0001f)

        val oldIdxMid = byLine.getValue("[0:11] 50.00%  2 / 4 old indexes deleted")!!
        assertEquals(PrunePhase.REBUILDING_INDEX, oldIdxMid.phase)
        assertEquals(0.905f, oldIdxMid.overallFraction, 0.0001f)

        val oldPacksMid = byLine.getValue("[0:13] 25.00%  3 / 12 files deleted")!!
        assertEquals(PrunePhase.REMOVING_OLD_PACKS, oldPacksMid.phase)
        assertEquals(0.25f, oldPacksMid.stagePercentage, 0.0001f)
        assertEquals(3, oldPacksMid.itemsProcessed)
        assertEquals(12, oldPacksMid.totalItems)
        assertEquals(0.9375f, oldPacksMid.overallFraction, 0.0001f)

        val doneUpdate = byLine.getValue("done")!!
        assertEquals(PrunePhase.DONE, doneUpdate.phase)
        assertEquals(1.0f, doneUpdate.overallFraction, 0.0001f)
        assertEquals(1.0f, doneUpdate.stagePercentage, 0.0001f)
        assertFalse(doneUpdate.isStageIndeterminate)
        assertTrue(doneUpdate.hasDeterminateOverall)
    }

    @Test
    fun `no-op prune stream maintains monotonic progress and parses zero summary stats`() {
        val output = """
            loading indexes...
            loading all snapshots...
            finding data that is still in use for 5 snapshots
            [0:00] 100.00%  5 / 5 snapshots
            searching used packs...
            collecting packs for deletion and repacking
            [0:00] 100.00%  10 / 10 packs processed
            to repack:         0 blobs / 0 B
            this removes:      0 blobs / 0 B
            to delete:         0 blobs / 0 B
            total prune:       0 blobs / 0 B
            remaining:         100 blobs / 50.000 MiB
            unused size after prune: 0 B (0.00% of remaining size)
            done
        """.trimIndent()

        val tracker = PruneProgressTracker()
        val updates = output.lines().mapNotNull { tracker.processLine(it) }

        assertTrue(updates.isNotEmpty())
        for (i in 1 until updates.size) {
            assertTrue(updates[i].overallFraction >= updates[i - 1].overallFraction)
        }
        assertEquals(PrunePhase.DONE, updates.last().phase)
        assertEquals(1.0f, updates.last().overallFraction, 0.0001f)

        val stats = PruneOutputParser.parseSummaryStats(output)
        assertNotNull(stats)
        assertEquals(0, stats!!.repackBlobs)
        assertEquals("0 B", stats.repackSize)
        assertEquals(0, stats.removedBlobs)
        assertEquals("0 B", stats.removedSize)
        assertEquals(100, stats.remainingBlobs)
        assertEquals("50.000 MiB", stats.remainingSize)
        assertFalse(stats.hasFreedData)
        assertFalse(stats.hasRepackedData)
    }

    @Test
    fun `empty repository zero-max counter emits indeterminate state without zero-over-zero ratio`() {
        val tracker = PruneProgressTracker()
        tracker.processLine("finding data that is still in use...")
        val update = tracker.processLine("[0:00]          0 snapshots")

        assertNotNull(update)
        assertEquals(PrunePhase.FINDING_USED_DATA, update!!.phase)
        assertTrue(update.isStageIndeterminate)
        assertEquals(0f, update.stagePercentage, 0.0001f)
        assertEquals(0, update.itemsProcessed)
        assertEquals(0, update.totalItems)
        assertFalse(update.hasDeterminateOverall)
        assertEquals(0.05f, update.overallFraction, 0.0001f)
    }

    @Test
    fun `multi-hour counter timestamp is parsed accurately`() {
        val tracker = PruneProgressTracker()
        val update = tracker.processLine("[1:02:03] 50.00%  50 / 100 packs repacked")

        assertNotNull(update)
        assertEquals(PrunePhase.REPACKING_PACKS, update!!.phase)
        assertFalse(update.isStageIndeterminate)
        assertTrue(update.hasDeterminateOverall)
        assertEquals(0.50f, update.stagePercentage, 0.0001f)
        assertEquals(50, update.itemsProcessed)
        assertEquals(100, update.totalItems)
        assertEquals(0.675f, update.overallFraction, 0.0001f)
    }

    @Test
    fun `stats lines including verbose stats are absorbed silently while warnings surface as rawActivity`() {
        val tracker = PruneProgressTracker()
        val progressBefore = tracker.processLine("[0:05] 50.00%  5 / 10 packs repacked")!!

        val verboseAndStatsLines = listOf(
            "used:               5000 blobs / 500.000 MiB",
            "duplicates:          100 blobs / 10.000 MiB",
            "unused:              456 blobs / 45.678 MiB",
            "unreferenced:          0 blobs / 0 B",
            "total:              5556 blobs / 555.678 MiB",
            "unused size:        55.678 MiB of total size",
            "totally used packs: 100",
            "partly used packs:  20",
            "unused packs:       5",
            "to keep:            90 packs",
            "to repack:          10 blobs / 1.000 MiB",
            "this removes:       5 blobs / 500 KiB",
            "to delete:          5 blobs / 500 KiB",
            "total prune:        10 blobs / 1.000 MiB",
            "not yet compressed: 0 blobs / 0 B",
            "remaining:          5000 blobs / 500.000 MiB",
            "unused size after prune: 0 B (0.00% of remaining size)"
        )
        for (line in verboseAndStatsLines) {
            assertNull("Expected stats line to be absorbed: $line", tracker.processLine(line))
        }

        val warningUpdate = tracker.processLine("repository is already locked exclusively by PID 1234")
        assertNotNull(warningUpdate)
        assertEquals("repository is already locked exclusively by PID 1234", warningUpdate!!.rawActivity)
        assertEquals(progressBefore.overallFraction, warningUpdate.overallFraction, 0.0001f)
        assertEquals(progressBefore.stagePercentage, warningUpdate.stagePercentage, 0.0001f)
        assertEquals(progressBefore.itemsProcessed, warningUpdate.itemsProcessed)
        assertEquals(progressBefore.totalItems, warningUpdate.totalItems)
        assertTrue(warningUpdate.hasDeterminateOverall)
    }

    @Test
    fun `parseSummaryStats handles freed and repacked`() {
        val output = """
            [0:01] 100.00%  5 / 5 snapshots
            to repack:         123 blobs / 45.678 MiB
            this removes:      45 blobs / 12.345 MiB
            to delete:         67 blobs / 23.456 MiB
            total prune:       112 blobs / 35.801 MiB
            remaining:         890 blobs / 234.567 MiB
            done
        """.trimIndent()

        val stats = PruneOutputParser.parseSummaryStats(output)!!
        assertEquals(123, stats.repackBlobs)
        assertEquals("45.678 MiB", stats.repackSize)
        assertEquals(112, stats.removedBlobs)
        assertEquals("35.801 MiB", stats.removedSize)
        assertEquals(890, stats.remainingBlobs)
        assertEquals("234.567 MiB", stats.remainingSize)
        assertTrue(stats.hasFreedData)
        assertTrue(stats.hasRepackedData)
    }

    @Test
    fun `parseSummaryStats handles freed only`() {
        val output = """
            to repack:         0 blobs / 0 B
            total prune:       42 blobs / 10.500 MiB
            remaining:         500 blobs / 120.000 MiB
        """.trimIndent()

        val stats = PruneOutputParser.parseSummaryStats(output)!!
        assertTrue(stats.hasFreedData)
        assertFalse(stats.hasRepackedData)
    }

    @Test
    fun `parseSummaryStats handles repacked only`() {
        val output = """
            to repack:         15 blobs / 3.250 MiB
            total prune:       0 blobs / 0 B
            remaining:         500 blobs / 120.000 MiB
        """.trimIndent()

        val stats = PruneOutputParser.parseSummaryStats(output)!!
        assertFalse(stats.hasFreedData)
        assertTrue(stats.hasRepackedData)
    }

    @Test
    fun `parseSummaryStats detects non-zero freed space when only unreferenced packs are removed`() {
        val output = """
            to repack:         0 blobs / 0 B
            this removes:      0 blobs / 0 B
            to delete:         0 blobs / 5.200 MiB
            total prune:       0 blobs / 5.200 MiB
            remaining:         500 blobs / 120.000 MiB
        """.trimIndent()

        val stats = PruneOutputParser.parseSummaryStats(output)!!
        assertEquals(0, stats.removedBlobs)
        assertEquals("5.200 MiB", stats.removedSize)
        assertTrue(stats.hasFreedData)
        assertFalse(stats.hasRepackedData)
    }

    @Test
    fun `parseSummaryStats returns null for empty or unrecognized output`() {
        assertNull(PruneOutputParser.parseSummaryStats(""))
        assertNull(PruneOutputParser.parseSummaryStats("loading indexes...\ndone\n"))
    }
}
