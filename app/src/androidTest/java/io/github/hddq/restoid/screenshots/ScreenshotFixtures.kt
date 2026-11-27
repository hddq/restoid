package io.github.hddq.restoid.screenshots

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import io.github.hddq.restoid.data.BatteryConditionStatus
import io.github.hddq.restoid.data.ChargingPowerSource
import io.github.hddq.restoid.data.ChargingState
import io.github.hddq.restoid.data.LocalRepository
import io.github.hddq.restoid.data.NetworkConditionStatus
import io.github.hddq.restoid.data.NetworkTransport
import io.github.hddq.restoid.data.RepositoryBackendType
import io.github.hddq.restoid.data.ResticBinaryManager
import io.github.hddq.restoid.data.ResticState
import io.github.hddq.restoid.data.SnapshotInfo
import io.github.hddq.restoid.data.TriggerConditionStatusSnapshot
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.model.AppMetadata
import io.github.hddq.restoid.model.BackupDetail
import io.github.hddq.restoid.model.BackupTag
import io.github.hddq.restoid.model.CalendarBackupMetadata
import io.github.hddq.restoid.model.CallLogsBackupMetadata
import io.github.hddq.restoid.model.ContactsBackupMetadata
import io.github.hddq.restoid.model.CustomDirectory
import io.github.hddq.restoid.model.CustomDirectoryMetadata
import io.github.hddq.restoid.model.DeviceDataMetadata
import io.github.hddq.restoid.model.MaintenanceConfig
import io.github.hddq.restoid.model.RestoidMetadata
import io.github.hddq.restoid.model.Schedule
import io.github.hddq.restoid.model.TaskPreset
import io.github.hddq.restoid.model.TriggerConditions
import io.github.hddq.restoid.test.R as TestR
import io.github.hddq.restoid.ui.home.HomeUiState
import io.github.hddq.restoid.ui.home.SnapshotWithMetadata
import io.github.hddq.restoid.ui.presets.PresetsUiState
import io.github.hddq.restoid.ui.runtasks.RunTasksUiState
import io.github.hddq.restoid.ui.schedules.AddEditScheduleUiState
import io.github.hddq.restoid.ui.settings.AddRepoUiState
import io.github.hddq.restoid.ui.shared.BackupTypes
import io.github.hddq.restoid.ui.shared.OperationProgress
import io.github.hddq.restoid.ui.shared.TaskConfigEditorState
import io.github.hddq.restoid.work.BackupTypeSelection
import io.github.hddq.restoid.work.DeviceDataBackupSelection
import io.github.hddq.restoid.work.ExclusionConflict
import io.github.hddq.restoid.work.RunTasksConfig
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit

internal object ScreenshotFixtures {

    const val HOSTNAME = "Pixel 9"
    const val PRIMARY_REPO_KEY = "sftp:backup@nas.home:/backups/restoid"

    private const val MB = 1024L * 1024L
    private const val KB = 1024L

    val tags: List<BackupTag> = listOf(
        BackupTag(name = "daily", colorArgb = 0xFF00ACC1.toInt()),
        BackupTag(name = "photos", colorArgb = 0xFF43A047.toInt()),
        BackupTag(name = "manual", colorArgb = 0xFFFB8C00.toInt())
    )

    val tagsByName: Map<String, BackupTag> = tags.associateBy { it.name }

    val repositories: List<LocalRepository> = listOf(
        LocalRepository(
            path = PRIMARY_REPO_KEY,
            backendType = RepositoryBackendType.SFTP,
            name = "Home NAS",
            id = "7f50df75aef74c82b91e04a3d12c9f8b"
        ),
        LocalRepository(
            path = "rclone:nextcloud:Backups/Restoid",
            backendType = RepositoryBackendType.RCLONE,
            name = "Nextcloud",
            id = "3c81a9e42b105f7d6e43a01b89d2c4f6"
        )
    )

    fun bundledBinaryStates(targetContext: Context): Pair<ResticState.Installed, ResticState.Installed> {
        val manager = ResticBinaryManager(targetContext)
        runBlocking { manager.checkResticStatus() }
        val restic = manager.resticState.value as? ResticState.Installed
            ?: error("Bundled restic binary unavailable for screenshots: ${manager.resticState.value}")
        val rclone = manager.rcloneState.value as? ResticState.Installed
            ?: error("Bundled rclone binary unavailable for screenshots: ${manager.rcloneState.value}")
        require(restic.fullVersionOutput.isNotBlank()) { "Bundled restic version output was blank" }
        require(rclone.fullVersionOutput.isNotBlank()) { "Bundled rclone version output was blank" }
        return restic to rclone
    }

    fun sampleApps(testContext: Context): List<AppInfo> {
        fun icon(resId: Int): Drawable =
            ContextCompat.getDrawable(testContext, resId) ?: ColorDrawable(Color.DKGRAY)

        return listOf(
            AppInfo("Aegis", "com.beemdevelopment.aegis", "3.4.3", 30403, icon(TestR.drawable.ic_sample_aegis), listOf("/data/app/com.beemdevelopment.aegis/base.apk"), true, 1700000001000L),
            AppInfo("AntennaPod", "de.danoeh.antennapod", "3.8.0", 30800, icon(TestR.drawable.ic_sample_antennapod), listOf("/data/app/de.danoeh.antennapod/base.apk"), true, 1700000002000L),
            AppInfo("BasicSync", "com.chiller3.basicsync", "1.12", 112, icon(TestR.drawable.ic_sample_basicsync), listOf("/data/app/com.chiller3.basicsync/base.apk"), true, 1700000003000L),
            AppInfo("Breezy Weather", "org.breezyweather", "5.4.6", 50406, icon(TestR.drawable.ic_sample_breezy_weather), listOf("/data/app/org.breezyweather/base.apk"), true, 1700000004000L),
            AppInfo("F-Droid", "org.fdroid.fdroid", "1.23.0", 1023000, icon(TestR.drawable.ic_sample_fdroid), listOf("/data/app/org.fdroid.fdroid/base.apk"), false, 1700000005000L),
            AppInfo("Gadgetbridge", "nodomain.freeyourgadget.gadgetbridge", "0.85.0", 850, icon(TestR.drawable.ic_sample_gadgetbridge), listOf("/data/app/nodomain.freeyourgadget.gadgetbridge/base.apk"), true, 1700000006000L),
            AppInfo("Immich", "app.alextran.immich", "1.131.3", 113103, icon(TestR.drawable.ic_sample_immich), listOf("/data/app/app.alextran.immich/base.apk"), true, 1700000007000L),
            AppInfo("KeePassDX", "com.kunzisoft.keepass.libre", "4.1.2", 40102, icon(TestR.drawable.ic_sample_keepassdx), listOf("/data/app/com.kunzisoft.keepass.libre/base.apk"), true, 1700000008000L),
            AppInfo("Navic", "paige.navic", "1.0.0", 10000, icon(TestR.drawable.ic_sample_navic), listOf("/data/app/paige.navic/base.apk"), true, 1700000009000L),
            AppInfo("Obtainium", "dev.imranr.obtainium.fdroid", "1.2.1", 10201, icon(TestR.drawable.ic_sample_obtainium), listOf("/data/app/dev.imranr.obtainium.fdroid/base.apk"), true, 1700000010000L),
            AppInfo("Organic Maps", "app.organicmaps", "2026.10.14", 261014, icon(TestR.drawable.ic_sample_organic_maps), listOf("/data/app/app.organicmaps/base.apk"), true, 1700000011000L),
            AppInfo("Thunderbird", "net.thunderbird.android", "10.0", 100000, icon(TestR.drawable.ic_sample_thunderbird), listOf("/data/app/net.thunderbird.android/base.apk"), false, 1700000012000L)
        )
    }

    fun homeUiState(testContext: Context, resticState: ResticState): HomeUiState {
        val apps = sampleApps(testContext)
        val now = Instant.now()

        val snap1Id = "21aac4a1c455944fdf902abbe174ccb61377b798a28c7c85b70febf1c94d1368"
        val snap2Id = "dc648eda9b3102f8c7a4e5b1903d4f8a21c7e9045a1b6d3c8e2f4a7b9c0d1e2f"
        val snap3Id = "8f3b19c04e72a6d51b8c9f0e2a4d6c8b1e3f5a7c9d0b2e4f6a8c1d3e5f7a9b0c"

        val snap1Apps = listOf(
            apps.first { it.packageName == "paige.navic" },
            apps.first { it.packageName == "com.beemdevelopment.aegis" },
            apps.first { it.packageName == "com.kunzisoft.keepass.libre" },
            apps.first { it.packageName == "com.chiller3.basicsync" },
            apps.first { it.packageName == "nodomain.freeyourgadget.gadgetbridge" },
            apps.first { it.packageName == "dev.imranr.obtainium.fdroid" },
            apps.first { it.packageName == "de.danoeh.antennapod" },
            apps.first { it.packageName == "org.breezyweather" },
            apps.first { it.packageName == "app.alextran.immich" },
            apps.first { it.packageName == "app.organicmaps" }
        )

        val snap2Apps = listOf(
            apps.first { it.packageName == "paige.navic" },
            apps.first { it.packageName == "com.beemdevelopment.aegis" },
            apps.first { it.packageName == "com.kunzisoft.keepass.libre" },
            apps.first { it.packageName == "net.thunderbird.android" },
            apps.first { it.packageName == "org.fdroid.fdroid" }
        )

        val snap1 = SnapshotWithMetadata(
            snapshotInfo = SnapshotInfo(
                id = snap1Id,
                time = now.minus(12, ChronoUnit.MINUTES).toString(),
                paths = listOf("/data/user/0", "/storage/emulated/0/Documents"),
                tags = listOf("restoid", "daily"),
                hostname = HOSTNAME
            ),
            metadata = RestoidMetadata(
                apps = snap1Apps.associate { app ->
                    app.packageName to AppMetadata(
                        size = 18 * MB,
                        types = listOf("apk", "data", "user_de", "permissions"),
                        versionCode = app.versionCode,
                        versionName = app.versionName,
                        appName = app.name
                    )
                },
                customDirectories = mapOf(
                    "/storage/emulated/0/Documents" to CustomDirectoryMetadata(size = 148 * MB, isDirectory = true),
                    "/storage/emulated/0/backups/gadgetbridge" to CustomDirectoryMetadata(size = 64 * MB, isDirectory = true)
                ),
                deviceData = DeviceDataMetadata(
                    contacts = ContactsBackupMetadata(count = 248, size = 1840 * KB, path = "device_data/contacts.vcf"),
                    callLogs = CallLogsBackupMetadata(count = 412, size = 320 * KB, path = "device_data/call_logs.json"),
                    calendar = CalendarBackupMetadata(count = 86, size = 512 * KB, path = "device_data/calendars.ics", calendarCount = 3)
                )
            )
        )

        val snap2 = SnapshotWithMetadata(
            snapshotInfo = SnapshotInfo(
                id = snap2Id,
                time = now.minus(1, ChronoUnit.DAYS).minus(2, ChronoUnit.HOURS).toString(),
                paths = listOf("/storage/emulated/0/DCIM/Camera"),
                tags = listOf("restoid", "photos"),
                hostname = HOSTNAME
            ),
            metadata = RestoidMetadata(
                apps = emptyMap(),
                customDirectories = mapOf(
                    "/storage/emulated/0/DCIM/Camera" to CustomDirectoryMetadata(size = 4280 * MB, isDirectory = true),
                    "/storage/emulated/0/Pictures/Screenshots" to CustomDirectoryMetadata(size = 312 * MB, isDirectory = true)
                )
            )
        )

        val snap3 = SnapshotWithMetadata(
            snapshotInfo = SnapshotInfo(
                id = snap3Id,
                time = now.minus(3, ChronoUnit.DAYS).toString(),
                paths = listOf("/data/user/0"),
                tags = listOf("restoid", "manual"),
                hostname = HOSTNAME
            ),
            metadata = RestoidMetadata(
                apps = snap2Apps.associate { app ->
                    app.packageName to AppMetadata(
                        size = 12 * MB,
                        types = listOf("apk", "data", "permissions"),
                        versionCode = app.versionCode,
                        versionName = app.versionName,
                        appName = app.name
                    )
                },
                customDirectories = mapOf(
                    "/storage/emulated/0/Documents" to CustomDirectoryMetadata(size = 142 * MB, isDirectory = true)
                ),
                deviceData = DeviceDataMetadata(
                    contacts = ContactsBackupMetadata(count = 245, size = 1810 * KB, path = "device_data/contacts.vcf")
                )
            )
        )

        return HomeUiState(
            snapshotsWithMetadata = listOf(snap1, snap2, snap3),
            appInfoMap = mapOf(
                snap1Id to snap1Apps,
                snap2Id to emptyList(),
                snap3Id to snap2Apps
            ),
            selectedRepo = PRIMARY_REPO_KEY,
            resticState = resticState,
            isRepoReady = true,
            repoTagsByName = tagsByName
        )
    }

    fun snapshotDetailsData(testContext: Context): Triple<SnapshotInfo, RestoidMetadata, List<BackupDetail>> {
        val apps = sampleApps(testContext)
        val navic = apps.first { it.packageName == "paige.navic" }
        val aegis = apps.first { it.packageName == "com.beemdevelopment.aegis" }
        val keepass = apps.first { it.packageName == "com.kunzisoft.keepass.libre" }
        val basicSync = apps.first { it.packageName == "com.chiller3.basicsync" }

        val snapshot = SnapshotInfo(
            id = "21aac4a1c455944fdf902abbe174ccb61377b798a28c7c85b70febf1c94d1368",
            time = Instant.now().minus(12, ChronoUnit.MINUTES).toString(),
            paths = listOf("/data/user/0", "/storage/emulated/0/Documents"),
            tags = listOf("restoid", "daily"),
            hostname = HOSTNAME
        )

        val backupDetails = listOf(
            BackupDetail(
                appInfo = navic,
                backedUpItems = listOf("APK", "Data", "Device Protected Data", "Permissions"),
                versionName = navic.versionName,
                versionCode = navic.versionCode,
                backupSize = 46 * MB,
                isDowngrade = false,
                isInstalled = true,
                detailedSizes = mapOf("apk" to 38 * MB, "data" to 7 * MB, "user_de" to 1 * MB)
            ),
            BackupDetail(
                appInfo = aegis,
                backedUpItems = listOf("Data", "Device Protected Data", "Permissions"),
                versionName = aegis.versionName,
                versionCode = aegis.versionCode,
                backupSize = 720 * KB,
                isDowngrade = false,
                isInstalled = true,
                detailedSizes = mapOf("data" to 540 * KB, "user_de" to 180 * KB)
            ),
            BackupDetail(
                appInfo = keepass,
                backedUpItems = listOf("APK", "Data", "Permissions"),
                versionName = keepass.versionName,
                versionCode = keepass.versionCode,
                backupSize = 24 * MB,
                isDowngrade = false,
                isInstalled = true,
                detailedSizes = mapOf("apk" to 21 * MB, "data" to 3 * MB)
            ),
            BackupDetail(
                appInfo = basicSync,
                backedUpItems = listOf("APK", "Data", "Device Protected Data", "Permissions"),
                versionName = basicSync.versionName,
                versionCode = basicSync.versionCode,
                backupSize = 11 * MB,
                isDowngrade = false,
                isInstalled = true,
                detailedSizes = mapOf("apk" to 9 * MB, "data" to 2 * MB)
            )
        )

        val metadata = RestoidMetadata(
            apps = backupDetails.associate { detail ->
                detail.appInfo.packageName to AppMetadata(
                    size = detail.backupSize ?: 0L,
                    types = listOf("apk", "data", "user_de", "permissions"),
                    versionCode = detail.versionCode ?: 1L,
                    versionName = detail.versionName ?: "1.0",
                    appName = detail.appInfo.name,
                    detailedSizes = detail.detailedSizes
                )
            },
            customDirectories = mapOf(
                "/storage/emulated/0/Documents" to CustomDirectoryMetadata(size = 148 * MB, isDirectory = true)
            ),
            deviceData = DeviceDataMetadata(
                contacts = ContactsBackupMetadata(count = 248, size = 1840 * KB, path = "device_data/contacts.vcf"),
                callLogs = CallLogsBackupMetadata(count = 412, size = 320 * KB, path = "device_data/call_logs.json"),
                calendar = CalendarBackupMetadata(count = 86, size = 512 * KB, path = "device_data/calendars.ics", calendarCount = 3)
            )
        )

        return Triple(snapshot, metadata, backupDetails)
    }

    fun taskPresets(): List<TaskPreset> {
        val nowMs = System.currentTimeMillis()
        return listOf(
            TaskPreset(
                id = "preset-nightly",
                name = "Nightly Full Backup",
                config = RunTasksConfig(
                    backupEnabled = true,
                    backupTypes = BackupTypeSelection(),
                    selectedPackageNames = listOf(
                        "com.beemdevelopment.aegis",
                        "de.danoeh.antennapod",
                        "com.chiller3.basicsync",
                        "org.breezyweather",
                        "nodomain.freeyourgadget.gadgetbridge",
                        "app.alextran.immich",
                        "com.kunzisoft.keepass.libre",
                        "paige.navic",
                        "dev.imranr.obtainium.fdroid",
                        "app.organicmaps"
                    ),
                    customDirectories = listOf("/storage/emulated/0/Documents", "/storage/emulated/0/backups/gadgetbridge"),
                    excludedCustomDirectories = listOf("/data/user/0/paige.navic/files/downloads"),
                    deviceData = DeviceDataBackupSelection(contacts = true, callLogs = true, calendar = true),
                    forgetSnapshots = true,
                    pruneRepo = false,
                    checkRepo = true,
                    backupTags = listOf("daily")
                ),
                lastRunTimestamp = nowMs - 12 * 60 * 1000L
            ),
            TaskPreset(
                id = "preset-photos",
                name = "Photos & Media",
                config = RunTasksConfig(
                    backupEnabled = false,
                    customDirectories = listOf("/storage/emulated/0/DCIM/Camera", "/storage/emulated/0/Pictures/Screenshots"),
                    backupTags = listOf("photos")
                ),
                lastRunTimestamp = nowMs - 26 * 60 * 60 * 1000L
            ),
            TaskPreset(
                id = "preset-maintenance",
                name = "Weekly Maintenance",
                config = RunTasksConfig(
                    backupEnabled = false,
                    unlockRepo = true,
                    forgetSnapshots = true,
                    pruneRepo = true,
                    checkRepo = true,
                    readData = false,
                    keepLast = 5,
                    keepDaily = 7,
                    keepWeekly = 4,
                    keepMonthly = 6
                ),
                lastRunTimestamp = nowMs - 4 * 24 * 60 * 60 * 1000L
            )
        )
    }

    fun runTasksUiState(testContext: Context): RunTasksUiState {
        val apps = sampleApps(testContext)
        val navic = apps.first { it.packageName == "paige.navic" }
        val presets = taskPresets()

        val editor = TaskConfigEditorState(
            apps = apps,
            isLoadingApps = false,
            backupEnabled = true,
            backupTypes = BackupTypes(),
            customDirectoriesBackupEnabled = true,
            deviceDataBackupEnabled = true,
            contactsBackupEnabled = true,
            callLogsBackupEnabled = true,
            calendarBackupEnabled = true,
            customDirectories = listOf(
                CustomDirectory("/storage/emulated/0/Documents", isSelected = true),
                CustomDirectory("/storage/emulated/0/backups/gadgetbridge", isSelected = true)
            ),
            excludedCustomDirectories = listOf(
                CustomDirectory("/data/user/0/paige.navic/files/downloads", isSelected = true)
            ),
            exclusionConflicts = listOf(
                ExclusionConflict(
                    app = navic,
                    conflictingPath = "/data/user/0/paige.navic/files/downloads",
                    appPath = "/data/user/0/paige.navic"
                )
            ),
            maintenance = MaintenanceConfig(
                unlockRepo = false,
                forgetSnapshots = true,
                pruneRepo = false,
                checkRepo = true,
                keepLast = 5,
                keepDaily = 7,
                keepWeekly = 4,
                keepMonthly = 6
            ),
            backupTags = listOf("daily"),
            availableTags = tags
        )

        return RunTasksUiState(
            editor = editor,
            availablePresets = presets,
            selectedPresetId = presets.first().id
        )
    }

    fun presetsUiState(): PresetsUiState {
        val presets = taskPresets()
        val schedules = mapOf(
            "preset-nightly" to listOf(
                Schedule(
                    id = "sched-nightly",
                    presetId = "preset-nightly",
                    name = "Nightly",
                    intervalHours = 24,
                    triggerConditions = TriggerConditions(
                        requireBatteryNotLow = true,
                        requireCharging = true,
                        requireUnmeteredNetwork = true
                    )
                )
            ),
            "preset-maintenance" to listOf(
                Schedule(
                    id = "sched-weekly",
                    presetId = "preset-maintenance",
                    name = "Weekly Prune",
                    intervalHours = 168
                )
            )
        )
        return PresetsUiState(
            presets = presets,
            schedulesByPresetId = schedules,
            selectedRepoKey = PRIMARY_REPO_KEY,
            isLoading = false,
            isOperationRunning = false
        )
    }

    fun editScheduleUiState(): Pair<AddEditScheduleUiState, TriggerConditionStatusSnapshot> {
        val presets = taskPresets()
        val state = AddEditScheduleUiState(
            id = "sched-nightly",
            presetId = presets.first().id,
            name = "Nightly",
            intervalHours = 24,
            isEnabled = true,
            triggerConditions = TriggerConditions(
                requireBatteryNotLow = true,
                requireCharging = true,
                requireUnmeteredNetwork = true
            ),
            lastRunTimestamp = System.currentTimeMillis() - 12 * 60 * 1000L,
            availablePresets = presets
        )
        val conditionStatus = TriggerConditionStatusSnapshot(
            battery = BatteryConditionStatus(
                levelPercent = 94,
                isBatteryNotLow = true,
                chargingState = ChargingState.CHARGING,
                powerSource = ChargingPowerSource.AC
            ),
            network = NetworkConditionStatus(
                isConnected = true,
                isUnmetered = true,
                transport = NetworkTransport.WIFI
            )
        )
        return state to conditionStatus
    }

    fun operationProgress(): OperationProgress = OperationProgress(
        stageTitle = "[2/5] Backing up items...",
        overallPercentage = 0.34f,
        stagePercentage = 0.62f,
        totalFiles = 4270,
        filesProcessed = 2648,
        totalBytes = 2_480_000_000L,
        bytesProcessed = 1_537_000_000L,
        currentFile = "/data/user/0/paige.navic/databases/navic.db",
        elapsedTime = 18L,
        isFinished = false
    )

    fun addRepoDialogState(): AddRepoUiState = AddRepoUiState(
        backendType = RepositoryBackendType.LOCAL,
        path = "/storage/emulated/0/Restoid",
        password = "",
        savePassword = true,
        showDialog = true
    )
}
