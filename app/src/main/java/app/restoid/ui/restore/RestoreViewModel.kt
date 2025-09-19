package app.restoid.ui.restore

import android.app.Application
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
            // Stages: 1. Restore Files, 2. Install APKs (optional), 3. Cleanup
            val totalStages = if (apkRestoreSelected) 3 else 2

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

                // --- Generate list of paths to restore based on user selection ---
                val pathsToRestore = generatePathsToRestore(selectedApps, currentSnapshot)

                if (pathsToRestore.isEmpty()) {
                    throw IllegalStateException("No files found in the snapshot for the selected apps and types.")
                }

                // --- Stage 1: Execute restic restore with real-time progress ---
                val includes = pathsToRestore.joinToString(" ") { "--include '$it'" }
                val env = "HOME='${application.filesDir.absolutePath}' TMPDIR='${application.cacheDir.absolutePath}'"
                val command = "$env RESTIC_PASSWORD_FILE='${passwordFile.absolutePath}' ${resticState.path} -r '$selectedRepoPath' restore ${currentSnapshot.id} --target '${tempRestoreDir.absolutePath}' $includes --json"

                val stdoutCallback = object : CallbackList<String>() {
                    override fun onAddElement(line: String) {
                        ResticOutputParser.parse(line)?.let { progressUpdate ->
                            val elapsedTime = (System.currentTimeMillis() - startTime) / 1000
                            val newProgress = progressUpdate.copy(
                                elapsedTime = elapsedTime,
                                stageTitle = "[1/$totalStages] Restoring Files",
                                stagePercentage = progressUpdate.stagePercentage,
                                overallPercentage = progressUpdate.stagePercentage / totalStages
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
                    val errorMsg = if (errorOutput.isEmpty()) "Restic restore command failed with exit code ${restoreResult.code}." else errorOutput
                    throw IllegalStateException(errorMsg)
                }

                // --- Stage 2: Installation Loop (if APKs were selected) ---
                if (apkRestoreSelected) {
                    var bytesInstalledSoFar = 0L
                    val totalBytesToRestore = selectedApps.sumOf { it.backupSize ?: 0L }

                    for ((index, detail) in selectedApps.withIndex()) {
                        val appName = detail.appInfo.name
                        val appSize = detail.backupSize ?: 0L

                        val currentElapsedTime = (System.currentTimeMillis() - startTime) / 1000
                        val installStagePercentage = (index + 1).toFloat() / selectedApps.size.toFloat()
                        val overallPercentage = (1f / totalStages) + (installStagePercentage / totalStages)

                        _restoreProgress.value = OperationProgress(
                            stageTitle = "[2/$totalStages] Installing Apps",
                            stagePercentage = installStagePercentage,
                            overallPercentage = overallPercentage,
                            elapsedTime = currentElapsedTime,
                            totalFiles = selectedApps.size,
                            filesProcessed = index + 1,
                            totalBytes = totalBytesToRestore,
                            bytesProcessed = bytesInstalledSoFar
                        )
                        notificationRepository.showOperationProgressNotification("Restore", _restoreProgress.value)

                        val originalApkPath = pathsToRestore.find { it.startsWith("/data/app/") && it.contains("/${detail.appInfo.packageName}-") }
                        if (originalApkPath == null) {
                            failures++
                            failureDetails.add("$appName: Could not find its path in the restored files list.")
                            continue
                        }

                        val restoredContentDir = File(tempRestoreDir, originalApkPath.drop(1))
                        val apkFiles = restoredContentDir.walk().filter { it.isFile && it.extension == "apk" }.toList()

                        if (apkFiles.isNotEmpty()) {
                            val installFlags = if (_allowDowngrade.value) "-r -d" else "-r"
                            val createResult = Shell.cmd("pm install-create $installFlags").exec()
                            val sessionId = createResult.out.firstOrNull()?.substringAfterLast('[')?.substringBefore(']')

                            if (createResult.isSuccess && sessionId != null) {
                                var allWritesSucceeded = true
                                for ((splitIndex, apkFile) in apkFiles.withIndex()) {
                                    val splitName = "${splitIndex}_${apkFile.name}"
                                    val writeCommand = "pm install-write -S ${apkFile.length()} $sessionId '$splitName' '${apkFile.absolutePath}'"
                                    val writeResult = Shell.cmd(writeCommand).exec()
                                    if (!writeResult.isSuccess) {
                                        allWritesSucceeded = false
                                        failures++
                                        failureDetails.add("$appName: Failed to write ${apkFile.name}: ${writeResult.err.joinToString(" ")}")
                                        Shell.cmd("pm install-abandon $sessionId").exec()
                                        break
                                    }
                                }

                                if (allWritesSucceeded) {
                                    val commitResult = Shell.cmd("pm install-commit $sessionId").exec()
                                    if (commitResult.isSuccess && commitResult.out.any { it.contains("Success") }) {
                                        successes++
                                        bytesInstalledSoFar += appSize
                                    } else {
                                        failures++
                                        failureDetails.add("$appName: Install commit failed: ${commitResult.err.joinToString(" ")}")
                                    }
                                }
                            } else {
                                failures++
                                failureDetails.add("$appName: Failed to create install session: ${createResult.err.joinToString(" ")}")
                            }
                        } else {
                            failures++
                            failureDetails.add("$appName: No APK files found after restore.")
                        }
                    }
                }

                // --- Final Stage: Cleanup ---
                val cleanupStage = if (apkRestoreSelected) 3 else 2
                _restoreProgress.update {
                    it.copy(
                        stageTitle = "[$cleanupStage/$totalStages] Cleaning up...",
                        stagePercentage = 0f,
                        overallPercentage = (cleanupStage - 1).toFloat() / totalStages
                    )
                }
                notificationRepository.showOperationProgressNotification("Restore", _restoreProgress.value)

                var cleanupMessage = ""
                tempRestoreDir?.let { dir ->
                    val cleanupSuccess = Shell.cmd("rm -rf '${dir.absolutePath}'").exec().isSuccess
                    cleanupMessage = if (cleanupSuccess) {
                        "\n\nTemporary restore data cleaned up successfully."
                    } else {
                        "\n\nWarning: Failed to clean up temporary restore data. Please clear the app cache manually."
                    }
                }

                _restoreProgress.update {
                    it.copy(
                        stagePercentage = 1f,
                        overallPercentage = 1f
                    )
                }

                // --- Final Summary ---
                val finalElapsedTime = (System.currentTimeMillis() - startTime) / 1000
                val summary = buildString {
                    append("Restore finished in ${formatElapsedTime(finalElapsedTime)}. ")
                    if (apkRestoreSelected) {
                        append("Successfully installed $successes app(s).")
                        if (failures > 0) {
                            append(" Failed to restore $failures app(s).")
                        }
                    } else {
                        append("Successfully restored data for ${selectedApps.size} app(s) to cache.")
                    }
                    val otherDataTypes = mutableListOf<String>()
                    if (restoreTypes.value.data) otherDataTypes.add("Data")
                    if (restoreTypes.value.deviceProtectedData) otherDataTypes.add("Device Protected Data")
                    if (restoreTypes.value.externalData) otherDataTypes.add("External Data")
                    if (restoreTypes.value.obb) otherDataTypes.add("OBB")
                    if (restoreTypes.value.media) otherDataTypes.add("Media")

                    if (otherDataTypes.isNotEmpty()) {
                        append("\n\nRestored ${otherDataTypes.joinToString()} is located in:\n${tempRestoreDir?.absolutePath}")
                    }

                    if (failures > 0) {
                        append("\n\nErrors:\n- ${failureDetails.joinToString("\n- ")}")
                    }
                    append(cleanupMessage)
                }

                _restoreProgress.value = OperationProgress(
                    isFinished = true,
                    overallPercentage = 1f,
                    finalSummary = summary,
                    error = if (failures > 0) failureDetails.joinToString(", ") else null,
                    elapsedTime = finalElapsedTime,
                    filesProcessed = successes,
                    totalFiles = selectedApps.size
                )

                notificationRepository.showOperationFinishedNotification(
                    "Restore",
                    failures == 0,
                    summary
                )

            } catch (e: Exception) {
                _restoreProgress.value = _restoreProgress.value.copy(
                    isFinished = true,
                    error = "A fatal error occurred: ${e.message}",
                    finalSummary = "A fatal error occurred: ${e.message}",
                    elapsedTime = (System.currentTimeMillis() - startTime) / 1000
                )
                notificationRepository.showOperationFinishedNotification(
                    "Restore",
                    false,
                    _restoreProgress.value.finalSummary ?: "Restore failed."
                )
            } finally {
                passwordFile?.delete()
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
