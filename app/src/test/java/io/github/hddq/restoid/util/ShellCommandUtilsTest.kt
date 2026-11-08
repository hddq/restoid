package io.github.hddq.restoid.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ShellCommandUtilsTest {

    @Test
    fun customFileRestoreCommand_formatsCorrectly() {
        val command = buildCustomFileRestoreCommand("/cache/staged/file.txt", "/sdcard/Download/file.txt")
        assertEquals(
            "rm -rf '/sdcard/Download/file.txt' && cp -a '/cache/staged/file.txt' '/sdcard/Download/file.txt'",
            command
        )
    }

    @Test
    fun customFileRestoreCommand_stripsTrailingSlashFromTarget() {
        val command = buildCustomFileRestoreCommand("/cache/staged/file.txt", "/sdcard/Download/file.txt/")
        assertEquals(
            "rm -rf '/sdcard/Download/file.txt' && cp -a '/cache/staged/file.txt' '/sdcard/Download/file.txt'",
            command
        )
    }

    @Test
    fun customDirectoryRestoreCommand_formatsCorrectly() {
        val command = buildCustomDirectoryRestoreCommand("/cache/staged/dir", "/sdcard/Download/dir")
        assertTrue(command.contains("rm -rf '/sdcard/Download/dir' && mkdir -p '/sdcard/Download/dir'"))
        assertTrue(command.contains("cp -a '/cache/staged/dir/.' '/sdcard/Download/dir/'"))
    }

    @Test
    fun customFileRestore_executesSuccessfullyUnderShell() {
        val tempDir = Files.createTempDirectory("file_restore_test").toFile()
        try {
            val sourceFile = File(tempDir, "staged_file.txt").apply { writeText("new content") }

            // 1. Destination does not exist
            val destNew = File(tempDir, "dest_new.txt")
            val cmdNew = buildCustomFileRestoreCommand(sourceFile.absolutePath, destNew.absolutePath)
            val exitCodeNew = ProcessBuilder("/bin/sh", "-c", cmdNew).start().waitFor()
            assertEquals(0, exitCodeNew)
            assertEquals("new content", destNew.readText())

            // 2. Destination is existing file
            val destExisting = File(tempDir, "dest_existing.txt").apply { writeText("old content") }
            val cmdExisting = buildCustomFileRestoreCommand(sourceFile.absolutePath, destExisting.absolutePath)
            val exitCodeExisting = ProcessBuilder("/bin/sh", "-c", cmdExisting).start().waitFor()
            assertEquals(0, exitCodeExisting)
            assertEquals("new content", destExisting.readText())

            // 3. Destination is a directory (type conflict - Issue 2)
            val destDir = File(tempDir, "dest_dir").apply { mkdirs(); File(this, "old.txt").writeText("old") }
            val cmdDir = buildCustomFileRestoreCommand(sourceFile.absolutePath, destDir.absolutePath)
            val exitCodeDir = ProcessBuilder("/bin/sh", "-c", cmdDir).start().waitFor()
            assertEquals(0, exitCodeDir)
            assertTrue(destDir.isFile)
            assertEquals("new content", destDir.readText())

            // 4. Destination is a symlink (Issue 3)
            val externalFile = File(tempDir, "external.txt").apply { writeText("external content") }
            val destLink = File(tempDir, "dest_link")
            Files.createSymbolicLink(destLink.toPath(), externalFile.toPath())
            val cmdLink = buildCustomFileRestoreCommand(sourceFile.absolutePath, destLink.absolutePath)
            val exitCodeLink = ProcessBuilder("/bin/sh", "-c", cmdLink).start().waitFor()
            assertEquals(0, exitCodeLink)
            assertFalse(Files.isSymbolicLink(destLink.toPath()))
            assertEquals("new content", destLink.readText())
            assertEquals("external content", externalFile.readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun customDirectoryRestore_executesSuccessfullyUnderShell() {
        val tempDir = Files.createTempDirectory("dir_restore_test").toFile()
        try {
            val sourceDir = File(tempDir, "src").apply { mkdirs() }
            File(sourceDir, "normal.txt").writeText("snapshot normal")
            val srcSubDir = File(sourceDir, "subdir").apply { mkdirs() }
            File(srcSubDir, "nested.txt").writeText("snapshot nested")

            // Conflict 1: file in snapshot, directory at destination (Issue 2)
            val srcFileBecomesDir = File(sourceDir, "file_becomes_dir").apply { writeText("restored file") }

            // Conflict 2: directory in snapshot, file at destination (Issue 2)
            val srcDirBecomesFile = File(sourceDir, "dir_becomes_file").apply { mkdirs() }
            File(srcDirBecomesFile, "child.txt").writeText("restored child")

            // Destination setup
            val destDir = File(tempDir, "dst").apply { mkdirs() }
            File(destDir, "normal.txt").writeText("old normal")
            File(destDir, "absent_from_snapshot.txt").writeText("must be preserved")
            File(File(destDir, "subdir").apply { mkdirs() }, "nested_preserved.txt").writeText("preserve nested")

            // Create destination conflicts
            val dstFileBecomesDir = File(destDir, "file_becomes_dir").apply { mkdirs() }
            File(dstFileBecomesDir, "old_child.txt").writeText("old child")
            File(destDir, "dir_becomes_file").writeText("old file")

            // Symlink in destination (Issue 3)
            val externalDir = File(tempDir, "external_dir").apply { mkdirs() }
            File(externalDir, "external.txt").writeText("external data")
            val dstLink = File(destDir, "link_to_external")
            Files.createSymbolicLink(dstLink.toPath(), externalDir.toPath())
            File(sourceDir, "link_to_external").apply { mkdirs(); File(this, "restored.txt").writeText("restored") }

            val command = buildCustomDirectoryRestoreCommand(sourceDir.absolutePath, destDir.absolutePath)
            val exitCode = ProcessBuilder("/bin/sh", "-c", command).start().waitFor()
            assertEquals(0, exitCode)

            // Verify merged files
            assertEquals("snapshot normal", File(destDir, "normal.txt").readText())
            assertEquals("snapshot nested", File(destDir, "subdir/nested.txt").readText())
            assertEquals("must be preserved", File(destDir, "absent_from_snapshot.txt").readText())
            assertEquals("preserve nested", File(destDir, "subdir/nested_preserved.txt").readText())

            // Verify resolved type conflicts
            assertTrue(File(destDir, "file_becomes_dir").isFile)
            assertEquals("restored file", File(destDir, "file_becomes_dir").readText())
            assertTrue(File(destDir, "dir_becomes_file").isDirectory)
            assertEquals("restored child", File(destDir, "dir_becomes_file/child.txt").readText())

            // Verify symlink was replaced safely without affecting external target
            assertFalse(Files.isSymbolicLink(dstLink.toPath()))
            assertTrue(dstLink.isDirectory)
            assertEquals("restored", File(dstLink, "restored.txt").readText())
            assertEquals("external data", File(externalDir, "external.txt").readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun customDirectoryRestore_replacesDestinationRootLink() {
        val tempDir = Files.createTempDirectory("root_link_test").toFile()
        try {
            val sourceDir = File(tempDir, "src").apply { mkdirs() }
            File(sourceDir, "restored.txt").writeText("hello")

            val externalDir = File(tempDir, "external_target").apply { mkdirs() }
            File(externalDir, "external_file.txt").writeText("original external")

            val destLink = File(tempDir, "dest_link")
            Files.createSymbolicLink(destLink.toPath(), externalDir.toPath())

            val command = buildCustomDirectoryRestoreCommand(sourceDir.absolutePath, destLink.absolutePath)
            val exitCode = ProcessBuilder("/bin/sh", "-c", command).start().waitFor()
            assertEquals(0, exitCode)

            // destLink should now be a directory, not a symbolic link
            assertFalse(Files.isSymbolicLink(destLink.toPath()))
            assertTrue(destLink.isDirectory)
            assertEquals("hello", File(destLink, "restored.txt").readText())

            // externalDir must be unharmed
            assertEquals("original external", File(externalDir, "external_file.txt").readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
