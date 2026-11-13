package io.github.hddq.restoid.data

import android.content.ContextWrapper
import io.github.hddq.restoid.model.RestoidMetadata
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ResticSnapshotParsingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeContext : ContextWrapper(null)

    private fun createRepository(): ResticRepository {
        val context = FakeContext()
        val binaryManager = ResticBinaryManager(
            context = context,
            nativeLibDirProvider = { java.io.File(".") },
            stringResolver = { "unknown" }
        )
        val executor = ResticExecutor(context = context, binaryManager = binaryManager)
        return ResticRepository(context = context, executor = executor)
    }

    @Test
    fun parseSnapshotsJson_withHostname_populatesHostnameCorrectly() {
        val repository = createRepository()
        val jsonOutput = """
            [
              {
                "time": "2026-11-10T12:00:00.000000000Z",
                "parent": "12345678",
                "tree": "abcdef12",
                "paths": ["/data/app/org.fdroid.fdroid-1"],
                "hostname": "Pixel-8-Pro",
                "username": "root",
                "id": "a1b2c3d4e5f67890",
                "short_id": "a1b2c3d4"
              }
            ]
        """.trimIndent()

        val snapshots = repository.parseSnapshotsJson(jsonOutput)

        assertEquals(1, snapshots.size)
        val snapshot = snapshots.first()
        assertEquals("a1b2c3d4e5f67890", snapshot.id)
        assertEquals("2026-11-10T12:00:00.000000000Z", snapshot.time)
        assertEquals(listOf("/data/app/org.fdroid.fdroid-1"), snapshot.paths)
        assertEquals("Pixel-8-Pro", snapshot.hostname)
    }

    @Test
    fun parseSnapshotsJson_withoutHostname_defaultsToNull() {
        val repository = createRepository()
        val jsonOutput = """
            [
              {
                "time": "2026-10-01T08:30:00.000000000Z",
                "paths": ["/storage/emulated/0/DCIM"],
                "id": "fedcba9876543210"
              }
            ]
        """.trimIndent()

        val snapshots = repository.parseSnapshotsJson(jsonOutput)

        assertEquals(1, snapshots.size)
        val snapshot = snapshots.first()
        assertEquals("fedcba9876543210", snapshot.id)
        assertNull(snapshot.hostname)
    }

    @Test
    fun snapshotInfo_constructorDefaultHostname_isNull() {
        val snapshot = SnapshotInfo(
            id = "test_id",
            time = "test_time",
            paths = emptyList(),
            tags = emptyList()
        )
        assertNull(snapshot.hostname)
    }

    @Test
    fun parseSnapshotsJson_withQuotesAndBackslashesInHostname_decodesAccurately() {
        val repository = createRepository()
        val jsonOutput = """
            [
              {
                "id": "12345678abcdef",
                "time": "2026-11-10T12:00:00Z",
                "paths": ["/data"],
                "hostname": "My \"Pixel\" with \\backslash"
              }
            ]
        """.trimIndent()

        val snapshots = repository.parseSnapshotsJson(jsonOutput)

        assertEquals(1, snapshots.size)
        assertEquals("My \"Pixel\" with \\backslash", snapshots.first().hostname)
    }

    @Test
    fun parseSnapshotObject_withEscapedQuotesAndBackslashes_decodesAccurately() {
        val repository = createRepository()
        val jsonObj = """{"id":"abc123","time":"2026-11-10T12:00:00Z","hostname":"Device with \"quotes\" and \\slash and \u0041"}"""

        val snapshot = repository.parseSnapshotObject(jsonObj)

        assertNotNull(snapshot)
        assertEquals("Device with \"quotes\" and \\slash and A", snapshot?.hostname)
    }

    @Test
    fun parseSnapshotsJson_ndjsonWithEscapes_decodesAccurately() {
        val repository = createRepository()
        val ndjsonOutput = """
            {"id":"snap1","time":"2026-11-10T10:00:00Z","hostname":"Host \"One\""}
            {"id":"snap2","time":"2026-11-10T11:00:00Z","hostname":"Host\\Two"}
        """.trimIndent()

        val snapshots = repository.parseSnapshotsJson(ndjsonOutput)

        assertEquals(2, snapshots.size)
        assertEquals("Host \"One\"", snapshots[0].hostname)
        assertEquals("Host\\Two", snapshots[1].hostname)
    }

    @Test
    fun parseSnapshotsJsonStrict_rejectsMalformedAndIncompleteOutput() {
        val repository = createRepository()

        assertTrue(repository.parseSnapshotsJsonStrict("").isFailure)
        assertTrue(repository.parseSnapshotsJsonStrict("Fatal: unable to open repo").isFailure)
        assertTrue(repository.parseSnapshotsJsonStrict("""[{"id":"a1b2c3d4"""").isFailure)
        assertTrue(repository.parseSnapshotsJsonStrict("""[{"time":"2026-11-10T12:00:00Z"}]""").isFailure)
        assertTrue(
            repository.parseSnapshotsJsonStrict(
                """[{"id":"a1b2c3d4"},{"time":"2026-11-10T12:00:00Z"}]"""
            ).isFailure
        )
    }

    @Test
    fun parseSnapshotsJsonStrict_acceptsValidEmptyArray() {
        val repository = createRepository()

        val result = repository.parseSnapshotsJsonStrict("[]")

        assertTrue(result.isSuccess)
        assertEquals(emptyList<SnapshotInfo>(), result.getOrNull())
    }

    @Test
    fun parseSnapshotsJsonStrict_malformedOutputPreventsMetadataDeletionWhileEmptyArrayAllowsIt() = runTest {
        val repository = createRepository()
        val metadataRoot = tempFolder.newFolder("metadata")
        val metadataRepository = MetadataRepository(metadataRoot)
        val repoId = "6102bcf8"
        val snapshotId = "6fb005a58bd12d7c92ffa63efc0d0747c3db013820203aa6480a23dc11111111"
        val sampleMetadata = RestoidMetadata(apps = emptyMap(), customDirectories = emptyMap())
        metadataRepository.saveMetadataForSnapshot(repoId, snapshotId, sampleMetadata)
        val metadataFile = File(File(metadataRoot, repoId), "$snapshotId.json")
        assertTrue(metadataFile.exists())

        // Malformed output must fail strict parsing and skip cleanup.
        val malformedIds = repository.parseSnapshotsJsonStrict("""[{"id":"$snapshotId"""").getOrNull()?.map { it.id }?.toSet()
        assertNull(malformedIds)
        assertTrue(metadataFile.exists())

        // Valid empty array "[]" succeeds and allows cleanup.
        val emptyIds = repository.parseSnapshotsJsonStrict("[]").getOrNull()?.map { it.id }?.toSet()
        assertNotNull(emptyIds)
        metadataRepository.pruneOrphanedMetadata(repoId, emptyIds!!)
        assertFalse(metadataFile.exists())
    }
}
