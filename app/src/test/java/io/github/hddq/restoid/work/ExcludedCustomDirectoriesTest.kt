package io.github.hddq.restoid.work

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.util.buildResticExcludeFlags
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcludedCustomDirectoriesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun backupWorkRequest_serializesAndDeserializesWithExclusions() {
        val request = BackupWorkRequest(
            repositoryKey = "local:/repo",
            backupTypes = BackupTypeSelection(),
            selectedPackageNames = listOf("com.example.app"),
            customDirectories = listOf("/storage/emulated/0/DCIM"),
            excludedCustomDirectories = listOf("/storage/emulated/0/DCIM/Camera2")
        )

        val serialized = json.encodeToString(request)
        val deserialized = json.decodeFromString<BackupWorkRequest>(serialized)

        assertEquals(listOf("/storage/emulated/0/DCIM"), deserialized.customDirectories)
        assertEquals(listOf("/storage/emulated/0/DCIM/Camera2"), deserialized.excludedCustomDirectories)
    }

    @Test
    fun backupWorkRequest_backwardCompatibility_missingExcludedFieldDefaultsToEmpty() {
        val legacyJson = """
            {
                "repositoryKey": "local:/repo",
                "backupTypes": {},
                "selectedPackageNames": ["com.example.app"],
                "customDirectories": ["/storage/emulated/0/DCIM"]
            }
        """.trimIndent()

        val deserialized = json.decodeFromString<BackupWorkRequest>(legacyJson)
        assertEquals(listOf("/storage/emulated/0/DCIM"), deserialized.customDirectories)
        assertTrue(deserialized.excludedCustomDirectories.isEmpty())
    }

    @Test
    fun runTasksConfig_backwardCompatibility_missingExcludedFieldDefaultsToEmpty() {
        val legacyJson = """
            {
                "backupEnabled": true,
                "backupTypes": {},
                "selectedPackageNames": [],
                "customDirectories": ["/storage/emulated/0/DCIM"],
                "unlockRepo": false,
                "forgetSnapshots": false,
                "pruneRepo": false,
                "checkRepo": false,
                "readData": false,
                "keepLast": 0,
                "keepDaily": 0,
                "keepWeekly": 0,
                "keepMonthly": 0
            }
        """.trimIndent()

        val deserialized = json.decodeFromString<RunTasksConfig>(legacyJson)
        assertEquals(listOf("/storage/emulated/0/DCIM"), deserialized.customDirectories)
        assertTrue(deserialized.excludedCustomDirectories.isEmpty())
        org.junit.Assert.assertNull(deserialized.customDirectoriesBackupEnabled)
    }

    @Test
    fun runTasksConfig_serializesAndDeserializesWithCustomDirectoriesBackupEnabled() {
        val config = RunTasksConfig(
            backupEnabled = true,
            backupTypes = BackupTypeSelection(),
            selectedPackageNames = emptyList(),
            customDirectoriesBackupEnabled = false,
            customDirectories = listOf("/storage/emulated/0/DCIM"),
            excludedCustomDirectories = listOf("/storage/emulated/0/DCIM/Camera2"),
            unlockRepo = false,
            forgetSnapshots = false,
            pruneRepo = false,
            checkRepo = false,
            readData = false,
            keepLast = 0,
            keepDaily = 0,
            keepWeekly = 0,
            keepMonthly = 0
        )

        val serialized = json.encodeToString(config)
        val deserialized = json.decodeFromString<RunTasksConfig>(serialized)
        org.junit.Assert.assertEquals(false, deserialized.customDirectoriesBackupEnabled)
        assertEquals(listOf("/storage/emulated/0/DCIM"), deserialized.customDirectories)
        assertEquals(listOf("/storage/emulated/0/DCIM/Camera2"), deserialized.excludedCustomDirectories)
    }

    @Test
    fun runTasksWorkRequest_serializesAndDeserializesWithExclusions() {
        val request = RunTasksWorkRequest(
            repositoryKey = "local:/repo",
            backupEnabled = true,
            backupTypes = BackupTypeSelection(),
            selectedPackageNames = emptyList(),
            customDirectories = listOf("/storage/emulated/0/DCIM"),
            excludedCustomDirectories = listOf("/storage/emulated/0/DCIM/Camera2"),
            unlockRepo = false,
            forgetSnapshots = false,
            pruneRepo = false,
            checkRepo = false,
            readData = false,
            keepLast = 0,
            keepDaily = 0,
            keepWeekly = 0,
            keepMonthly = 0
        )

        val serialized = json.encodeToString(request)
        val deserialized = json.decodeFromString<RunTasksWorkRequest>(serialized)

        assertEquals(listOf("/storage/emulated/0/DCIM"), deserialized.customDirectories)
        assertEquals(listOf("/storage/emulated/0/DCIM/Camera2"), deserialized.excludedCustomDirectories)
    }

    @Test
    fun resticExcludeFlags_matchesUserDCIMScenario() {
        val excluded = listOf("/storage/emulated/0/DCIM/Camera2")
        val flags = buildResticExcludeFlags(excluded)
        assertEquals("--exclude '/storage/emulated/0/DCIM/Camera2'", flags)
    }

    private fun createTestApp(
        packageName: String = "com.example.app",
        name: String = "Example App"
    ): AppInfo {
        val dummyDrawable = object : Drawable() {
            override fun draw(canvas: Canvas) {}
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(colorFilter: ColorFilter?) {}
            @Deprecated("Deprecated in Java")
            override fun getOpacity(): Int = PixelFormat.UNKNOWN
        }
        return AppInfo(
            name = name,
            packageName = packageName,
            versionName = "1.0",
            versionCode = 1L,
            icon = dummyDrawable,
            apkPaths = emptyList()
        )
    }

    @Test
    fun findConflictingExclusion_detectsExclusionCoveringAppExternalData() {
        val app = createTestApp(packageName = "com.example.app")
        val request = BackupWorkRequest(
            repositoryKey = "local:/repo",
            backupTypes = BackupTypeSelection(externalData = true),
            selectedPackageNames = listOf(app.packageName),
            customDirectories = listOf("/storage/emulated/0"),
            excludedCustomDirectories = listOf("/storage/emulated/0/Android/data")
        )

        val conflict = findConflictingExclusion(listOf(app), request, currentUserId = 0)
        assertNotNull(conflict)
        assertEquals(app.packageName, conflict?.app?.packageName)
        assertEquals("/storage/emulated/0/Android/data", conflict?.conflictingPath)
        assertEquals("/storage/emulated/0/Android/data/com.example.app", conflict?.appPath)
    }

    @Test
    fun findConflictingExclusion_noConflictWhenExclusionIsUnrelated() {
        val app = createTestApp(packageName = "com.example.app")
        val request = BackupWorkRequest(
            repositoryKey = "local:/repo",
            backupTypes = BackupTypeSelection(externalData = true),
            selectedPackageNames = listOf(app.packageName),
            customDirectories = listOf("/storage/emulated/0"),
            excludedCustomDirectories = listOf("/storage/emulated/0/DCIM/Camera2")
        )

        val conflict = findConflictingExclusion(listOf(app), request, currentUserId = 0)
        assertNull(conflict)
    }

    @Test
    fun findConflictingExclusion_detectsExclusionInsideAppData() {
        val app = createTestApp(packageName = "com.example.app")
        val request = BackupWorkRequest(
            repositoryKey = "local:/repo",
            backupTypes = BackupTypeSelection(data = true),
            selectedPackageNames = listOf(app.packageName),
            customDirectories = listOf("/"),
            excludedCustomDirectories = listOf("/data/user/0/com.example.app/databases")
        )

        val conflict = findConflictingExclusion(listOf(app), request, currentUserId = 0)
        assertNotNull(conflict)
        assertEquals(app.packageName, conflict?.app?.packageName)
        assertEquals("/data/user/0/com.example.app/databases", conflict?.conflictingPath)
        assertEquals("/data/user/0/com.example.app", conflict?.appPath)
    }

    @Test
    fun findConflictingExclusions_detectsNavicMusicDownloadCacheExclusion() {
        val navic = createTestApp(packageName = "paige.navic", name = "Navic")
        val request = BackupWorkRequest(
            repositoryKey = "local:/repo",
            backupTypes = BackupTypeSelection(data = true),
            selectedPackageNames = listOf(navic.packageName),
            customDirectories = emptyList(),
            excludedCustomDirectories = listOf("/data/user/0/paige.navic/files/downloads")
        )

        val conflicts = findConflictingExclusions(listOf(navic), request, currentUserId = 0)
        assertEquals(1, conflicts.size)
        val conflict = conflicts.first()
        assertEquals("paige.navic", conflict.app.packageName)
        assertEquals("/data/user/0/paige.navic/files/downloads", conflict.conflictingPath)
        assertEquals("/data/user/0/paige.navic", conflict.appPath)
    }

    @Test
    fun findConflictingExclusions_multipleAppsAndExclusionsWithDuplicates() {
        val app1 = createTestApp(packageName = "com.app.one", name = "App One")
        val app2 = createTestApp(packageName = "com.app.two", name = "App Two")
        val app3 = createTestApp(packageName = "com.app.three", name = "App Three")
        val request = BackupWorkRequest(
            repositoryKey = "local:/repo",
            backupTypes = BackupTypeSelection(data = true),
            selectedPackageNames = listOf(app1.packageName, app2.packageName, app3.packageName),
            customDirectories = emptyList(),
            excludedCustomDirectories = listOf(
                "/data/user/0/com.app.one/cache",
                "/data/user/0/com.app.one/cache", // duplicate exclusion
                "/data/user/0/com.app.two/files/temp",
                "/storage/emulated/0/DCIM/Camera2" // unrelated
            )
        )

        val conflicts = findConflictingExclusions(listOf(app1, app2, app3), request, currentUserId = 0)
        assertEquals(2, conflicts.size)
        assertEquals(setOf("com.app.one", "com.app.two"), conflicts.map { it.app.packageName }.toSet())
        val app1Conflict = conflicts.first { it.app.packageName == "com.app.one" }
        assertEquals("/data/user/0/com.app.one/cache", app1Conflict.conflictingPath)
        val app2Conflict = conflicts.first { it.app.packageName == "com.app.two" }
        assertEquals("/data/user/0/com.app.two/files/temp", app2Conflict.conflictingPath)
    }

    @Test
    fun findConflictingExclusions_reusesResolvedExclusions() {
        val app = createTestApp(packageName = "com.example.app")
        val request = BackupWorkRequest(
            repositoryKey = "local:/repo",
            backupTypes = BackupTypeSelection(data = true),
            selectedPackageNames = listOf(app.packageName),
            customDirectories = emptyList(),
            excludedCustomDirectories = listOf("raw_uri_that_wont_match")
        )
        val preResolved = listOf("/data/user/0/com.example.app/cache")

        val conflicts = findConflictingExclusions(
            selectedApps = listOf(app),
            request = request,
            currentUserId = 0,
            resolvedExclusions = preResolved
        )
        assertEquals(1, conflicts.size)
        assertEquals("/data/user/0/com.example.app/cache", conflicts.first().conflictingPath)
    }
}
