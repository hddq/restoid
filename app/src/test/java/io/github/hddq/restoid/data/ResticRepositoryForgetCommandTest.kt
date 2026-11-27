package io.github.hddq.restoid.data

import android.content.ContextWrapper
import io.github.hddq.restoid.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ResticRepositoryForgetCommandTest {

    private class FakeContext : ContextWrapper(null)

    private fun createRepository(): ResticRepository {
        val context = FakeContext()
        val binaryManager = ResticBinaryManager(
            context = context,
            nativeLibDirProvider = { File(".") },
            stringResolver = { "unknown" }
        )
        val executor = ResticExecutor(context = context, binaryManager = binaryManager)
        return ResticRepository(
            context = context,
            executor = executor,
            stringResolver = { resId ->
                when (resId) {
                    R.string.restic_failure_no_forget_policy -> "no_forget_policy"
                    R.string.restic_failure_no_forget_hosts -> "no_forget_hosts"
                    else -> "error_$resId"
                }
            }
        )
    }

    @Test
    fun buildForgetCommand_allHosts_omitsHostFlagsEvenIfListNonEmpty() {
        val repository = createRepository()

        val result = repository.buildForgetCommand(
            keepLast = 5,
            keepDaily = 7,
            keepWeekly = 4,
            keepMonthly = 6,
            forgetAllHosts = true,
            forgetHosts = listOf("Pixel-8")
        )

        assertTrue(result.isSuccess)
        assertEquals(
            "forget --keep-last 5 --keep-daily 7 --keep-weekly 4 --keep-monthly 6 --group-by host,tags --tag 'restoid,backup'",
            result.getOrNull()
        )
    }

    @Test
    fun buildForgetCommand_selectedHosts_quotesAndDeduplicatesHosts() {
        val repository = createRepository()

        val result = repository.buildForgetCommand(
            keepLast = 3,
            keepDaily = 0,
            keepWeekly = 0,
            keepMonthly = 0,
            forgetAllHosts = false,
            forgetHosts = listOf("  Pixel 8  ", "", "Bob's Phone", "Pixel 8")
        )

        assertTrue(result.isSuccess)
        assertEquals(
            "forget --keep-last 3 --host 'Pixel 8' --host 'Bob'\"'\"'s Phone' --group-by host,tags --tag 'restoid,backup'",
            result.getOrNull()
        )
    }

    @Test
    fun buildForgetCommand_selectedTags_emitsCombinedOrTagListsAndSanitizes() {
        val repository = createRepository()

        val result = repository.buildForgetCommand(
            keepLast = 5,
            keepDaily = 0,
            keepWeekly = 0,
            keepMonthly = 0,
            forgetAllHosts = true,
            forgetHosts = emptyList(),
            forgetTags = listOf(" daily ", "restoid", "bad,tag", "daily", "Bob's Tag")
        )

        assertTrue(result.isSuccess)
        assertEquals(
            "forget --keep-last 5 --group-by host --tag 'restoid,backup,daily' --tag 'restoid,backup,Bob'\"'\"'s Tag'",
            result.getOrNull()
        )
    }

    @Test
    fun buildForgetCommand_selectedHostsWithNoValidEntries_returnsFailure() {
        val repository = createRepository()

        val result = repository.buildForgetCommand(
            keepLast = 5,
            keepDaily = 0,
            keepWeekly = 0,
            keepMonthly = 0,
            forgetAllHosts = false,
            forgetHosts = listOf("", "   ")
        )

        assertTrue(result.isFailure)
        assertEquals("no_forget_hosts", result.exceptionOrNull()?.message)
    }

    @Test
    fun buildForgetCommand_noKeepPolicy_returnsFailure() {
        val repository = createRepository()

        val result = repository.buildForgetCommand(
            keepLast = 0,
            keepDaily = 0,
            keepWeekly = 0,
            keepMonthly = 0,
            forgetAllHosts = false,
            forgetHosts = listOf("Pixel-8")
        )

        assertTrue(result.isFailure)
        assertEquals("no_forget_policy", result.exceptionOrNull()?.message)
    }

    @Test
    fun buildForgetCommand_dryRunJsonNoLock_appendsExpectedFlags() {
        val repository = createRepository()

        val result = repository.buildForgetCommand(
            keepLast = 2,
            keepDaily = 0,
            keepWeekly = 0,
            keepMonthly = 0,
            forgetAllHosts = false,
            forgetHosts = listOf("Pixel-8"),
            forgetTags = listOf("daily"),
            dryRun = true,
            jsonOutput = true,
            noLock = true
        )

        assertTrue(result.isSuccess)
        assertEquals(
            "forget --keep-last 2 --host 'Pixel-8' --group-by host --tag 'restoid,backup,daily' --dry-run --json --no-lock",
            result.getOrNull()
        )
    }

    @Test
    fun parseForgetDryRunJson_parsesGroupsKeepRemoveAndReasons() {
        val repository = createRepository()
        val sampleJson = """
            [
              {
                "host": "Pixel-8",
                "tags": ["restoid", "backup", "daily"],
                "keep": [
                  {"id": "aaaa11112222", "short_id": "aaaa1111", "time": "2026-11-26T10:00:00Z", "hostname": "Pixel-8", "tags": ["restoid", "backup", "daily"]}
                ],
                "remove": [
                  {"id": "bbbb33334444", "short_id": "bbbb3333", "time": "2026-11-25T10:00:00Z", "hostname": "Pixel-8", "tags": ["restoid", "backup", "daily"]}
                ],
                "reasons": [
                  {"snapshot": {"id": "aaaa11112222", "short_id": "aaaa1111"}, "matches": ["last snapshot", "daily snapshot"]}
                ]
              }
            ]
        """.trimIndent()

        val parsed = repository.parseForgetDryRunJson(sampleJson)
        assertTrue(parsed.isSuccess)
        val result = parsed.getOrThrow()
        assertEquals(1, result.groups.size)
        assertEquals(1, result.totalKeep)
        assertEquals(1, result.totalRemove)
        assertEquals("Pixel-8", result.groups[0].host)
        assertEquals(listOf("last snapshot", "daily snapshot"), result.groups[0].keep[0].keepReasons)
        assertEquals("bbbb3333", result.groups[0].remove[0].shortId)
    }

    @Test
    fun parseForgetDryRunJson_emptyOutput_returnsEmptyGroups() {
        val repository = createRepository()
        val parsed = repository.parseForgetDryRunJson("   ")
        assertTrue(parsed.isSuccess)
        assertEquals(0, parsed.getOrThrow().groups.size)
    }

    @Test
    fun parseForgetDryRunJson_nonArrayJson_returnsFailure() {
        val repository = createRepository()
        val parsed = repository.parseForgetDryRunJson("""{"unexpected": true}""")
        assertTrue(parsed.isFailure)
    }
}
