package app.restoid.ui.restore

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.restoid.data.AppInfoRepository
import app.restoid.data.NotificationRepository
import app.restoid.data.RepositoriesRepository
import app.restoid.data.ResticRepository
import app.restoid.data.ResticState
import app.restoid.data.SnapshotInfo
import app.restoid.model.AppInfo
import app.restoid.model.BackupDetail
import app.restoid.ui.shared.OperationProgress
import app.restoid.util.ResticOutputParser
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

                if (repoPath != null && password != null) {
                    val result = resticRepository.getSnapshots(repoPath, password)
                    result.fold(
                        onSuccess = { snapshots ->
                            val foundSnapshot = snapshots.find { it.id.startsWith(snapshotId) }
                            _snapshot.value = foundSnapshot
                            foundSnapshot?.let { processSnapshot(it) }
                        },
                        onFailure = { _error.value = it.message }
                    )
                } else {
                    _error.value = "Repository or password not found"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun processSnapshot(snapshot: SnapshotInfo) {
        val packageNames = snapshot.tags.map { it.split('|').first() }

        if (packageNames.isEmpty()) {
            _backupDetails.value = emptyList()
            return
        }

        val appInfos = appInfoRepository.getAppInfoForPackages(packageNames)
        val appInfoMap = appInfos.associateBy { it.packageName }

        val details = snapshot.tags.map { tag ->
            val parts = tag.split('|')
            val packageName = parts.getOrNull(0) ?: ""
            val versionName = parts.getOrNull(1)
            val versionCode = parts.getOrNull(2)?.toLongOrNull()
            val backupSize = parts.getOrNull(3)?.toLongOrNull()

            val appInfo = appInfoMap[packageName]
            val items = findBackedUpItems(snapshot, packageName)

            val isInstalled = appInfo != null
            val isDowngrade = if (isInstalled && versionCode != null) {
                versionCode < appInfo!!.versionCode
            } else {
                false
            }

            val finalAppInfo = appInfo ?: AppInfo(
                name = packageName,
                packageName = packageName,
                versionName = versionName ?: "N/A",
                versionCode = versionCode ?: 0L,
                icon = application.packageManager.defaultActivityIcon,
                apkPaths = emptyList(),
                isSelected = true
            )

            BackupDetail(finalAppInfo, items, versionName, versionCode, backupSize, isDowngrade, isInstalled)
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
            if (apkRestoreSelected) stageList.add("Install APKs")
            if (anyDataRestoreSelected) stageList.add("Restore Data")
            stageList.add("Cleanup")
            val totalStages = stageList.size
            var currentStage = 1

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
                val restoreStageTitle = "[${currentStage++}/${totalStages}] ${stageList[0]}"
                val includes = pathsToRestore.joinToString(" ") { "--include '$it'" }
                val env = "HOME='${application.filesDir.absolutePath}' TMPDIR='${application.cacheDir.absolutePath}'"
                val command = "$env RESTIC_PASSWORD_FILE='${passwordFile.absolutePath}' ${resticState.path} -r '$selectedRepoPath' restore ${currentSnapshot.id} --target '${tempRestoreDir.absolutePath}' $includes --json"

                val stdoutCallback = object : CallbackList<String>() {
                    override fun onAddElement(line: String) {
                        ResticOutputParser.parse(line)?.let { progressUpdate ->
                            val elapsedTime = (System.currentTimeMillis() - startTime) / 1000
                            val newProgress = progressUpdate.copy(
                                elapsedTime = elapsedTime,
                                stageTitle = restoreStageTitle,
                                stagePercentage = progressUpdate.stagePercentage,
                                overallPercentage = (currentStage - 1 + progressUpdate.stagePercentage) / totalStages
                            )
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

                // --- Stage 2 & 3: Process each app (Install APKs and/or Restore Data) ---
                for ((index, detail) in selectedApps.withIndex()) {
                    val appName = detail.appInfo.name
                    var appProcessSuccess = true

                    val processProgress = (index + 1).toFloat() / selectedApps.size.toFloat()

                    // Install APK if selected
                    if (apkRestoreSelected) {
                        val installStageTitle = "[${currentStage}/${totalStages}] ${stageList.indexOf("Install APKs") + 1}/${totalStages}] Install APKs"
                        _restoreProgress.value = _restoreProgress.value.copy(
                            stageTitle = installStageTitle,
                            stagePercentage = processProgress,
                            overallPercentage = (stageList.indexOf("Install APKs") + processProgress) / totalStages,
                            currentFile = appName
                        )
                        notificationRepository.showOperationProgressNotification("Restore", _restoreProgress.value)

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

                    // Restore Data if selected and APK install was successful (or skipped)
                    if (appProcessSuccess && anyDataRestoreSelected) {
                        val dataStageIdx = stageList.indexOf("Restore Data")
                        if (dataStageIdx != -1) {
                            val dataStageTitle = "[${dataStageIdx + 1}/${totalStages}] Restore Data"
                            _restoreProgress.value = _restoreProgress.value.copy(
                                stageTitle = dataStageTitle,
                                stagePercentage = processProgress,
                                overallPercentage = (dataStageIdx + processProgress) / totalStages,
                                currentFile = "$appName (data)"
                            )
                            notificationRepository.showOperationProgressNotification("Restore", _restoreProgress.value)
                        }

                        if (!moveRestoredDataAndFixPerms(detail, tempRestoreDir, restoreTypes.value)) {
                            // This indicates a partial failure; data didn't restore correctly.
                            // We will still count the app as a "success" if the APK installed, but log this failure.
                            failureDetails.add("$appName: Data restore failed or incomplete. Check logs.")
                        }
                    }

                    if (appProcessSuccess) successes++ else failures++
                }


                // --- Final Stage: Cleanup ---
                val cleanupStageTitle = "[${totalStages}/${totalStages}] Cleanup"
                _restoreProgress.update { it.copy(stageTitle = cleanupStageTitle, stagePercentage = 0f, overallPercentage = (totalStages - 1).toFloat() / totalStages) }
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
