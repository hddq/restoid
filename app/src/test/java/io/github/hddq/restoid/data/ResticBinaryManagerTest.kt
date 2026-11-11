package io.github.hddq.restoid.data

import android.content.Context
import kotlinx.coroutines.async
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ResticBinaryManagerTest {

    private class FakeContext : android.content.ContextWrapper(null)

    @Test
    fun binaryPresentAndExecutable_isInstalledImmediatelySynchronously() {
        val tempDir = Files.createTempDirectory("restoid_test_native_libs").toFile()
        try {
            val resticBin = File(tempDir, "librestic.so")
            resticBin.writeText("#!/bin/sh\necho restic 0.19.1")
            resticBin.setExecutable(true)

            val manager = ResticBinaryManager(
                context = FakeContext(),
                nativeLibDirProvider = { tempDir },
                stringResolver = { "bundled_not_found" }
            )

            assertTrue(manager.isInstalled)
            assertNotNull(manager.getBinaryPath())
            assertEquals(resticBin.absolutePath, manager.getBinaryPath())
            assertTrue(manager.resticState.value is ResticState.Installed)

            val installedState = manager.resticState.value as ResticState.Installed
            assertEquals(resticBin.absolutePath, installedState.path)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun binaryMissing_isNotInstalledAndStateIsError() {
        val tempDir = Files.createTempDirectory("restoid_test_native_libs_empty").toFile()
        try {
            val manager = ResticBinaryManager(
                context = FakeContext(),
                nativeLibDirProvider = { tempDir },
                stringResolver = { "bundled_not_found" }
            )

            assertFalse(manager.isInstalled)
            assertNull(manager.getBinaryPath())
            assertTrue(manager.resticState.value is ResticState.Error)
            assertEquals("bundled_not_found", (manager.resticState.value as ResticState.Error).message)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun sshAskpass_resolvedWhenPresent() {
        val tempDir = Files.createTempDirectory("restoid_test_native_libs_askpass").toFile()
        try {
            val askpassBin = File(tempDir, "libssh_askpass.so")
            askpassBin.writeText("#!/bin/sh\n")
            askpassBin.setExecutable(true)

            val manager = ResticBinaryManager(
                context = FakeContext(),
                nativeLibDirProvider = { tempDir },
                stringResolver = { "not_found" }
            )

            assertNotNull(manager.getSshAskpassPath())
            assertEquals(askpassBin.absolutePath, manager.getSshAskpassPath())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun checkResticStatus_whenAlreadyInstalledWithVersion_memoizesWithoutCallingShell() = kotlinx.coroutines.test.runTest {
        val tempDir = Files.createTempDirectory("restoid_test_memoize").toFile()
        try {
            val resticBin = File(tempDir, "librestic.so")
            resticBin.writeText("#!/bin/sh\n")
            resticBin.setExecutable(true)

            val manager = ResticBinaryManager(
                context = FakeContext(),
                nativeLibDirProvider = { tempDir },
                stringResolver = { "error" }
            )

            // Simulate that version was already discovered
            val field = ResticBinaryManager::class.java.getDeclaredField("_resticState")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val stateFlow = field.get(manager) as kotlinx.coroutines.flow.MutableStateFlow<ResticState>
            stateFlow.value = ResticState.Installed(resticBin.absolutePath, "0.19.1", "restic 0.19.1 compiled...")

            // Calling checkResticStatus should return immediately without executing shell
            manager.checkResticStatus()

            assertEquals("restic 0.19.1 compiled...", (manager.resticState.value as ResticState.Installed).fullVersionOutput)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun checkResticStatus_concurrentCalls_executeCommandOnlyOnce() = kotlinx.coroutines.test.runTest {
        val tempDir = Files.createTempDirectory("restoid_test_concurrent").toFile()
        try {
            val resticBin = File(tempDir, "librestic.so")
            resticBin.writeText("#!/bin/sh\n")
            resticBin.setExecutable(true)

            var commandExecutions = 0
            val manager = ResticBinaryManager(
                context = FakeContext(),
                nativeLibDirProvider = { tempDir },
                stringResolver = { "error" },
                versionCommandRunner = {
                    kotlinx.coroutines.delay(50)
                    commandExecutions++
                    Pair(true, listOf("restic 0.19.1 (compiled go1.24)"))
                }
            )

            // Start two overlapping checkResticStatus calls concurrently
            val call1 = async { manager.checkResticStatus() }
            val call2 = async { manager.checkResticStatus() }
            call1.await()
            call2.await()

            // Subsequent third call
            manager.checkResticStatus()

            assertEquals(1, commandExecutions)
            assertTrue(manager.resticState.value is ResticState.Installed)
            assertEquals("0.19.1", (manager.resticState.value as ResticState.Installed).version)
            assertEquals("restic 0.19.1 (compiled go1.24)", (manager.resticState.value as ResticState.Installed).fullVersionOutput)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
