package io.github.hddq.restoid.ui.restore

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hddq.restoid.data.*
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
    private val resticBinaryManager: ResticBinaryManager, // Inject Manager
    private val resticRepository: ResticRepository,
    private val appInfoRepository: AppInfoRepository,
    private val notificationRepository: NotificationRepository,
    private val metadataRepository: MetadataRepository,
    private val preferencesRepository: PreferencesRepository,
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
        _restoreTypes.value = preferencesRepository.loadRestoreTypes()
        _allowDowngrade.value = preferencesRepository.loadAllowDowngrade()
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
        val packageNames = appMetadataMap.keys.toList().filter { it != application.packageName }

        if (packageNames.isEmpty()) {
            _backupDetails.value = emptyList()
            return
        }

        val appInfos = appInfoRepository.getAppInfoForPackages(packageNames)
        val appInfoMap = appInfos.associateBy { it.packageName }

        val details = appMetadataMap.filterKeys { it != application.packageName }.map { (packageName, appMeta) ->
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

        _backupDetails.value = details.sortedWith(
            compareByDescending<BackupDetail> { it.backupSize ?: 0L }
                .thenBy { it.appInfo.name.lowercase() }
        )
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

    private fun generatePathsToRestore(selectedApps: List<BackupDetail>, snapshot: SnapshotInfo): List<String> {
        val paths = mutableListOf<String>()
        val types = restoreTypes.value

        selectedApps.forEach { detail ->
            val pkg = detail.appInfo.packageName
            fun addPathIfExists(path: String) {
                if (snapshot.paths.contains(path)) paths.add(path)
            }
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

    private fun moveRestoredDataAndFixPerms(detail: BackupDetail, tempRestoreDir: File, types: RestoreTypes): Boolean {
        val pkg = detail.appInfo.packageName
        var allSucceeded = true

        val ownerResult = Shell.cmd("stat -c '%U:%G' /data/data/$pkg").exec()
        if (!ownerResult.isSuccess || ownerResult.out.isEmpty()) return false
        val owner = ownerResult.out.first().trim()

        val dataMappings = mutableListOf<Pair<File, String>>()
        if (types.data) dataMappings.add(File(tempRestoreDir, "data/data/$pkg") to "/data/data/$pkg")
        if (types.deviceProtectedData) dataMappings.add(File(tempRestoreDir, "data/user_de/0/$pkg") to "/data/user_de/0/$pkg")
        if (types.externalData) dataMappings.add(File(tempRestoreDir, "storage/emulated/0/Android/data/$pkg") to "/storage/emulated/0/Android/data/$pkg")
        if (types.obb) dataMappings.add(File(tempRestoreDir, "storage/emulated/0/Android/obb/$pkg") to "/storage/emulated/0/Android/obb/$pkg")
        if (types.media) dataMappings.add(File(tempRestoreDir, "storage/emulated/0/Android/media/$pkg") to "/storage/emulated/0/Android/media/$pkg")

        Shell.cmd("am force-stop $pkg").exec()

        for ((source, destination) in dataMappings) {
            if (Shell.cmd("[ -e '${source.absolutePath}' ]").exec().isSuccess) {
                Shell.cmd("mkdir -p '$destination'").exec()
                val copyResult = Shell.cmd("cp -a '${source.absolutePath}/.' '$destination/'").exec()
                if (!copyResult.isSuccess) {
                    allSucceeded = false
                    continue
                }
                val chownResult = Shell.cmd("chown -R $owner '$destination'").exec()
                if (!chownResult.isSuccess) {
                    allSucceeded = false
                }
            }
        }
        return allSucceeded
    }

    fun startRestore() {
        if (_isRestoring.value) return
        preferencesRepository.saveRestoreTypes(_restoreTypes.value)
        preferencesRepository.saveAllowDowngrade(_allowDowngrade.value)

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

            val stageList = mutableListOf("Restore Files")
            if (apkRestoreSelected || anyDataRestoreSelected) stageList.add("Processing Apps")
            stageList.add("Cleanup")
            val totalStages = stageList.size
            var currentStageNum = 1

            try {
                // Use Manager for state check
                val resticState = resticBinaryManager.resticState.value
                val selectedRepoPath = repositoriesRepository.selectedRepository.value
                val currentSnapshot = _snapshot.value
                val selectedApps = _backupDetails.value.filter { it.appInfo.isSelected && (_allowDowngrade.value || !it.isDowngrade) }

                if (resticState !is ResticState.Installed || selectedRepoPath == null || currentSnapshot == null) {
                    throw IllegalStateException("Pre-restore checks failed.")
                }
                if (selectedApps.isEmpty()) throw IllegalStateException("No apps selected for restore.")

                val password = repositoriesRepository.getRepositoryPassword(selectedRepoPath)
                    ?: throw IllegalStateException("Password not found for repository.")

                passwordFile = File.createTempFile("restic-pass", ".tmp", application.cacheDir)
                passwordFile.writeText(password)

                tempRestoreDir = File(application.cacheDir, "restic-restore-${System.currentTimeMillis()}").also { it.mkdirs() }

                val pathsToRestore = generatePathsToRestore(selectedApps, currentSnapshot)
                if (pathsToRestore.isEmpty()) throw IllegalStateException("No files found in the snapshot for the selected apps.")

                // --- Stage 1: Execute restic restore ---
                val restoreStageTitle = "[${currentStageNum}/${totalStages}] ${stageList[0]}"
                val includes = pathsToRestore.joinToString(" ") { "--include '$it'" }
                // TODO: This should also move to ResticExecutor ideally, but it uses restore output streaming.
                // We continue manual command construction here for now.
                val env = "HOME='${application.filesDir.absolutePath}' TMPDIR='${application.cacheDir.absolutePath}'"
                val command = "$env RESTIC_PASSWORD_FILE='${passwordFile.absolutePath}' ${resticState.path} -r '$selectedRepoPath' restore ${currentSnapshot.id} --target '${tempRestoreDir.absolutePath}' --exclude-xattr 'security.selinux' $includes --json"

                val stdoutCallback = object : CallbackList<String>() {
                    override fun onAddElement(line: String) {
                        ResticOutputParser.parse(line)?.let { progressUpdate ->
                            val elapsedTime = (System.currentTimeMillis() - startTime) / 1000
                            val newProgress = if (progressUpdate.isFinished) {
                                _restoreProgress.value.copy(
                                    isFinished = false,
                                    stageTitle = restoreStageTitle,
                                    stagePercentage = 1.0f,
                                    overallPercentage = currentStageNum.toFloat() / totalStages.toFloat(),
                                    elapsedTime = elapsedTime,
                                    finalSummary = ""
                                )
                            } else {
                                progressUpdate.copy(
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
                    throw IllegalStateException(if (errorOutput.isEmpty()) "Restic restore command failed." else errorOutput)
                }

                // --- Stage 2: Processing Apps ---
                val processingAppsStageIndex = stageList.indexOf("Processing Apps")
                if (processingAppsStageIndex != -1) {
                    currentStageNum = processingAppsStageIndex + 1
                    val processingStageTitle = "[${currentStageNum}/${totalStages}] Processing Apps"

                    for ((index, detail) in selectedApps.withIndex()) {
                        val appName = detail.appInfo.name
                        var appProcessSuccess = true
                        val processProgress = (index + 1).toFloat() / selectedApps.size.toFloat()

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
                                    failureDetails.add("$appName: Failed to create install session.")
                                }
                            } else {
                                appProcessSuccess = false
                                failureDetails.add("$appName: No APK files found in restored data.")
                            }
                        }

                        if (appProcessSuccess && anyDataRestoreSelected) {
                            if (!moveRestoredDataAndFixPerms(detail, tempRestoreDir, restoreTypes.value)) {
                                failureDetails.add("$appName: Data restore failed or incomplete.")
                            }
                        }

                        if (appProcessSuccess) successes++ else failures++
                    }
                } else {
                    successes = selectedApps.size
                }

                // --- Final Stage: Cleanup ---
                currentStageNum = totalStages
                val cleanupStageTitle = "[${currentStageNum}/${totalStages}] Cleanup"
                _restoreProgress.update { it.copy(stageTitle = cleanupStageTitle, stagePercentage = 0f, overallPercentage = (totalStages - 1).toFloat() / totalStages.toFloat()) }

                tempRestoreDir.let { dir -> Shell.cmd("rm -rf '${dir.absolutePath}'").exec() }
                _restoreProgress.update { it.copy(stagePercentage = 1f, overallPercentage = 1f) }

                val finalElapsedTime = (System.currentTimeMillis() - startTime) / 1000
                val summary = buildString {
                    append("Restore finished in ${formatElapsedTime(finalElapsedTime)}. ")
                    append("Successfully processed $successes app(s).")
                    if (failures > 0) append(" Failed to restore $failures app(s).")
                    if (failureDetails.isNotEmpty()) append("\n\nDetails:\n- ${failureDetails.joinToString("\n- ")}")
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
                tempRestoreDir?.let { dir -> Shell.cmd("rm -rf '${dir.absolutePath}'").exec() }
                _isRestoring.value = false
            }
        }
    }

    private fun formatElapsedTime(seconds: Long): String {
        val hours = java.util.concurrent.TimeUnit.SECONDS.toHours(seconds)
        val minutes = java.util.concurrent.TimeUnit.SECONDS.toMinutes(seconds) % 60
        val secs = seconds % 60
        return if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, secs) else String.format("%02d:%02d", minutes, secs)
    }

    fun onDone() { _restoreProgress.value = OperationProgress() }
    fun toggleRestoreAppSelection(packageName: String) {
        _backupDetails.update { currentDetails ->
            currentDetails.map { detail ->
                if (detail.appInfo.packageName == packageName) {
                    if (_allowDowngrade.value || !detail.isDowngrade) detail.copy(appInfo = detail.appInfo.copy(isSelected = !detail.appInfo.isSelected)) else detail
                } else detail
            }
        }
    }

    fun toggleAllRestoreSelection() {
        _backupDetails.update { currentDetails ->
            val shouldSelectAll = currentDetails.any { !it.appInfo.isSelected }
            currentDetails.map { detail ->
                val canBeSelected = _allowDowngrade.value || !detail.isDowngrade
                detail.copy(appInfo = detail.appInfo.copy(isSelected = if (shouldSelectAll) canBeSelected else false))
            }
        }
    }

    fun setAllowDowngrade(value: Boolean) {
        _allowDowngrade.value = value
        if (!value) {
            _backupDetails.update { currentDetails ->
                currentDetails.map { detail ->
                    if (detail.isDowngrade) detail.copy(appInfo = detail.appInfo.copy(isSelected = false)) else detail
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