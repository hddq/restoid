package io.github.hddq.restoid

import io.github.hddq.restoid.util.StorageUtils
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageUtilsTest {
    @Test
    fun isFileUri_detectsScheme() {
        assertTrue(StorageUtils.isFileUri("file:///storage/emulated/0/Download"))
        assertFalse(StorageUtils.isFileUri("content://com.android.externalstorage.documents/tree/primary%3ADownload"))
        assertFalse(StorageUtils.isFileUri("/storage/emulated/0/Download"))
        assertFalse(StorageUtils.isFileUri(""))
    }

    @Test
    fun normalizeTypedPathToFileUri_acceptsRawAndFilePaths() {
        assertEquals(
            "file:///storage/emulated/0/Download",
            StorageUtils.normalizeTypedPathToFileUri("/storage/emulated/0/Download")
        )
        assertEquals(
            "file:///storage/emulated/0/Download",
            StorageUtils.normalizeTypedPathToFileUri("file:///storage/emulated/0/Download")
        )
        assertEquals(
            "file:///storage/emulated/0/Download",
            StorageUtils.normalizeTypedPathToFileUri("  /storage//emulated/0/Download/  ")
        )
    }

    @Test
    fun normalizeTypedPathToFileUri_rejectsBadInput() {
        assertNull(StorageUtils.normalizeTypedPathToFileUri(""))
        assertNull(StorageUtils.normalizeTypedPathToFileUri("   "))
        assertNull(StorageUtils.normalizeTypedPathToFileUri("content://com.android.providers.downloads.documents/tree/downloads"))
        assertNull(StorageUtils.normalizeTypedPathToFileUri("relative/path"))
    }

    @Test
    fun pathFromFileUri_roundTrips() {
        assertEquals(
            "/storage/emulated/0/Download",
            StorageUtils.pathFromFileUri("file:///storage/emulated/0/Download")
        )
        assertNull(StorageUtils.pathFromFileUri("content://com.android.externalstorage.documents/tree/primary%3ADownload"))
        assertNull(StorageUtils.pathFromFileUri("file://"))
    }

    @Test
    fun resolveCustomDirectoryKey_dedupesEquivalentPaths() {
        val a = StorageUtils.resolveCustomDirectoryKey("file:///storage/emulated/0/Download")
        val b = StorageUtils.resolveCustomDirectoryKey("/storage/emulated/0/Download")
        val c = StorageUtils.resolveCustomDirectoryKey("file:///storage/emulated/0/Download/")
        assertEquals(a, b)
        assertEquals(a, c)
    }

    @Test
    fun resolveCustomDirectoryKey_distinguishesDifferentPaths() {
        val a = StorageUtils.resolveCustomDirectoryKey("file:///storage/emulated/0/Download")
        val b = StorageUtils.resolveCustomDirectoryKey("file:///storage/emulated/0/DCIM")
        assertNotEquals(a, b)
    }

    @Test
    fun resolveCustomDirectoryKey_fallsBackToRawStringWhenUnresolvable() {
        val uri = "content://com.android.providers.downloads.documents/tree/downloads"
        assertEquals(uri, StorageUtils.resolveCustomDirectoryKey(uri))
    }

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun listChildDirectories_listsOnlyVisibleDirsSorted() {
        val root = tempFolder.root
        java.io.File(root, "b-dir").mkdir()
        java.io.File(root, "a-dir").mkdir()
        java.io.File(root, ".hidden").mkdir()
        java.io.File(root, "file.txt").writeText("x")

        assertEquals(listOf("a-dir", "b-dir"), StorageUtils.listChildDirectories(root.absolutePath, showHidden = false))
        assertEquals(listOf(".hidden", "a-dir", "b-dir"), StorageUtils.listChildDirectories(root.absolutePath, showHidden = true))
    }

    @Test
    fun listChildDirectories_returnsNullWhenNotListable() {
        assertNull(StorageUtils.listChildDirectories("/definitely/not/here-12345", showHidden = false))
        val file = tempFolder.newFile("plain.txt")
        assertNull(StorageUtils.listChildDirectories(file.absolutePath, showHidden = false))
    }

    @Test
    fun listStorageRoots_doesNotCrashAndReturnsSortedUnique() {
        val roots = StorageUtils.listStorageRoots()
        assertNotNull(roots)
        assertEquals(roots.sorted().distinct(), roots)
    }
}
