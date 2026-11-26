package io.github.hddq.restoid.ui.home

import io.github.hddq.restoid.data.SnapshotInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTagFilteringTest {

    private fun snapshot(id: String, tags: List<String>): SnapshotWithMetadata {
        return SnapshotWithMetadata(
            snapshotInfo = SnapshotInfo(
                id = id,
                time = "2026-01-01T00:00:00Z",
                paths = emptyList(),
                tags = tags
            ),
            metadata = null
        )
    }

    @Test
    fun extractAvailableFilterTags_excludesReservedDeduplicatesSortsCaseInsensitivelyAndIncludesExternal() {
        val snapshots = listOf(
            snapshot("s1", listOf("restoid", "backup", "metadata", "Work", "daily")),
            snapshot("s2", listOf("restoid", "backup", "daily", "alpha", "untracked-cli")),
            snapshot("s3", listOf("RESTOID", "BACKUP", "  beta  "))
        )

        val result = extractAvailableFilterTags(snapshots)

        assertEquals(listOf("alpha", "beta", "daily", "untracked-cli", "Work"), result)
    }

    @Test
    fun filterSnapshotsByTags_emptySelectionReturnsAll() {
        val snapshots = listOf(
            snapshot("s1", listOf("restoid", "backup", "daily")),
            snapshot("s2", listOf("restoid", "backup"))
        )

        val result = filterSnapshotsByTags(snapshots, emptySet())

        assertEquals(listOf("s1", "s2"), result.map { it.snapshotInfo.id })
    }

    @Test
    fun filterSnapshotsByTags_singleTagFiltersMatchingSnapshots() {
        val snapshots = listOf(
            snapshot("s1", listOf("restoid", "backup", "daily")),
            snapshot("s2", listOf("restoid", "backup", "weekly")),
            snapshot("s3", listOf("restoid", "backup", "daily", "work"))
        )

        val result = filterSnapshotsByTags(snapshots, setOf("daily"))

        assertEquals(listOf("s1", "s3"), result.map { it.snapshotInfo.id })
    }

    @Test
    fun filterSnapshotsByTags_multipleTagsUsesAndSemantics() {
        val snapshots = listOf(
            snapshot("s1", listOf("restoid", "backup", "daily")),
            snapshot("s2", listOf("restoid", "backup", "work")),
            snapshot("s3", listOf("restoid", "backup", "daily", "work")),
            snapshot("s4", listOf("restoid", "backup", "daily", "work", "extra"))
        )

        val result = filterSnapshotsByTags(snapshots, setOf("daily", "work"))

        assertEquals(listOf("s3", "s4"), result.map { it.snapshotInfo.id })
    }

    @Test
    fun filterSnapshotsByTags_nonMatchingCombinationReturnsEmpty() {
        val snapshots = listOf(
            snapshot("s1", listOf("restoid", "backup", "daily")),
            snapshot("s2", listOf("restoid", "backup", "work"))
        )

        val result = filterSnapshotsByTags(snapshots, setOf("daily", "work"))

        assertEquals(emptyList<SnapshotWithMetadata>(), result)
    }
}
