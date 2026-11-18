package io.github.hddq.restoid.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hddq.restoid.ui.theme.RestoidTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnapshotHeaderLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun singleLineAndTwoLineAroundCutoff_withHostname() {
        assertCutoffBehavior(hostname = "OnePlus8T")
    }

    @Test
    fun singleLineAndTwoLineAroundCutoff_withoutHostname() {
        assertCutoffBehavior(hostname = null)
    }

    @Test
    fun largeFont_movesTimestampBelowIdAndClearsChevron() {
        val snapshotId = "a1b2c3d4e5f6"
        val timeText = "10 months ago (2025-01-18)"

        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            RestoidTheme(dynamicColor = false) {
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = baseDensity.density,
                        fontScale = 2.0f
                    )
                ) {
                    Box(modifier = Modifier.width(348.dp)) {
                        SnapshotHeader(
                            snapshotId = snapshotId,
                            hostname = "OnePlus8T",
                            timeText = timeText
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        val idRowBounds = composeTestRule
            .onNodeWithTag(SNAPSHOT_HEADER_ID_ROW_TAG)
            .getUnclippedBoundsInRoot()
        val timeBounds = composeTestRule
            .onNodeWithText(timeText)
            .getUnclippedBoundsInRoot()
        val chevronBounds = composeTestRule
            .onNodeWithTag(SNAPSHOT_HEADER_CHEVRON_TAG)
            .getUnclippedBoundsInRoot()

        assertTrue(
            "Expected timestamp top (${timeBounds.top}) >= idRow bottom (${idRowBounds.bottom})",
            timeBounds.top >= idRowBounds.bottom
        )
        assertTrue(
            "Expected timestamp left (${timeBounds.left}) aligned with idRow left (${idRowBounds.left})",
            timeBounds.left <= idRowBounds.left + 1.dp
        )
        assertTrue(
            "Expected timestamp right (${timeBounds.right}) clear of chevron left (${chevronBounds.left})",
            timeBounds.right <= chevronBounds.left - 3.dp
        )
        assertTrue(
            "Expected idRow right (${idRowBounds.right}) clear of chevron left (${chevronBounds.left})",
            idRowBounds.right <= chevronBounds.left - 3.dp
        )
    }

    private fun assertCutoffBehavior(hostname: String?) {
        val snapshotId = "a1b2c3d4e5f6"
        val timeText = "10 months ago (2025-01-18)"
        var containerWidth by mutableStateOf(600.dp)

        composeTestRule.setContent {
            RestoidTheme(dynamicColor = false) {
                Box(modifier = Modifier.width(containerWidth)) {
                    SnapshotHeader(
                        snapshotId = snapshotId,
                        hostname = hostname,
                        timeText = timeText
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        val wideIdRowBounds = composeTestRule
            .onNodeWithTag(SNAPSHOT_HEADER_ID_ROW_TAG)
            .getUnclippedBoundsInRoot()
        val wideTimeBounds = composeTestRule
            .onNodeWithText(timeText)
            .getUnclippedBoundsInRoot()
        val wideChevronBounds = composeTestRule
            .onNodeWithTag(SNAPSHOT_HEADER_CHEVRON_TAG)
            .getUnclippedBoundsInRoot()

        val cutoffWidth: Dp =
            wideIdRowBounds.width + 8.dp + wideTimeBounds.width + 4.dp + wideChevronBounds.width

        // Just above cutoff: timestamp shares line with ID row, right-aligned before chevron.
        containerWidth = cutoffWidth + 2.dp
        composeTestRule.waitForIdle()

        val aboveIdRowBounds = composeTestRule
            .onNodeWithTag(SNAPSHOT_HEADER_ID_ROW_TAG)
            .getUnclippedBoundsInRoot()
        val aboveTimeBounds = composeTestRule
            .onNodeWithText(timeText)
            .getUnclippedBoundsInRoot()
        val aboveChevronBounds = composeTestRule
            .onNodeWithTag(SNAPSHOT_HEADER_CHEVRON_TAG)
            .getUnclippedBoundsInRoot()

        assertTrue(
            "Expected single-line header above cutoff: time top=${aboveTimeBounds.top}, idRow bottom=${aboveIdRowBounds.bottom}",
            aboveTimeBounds.top < aboveIdRowBounds.bottom && aboveTimeBounds.bottom > aboveIdRowBounds.top
        )
        assertTrue(
            "Expected >= 8dp gap between idRow right (${aboveIdRowBounds.right}) and time left (${aboveTimeBounds.left})",
            aboveTimeBounds.left >= aboveIdRowBounds.right + 7.dp
        )
        assertTrue(
            "Expected timestamp right (${aboveTimeBounds.right}) clear of chevron left (${aboveChevronBounds.left})",
            aboveTimeBounds.right <= aboveChevronBounds.left - 3.dp
        )

        // Just below cutoff: timestamp moves below ID row, left-aligned, clear of chevron.
        containerWidth = cutoffWidth - 2.dp
        composeTestRule.waitForIdle()

        val belowIdRowBounds = composeTestRule
            .onNodeWithTag(SNAPSHOT_HEADER_ID_ROW_TAG)
            .getUnclippedBoundsInRoot()
        val belowTimeBounds = composeTestRule
            .onNodeWithText(timeText)
            .getUnclippedBoundsInRoot()
        val belowChevronBounds = composeTestRule
            .onNodeWithTag(SNAPSHOT_HEADER_CHEVRON_TAG)
            .getUnclippedBoundsInRoot()

        assertTrue(
            "Expected two-line header below cutoff: time top=${belowTimeBounds.top}, idRow bottom=${belowIdRowBounds.bottom}",
            belowTimeBounds.top >= belowIdRowBounds.bottom
        )
        assertTrue(
            "Expected timestamp left (${belowTimeBounds.left}) aligned with idRow left (${belowIdRowBounds.left})",
            belowTimeBounds.left <= belowIdRowBounds.left + 1.dp
        )
        assertTrue(
            "Expected timestamp right (${belowTimeBounds.right}) clear of chevron left (${belowChevronBounds.left})",
            belowTimeBounds.right <= belowChevronBounds.left - 3.dp
        )
    }
}
