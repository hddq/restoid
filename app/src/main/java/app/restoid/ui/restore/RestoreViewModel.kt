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
                apkPath = "",
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
                (path.startsWith("/data/app/") && path.contains(pkg)) -> if (!items.contains("APK")) items.add("APK")
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
            _restoreProgress.value = OperationProgress(currentAction = "Starting restore...")

            var tempRestoreDir: File? = null
            var successes = 0
            var failures = 0
            val failureDetails = mutableListOf<String>()
            var bytesRestoredSoFar = 0L

            try {
                // --- Pre-flight checks ---
                val resticState = resticRepository.resticState.value
                val selectedRepoPath = repositoriesRepository.selectedRepository.value
                val currentSnapshot = _snapshot.value
                val selectedApps = _backupDetails.value.filter { it.appInfo.isSelected }
                val totalBytesToRestore = selectedApps.sumOf { it.backupSize ?: 0L }

                if (resticState !is ResticState.Installed || selectedRepoPath == null || currentSnapshot == null) {
                    throw IllegalStateException("Pre-restore checks failed: Restic not ready or no repo/snapshot selected.")
                }
                if (selectedApps.isEmpty()) {
                    throw IllegalStateException("No apps selected for restore.")
                }
                val password = repositoriesRepository.getRepositoryPassword(selectedRepoPath)
                    ?: throw IllegalStateException("Password not found for repository.")

                tempRestoreDir = File(application.cacheDir, "restic-restore-${System.currentTimeMillis()}").also { it.mkdirs() }

                // --- OPTIMIZATION: Bulk Restore ---
                // 1. Collect all APK paths to restore in one go.
                _restoreProgress.value = _restoreProgress.value.copy(currentAction = "Finding APKs in backup...")
                val pathsToRestore = selectedApps.mapNotNull { detail ->
                    currentSnapshot.paths.find { path ->
                        path.startsWith("/data/app/") && (path.contains("-${detail.appInfo.name}-") || path.contains("/${detail.appInfo.name}-") || path.contains(detail.appInfo.packageName))
                    }
                }

                if (pathsToRestore.isEmpty()) {
                    throw IllegalStateException("No APK paths found in the snapshot for the selected apps.")
                }

                // 2. Execute a single restore command for all APKs.
                val elapsedTime = (System.currentTimeMillis() - startTime) / 1000
                _restoreProgress.value = _restoreProgress.value.copy(
                    currentAction = "Restoring all APK files...",
                    elapsedTime = elapsedTime
                )

                val bulkRestoreResult = resticRepository.restore(
                    repoPath = selectedRepoPath,
                    password = password,
                    snapshotId = currentSnapshot.id,
                    targetPath = tempRestoreDir.absolutePath,
                    pathsToRestore = pathsToRestore
                )

                if (bulkRestoreResult.isFailure) {
                    throw IllegalStateException("Restic restore command failed: ${bulkRestoreResult.exceptionOrNull()?.message}")
                }


                // --- Installation Loop ---
                for ((index, detail) in selectedApps.withIndex()) {
                    val appName = detail.appInfo.name
                    val appSize = detail.backupSize ?: 0L

                    val currentElapsedTime = (System.currentTimeMillis() - startTime) / 1000
                    val percentage = if (totalBytesToRestore > 0) bytesRestoredSoFar.toFloat() / totalBytesToRestore.toFloat() else 0f

                    _restoreProgress.value = _restoreProgress.value.copy(
                        percentage = percentage,
                        currentAction = "Installing $appName (${index + 1}/${selectedApps.size})",
                        elapsedTime = currentElapsedTime,
                        filesProcessed = index,
                        totalFiles = selectedApps.size,
                        bytesProcessed = bytesRestoredSoFar,
                        totalBytes = totalBytesToRestore
                    )

                    val originalApkPath = pathsToRestore.find { it.contains(detail.appInfo.packageName) || it.contains(appName) }
                    if (originalApkPath == null) {
                        failures++
                        failureDetails.add("$appName: Could not find its path in the restored files list.")
                        continue
                    }

                    val restoredContentDir = File(tempRestoreDir, originalApkPath.drop(1))
                    val apkFiles = restoredContentDir.walk().filter { it.isFile && it.extension == "apk" }.toList()

                    if (apkFiles.isNotEmpty()) {
                        val apkPaths = apkFiles.joinToString(" ") { "'${it.absolutePath}'" }
                        val installCommand = if (apkFiles.size > 1) "pm install-create -r -d; pm install-write -S ${apkFiles.sumOf { it.length() }} 0 base.apk $apkPaths; pm install-commit 0" else "pm install -r -d $apkPaths"
                        val installResult = Shell.cmd(installCommand).exec()

                        if (installResult.isSuccess) {
                            successes++
                            bytesRestoredSoFar += appSize
                        } else {
                            failures++
                            failureDetails.add("$appName: Install failed: ${installResult.err.joinToString(" ")}")
                        }
                    } else {
                        failures++
                        failureDetails.add("$appName: No APK files found after restore.")
                    }
                }

                val finalElapsedTime = (System.currentTimeMillis() - startTime) / 1000
                val summary = buildString {
                    append("Restore finished in ${formatElapsedTime(finalElapsedTime)}. ")
                    append("Successfully installed $successes app(s).")
                    if (failures > 0) {
                        append(" Failed to restore $failures app(s).\n\nErrors:\n- ${failureDetails.joinToString("\n- ")}")
                    }
                }
                _restoreProgress.value = OperationProgress(
                    isFinished = true,
                    percentage = 1f,
                    finalSummary = summary,
                    error = if (failures > 0) failureDetails.joinToString(", ") else null,
                    elapsedTime = finalElapsedTime,
                    filesProcessed = successes,
                    totalFiles = selectedApps.size,
                    bytesProcessed = bytesRestoredSoFar,
                    totalBytes = totalBytesToRestore
                )

            } catch (e: Exception) {
                _restoreProgress.value = _restoreProgress.value.copy(
                    isFinished = true,
                    error = "A fatal error occurred: ${e.message}",
                    finalSummary = "A fatal error occurred: ${e.message}",
                    elapsedTime = (System.currentTimeMillis() - startTime) / 1000
                )
            } finally {
                tempRestoreDir?.deleteRecursively()
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

