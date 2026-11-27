package io.github.hddq.restoid.work

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.ui.shared.BackupTypes
import io.github.hddq.restoid.ui.shared.TaskConfigEditorState
import io.github.hddq.restoid.ui.shared.hasTaskConfigChanges
import io.github.hddq.restoid.ui.shared.toRunTasksConfig
import io.github.hddq.restoid.ui.shared.toUiModel
import io.github.hddq.restoid.ui.shared.withAppliedRunTasksConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectAllAppsBackupTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun testApp(
        packageName: String,
        name: String = packageName,
        isSelected: Boolean = true
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
            apkPaths = emptyList(),
            isSelected = isSelected,
            firstInstallTime = 0L
        )
    }

    @Test
    fun runTasksConfig_serializesSelectAllAndExcludedPackages() {
        val config = RunTasksConfig(
            backupEnabled = true,
            selectAllApps = true,
            excludedPackageNames = listOf("com.big.game", "com.temp.app")
        )

        val decoded = json.decodeFromString<RunTasksConfig>(json.encodeToString(config))

        assertTrue(decoded.selectAllApps)
        assertEquals(listOf("com.big.game", "com.temp.app"), decoded.excludedPackageNames)
        assertTrue(decoded.selectedPackageNames.isEmpty())
    }

    @Test
    fun legacyJson_defaultsSelectAllAppsToFalseAndExcludedToEmpty() {
        val legacyConfigJson = """
            {
                "backupEnabled": true,
                "backupTypes": {},
                "selectedPackageNames": ["com.example.a"]
            }
        """.trimIndent()
        val decodedConfig = json.decodeFromString<RunTasksConfig>(legacyConfigJson)
        assertFalse(decodedConfig.selectAllApps)
        assertTrue(decodedConfig.excludedPackageNames.isEmpty())
        assertEquals(listOf("com.example.a"), decodedConfig.selectedPackageNames)

        val legacyBackupRequestJson = """
            {
                "repositoryKey": "local:/repo",
                "backupTypes": {},
                "selectedPackageNames": ["com.example.a"]
            }
        """.trimIndent()
        val decodedBackupRequest = json.decodeFromString<BackupWorkRequest>(legacyBackupRequestJson)
        assertFalse(decodedBackupRequest.selectAllApps)
        assertTrue(decodedBackupRequest.excludedPackageNames.isEmpty())

        val legacyRunTasksRequestJson = """
            {
                "repositoryKey": "local:/repo",
                "backupEnabled": true,
                "backupTypes": {},
                "selectedPackageNames": ["com.example.a"],
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
        val decodedRunTasksRequest = json.decodeFromString<RunTasksWorkRequest>(legacyRunTasksRequestJson)
        assertFalse(decodedRunTasksRequest.selectAllApps)
        assertTrue(decodedRunTasksRequest.excludedPackageNames.isEmpty())
    }

    @Test
    fun toWorkRequest_propagatesSelectAllAndExclusionsOnlyWithRoot() {
        val config = RunTasksConfig(
            backupEnabled = true,
            selectAllApps = true,
            selectedPackageNames = listOf("com.stale.pkg"),
            excludedPackageNames = listOf("com.big.game", "com.big.game")
        )

        val withRoot = config.toWorkRequest(repositoryKey = "local:/repo", hasRoot = true)
        assertTrue(withRoot.backupEnabled)
        assertTrue(withRoot.selectAllApps)
        assertTrue(withRoot.selectedPackageNames.isEmpty())
        assertEquals(listOf("com.big.game"), withRoot.excludedPackageNames)

        val withoutRoot = config.toWorkRequest(repositoryKey = "local:/repo", hasRoot = false)
        assertFalse(withoutRoot.backupEnabled)
        assertFalse(withoutRoot.selectAllApps)
        assertTrue(withoutRoot.selectedPackageNames.isEmpty())
        assertTrue(withoutRoot.excludedPackageNames.isEmpty())
    }

    @Test
    fun resolveBackupTargetApps_includesNewlyInstalledAndFiltersExcludedWhenSelectAll() = runBlocking {
        val installed = listOf(
            testApp("com.existing.a"),
            testApp("com.big.game"),
            testApp("com.newly.installed")
        )
        val request = BackupWorkRequest(
            repositoryKey = "local:/repo",
            backupTypes = BackupTypeSelection(),
            selectedPackageNames = emptyList(),
            selectAllApps = true,
            excludedPackageNames = listOf("com.big.game")
        )

        val resolved = resolveBackupTargetApps(
            request = request,
            getInstalledUserApps = { installed },
            getAppInfoForPackages = { error("Should not query explicit package list when selectAllApps is true") }
        )

        assertEquals(
            listOf("com.existing.a", "com.newly.installed"),
            resolved.map { it.packageName }
        )
    }

    @Test
    fun resolveBackupTargetApps_usesExplicitPackageListWhenSelectAllDisabled() = runBlocking {
        val existingA = testApp("com.existing.a")
        val request = BackupWorkRequest(
            repositoryKey = "local:/repo",
            backupTypes = BackupTypeSelection(),
            selectedPackageNames = listOf("com.existing.a"),
            selectAllApps = false,
            excludedPackageNames = listOf("com.existing.a")
        )

        val resolved = resolveBackupTargetApps(
            request = request,
            getInstalledUserApps = { error("Should not query all installed apps when selectAllApps is false") },
            getAppInfoForPackages = { pkgs -> if (pkgs == listOf("com.existing.a")) listOf(existingA) else emptyList() }
        )

        assertEquals(listOf("com.existing.a"), resolved.map { it.packageName })
    }

    @Test
    fun editorStateToRunTasksConfig_savesExclusionsAndPerAppOverridesWhenSelectAllEnabled() {
        val defaultTypes = BackupTypes()
        val customTypes = BackupTypes(obb = true, externalData = true)
        val state = TaskConfigEditorState(
            backupEnabled = true,
            selectAllApps = true,
            excludedPackageNames = setOf("com.big.game", "com.uninstalled.excluded"),
            apps = listOf(
                testApp("com.normal.app", isSelected = true),
                testApp("com.custom.app", isSelected = true),
                testApp("com.big.game", isSelected = false)
            ),
            backupTypes = defaultTypes,
            appBackupTypes = mapOf(
                "com.normal.app" to defaultTypes,
                "com.custom.app" to customTypes,
                "com.big.game" to customTypes
            )
        )

        val config = state.toRunTasksConfig(hasRoot = true)

        assertTrue(config.selectAllApps)
        assertTrue(config.selectedPackageNames.isEmpty())
        assertEquals(listOf("com.big.game", "com.uninstalled.excluded"), config.excludedPackageNames)
        assertEquals(mapOf("com.custom.app" to customTypes.toSelection()), config.appBackupTypes)
    }

    @Test
    fun withAppliedRunTasksConfig_selectsNewAppsAndExcludesBlacklistedWhenSelectAllEnabled() {
        val customTypes = BackupTypeSelection(externalData = true)
        val config = RunTasksConfig(
            backupEnabled = true,
            selectAllApps = true,
            excludedPackageNames = listOf("com.big.game"),
            appBackupTypes = mapOf("com.custom.app" to customTypes)
        )
        val currentApps = listOf(
            testApp("com.custom.app", isSelected = false),
            testApp("com.big.game", isSelected = true),
            testApp("com.newly.installed", isSelected = false)
        )

        val updated = TaskConfigEditorState(selectAllApps = false)
            .withAppliedRunTasksConfig(config, existingApps = currentApps)

        assertTrue(updated.selectAllApps)
        assertEquals(setOf("com.big.game"), updated.excludedPackageNames)
        assertEquals(
            mapOf(
                "com.big.game" to false,
                "com.custom.app" to true,
                "com.newly.installed" to true
            ),
            updated.apps.associate { it.packageName to it.isSelected }
        )
        assertEquals(customTypes.toUiModel(), updated.appBackupTypes["com.custom.app"])
        assertEquals(config.backupTypes.toUiModel(), updated.appBackupTypes["com.newly.installed"])
    }

    @Test
    fun withAppliedRunTasksConfig_leavesNewAppsUnselectedWhenSelectAllDisabled() {
        val config = RunTasksConfig(
            backupEnabled = true,
            selectAllApps = false,
            selectedPackageNames = listOf("com.existing.a")
        )
        val currentApps = listOf(
            testApp("com.existing.a", isSelected = false),
            testApp("com.newly.installed", isSelected = true)
        )

        val updated = TaskConfigEditorState(selectAllApps = true)
            .withAppliedRunTasksConfig(config, existingApps = currentApps)

        assertFalse(updated.selectAllApps)
        assertTrue(updated.excludedPackageNames.isEmpty())
        assertEquals(
            mapOf(
                "com.existing.a" to true,
                "com.newly.installed" to false
            ),
            updated.apps.associate { it.packageName to it.isSelected }
        )
    }

    @Test
    fun hasTaskConfigChanges_detectsSelectAllAppsAndExclusions() {
        val initial = TaskConfigEditorState(
            selectAllApps = true,
            excludedPackageNames = setOf("com.big.game")
        )
        val unchanged = initial.copy(excludedPackageNames = setOf("com.big.game"))
        val changedMode = initial.copy(selectAllApps = false, excludedPackageNames = emptySet())
        val changedExcluded = initial.copy(excludedPackageNames = setOf("com.big.game", "com.other"))

        assertFalse(hasTaskConfigChanges(unchanged, initial))
        assertTrue(hasTaskConfigChanges(changedMode, initial))
        assertTrue(hasTaskConfigChanges(changedExcluded, initial))
    }
}
