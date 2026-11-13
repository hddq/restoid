package io.github.hddq.restoid.data

import io.github.hddq.restoid.model.AppMetadata
import io.github.hddq.restoid.model.AppOpPermission
import io.github.hddq.restoid.model.RestoidMetadata
import io.github.hddq.restoid.model.SpecialAccessMetadata
import io.github.hddq.restoid.ui.restore.RestoreTypes
import io.github.hddq.restoid.ui.restore.toUiModel
import io.github.hddq.restoid.ui.shared.BackupTypes
import io.github.hddq.restoid.ui.shared.toUiModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialPermissionsParserTest {

    @Test
    fun parseAppOpsOutput_handlesSingleLineAndSectionUidModesAndFiltersNonSpecialOps() {
        val output = listOf(
            "Uid mode: MANAGE_EXTERNAL_STORAGE: allow; time=+5m12s ago",
            "Uid mode:",
            "  SCHEDULE_EXACT_ALARM: allow",
            "  CAMERA: allow; time=+1m ago",
            "SYSTEM_ALERT_WINDOW: allow; time=+2s100ms ago",
            "WRITE_SETTINGS: ignore; rejectTime=+30s ago",
            "REQUEST_INSTALL_PACKAGES: default",
            "ACCESS_RESTRICTED_SETTINGS: allow",
            "TOAST_WINDOW: allow; duration=+2s"
        )

        val parsed = SpecialPermissionsParser.parseAppOpsOutput(output)

        assertEquals(
            listOf(
                AppOpPermission(op = "ACCESS_RESTRICTED_SETTINGS", isUidMode = false),
                AppOpPermission(op = "MANAGE_EXTERNAL_STORAGE", isUidMode = true),
                AppOpPermission(op = "SCHEDULE_EXACT_ALARM", isUidMode = true),
                AppOpPermission(op = "SYSTEM_ALERT_WINDOW", isUidMode = false)
            ),
            parsed
        )
    }

    @Test
    fun parseAppOpsOutput_handlesNoOperationsOrEmpty() {
        val output = listOf(
            "No operations.",
            "Default mode: allow"
        )

        assertEquals(emptyList<AppOpPermission>(), SpecialPermissionsParser.parseAppOpsOutput(output))
    }

    @Test
    fun parseColonSeparatedSetting_andGroupComponents() {
        assertEquals(emptyList<String>(), SpecialPermissionsParser.parseColonSeparatedSetting("null"))
        assertEquals(emptyList<String>(), SpecialPermissionsParser.parseColonSeparatedSetting("  "))

        val parsed = SpecialPermissionsParser.parseColonSeparatedSetting(
            "com.a/.ServiceOne:com.b/com.b.ServiceTwo::null"
        )
        assertEquals(listOf("com.a/.ServiceOne", "com.b/com.b.ServiceTwo"), parsed)

        val grouped = SpecialPermissionsParser.groupComponentsByPackage(
            parsed + listOf("com.a/.ServiceOne", "invalidEntry", "com.a/com.a.Other")
        )
        assertEquals(
            mapOf(
                "com.a" to listOf("com.a/.ServiceOne", "com.a/com.a.Other"),
                "com.b" to listOf("com.b/com.b.ServiceTwo")
            ),
            grouped
        )
    }

    @Test
    fun parseDeviceIdleWhitelist_extractsOnlyUserWhitelistedPackages() {
        val csvLines = listOf(
            "system-excidle,com.android.shell,2000",
            "system,com.google.android.gms,10012",
            "user,com.example.sync,10234",
            "user,org.example.backup,10250"
        )
        assertEquals(
            setOf("com.example.sync", "org.example.backup"),
            SpecialPermissionsParser.parseDeviceIdleWhitelist(csvLines)
        )

        val sectionLines = listOf(
            "Whitelist system apps:",
            "  com.android.providers.downloads",
            "Whitelist user apps:",
            "  com.example.alpha",
            "  com.example.beta"
        )
        assertEquals(
            setOf("com.example.alpha", "com.example.beta"),
            SpecialPermissionsParser.parseDeviceIdleWhitelist(sectionLines)
        )
    }

    @Test
    fun mergeColonSeparatedComponents_appendsWithoutDuplicatingShortOrFullNames() {
        val merged = SpecialPermissionsParser.mergeColonSeparatedComponents(
            existingRaw = "com.keepass/.AutofillService:com.other/com.other.AccService",
            componentsToAdd = listOf(
                "com.keepass/com.keepass.AutofillService",
                "com.newapp/.MyAccessibilityService"
            )
        )

        assertEquals(
            "com.keepass/.AutofillService:com.other/com.other.AccService:com.newapp/.MyAccessibilityService",
            merged
        )

        assertEquals(
            "com.newapp/.MyAccessibilityService",
            SpecialPermissionsParser.mergeColonSeparatedComponents(
                existingRaw = "null",
                componentsToAdd = listOf("com.newapp/.MyAccessibilityService")
            )
        )
    }

    @Test
    fun metadataSerialization_isBackwardCompatibleAndTracksFlags() {
        val json = Json { ignoreUnknownKeys = true }
        val legacyJson = """
            {
              "apps": {
                "com.legacy": {
                  "size": 1024,
                  "types": ["apk", "permissions"],
                  "versionCode": 10,
                  "versionName": "1.0",
                  "grantedRuntimePermissions": ["android.permission.CAMERA"]
                }
              }
            }
        """.trimIndent()

        val legacyDecoded = json.decodeFromString<RestoidMetadata>(legacyJson)
        val legacyApp = legacyDecoded.apps.getValue("com.legacy")
        assertNull(legacyApp.specialAccess)
        assertFalse(legacyApp.hasSpecialAccessBackup())
        assertFalse(legacyApp.hasAccessibilityBackup())

        val modern = RestoidMetadata(
            apps = mapOf(
                "com.modern" to AppMetadata(
                    size = 0L,
                    types = listOf("permissions", "special_access", "accessibility"),
                    versionCode = 20L,
                    versionName = "2.0",
                    specialAccess = SpecialAccessMetadata(
                        allowedAppOps = listOf(AppOpPermission("MANAGE_EXTERNAL_STORAGE", isUidMode = true)),
                        activeDeviceAdmins = listOf("com.modern/.AdminReceiver"),
                        enabledNotificationListeners = listOf("com.modern/.Listener"),
                        notificationPolicyAccess = true,
                        ignoringBatteryOptimizations = true
                    ),
                    enabledAccessibilityServices = listOf("com.modern/.AccService")
                )
            )
        )
        val roundTripped = json.decodeFromString<RestoidMetadata>(json.encodeToString(modern))
        val modernApp = roundTripped.apps.getValue("com.modern")
        assertTrue(modernApp.hasSpecialAccessBackup())
        assertTrue(modernApp.hasAccessibilityBackup())

        val legacyBackupTypes = json.decodeFromString<BackupTypes>("""{"apk":true,"permissions":false}""")
        assertFalse(legacyBackupTypes.permissions)
        assertTrue(legacyBackupTypes.specialAccess)
        assertTrue(legacyBackupTypes.accessibility)
    }

    @Test
    fun partitionComponentsByPackage_rejectsMismatchedAndMalformedComponents() {
        val (matching, mismatched) = SpecialPermissionsParser.partitionComponentsByPackage(
            components = listOf(
                "com.example/.ServiceOne",
                "com.example/com.example.ServiceTwo",
                "com.other/.ForeignService",
                "com.example.suffix/.Trick",
                "com.example/",
                "com.example/.Bad/Extra",
                "com.example/.Bad Service"
            ),
            packageName = "com.example"
        )

        assertEquals(
            listOf("com.example/.ServiceOne", "com.example/com.example.ServiceTwo"),
            matching
        )
        assertEquals(
            listOf(
                "com.other/.ForeignService",
                "com.example.suffix/.Trick",
                "com.example/",
                "com.example/.Bad/Extra",
                "com.example/.Bad Service"
            ),
            mismatched
        )
    }

    @Test
    fun backupAndRestoreTypeConversions_preserveDisabledFlagsInRoundTrip() {
        val disabledRestore = RestoreTypes(
            apk = false,
            data = false,
            deviceProtectedData = false,
            externalData = false,
            obb = false,
            media = false,
            permissions = false,
            specialAccess = false,
            accessibility = false
        )
        assertEquals(disabledRestore, disabledRestore.toSelection().toUiModel())
        assertFalse(disabledRestore.anyEnabled())

        val partialRestore = RestoreTypes(permissions = false, specialAccess = false, accessibility = true)
        assertEquals(partialRestore, partialRestore.toSelection().toUiModel())

        val disabledBackup = BackupTypes(
            apk = false,
            data = false,
            deviceProtectedData = false,
            externalData = false,
            obb = false,
            media = false,
            permissions = false,
            specialAccess = false,
            accessibility = false
        )
        assertEquals(disabledBackup, disabledBackup.toSelection().toUiModel())
        assertFalse(disabledBackup.anyEnabled())
    }
}
