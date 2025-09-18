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
import kotlinx.coroutines.delay

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

    fun startRestore() {
        if (_isRestoring.value) return

        restoreJob = viewModelScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            _isRestoring.value = true
            _restoreProgress.value = OperationProgress()

            var tempRestoreDir: File? = null
            var successes = 0
            var failures = 0
            val failureDetails = mutableListOf<String>()
            val totalStages = 3 // 1 for file restore, 1 for app install, 1 for cleanup

            try {
                // --- Pre-flight checks ---
                val resticState = resticRepository.resticState.value
                val selectedRepoPath = repositoriesRepository.selectedRepository.value
                val currentSnapshot = _snapshot.value
                val selectedApps = _backupDetails.value.filter { it.appInfo.isSelected }

                if (resticState !is ResticState.Installed || selectedRepoPath == null || currentSnapshot == null) {
                    throw IllegalStateException("Pre-restore checks failed: Restic not ready or no repo/snapshot selected.")
                }
                if (selectedApps.isEmpty()) {
                    throw IllegalStateException("No apps selected for restore.")
                }
                val password = repositoriesRepository.getRepositoryPassword(selectedRepoPath)
                    ?: throw IllegalStateException("Password not found for repository.")

                tempRestoreDir = File(application.cacheDir, "restic-restore-${System.currentTimeMillis()}").also { it.mkdirs() }

                // --- Find APKs to restore ---
                val pathsToRestore = selectedApps.mapNotNull { detail ->
                    currentSnapshot.paths.find { path ->
                        path.startsWith("/data/app/") && path.contains("/${detail.appInfo.packageName}-")
                    }
                }

                if (pathsToRestore.isEmpty()) {
                    throw IllegalStateException("No APK paths found in the snapshot for the selected apps.")
                }

                // --- Stage 1: Execute restic restore with real-time progress ---
                val includes = pathsToRestore.joinToString(" ") { "--include '$it'" }
                val sanitizedPassword = password.replace("'", "'\\''")
                val env = "HOME='${application.filesDir.absolutePath}' TMPDIR='${application.cacheDir.absolutePath}'"
                val command = "$env RESTIC_PASSWORD='$sanitizedPassword' ${resticState.path} -r '$selectedRepoPath' restore ${currentSnapshot.id} --target '${tempRestoreDir.absolutePath}' $includes --json"

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

                // --- Stage 2: Installation Loop ---
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

                    val originalApkPath = pathsToRestore.find { it.contains("/${detail.appInfo.packageName}-") }
                    if (originalApkPath == null) {
                        failures++
                        failureDetails.add("$appName: Could not find its path in the restored files list.")
                        continue
                    }

                    val restoredContentDir = File(tempRestoreDir, originalApkPath.drop(1))
                    val apkFiles = restoredContentDir.walk().filter { it.isFile && it.extension == "apk" }.toList()

                    if (apkFiles.isNotEmpty()) {
                        // 1. Create install session
                        val createResult = Shell.cmd("pm install-create -r -d").exec()
                        val sessionId = createResult.out.firstOrNull()?.substringAfterLast('[')?.substringBefore(']')

                        if (createResult.isSuccess && sessionId != null) {
                            var allWritesSucceeded = true
                            // 2. Write all APKs to the session
                            for ((splitIndex, apkFile) in apkFiles.withIndex()) {
                                // Split name needs to be unique. Using index + name.
                                val splitName = "${splitIndex}_${apkFile.name}"
                                val writeCommand = "pm install-write -S ${apkFile.length()} $sessionId '$splitName' '${apkFile.absolutePath}'"
                                val writeResult = Shell.cmd(writeCommand).exec()
                                if (!writeResult.isSuccess) {
                                    allWritesSucceeded = false
                                    failures++
                                    failureDetails.add("$appName: Failed to write ${apkFile.name}: ${writeResult.err.joinToString(" ")}")
                                    // Abandon this session if one write fails
                                    Shell.cmd("pm install-abandon $sessionId").exec()
                                    break
                                }
                            }

                            if (allWritesSucceeded) {
                                // 3. Commit the session
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

                // --- Stage 3: Cleanup ---
                val cleanupStartTime = System.currentTimeMillis()
                _restoreProgress.update {
                    it.copy(
                        stageTitle = "[3/$totalStages] Cleaning up...",
                        stagePercentage = 0f,
                        overallPercentage = (2f / totalStages)
                    )
                }
                notificationRepository.showOperationProgressNotification("Restore", _restoreProgress.value)

                var cleanupSuccess = false
                var cleanupMessage = ""
                tempRestoreDir?.let { dir ->
                    cleanupSuccess = Shell.cmd("rm -rf '${dir.absolutePath}'").exec().isSuccess
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
                    append("Successfully installed $successes app(s).")
                    if (failures > 0) {
                        append(" Failed to restore $failures app(s).\n\nErrors:\n- ${failureDetails.joinToString("\n- ")}")
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
                    totalFiles = selectedApps.size,
                    bytesProcessed = bytesInstalledSoFar,
                    totalBytes = totalBytesToRestore
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
                    failures == 0,
                    _restoreProgress.value.finalSummary ?: "Restore finished."
                )
            } finally {
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
                    detail.copy(appInfo = detail.appInfo.copy(isSelected = !detail.appInfo.isSelected))
                } else {
                    detail
                }
            }
        }
    }

    fun toggleAllRestoreSelection() {
        _backupDetails.update { currentDetails ->
            val shouldSelectAll = currentDetails.any { !it.appInfo.isSelected }
            currentDetails.map { detail ->
                detail.copy(appInfo = detail.appInfo.copy(isSelected = shouldSelectAll))
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

