package io.github.hddq.restoid.data

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import com.topjohnwu.superuser.Shell
import io.github.hddq.restoid.model.AppOpPermission
import io.github.hddq.restoid.model.SpecialAccessMetadata
import io.github.hddq.restoid.util.shellQuote

/**
 * Pure parsing and merging helpers for Android special app access and accessibility outputs.
 */
object SpecialPermissionsParser {
    val SPECIAL_ACCESS_APP_OPS = setOf(
        "MANAGE_EXTERNAL_STORAGE",
        "SYSTEM_ALERT_WINDOW",
        "WRITE_SETTINGS",
        "REQUEST_INSTALL_PACKAGES",
        "SCHEDULE_EXACT_ALARM",
        "GET_USAGE_STATS",
        "LOADER_USAGE_STATS",
        "MANAGE_MEDIA",
        "PICTURE_IN_PICTURE",
        "USE_FULL_SCREEN_INTENT",
        "TURN_SCREEN_ON",
        "ACCESS_RESTRICTED_SETTINGS",
        "RECEIVE_SENSITIVE_NOTIFICATIONS",
        "MANAGE_ONGOING_CALLS",
        "CHANGE_WIFI_STATE"
    )

    fun parseAppOpsOutput(lines: List<String>): List<AppOpPermission> {
        val parsed = linkedMapOf<String, AppOpPermission>()
        var inUidSection = false

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() ||
                trimmed.startsWith("No operations", ignoreCase = true) ||
                trimmed.startsWith("Default mode:", ignoreCase = true)
            ) {
                continue
            }

            val isUid: Boolean
            val content: String
            when {
                trimmed.equals("Uid mode:", ignoreCase = true) -> {
                    inUidSection = true
                    continue
                }
                trimmed.startsWith("Uid mode:", ignoreCase = true) -> {
                    isUid = true
                    content = trimmed.removePrefix("Uid mode:").trim()
                }
                else -> {
                    val indented = rawLine.firstOrNull()?.isWhitespace() == true
                    if (!indented) {
                        inUidSection = false
                    }
                    isUid = inUidSection && indented
                    content = trimmed
                }
            }

            val colonIndex = content.indexOf(':')
            if (colonIndex <= 0) continue

            val opName = content.substring(0, colonIndex).trim()
            if (opName !in SPECIAL_ACCESS_APP_OPS) continue

            val mode = content.substring(colonIndex + 1)
                .substringBefore(';')
                .trim()
                .lowercase()
            if (mode != "allow") continue

            val existing = parsed[opName]
            parsed[opName] = AppOpPermission(
                op = opName,
                isUidMode = isUid || existing?.isUidMode == true
            )
        }

        return parsed.values.sortedBy { it.op }
    }

    fun parseColonSeparatedSetting(rawOutput: String): List<String> {
        val trimmed = rawOutput.trim()
        if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true)) return emptyList()
        return trimmed.split(':')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }

    fun groupComponentsByPackage(components: List<String>): Map<String, List<String>> {
        val grouped = linkedMapOf<String, MutableList<String>>()
        for (component in components) {
            val slashIndex = component.indexOf('/')
            if (slashIndex <= 0 || slashIndex == component.lastIndex) continue
            val pkg = component.substring(0, slashIndex).trim()
            if (pkg.isEmpty()) continue
            val list = grouped.getOrPut(pkg) { mutableListOf() }
            if (component !in list) {
                list.add(component)
            }
        }
        return grouped
    }

    fun parseDeviceIdleWhitelist(lines: List<String>): Set<String> {
        val userPackages = linkedSetOf<String>()
        var inUserSection = false

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("user,", ignoreCase = true)) {
                val parts = trimmed.split(',')
                val pkg = parts.getOrNull(1)?.trim()
                if (!pkg.isNullOrEmpty()) userPackages.add(pkg)
                continue
            }

            if (trimmed.startsWith("Whitelist ", ignoreCase = true) && trimmed.endsWith(":")) {
                inUserSection = trimmed.equals("Whitelist user apps:", ignoreCase = true)
                continue
            }

            if (inUserSection && !trimmed.contains(',')) {
                userPackages.add(trimmed)
            }
        }

        return userPackages
    }

    fun normalizeComponentKey(component: String): String {
        val slashIndex = component.indexOf('/')
        if (slashIndex <= 0 || slashIndex == component.lastIndex) return component.trim()
        val pkg = component.substring(0, slashIndex).trim()
        val cls = component.substring(slashIndex + 1).trim()
        val fullClass = if (cls.startsWith(".")) "$pkg$cls" else cls
        return "$pkg/$fullClass"
    }

    fun mergeColonSeparatedComponents(existingRaw: String, componentsToAdd: List<String>): String {
        val merged = mutableListOf<String>()
        val seenKeys = hashSetOf<String>()

        for (entry in parseColonSeparatedSetting(existingRaw) + componentsToAdd) {
            val trimmed = entry.trim()
            if (trimmed.isEmpty()) continue
            val key = normalizeComponentKey(trimmed)
            if (seenKeys.add(key)) {
                merged.add(trimmed)
            }
        }

        return merged.joinToString(":")
    }
}

/**
 * Snapshot of system-wide per-user access lists collected once per backup run
 * to avoid repeating expensive global dumpsys/settings queries for every package.
 */
data class DeviceAccessSnapshot(
    val activeDeviceAdminsByPackage: Map<String, List<String>> = emptyMap(),
    val notificationListenersByPackage: Map<String, List<String>> = emptyMap(),
    val notificationPolicyPackages: Set<String> = emptySet(),
    val batteryUnrestrictedPackages: Set<String> = emptySet(),
    val accessibilityServicesByPackage: Map<String, List<String>> = emptyMap()
)

class SpecialPermissionsManager(private val context: Context) {

    fun captureDeviceAccessSnapshot(
        userId: Int,
        includeSpecialAccess: Boolean,
        includeAccessibility: Boolean
    ): DeviceAccessSnapshot {
        val adminsByPkg = if (includeSpecialAccess) queryActiveDeviceAdminsByPackage() else emptyMap()
        val listenersByPkg = if (includeSpecialAccess) {
            SpecialPermissionsParser.groupComponentsByPackage(
                queryColonSetting(userId, SETTING_NOTIFICATION_LISTENERS)
            )
        } else {
            emptyMap()
        }
        val dndPackages = if (includeSpecialAccess) {
            queryColonSetting(userId, SETTING_NOTIFICATION_POLICY_ACCESS).toSet()
        } else {
            emptySet()
        }
        val batteryPackages = if (includeSpecialAccess) {
            queryUserDeviceIdleWhitelist()
        } else {
            emptySet()
        }
        val accessibilityByPkg = if (includeAccessibility) {
            SpecialPermissionsParser.groupComponentsByPackage(
                queryColonSetting(userId, SETTING_ACCESSIBILITY_SERVICES)
            )
        } else {
            emptyMap()
        }

        return DeviceAccessSnapshot(
            activeDeviceAdminsByPackage = adminsByPkg,
            notificationListenersByPackage = listenersByPkg,
            notificationPolicyPackages = dndPackages,
            batteryUnrestrictedPackages = batteryPackages,
            accessibilityServicesByPackage = accessibilityByPkg
        )
    }

    fun collectSpecialAccess(
        packageName: String,
        userId: Int,
        snapshot: DeviceAccessSnapshot
    ): SpecialAccessMetadata? {
        val result = Shell.cmd("cmd appops get --user $userId ${shellQuote(packageName)}").exec()
        val appOps = if (result.isSuccess) {
            SpecialPermissionsParser.parseAppOpsOutput(result.out)
        } else {
            emptyList()
        }

        val metadata = SpecialAccessMetadata(
            allowedAppOps = appOps,
            activeDeviceAdmins = snapshot.activeDeviceAdminsByPackage[packageName].orEmpty(),
            enabledNotificationListeners = snapshot.notificationListenersByPackage[packageName].orEmpty(),
            notificationPolicyAccess = packageName in snapshot.notificationPolicyPackages,
            ignoringBatteryOptimizations = packageName in snapshot.batteryUnrestrictedPackages
        )

        return if (metadata.isEmpty()) null else metadata
    }

    fun restoreSpecialAccess(
        packageName: String,
        userId: Int,
        specialAccess: SpecialAccessMetadata
    ): List<String> {
        if (specialAccess.isEmpty()) return emptyList()
        val failures = mutableListOf<String>()
        val quotedPkg = shellQuote(packageName)

        // Ensure ACCESS_RESTRICTED_SETTINGS is restored before notification listeners / device admin
        // on Android 13+, in case the app was restored without Play Store installer metadata.
        val orderedOps = specialAccess.allowedAppOps.sortedByDescending {
            it.op == "ACCESS_RESTRICTED_SETTINGS"
        }
        for (appOp in orderedOps) {
            val quotedOp = shellQuote(appOp.op)
            val result = if (appOp.isUidMode) {
                val uidResult = Shell.cmd("cmd appops set --user $userId --uid $quotedPkg $quotedOp allow").exec()
                if (uidResult.isSuccess) {
                    uidResult
                } else {
                    Shell.cmd("cmd appops set --user $userId $quotedPkg $quotedOp allow").exec()
                }
            } else {
                Shell.cmd("cmd appops set --user $userId $quotedPkg $quotedOp allow").exec()
            }
            if (!result.isSuccess) {
                val output = (result.err + result.out).joinToString(" ").trim()
                failures.add(if (output.isBlank()) appOp.op else "${appOp.op} ($output)")
            }
        }

        for (listener in specialAccess.enabledNotificationListeners) {
            val result = Shell.cmd("cmd notification allow_listener ${shellQuote(listener)} $userId").exec()
            if (!result.isSuccess) {
                val output = (result.err + result.out).joinToString(" ").trim()
                failures.add(if (output.isBlank()) listener else "$listener ($output)")
            }
        }

        if (specialAccess.notificationPolicyAccess) {
            val result = Shell.cmd("cmd notification allow_dnd $quotedPkg $userId").exec()
            if (!result.isSuccess) {
                val output = (result.err + result.out).joinToString(" ").trim()
                failures.add(if (output.isBlank()) "DND access" else "DND access ($output)")
            }
        }

        if (specialAccess.ignoringBatteryOptimizations) {
            val result = Shell.cmd("dumpsys deviceidle whitelist +$quotedPkg").exec()
            if (!result.isSuccess) {
                val output = (result.err + result.out).joinToString(" ").trim()
                failures.add(if (output.isBlank()) "Battery unrestricted" else "Battery unrestricted ($output)")
            }
        }

        for (adminComponent in specialAccess.activeDeviceAdmins) {
            val result = Shell.cmd("dpm set-active-admin --user $userId ${shellQuote(adminComponent)}").exec()
            if (!result.isSuccess) {
                val output = (result.err + result.out).joinToString(" ").trim()
                failures.add(if (output.isBlank()) adminComponent else "$adminComponent ($output)")
            }
        }

        return failures
    }

    fun restoreAccessibilityServices(
        packageName: String,
        userId: Int,
        services: List<String>
    ): List<String> {
        if (services.isEmpty()) return emptyList()
        val failures = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Unblock sideloaded accessibility services on Android 13+ when supported by the ROM.
            Shell.cmd("cmd appops set --user $userId ${shellQuote(packageName)} ACCESS_RESTRICTED_SETTINGS allow").exec()
        }

        val currentResult = Shell.cmd("settings get --user $userId secure $SETTING_ACCESSIBILITY_SERVICES").exec()
        if (!currentResult.isSuccess) {
            val output = (currentResult.err + currentResult.out).joinToString(" ").trim()
            return listOf(if (output.isBlank()) SETTING_ACCESSIBILITY_SERVICES else "$SETTING_ACCESSIBILITY_SERVICES ($output)")
        }

        val currentRaw = currentResult.out.joinToString("\n").trim()
        val merged = SpecialPermissionsParser.mergeColonSeparatedComponents(currentRaw, services)
        if (merged.isEmpty()) return emptyList()

        val putServicesResult = Shell.cmd(
            "settings put --user $userId secure $SETTING_ACCESSIBILITY_SERVICES ${shellQuote(merged)}"
        ).exec()
        if (!putServicesResult.isSuccess) {
            val output = (putServicesResult.err + putServicesResult.out).joinToString(" ").trim()
            failures.add(if (output.isBlank()) "enabled_accessibility_services" else "enabled_accessibility_services ($output)")
            return failures
        }

        val enableResult = Shell.cmd("settings put --user $userId secure accessibility_enabled 1").exec()
        if (!enableResult.isSuccess) {
            val output = (enableResult.err + enableResult.out).joinToString(" ").trim()
            failures.add(if (output.isBlank()) "accessibility_enabled" else "accessibility_enabled ($output)")
        }

        return failures
    }

    private fun queryActiveDeviceAdminsByPackage(): Map<String, List<String>> {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return emptyMap()
        val admins = runCatching { dpm.activeAdmins }.getOrNull().orEmpty()
        if (admins.isEmpty()) return emptyMap()

        val grouped = linkedMapOf<String, MutableList<String>>()
        for (component in admins) {
            val pkg = component.packageName
            // Device Owner and Profile Owner apps cannot be re-provisioned via `dpm set-active-admin`
            // on an already set-up device; only track regular device admins.
            val isOwner = runCatching {
                dpm.isDeviceOwnerApp(pkg) || dpm.isProfileOwnerApp(pkg)
            }.getOrDefault(false)
            if (isOwner) continue

            val flattened = component.flattenToShortString()
            val list = grouped.getOrPut(pkg) { mutableListOf() }
            if (flattened !in list) {
                list.add(flattened)
            }
        }
        return grouped
    }

    private fun queryColonSetting(userId: Int, key: String): List<String> {
        val result = Shell.cmd("settings get --user $userId secure $key").exec()
        if (!result.isSuccess) return emptyList()
        return SpecialPermissionsParser.parseColonSeparatedSetting(result.out.joinToString("\n"))
    }

    private fun queryUserDeviceIdleWhitelist(): Set<String> {
        val result = Shell.cmd("dumpsys deviceidle whitelist").exec()
        if (!result.isSuccess) return emptySet()
        return SpecialPermissionsParser.parseDeviceIdleWhitelist(result.out)
    }

    private companion object {
        const val SETTING_NOTIFICATION_LISTENERS = "enabled_notification_listeners"
        const val SETTING_NOTIFICATION_POLICY_ACCESS = "enabled_notification_policy_access_packages"
        const val SETTING_ACCESSIBILITY_SERVICES = "enabled_accessibility_services"
    }
}
