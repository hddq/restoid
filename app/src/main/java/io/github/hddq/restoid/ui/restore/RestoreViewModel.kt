package io.github.hddq.restoid.ui.restore

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hddq.restoid.data.*
import io.github.hddq.restoid.R
import io.github.hddq.restoid.model.AppInfo
import io.github.hddq.restoid.model.BackupDetail
import io.github.hddq.restoid.model.RestoidMetadata
import io.github.hddq.restoid.ui.shared.OperationProgress
import io.github.hddq.restoid.util.ResticOutputParser
import io.github.hddq.restoid.util.buildShellEnvironmentPrefix
import io.github.hddq.restoid.util.shellQuote
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
import java.util.Locale

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

    private var snapshotMetadata: RestoidMetadata? = null

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
                val selectedRepoKey = repositoriesRepository.selectedRepository.first()
                val repo = selectedRepoKey?.let { repositoriesRepository.getRepositoryByKey(it) }
                val repoPath = repo?.path
                val password = selectedRepoKey?.let { repositoriesRepository.getRepositoryPassword(it) }
                val repoId = repo?.id

                if (repoPath != null && password != null && repoId != null) {
                    val result = resticRepository.getSnapshots(
                        repoPath,
                        password,
                        repositoriesRepository.getExecutionEnvironmentVariables(selectedRepoKey),
                        repositoriesRepository.getExecutionResticOptions(selectedRepoKey)
                    )
                    result.fold(
                        onSuccess = { snapshots ->
                            val foundSnapshot = snapshots.find { it.id.startsWith(snapshotId) }
                            _snapshot.value = foundSnapshot
                            if (foundSnapshot != null) {
                                val metadata = metadataRepository.getMetadataForSnapshot(repoId, foundSnapshot.id)
                                snapshotMetadata = metadata
                                processSnapshot(foundSnapshot, metadata)
                            } else {
                                _error.value = application.getString(R.string.error_snapshot_not_found)
                            }
                        },
                        onFailure = { _error.value = it.message }
                    )
                } else {
                    _error.value = application.getString(R.string.error_repository_password_or_id_not_found)
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
            val items = findBackedUpItems(snapshot, packageName, appMeta.grantedRuntimePermissions.isNotEmpty())

            val isInstalled = appInfo != null
            val isDowngrade = if (isInstalled) {
                appMeta.versionCode < appInfo.versionCode
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

    private fun findBackedUpItems(snapshot: SnapshotInfo, pkg: String, hasPermissionBackup: Boolean): List<String> {
        val items = mutableListOf<String>()
        snapshot.paths.forEach { path ->
            when {
                (path.startsWith("/data/app/") && path.contains("/${pkg}-")) -> if (!items.contains(application.getString(R.string.backup_type_apk))) items.add(application.getString(R.string.backup_type_apk))
                path == "/data/data/$pkg" -> if (!items.contains(application.getString(R.string.backup_type_data))) items.add(application.getString(R.string.backup_type_data))
                path == "/data/user_de/0/$pkg" -> if (!items.contains(application.getString(R.string.backup_type_device_protected_data))) items.add(application.getString(R.string.backup_type_device_protected_data))
                path == "/storage/emulated/0/Android/data/$pkg" -> if (!items.contains(application.getString(R.string.backup_type_external_data))) items.add(application.getString(R.string.backup_type_external_data))
                path == "/storage/emulated/0/Android/obb/$pkg" -> if (!items.contains(application.getString(R.string.backup_item_obb))) items.add(application.getString(R.string.backup_item_obb))
                path == "/storage/emulated/0/Android/media/$pkg" -> if (!items.contains(application.getString(R.string.backup_item_media))) items.add(application.getString(R.string.backup_item_media))
            }
        }
        if (hasPermissionBackup && !items.contains(application.getString(R.string.backup_item_permissions))) {
            items.add(application.getString(R.string.backup_item_permissions))
        }
        return if (items.isNotEmpty()) items else listOf(application.getString(R.string.backup_item_unknown))
    }

    private fun restoreGrantedRuntimePermissions(packageName: String, permissions: List<String>): List<String> {
        if (permissions.isEmpty()) return emptyList()

        val failures = mutableListOf<String>()
        permissions.forEach { permission ->
            val result = Shell.cmd("pm grant '$packageName' '$permission'").exec()
            if (!result.isSuccess) {
                val output = (result.err + result.out).joinToString(" ").trim()
                failures.add(if (output.isBlank()) permission else "$permission ($output)")
            }
        }
        return failures
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            application.packageManager.getPackageInfo(
                packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0)
            )
            true
        } catch (_: Exception) {
            false
        }
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

                // Relabel restored files so app-private SELinux categories are restored (including symlinks).
                val relabelCommand = if (destination.startsWith("/data/data/")) {
                    "restorecon -RFD '$destination'"
                } else {
                    "restorecon -RF '$destination'"
                }
                val relabelResult = Shell.cmd(relabelCommand).exec()
                if (!relabelResult.isSuccess) {
                    allSucceeded = false
                    Log.w("RestoreViewModel", "Failed to relabel restored path $destination")
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

            val stageList = mutableListOf(application.getString(R.string.restore_stage_restore_files))
            if (apkRestoreSelected || anyDataRestoreSelected) stageList.add(application.getString(R.string.restore_stage_processing_apps))
            stageList.add(application.getString(R.string.restore_stage_cleanup))
            val totalStages = stageList.size
            var currentStageNum = 1

            try {
                // Use Manager for state check
                val resticState = resticBinaryManager.resticState.value
                val selectedRepoKey = repositoriesRepository.selectedRepository.value
                val selectedRepository = selectedRepoKey?.let { repositoriesRepository.getRepositoryByKey(it) }
                val selectedRepoPath = selectedRepository?.path
                val currentSnapshot = _snapshot.value
                val selectedApps = _backupDetails.value.filter { it.appInfo.isSelected && (_allowDowngrade.value || !it.isDowngrade) }

                if (resticState !is ResticState.Installed || selectedRepoPath == null || currentSnapshot == null) {
                    throw IllegalStateException(application.getString(R.string.restore_error_preflight_failed))
                }
                if (selectedApps.isEmpty()) throw IllegalStateException(application.getString(R.string.restore_error_no_apps_selected))

                val actualRepoKey = selectedRepoKey

                if (selectedRepository.backendType == RepositoryBackendType.SFTP && !repositoriesRepository.hasSftpPassword(actualRepoKey)) {
                    throw IllegalStateException(application.getString(R.string.error_sftp_password_not_found_for_repository))
                }

                val password = repositoriesRepository.getRepositoryPassword(actualRepoKey)
                    ?: throw IllegalStateException(application.getString(R.string.restore_error_password_not_found))

                passwordFile = File.createTempFile("restic-pass", ".tmp", application.cacheDir)
                passwordFile.writeText(password)

                tempRestoreDir = File(application.cacheDir, "restic-restore-${System.currentTimeMillis()}").also { it.mkdirs() }

                val pathsToRestore = generatePathsToRestore(selectedApps, currentSnapshot)
                if (pathsToRestore.isEmpty()) throw IllegalStateException(application.getString(R.string.restore_error_no_files_found))

                val operationRestore = application.getString(R.string.operation_restore)

                // --- Stage 1: Execute restic restore ---
                val restoreStageTitle = "[${currentStageNum}/${totalStages}] ${stageList[0]}"
                val includes = pathsToRestore.joinToString(" ") { "--include ${shellQuote(it)}" }
                // TODO: This should also move to ResticExecutor ideally, but it uses restore output streaming.
                // We continue manual command construction here for now.
                val commandEnvironment = linkedMapOf(
                    "HOME" to application.filesDir.absolutePath,
                    "TMPDIR" to application.cacheDir.absolutePath
                ).apply {
                    putAll(repositoriesRepository.getExecutionEnvironmentVariables(actualRepoKey))
                }
                val envPrefix = buildShellEnvironmentPrefix(commandEnvironment)
                val resticOptionFlags = io.github.hddq.restoid.util.buildResticOptionFlags(
                    repositoriesRepository.getExecutionResticOptions(actualRepoKey)
                )
                val command = buildString {
                    if (envPrefix.isNotEmpty()) append(envPrefix).append(' ')
                    append("RESTIC_PASSWORD_FILE=").append(shellQuote(passwordFile.absolutePath)).append(' ')
                    append(shellQuote(resticState.path)).append(' ')
                    if (resticOptionFlags.isNotEmpty()) append(resticOptionFlags).append(' ')
                    append("-r ")
                    append(shellQuote(selectedRepoPath)).append(' ')
                    append("restore ").append(currentSnapshot.id).append(" --target ")
                    append(shellQuote(tempRestoreDir.absolutePath)).append(' ')
                    append("--exclude-xattr 'security.selinux' ")
                    append(includes)
                    append(" --json")
                }

                val stdoutCallback = object : CallbackList<String>() {
                    override fun onAddElement(line: String) {
                        ResticOutputParser.parse(line, application)?.let { progressUpdate ->
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
                            notificationRepository.showOperationProgressNotification(operationRestore, newProgress)
                        }
                    }
                }
                val stderr = mutableListOf<String>()
                val restoreResult = Shell.cmd(command).to(stdoutCallback, stderr).exec()

                if (!restoreResult.isSuccess) {
                    val errorOutput = stderr.joinToString("\n")
                    throw IllegalStateException(if (errorOutput.isEmpty()) application.getString(R.string.restore_error_command_failed) else errorOutput)
                }

                // --- Stage 2: Processing Apps ---
                val processingAppsStageIndex = stageList.indexOf(application.getString(R.string.restore_stage_processing_apps))
                if (processingAppsStageIndex != -1) {
                    currentStageNum = processingAppsStageIndex + 1
                    val processingStageTitle = application.getString(R.string.restore_stage_processing_template, currentStageNum, totalStages)

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
                        notificationRepository.showOperationProgressNotification(operationRestore, _restoreProgress.value)

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
                                            failureDetails.add(application.getString(R.string.restore_failure_install_commit, appName, commitResult.err.joinToString(" ")))
                                        }
                                    } else {
                                        appProcessSuccess = false
                                        failureDetails.add(application.getString(R.string.restore_failure_write_apk_splits, appName))
                                        Shell.cmd("pm install-abandon $sessionId").exec()
                                    }
                                } else {
                                    appProcessSuccess = false
                                    failureDetails.add(application.getString(R.string.restore_failure_create_install_session, appName))
                                }
                            } else {
                                appProcessSuccess = false
                                failureDetails.add(application.getString(R.string.restore_failure_no_apk_files, appName))
                            }
                        }

                        if (appProcessSuccess && anyDataRestoreSelected) {
                            if (!moveRestoredDataAndFixPerms(detail, tempRestoreDir, restoreTypes.value)) {
                                appProcessSuccess = false
                                failureDetails.add(application.getString(R.string.restore_failure_data_restore, appName))
                            }
                        }

                        val permissionsToRestore = snapshotMetadata
                            ?.apps
                            ?.get(detail.appInfo.packageName)
                            ?.grantedRuntimePermissions
                            .orEmpty()
                        if (permissionsToRestore.isNotEmpty()) {
                            if (!isPackageInstalled(detail.appInfo.packageName)) {
                                appProcessSuccess = false
                                failureDetails.add(application.getString(R.string.restore_failure_permissions_app_not_installed, appName))
                            } else {
                                val permissionFailures = restoreGrantedRuntimePermissions(detail.appInfo.packageName, permissionsToRestore)
                                if (permissionFailures.isNotEmpty()) {
                                    appProcessSuccess = false
                                    failureDetails.add(application.getString(R.string.restore_failure_permissions_partial, appName, permissionFailures.joinToString(", ")))
                                }
                            }
                        }

                        if (appProcessSuccess) successes++ else failures++
                    }
                } else {
                    successes = selectedApps.size
                }

                // --- Final Stage: Cleanup ---
                currentStageNum = totalStages
                val cleanupStageTitle = "[${currentStageNum}/${totalStages}] ${application.getString(R.string.restore_stage_cleanup)}"
                _restoreProgress.update { it.copy(stageTitle = cleanupStageTitle, stagePercentage = 0f, overallPercentage = (totalStages - 1).toFloat() / totalStages.toFloat()) }

                tempRestoreDir.let { dir -> Shell.cmd("rm -rf '${dir.absolutePath}'").exec() }
                _restoreProgress.update { it.copy(stagePercentage = 1f, overallPercentage = 1f) }

                val finalElapsedTime = (System.currentTimeMillis() - startTime) / 1000
                val summary = buildString {
                    append(application.getString(R.string.restore_summary_finished_in, formatElapsedTime(finalElapsedTime)))
                    append(application.getString(R.string.restore_summary_processed, successes))
                    if (failures > 0) append(application.getString(R.string.restore_summary_failed, failures))
                    if (failureDetails.isNotEmpty()) append(application.getString(R.string.restore_summary_details, failureDetails.joinToString("\n- ")))
                }

                _restoreProgress.value = OperationProgress(
                    isFinished = true,
                    finalSummary = summary,
                    error = if (failures > 0) failureDetails.joinToString(", ") else null,
                    elapsedTime = finalElapsedTime,
                    filesProcessed = successes,
                    totalFiles = selectedApps.size
                )
                notificationRepository.showOperationFinishedNotification(operationRestore, failures == 0, summary)

            } catch (e: Exception) {
                _restoreProgress.value = _restoreProgress.value.copy(
                    isFinished = true,
                    error = application.getString(R.string.error_fatal_with_message, e.message ?: ""),
                    finalSummary = application.getString(R.string.error_fatal_with_message, e.message ?: ""),
                    elapsedTime = (System.currentTimeMillis() - startTime) / 1000
                )
                notificationRepository.showOperationFinishedNotification(application.getString(R.string.operation_restore), false, _restoreProgress.value.finalSummary)
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
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, secs)
        }
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
