package io.github.hddq.restoid.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotHeaderPlacementTest {

    @Test
    fun `places timestamp on right on single line when width is at or above cutoff with hostname`() {
        val idRowWidth = 400
        val idRowHeight = 64
        val timeWidth = 450
        val timeHeight = 50
        val chevronWidth = 48
        val chevronHeight = 48
        val minHorizontalGapPx = 24
        val chevronGapPx = 12
        val lineGapPx = 6
        val cutoffWidth = idRowWidth + minHorizontalGapPx + timeWidth + chevronGapPx + chevronWidth

        val atCutoff = computeSnapshotHeaderPlacement(
            maxWidth = cutoffWidth,
            minHeight = 0,
            hasBoundedWidth = true,
            idRowWidth = idRowWidth,
            idRowHeight = idRowHeight,
            singleLineTimeWidth = timeWidth,
            timeWidth = timeWidth,
            timeHeight = timeHeight,
            chevronWidth = chevronWidth,
            chevronHeight = chevronHeight,
            minHorizontalGapPx = minHorizontalGapPx,
            chevronGapPx = chevronGapPx,
            lineGapPx = lineGapPx
        )

        assertTrue(atCutoff.placeOnSingleLine)
        assertEquals(idRowWidth + minHorizontalGapPx, atCutoff.timeX)
        assertTrue(atCutoff.timeX + timeWidth <= atCutoff.chevronX - chevronGapPx)

        val aboveCutoff = computeSnapshotHeaderPlacement(
            maxWidth = cutoffWidth + 100,
            minHeight = 0,
            hasBoundedWidth = true,
            idRowWidth = idRowWidth,
            idRowHeight = idRowHeight,
            singleLineTimeWidth = timeWidth,
            timeWidth = timeWidth,
            timeHeight = timeHeight,
            chevronWidth = chevronWidth,
            chevronHeight = chevronHeight,
            minHorizontalGapPx = minHorizontalGapPx,
            chevronGapPx = chevronGapPx,
            lineGapPx = lineGapPx
        )

        assertTrue(aboveCutoff.placeOnSingleLine)
        assertEquals(cutoffWidth + 100 - chevronWidth - chevronGapPx - timeWidth, aboveCutoff.timeX)
        assertTrue(aboveCutoff.timeX >= idRowWidth + minHorizontalGapPx)
        assertEquals(cutoffWidth + 100 - chevronWidth, aboveCutoff.chevronX)
    }

    @Test
    fun `moves timestamp below id row when width is 1px below cutoff with hostname`() {
        val idRowWidth = 400
        val idRowHeight = 64
        val timeWidth = 450
        val timeHeight = 50
        val chevronWidth = 48
        val chevronHeight = 48
        val minHorizontalGapPx = 24
        val chevronGapPx = 12
        val lineGapPx = 6
        val cutoffWidth = idRowWidth + minHorizontalGapPx + timeWidth + chevronGapPx + chevronWidth

        val belowCutoff = computeSnapshotHeaderPlacement(
            maxWidth = cutoffWidth - 1,
            minHeight = 0,
            hasBoundedWidth = true,
            idRowWidth = idRowWidth,
            idRowHeight = idRowHeight,
            singleLineTimeWidth = timeWidth,
            timeWidth = timeWidth,
            timeHeight = timeHeight,
            chevronWidth = chevronWidth,
            chevronHeight = chevronHeight,
            minHorizontalGapPx = minHorizontalGapPx,
            chevronGapPx = chevronGapPx,
            lineGapPx = lineGapPx
        )

        assertFalse(belowCutoff.placeOnSingleLine)
        assertEquals(0, belowCutoff.idRowX)
        assertEquals(0, belowCutoff.timeX)
        assertEquals(idRowHeight + lineGapPx, belowCutoff.timeY)
        assertEquals(idRowHeight + lineGapPx + timeHeight, belowCutoff.layoutHeight)
        assertEquals(cutoffWidth - 1 - chevronWidth, belowCutoff.chevronX)
        assertTrue(belowCutoff.timeX + timeWidth <= belowCutoff.chevronX - chevronGapPx)
    }

    @Test
    fun `handles cutoff without hostname`() {
        val idRowWidth = 220
        val idRowHeight = 64
        val timeWidth = 450
        val timeHeight = 50
        val chevronWidth = 48
        val chevronHeight = 48
        val minHorizontalGapPx = 24
        val chevronGapPx = 12
        val lineGapPx = 6
        val cutoffWidth = idRowWidth + minHorizontalGapPx + timeWidth + chevronGapPx + chevronWidth

        val aboveCutoff = computeSnapshotHeaderPlacement(
            maxWidth = cutoffWidth + 1,
            minHeight = 0,
            hasBoundedWidth = true,
            idRowWidth = idRowWidth,
            idRowHeight = idRowHeight,
            singleLineTimeWidth = timeWidth,
            timeWidth = timeWidth,
            timeHeight = timeHeight,
            chevronWidth = chevronWidth,
            chevronHeight = chevronHeight,
            minHorizontalGapPx = minHorizontalGapPx,
            chevronGapPx = chevronGapPx,
            lineGapPx = lineGapPx
        )
        assertTrue(aboveCutoff.placeOnSingleLine)

        val belowCutoff = computeSnapshotHeaderPlacement(
            maxWidth = cutoffWidth - 1,
            minHeight = 0,
            hasBoundedWidth = true,
            idRowWidth = idRowWidth,
            idRowHeight = idRowHeight,
            singleLineTimeWidth = timeWidth,
            timeWidth = timeWidth,
            timeHeight = timeHeight,
            chevronWidth = chevronWidth,
            chevronHeight = chevronHeight,
            minHorizontalGapPx = minHorizontalGapPx,
            chevronGapPx = chevronGapPx,
            lineGapPx = lineGapPx
        )
        assertFalse(belowCutoff.placeOnSingleLine)
        assertEquals(idRowHeight + lineGapPx, belowCutoff.timeY)
    }

    @Test
    fun `falls back to two lines when timestamp wraps onto multiple lines even if wrapped width is small`() {
        val idRowWidth = 220
        val idRowHeight = 64
        val singleLineTimeWidth = 650
        val wrappedTimeWidth = 340
        val wrappedTimeHeight = 100
        val chevronWidth = 48
        val chevronHeight = 48
        val minHorizontalGapPx = 24
        val chevronGapPx = 12
        val lineGapPx = 6
        val maxWidth = 700

        val placement = computeSnapshotHeaderPlacement(
            maxWidth = maxWidth,
            minHeight = 0,
            hasBoundedWidth = true,
            idRowWidth = idRowWidth,
            idRowHeight = idRowHeight,
            singleLineTimeWidth = singleLineTimeWidth,
            timeWidth = wrappedTimeWidth,
            timeHeight = wrappedTimeHeight,
            chevronWidth = chevronWidth,
            chevronHeight = chevronHeight,
            minHorizontalGapPx = minHorizontalGapPx,
            chevronGapPx = chevronGapPx,
            lineGapPx = lineGapPx
        )

        assertFalse(placement.placeOnSingleLine)
        assertEquals(0, placement.timeX)
        assertEquals(idRowHeight + lineGapPx, placement.timeY)
        assertTrue(placement.timeX + wrappedTimeWidth <= placement.chevronX - chevronGapPx)
    }
}
