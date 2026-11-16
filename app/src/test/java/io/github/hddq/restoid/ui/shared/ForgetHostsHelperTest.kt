package io.github.hddq.restoid.ui.shared

import io.github.hddq.restoid.data.SnapshotInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgetHostsHelperTest {

    private fun snapshot(
        id: String,
        hostname: String?,
        tags: List<String> = listOf("restoid", "backup")
    ) = SnapshotInfo(
        id = id,
        time = "2026-11-15T08:00:00Z",
        paths = listOf("/data"),
        tags = tags,
        hostname = hostname
    )

    @Test
    fun normalizeForgetHosts_trimsBlanksAndDeduplicatesPreservingOrder() {
        val normalized = normalizeForgetHosts(listOf("  Pixel-8  ", "", "   ", "Tablet", "Pixel-8", "NAS"))

        assertEquals(listOf("Pixel-8", "Tablet", "NAS"), normalized)
    }

    @Test
    fun toggleForgetHostSelection_addsAndRemovesTrimmedHosts() {
        val afterAdd = toggleForgetHostSelection(listOf("Pixel-8"), "  Tablet ")
        assertEquals(listOf("Pixel-8", "Tablet"), afterAdd)

        val afterRemove = toggleForgetHostSelection(afterAdd, "Pixel-8")
        assertEquals(listOf("Tablet"), afterRemove)

        val afterBlank = toggleForgetHostSelection(afterRemove, "   ")
        assertEquals(listOf("Tablet"), afterBlank)
    }

    @Test
    fun buildAvailableForgetHosts_currentDeviceFirstWhenSnapshotsNotLoaded() {
        val hosts = buildAvailableForgetHosts(
            currentDeviceHostname = "Pixel-8",
            snapshots = null,
            selectedHosts = listOf("Old-Phone"),
            manualHosts = listOf("Work-Tablet")
        )

        assertEquals(3, hosts.size)
        assertEquals("Pixel-8", hosts[0].hostname)
        assertTrue(hosts[0].isCurrentDevice)
        assertNull(hosts[0].snapshotCount)
        assertFalse(hosts[0].isRemovable)

        assertEquals("Old-Phone", hosts[1].hostname)
        assertFalse(hosts[1].isCurrentDevice)
        assertNull(hosts[1].snapshotCount)
        assertTrue(hosts[1].isRemovable)

        assertEquals("Work-Tablet", hosts[2].hostname)
        assertFalse(hosts[2].isCurrentDevice)
        assertNull(hosts[2].snapshotCount)
        assertTrue(hosts[2].isRemovable)
    }

    @Test
    fun buildAvailableForgetHosts_filtersBackupSnapshotsAndCountsPerHost() {
        val snapshots = listOf(
            snapshot("s1", "zeta-phone"),
            snapshot("s2", "Alpha-Tablet"),
            snapshot("s3", "zeta-phone"),
            snapshot("s4", "metadata-only-host", tags = listOf("restoid", "metadata")),
            snapshot("s5", "untagged-host", tags = emptyList()),
            snapshot("s6", "   "),
            snapshot("s7", null)
        )

        val hosts = buildAvailableForgetHosts(
            currentDeviceHostname = "Pixel-8",
            snapshots = snapshots,
            selectedHosts = listOf("Alpha-Tablet", "Missing-Host")
        )

        assertEquals(4, hosts.size)

        // Current device is always first with 0 snapshots when none match it
        assertEquals("Pixel-8", hosts[0].hostname)
        assertTrue(hosts[0].isCurrentDevice)
        assertEquals(0, hosts[0].snapshotCount)
        assertFalse(hosts[0].isRemovable)

        // Repo hosts sorted case-insensitively
        assertEquals("Alpha-Tablet", hosts[1].hostname)
        assertFalse(hosts[1].isCurrentDevice)
        assertEquals(1, hosts[1].snapshotCount)
        assertFalse(hosts[1].isRemovable)

        assertEquals("zeta-phone", hosts[2].hostname)
        assertFalse(hosts[2].isCurrentDevice)
        assertEquals(2, hosts[2].snapshotCount)
        assertFalse(hosts[2].isRemovable)

        // Saved host not present in repo is marked removable with count 0
        assertEquals("Missing-Host", hosts[3].hostname)
        assertFalse(hosts[3].isCurrentDevice)
        assertEquals(0, hosts[3].snapshotCount)
        assertTrue(hosts[3].isRemovable)
    }

    @Test
    fun toggleAllForgetHostsSelection_selectsAllWhenPartiallySelected() {
        val result = toggleAllForgetHostsSelection(
            availableHostnames = listOf("Pixel-8", "Tablet", "  Pixel-8  ", " "),
            selectedHosts = listOf("Pixel-8"),
            forgetAllHosts = false
        )

        assertEquals(listOf("Pixel-8", "Tablet"), result)
    }

    @Test
    fun toggleAllForgetHostsSelection_clearsWhenAllAvailableHostsSelected() {
        val result = toggleAllForgetHostsSelection(
            availableHostnames = listOf("Pixel-8", "Tablet"),
            selectedHosts = listOf("Tablet", "Pixel-8"),
            forgetAllHosts = false
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun toggleAllForgetHostsSelection_clearsWhenLegacyForgetAllHostsEnabled() {
        val result = toggleAllForgetHostsSelection(
            availableHostnames = listOf("Pixel-8", "Tablet"),
            selectedHosts = emptyList(),
            forgetAllHosts = true
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun normalizeCustomForgetHostname_preservesInteriorWhitespaceAndStripsControlCharacters() {
        val normalized = normalizeCustomForgetHostname("  Work  Tablet\u0000\n ")

        assertEquals("Work  Tablet", normalized)
    }
}
