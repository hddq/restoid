package io.github.hddq.restoid.ui.restore

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hddq.restoid.data.AppInfoRepository
import io.github.hddq.restoid.data.MetadataRepository
import io.github.hddq.restoid.data.NotificationRepository
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.ResticRepository
import io.github.hddq.restoid.data.ResticState
import io.github.hddq.restoid.data.SnapshotInfo
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.model.BackupDetail
import io.github.hddq.restoid.model.RestoidMetadata
import io.github.hddq.restoid.ui.shared.OperationProgress
import io.github.hddq.restoid.util.ResticOutputParser
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

// UI state for selecting what to restore
data class RestoreTypes(
    val apk: Boolean = true,
    val data: Boolean = true,
    val deviceProtectedData: Boolean = true,
    val externalData: Boolean = false,
    val obb: Boolean = false,
    val media: Boolean = false
)

class RestoreViewModel(
    private val application: Application,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticRepository: ResticRepository,
    private val appInfoRepository: AppInfoRepository,
    private val notificationRepository: NotificationRepository,
    private val metadataRepository: MetadataRepository,
    val snapshotId: String
) : ViewModel() {

    private val _snapshot = MutableStateFlow<SnapshotInfo?>(null)
    val snapshot = _snapshot.asStateFlow()

    private val _backupDetails = MutableStateFlow<List<BackupDetail>>(emptyList())
    val backupDetails = _backupDetails.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _restoreTypes = MutableStateFlow(RestoreTypes())
    val restoreTypes = _restoreTypes.asStateFlow()

    private val _allowDowngrade = MutableStateFlow(false)
    val allowDowngrade = _allowDowngrade.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring = _isRestoring.asStateFlow()

    private val _restoreProgress = MutableStateFlow(OperationProgress())
    val restoreProgress = _restoreProgress.asStateFlow()

    private var restoreJob: Job? = null

    init {
        if (snapshotId.isNotBlank()) {
            loadSnapshotDetails(snapshotId)
        }
    }

    private fun loadSnapshotDetails(snapshotId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _backupDetails.value = emptyList()
            try {
                val repoPath = repositoriesRepository.selectedRepository.first()
                val password = repoPath?.let { repositoriesRepository.getRepositoryPassword(it) }
                val repo = repositoriesRepository.repositories.value.find { it.path == repoPath }

                if (repoPath != null && password != null && repo?.id != null) {
                    val result = resticRepository.getSnapshots(repoPath, password)
                    result.fold(
                        onSuccess = { snapshots ->
                            val foundSnapshot = snapshots.find { it.id.startsWith(snapshotId) }
                            _snapshot.value = foundSnapshot
                            if (foundSnapshot != null) {
                                val metadata = metadataRepository.getMetadataForSnapshot(repo.id, foundSnapshot.id)
                                processSnapshot(foundSnapshot, metadata)
                            } else {
                                _error.value = "Snapshot not found."
                            }
                        },
                        onFailure = { _error.value = it.message }
                    )
                } else {
                    _error.value = "Repository, password, or repo ID not found"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun processSnapshot(snapshot: SnapshotInfo, metadata: RestoidMetadata?) {
        val appMetadataMap = metadata?.apps ?: emptyMap()
        val packageNames = appMetadataMap.keys.toList()


        if (packageNames.isEmpty()) {
            _backupDetails.value = emptyList()
            return
        }

        val appInfos = appInfoRepository.getAppInfoForPackages(packageNames)
        val appInfoMap = appInfos.associateBy { it.packageName }

        val details = appMetadataMap.map { (packageName, appMeta) ->
            val appInfo = appInfoMap[packageName]
            val items = findBackedUpItems(snapshot, packageName)

            val isInstalled = appInfo != null
            val isDowngrade = if (isInstalled) {
                appMeta.versionCode < appInfo!!.versionCode
            } else {
                false
            }

            val finalAppInfo = appInfo ?: AppInfo(
                name = packageName,
                packageName = packageName,
                versionName = appMeta.versionName,
                versionCode = appMeta.versionCode,
                icon = application.packageManager.defaultActivityIcon,
                apkPaths = emptyList(),
                isSelected = true
            )

            BackupDetail(finalAppInfo, items, appMeta.versionName, appMeta.versionCode, appMeta.size, isDowngrade, isInstalled)
        }
        _backupDetails.value = details.sortedBy { it.appInfo.name.lowercase() }
    }


    private fun findBackedUpItems(snapshot: SnapshotInfo, pkg: String): List<String> {
        val items = mutableListOf<String>()
        snapshot.paths.forEach { path ->
            when {
                (path.startsWith("/data/app/") && path.contains("/${pkg}-")) -> if (!items.contains("APK")) items.add("APK")
                path == "/data/data/$pkg" -> if (!items.contains("Data")) items.add("Data")
                path == "/data/user_de/0/$pkg" -> if (!items.contains("Device Protected Data")) items.add("Device Protected Data")
                path == "/storage/emulated/0/Android/data/$pkg" -> if (!items.contains("External Data")) items.add("External Data")
                path == "/storage/emulated/0/Android/obb/$pkg" -> if (!items.contains("OBB")) items.add("OBB")
                path == "/storage/emulated/0/Android/media/$pkg" -> if (!items.contains("Media")) items.add("Media")
            }
        }
        return if (items.isNotEmpty()) items else listOf("Unknown items")
    }

    private fun generatePathsToRestore(
        selectedApps: List<BackupDetail>,
        snapshot: SnapshotInfo
    ): List<String> {
        val paths = mutableListOf<String>()
        val types = restoreTypes.value

        selectedApps.forEach { detail ->
            val pkg = detail.appInfo.packageName
            // A helper to find a path and add it if it exists in the snapshot's path list
            fun addPathIfExists(path: String) {
                if (snapshot.paths.contains(path)) {
                    paths.add(path)
                }
            }
            // A helper for APKs since the path is not exact
            fun addApkPathIfExists() {
                snapshot.paths.find { it.startsWith("/data/app/") && it.contains("/$pkg-") }?.let { paths.add(it) }
            }

            if (types.apk) addApkPathIfExists()
            if (types.data) addPathIfExists("/data/data/$pkg")
            if (types.deviceProtectedData) addPathIfExists("/data/user_de/0/$pkg")
            if (types.externalData) addPathIfExists("/storage/emulated/0/Android/data/$pkg")
            if (types.obb) addPathIfExists("/storage/emulated/0/Android/obb/$pkg")
            if (types.media) addPathIfExists("/storage/emulated/0/Android/media/$pkg")
        }
        return paths.distinct()
    }

    /**
     * Copies restored data from a temporary location to its final destination and fixes file ownership.
     * This is crucial for app data to be accessible by the app after restoration.
     *
     * @return `true` if all operations for the app succeeded, `false` otherwise.
     */
    private fun moveRestoredDataAndFixPerms(
        detail: BackupDetail,
        tempRestoreDir: File,
        types: RestoreTypes
    ): Boolean {
        val pkg = detail.appInfo.packageName
        var allSucceeded = true
        Log.d("RestoreViewModel", "Processing data restore for $pkg")

        // First, get the correct user:group for the app. The app MUST be installed for this to work.
        val ownerResult = Shell.cmd("stat -c '%U:%G' /data/data/$pkg").exec()
        if (!ownerResult.isSuccess || ownerResult.out.isEmpty()) {
            Log.e("RestoreViewModel", "Could not get owner for $pkg. Aborting data restore for this app.")
            return false // This is a hard failure for data restore.
        }
        val owner = ownerResult.out.first().trim()
        Log.d("RestoreViewModel", "Owner for $pkg is $owner")

        // Define source -> destination mappings for all possible data types
        val dataMappings = mutableListOf<Pair<File, String>>()
        if (types.data) {
            dataMappings.add(File(tempRestoreDir, "data/data/$pkg") to "/data/data/$pkg")
        }
        if (types.deviceProtectedData) {
            dataMappings.add(File(tempRestoreDir, "data/user_de/0/$pkg") to "/data/user_de/0/$pkg")
        }
        if (types.externalData) {
            dataMappings.add(File(tempRestoreDir, "storage/emulated/0/Android/data/$pkg") to "/storage/emulated/0/Android/data/$pkg")
        }
        if (types.obb) {
            dataMappings.add(File(tempRestoreDir, "storage/emulated/0/Android/obb/$pkg") to "/storage/emulated/0/Android/obb/$pkg")
        }
        if (types.media) {
            dataMappings.add(File(tempRestoreDir, "storage/emulated/0/Android/media/$pkg") to "/storage/emulated/0/Android/media/$pkg")
        }

        // It's a good practice to stop the app before replacing its data
        Shell.cmd("am force-stop $pkg").exec()

        for ((source, destination) in dataMappings) {
            if (source.exists()) {
                Log.d("RestoreViewModel", "Restoring from '${source.absolutePath}' to '$destination'")

                // Ensure destination directory exists
                Shell.cmd("mkdir -p '$destination'").exec()

                // Copy the restored files over. `cp -a` preserves permissions and timestamps from the archive.
                val copyResult = Shell.cmd("cp -a '${source.absolutePath}/.' '$destination/'").exec()
                if (!copyResult.isSuccess) {
                    Log.e("RestoreViewModel", "Failed to copy data for $pkg to '$destination': ${copyResult.err.joinToString("\n")}")
                    allSucceeded = false
                    continue // Skip to the next data type for this app
                }

                // IMPORTANT: Recursively change ownership of the destination directory to the app's user.
                val chownResult = Shell.cmd("chown -R $owner '$destination'").exec()
                if (!chownResult.isSuccess) {
                    Log.w("RestoreViewModel", "Failed to chown '$destination' for $pkg: ${chownResult.err.joinToString("\n")}")
                    allSucceeded = false // Mark as partial failure
                } else {
                    Log.i("RestoreViewModel", "Successfully restored and chowned '$destination' for $pkg")
                }
            } else {
                Log.d("RestoreViewModel", "Source path '${source.absolutePath}' does not exist in restore cache. Skipping.")
            }
        }
        return allSucceeded
    }


    fun startRestore() {
        if (_isRestoring.value) return

        restoreJob = viewModelScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            _isRestoring.value = true
            _restoreProgress.value = OperationProgress()

            var tempRestoreDir: File? = null
            var passwordFile: File? = null
            var successes = 0
            var failures = 0
            val failureDetails = mutableListOf<String>()

            val apkRestoreSelected = restoreTypes.value.apk
            val anyDataRestoreSelected = with(restoreTypes.value) { data || deviceProtectedData || externalData || obb || media }

            // Dynamic stage counting
            val stageList = mutableListOf("Restore Files")
            if (apkRestoreSelected || anyDataRestoreSelected) {
                stageList.add("Processing Apps")
            }
            stageList.add("Cleanup")
            val totalStages = stageList.size
            var currentStageNum = 1

            try {
                // --- Pre-flight checks ---
                val resticState = resticRepository.resticState.value
                val selectedRepoPath = repositoriesRepository.selectedRepository.value
                val currentSnapshot = _snapshot.value
                val selectedApps = _backupDetails.value.filter { it.appInfo.isSelected && (_allowDowngrade.value || !it.isDowngrade) }

                if (resticState !is ResticState.Installed || selectedRepoPath == null || currentSnapshot == null) {
                    throw IllegalStateException("Pre-restore checks failed: Restic not ready or no repo/snapshot selected.")
                }
                if (selectedApps.isEmpty()) {
                    throw IllegalStateException("No apps selected for restore.")
                }
                val password = repositoriesRepository.getRepositoryPassword(selectedRepoPath)
                    ?: throw IllegalStateException("Password not found for repository.")

                passwordFile = File.createTempFile("restic-pass", ".tmp", application.cacheDir)
                passwordFile.writeText(password)

                tempRestoreDir = File(application.cacheDir, "restic-restore-${System.currentTimeMillis()}").also { it.mkdirs() }

                // --- Generate list of paths to restore ---
                val pathsToRestore = generatePathsToRestore(selectedApps, currentSnapshot)

                if (pathsToRestore.isEmpty()) {
                    throw IllegalStateException("No files found in the snapshot for the selected apps and types.")
                }

                // --- Stage 1: Execute restic restore ---
                val restoreStageTitle = "[${currentStageNum}/${totalStages}] ${stageList[0]}"
                val includes = pathsToRestore.joinToString(" ") { "--include '$it'" }
                val env = "HOME='${application.filesDir.absolutePath}' TMPDIR='${application.cacheDir.absolutePath}'"
                val command = "$env RESTIC_PASSWORD_FILE='${passwordFile.absolutePath}' ${resticState.path} -r '$selectedRepoPath' restore ${currentSnapshot.id} --target '${tempRestoreDir.absolutePath}' $includes --json"

                val stdoutCallback = object : CallbackList<String>() {
                    override fun onAddElement(line: String) {
                        ResticOutputParser.parse(line)?.let { progressUpdate ->
                            val elapsedTime = (System.currentTimeMillis() - startTime) / 1000
                            val newProgress: OperationProgress

                            if (progressUpdate.isFinished) {
                                // This is the summary from restic, marks the end of THIS STAGE.
                                newProgress = _restoreProgress.value.copy(
                                    isFinished = false, // DO NOT finish the whole operation
                                    stageTitle = restoreStageTitle,
                                    stagePercentage = 1.0f,
                                    overallPercentage = currentStageNum.toFloat() / totalStages.toFloat(),
                                    elapsedTime = elapsedTime,
                                    finalSummary = "" // Clear summary from parser, we'll build our own at the end
                                )
                            } else {
                                // Regular progress update for the current stage.
                                newProgress = progressUpdate.copy(
                                    stageTitle = restoreStageTitle,
                                    overallPercentage = ((currentStageNum - 1) + progressUpdate.stagePercentage) / totalStages.toFloat(),
                                    elapsedTime = elapsedTime
                                )
                            }
                            _restoreProgress.value = newProgress
                            notificationRepository.showOperationProgressNotification("Restore", newProgress)
                        }
                    }
                }
                val stderr = mutableListOf<String>()
                val restoreResult = Shell.cmd(command).to(stdoutCallback, stderr).exec()

                if (!restoreResult.isSuccess) {
                    val errorOutput = stderr.joinToString("\n")
                    throw IllegalStateException(if (errorOutput.isEmpty()) "Restic restore command failed with code ${restoreResult.code}." else errorOutput)
                }

                // --- Stage 2: Processing Apps (Install + Data Restore) ---
                val processingAppsStageIndex = stageList.indexOf("Processing Apps")
                if (processingAppsStageIndex != -1) {
                    currentStageNum = processingAppsStageIndex + 1
                    val processingStageTitle = "[${currentStageNum}/${totalStages}] Processing Apps"

                    for ((index, detail) in selectedApps.withIndex()) {
                        val appName = detail.appInfo.name
                        var appProcessSuccess = true
                        val processProgress = (index + 1).toFloat() / selectedApps.size.toFloat()

                        // Single progress update for the combined "Processing Apps" stage
                        _restoreProgress.update {
                            it.copy(
                                stageTitle = processingStageTitle,
                                stagePercentage = processProgress,
                                overallPercentage = (processingAppsStageIndex + processProgress) / totalStages.toFloat(),
                                currentFile = appName,
                                filesProcessed = index + 1,
                                totalFiles = selectedApps.size
                            )
                        }
                        notificationRepository.showOperationProgressNotification("Restore", _restoreProgress.value)

                        // Install APK if selected
                        if (apkRestoreSelected) {
                            val originalApkPath = pathsToRestore.find { it.startsWith("/data/app/") && it.contains("/${detail.appInfo.packageName}-") }
                            val restoredContentDir = originalApkPath?.let { File(tempRestoreDir, it.drop(1)) }
                            val apkFiles = restoredContentDir?.walk()?.filter { it.isFile && it.extension == "apk" }?.toList() ?: emptyList()

                            if (apkFiles.isNotEmpty()) {
                                val installFlags = if (_allowDowngrade.value) "-r -d" else "-r"
                                val createResult = Shell.cmd("pm install-create $installFlags").exec()
                                val sessionId = createResult.out.firstOrNull()?.substringAfterLast('[')?.substringBefore(']')

                                if (createResult.isSuccess && sessionId != null) {
                                    var allWritesSucceeded = true
                                    apkFiles.forEachIndexed { splitIndex, apkFile ->
                                        val writeCmd = "pm install-write -S ${apkFile.length()} $sessionId '${splitIndex}_${apkFile.name}' '${apkFile.absolutePath}'"
                                        if (!Shell.cmd(writeCmd).exec().isSuccess) {
                                            allWritesSucceeded = false
                                            return@forEachIndexed
                                        }
                                    }
                                    if (allWritesSucceeded) {
                                        val commitResult = Shell.cmd("pm install-commit $sessionId").exec()
                                        if (!commitResult.isSuccess || !commitResult.out.any { it.contains("Success") }) {
                                            appProcessSuccess = false
                                            failureDetails.add("$appName: Install commit failed: ${commitResult.err.joinToString(" ")}")
                                        }
                                    } else {
                                        appProcessSuccess = false
                                        failureDetails.add("$appName: Failed to write APK splits.")
                                        Shell.cmd("pm install-abandon $sessionId").exec()
                                    }
                                } else {
                                    appProcessSuccess = false
                                    failureDetails.add("$appName: Failed to create install session: ${createResult.err.joinToString(" ")}")
                                }
                            } else {
                                appProcessSuccess = false
                                failureDetails.add("$appName: No APK files found in restored data.")
                            }
                        }

                        // Restore Data if selected and app is considered "successfully processed" so far
                        if (appProcessSuccess && anyDataRestoreSelected) {
                            if (!moveRestoredDataAndFixPerms(detail, tempRestoreDir, restoreTypes.value)) {
                                failureDetails.add("$appName: Data restore failed or incomplete. Check logs.")
                            }
                        }

                        if (appProcessSuccess) successes++ else failures++
                    }
                } else {
                    // This case handles when only "Restore Files" is selected, and no further app processing is needed.
                    // We can consider all selected apps as "processed" for the summary.
                    successes = selectedApps.size
                }


                // --- Final Stage: Cleanup ---
                currentStageNum = totalStages
                val cleanupStageTitle = "[${currentStageNum}/${totalStages}] Cleanup"
                _restoreProgress.update { it.copy(stageTitle = cleanupStageTitle, stagePercentage = 0f, overallPercentage = (totalStages - 1).toFloat() / totalStages.toFloat()) }
                notificationRepository.showOperationProgressNotification("Restore", _restoreProgress.value)

                var cleanupMessage = ""
                tempRestoreDir.let { dir ->
                    val cleanupSuccess = Shell.cmd("rm -rf '${dir.absolutePath}'").exec().isSuccess
                    cleanupMessage = if (cleanupSuccess) "\n\nTemporary data cleaned up." else "\n\nWarning: Failed to clean up temp data."
                }
                _restoreProgress.update { it.copy(stagePercentage = 1f, overallPercentage = 1f) }


                // --- Final Summary ---
                val finalElapsedTime = (System.currentTimeMillis() - startTime) / 1000
                val summary = buildString {
                    append("Restore finished in ${formatElapsedTime(finalElapsedTime)}. ")
                    append("Successfully processed $successes app(s).")
                    if (failures > 0) append(" Failed to restore $failures app(s).")
                    append(cleanupMessage)
                    if (failureDetails.isNotEmpty()) {
                        append("\n\nDetails:\n- ${failureDetails.joinToString("\n- ")}")
                    }
                }

                _restoreProgress.value = OperationProgress(
                    isFinished = true,
                    finalSummary = summary,
                    error = if (failures > 0) failureDetails.joinToString(", ") else null,
                    elapsedTime = finalElapsedTime,
                    filesProcessed = successes,
                    totalFiles = selectedApps.size
                )
                notificationRepository.showOperationFinishedNotification("Restore", failures == 0, summary)

            } catch (e: Exception) {
                _restoreProgress.value = _restoreProgress.value.copy(
                    isFinished = true,
                    error = "A fatal error occurred: ${e.message}",
                    finalSummary = "A fatal error occurred: ${e.message}",
                    elapsedTime = (System.currentTimeMillis() - startTime) / 1000
                )
                notificationRepository.showOperationFinishedNotification("Restore", false, _restoreProgress.value.finalSummary ?: "Restore failed.")
            } finally {
                passwordFile?.delete()
                tempRestoreDir?.deleteRecursively() // Extra cleanup just in case
                _isRestoring.value = false
            }
        }
    }

    private fun formatElapsedTime(seconds: Long): String {
        val hours = java.util.concurrent.TimeUnit.SECONDS.toHours(seconds)
        val minutes = java.util.concurrent.TimeUnit.SECONDS.toMinutes(seconds) % 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }


    fun onDone() {
        _restoreProgress.value = OperationProgress()
    }

    fun toggleRestoreAppSelection(packageName: String) {
        _backupDetails.update { currentDetails ->
            currentDetails.map { detail ->
                if (detail.appInfo.packageName == packageName) {
                    // Prevent selecting a downgrade if not allowed
                    val canBeSelected = _allowDowngrade.value || !detail.isDowngrade
                    if (canBeSelected) {
                        detail.copy(appInfo = detail.appInfo.copy(isSelected = !detail.appInfo.isSelected))
                    } else {
                        detail // Return unchanged if selection is not allowed
                    }
                } else {
                    detail
                }
            }
        }
    }

    fun toggleAllRestoreSelection() {
        _backupDetails.update { currentDetails ->
            // If any app is not selected, the action is to select all possible. Otherwise, deselect all.
            val shouldSelectAll = currentDetails.any { !it.appInfo.isSelected }
            currentDetails.map { detail ->
                val canBeSelected = _allowDowngrade.value || !detail.isDowngrade
                detail.copy(appInfo = detail.appInfo.copy(isSelected = if (shouldSelectAll) canBeSelected else false))
            }
        }
    }

    fun setAllowDowngrade(value: Boolean) {
        _allowDowngrade.value = value
        // If downgrades are now disallowed, deselect any selected apps that are downgrades
        if (!value) {
            _backupDetails.update { currentDetails ->
                currentDetails.map { detail ->
                    if (detail.isDowngrade) {
                        detail.copy(appInfo = detail.appInfo.copy(isSelected = false))
                    } else {
                        detail
                    }
                }
            }
        }
    }

    fun setRestoreApk(value: Boolean) = _restoreTypes.update { it.copy(apk = value) }
    fun setRestoreData(value: Boolean) = _restoreTypes.update { it.copy(data = value) }
    fun setRestoreDeviceProtectedData(value: Boolean) = _restoreTypes.update { it.copy(deviceProtectedData = value) }
    fun setRestoreExternalData(value: Boolean) = _restoreTypes.update { it.copy(externalData = value) }
    fun setRestoreObb(value: Boolean) = _restoreTypes.update { it.copy(obb = value) }
    fun setRestoreMedia(value: Boolean) = _restoreTypes.update { it.copy(media = value) }
}
