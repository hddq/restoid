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
            "forget --keep-last 5 --keep-daily 7 --keep-weekly 4 --keep-monthly 6 --group-by host,tags --tag 'restoid' --tag 'backup'",
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
            "forget --keep-last 3 --host 'Pixel 8' --host 'Bob'\"'\"'s Phone' --group-by host,tags --tag 'restoid' --tag 'backup'",
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
}
