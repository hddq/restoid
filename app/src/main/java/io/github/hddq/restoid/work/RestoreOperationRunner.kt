package io.github.hddq.restoid.work

import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.MetadataRepository
import io.github.hddq.restoid.data.OperationLockManager
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.RepositoryBackendType
import io.github.hddq.restoid.data.ResticBinaryManager
import io.github.hddq.restoid.data.ResticRepository
import io.github.hddq.restoid.data.ResticState
import io.github.hddq.restoid.data.SnapshotInfo
import io.github.hddq.restoid.ui.shared.OperationProgress
import io.github.hddq.restoid.util.ResticOutputParser
import io.github.hddq.restoid.util.buildResticOptionFlags
import io.github.hddq.restoid.util.buildShellEnvironmentPrefix
import io.github.hddq.restoid.util.shellQuote
import java.io.File
import java.util.Locale

class RestoreOperationRunner(
    private val context: Context,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticBinaryManager: ResticBinaryManager,
    private val resticRepository: ResticRepository,
    private val metadataRepository: MetadataRepository,
    private val operationLockManager: OperationLockManager
) {

    suspend fun run(
        request: RestoreWorkRequest,
        onProgress: (OperationProgress) -> Unit,
        shouldStop: () -> Boolean = { false }
    ): OperationRunResult {
        fun throwIfCancelled() {
            if (shouldStop()) {
                throw OperationCancelledException(context.getString(R.string.operation_interrupted))
            }
        }

        val startTime = System.currentTimeMillis()
        var progressState = OperationProgress(stageTitle = context.getString(R.string.progress_initializing))
        onProgress(progressState)
        throwIfCancelled()

        var operationLockAcquired = false
        var tempRestoreDir: File? = null
        var passwordFile: File? = null
        var successes = 0
        var failures = 0
        val failureDetails = mutableListOf<String>()

        val selectedAppPackages = request.selectedApps.map { it.packageName }
        val selectedAppNames = request.selectedApps.associate { it.packageName to it.appName }

        val apkRestoreSelected = request.restoreTypes.apk
        val anyDataRestoreSelected = with(request.restoreTypes) { data || deviceProtectedData || externalData || obb || media }

        val stageList = mutableListOf(context.getString(R.string.restore_stage_restore_files))
        if (apkRestoreSelected || anyDataRestoreSelected) stageList.add(context.getString(R.string.restore_stage_processing_apps))
        stageList.add(context.getString(R.string.restore_stage_cleanup))
        val totalStages = stageList.size
        var currentStageNum = 1

        val finalResult = try {
            throwIfCancelled()
            val resticState = resticBinaryManager.resticState.value
            val selectedRepository = repositoriesRepository.getRepositoryByKey(request.repositoryKey)
            val selectedRepoPath = selectedRepository?.path

            if (resticState !is ResticState.Installed || selectedRepoPath == null) {
                throw IllegalStateException(context.getString(R.string.restore_error_preflight_failed))
            }

            if (selectedAppPackages.isEmpty()) {
                throw IllegalStateException(context.getString(R.string.restore_error_no_apps_selected))
            }

            val currentSnapshot = findSnapshot(request)
                ?: throw IllegalStateException(context.getString(R.string.error_snapshot_not_found))

            operationLockManager.acquire(selectedRepository.backendType)
            operationLockAcquired = true

            if (selectedRepository.backendType == RepositoryBackendType.SFTP && !repositoriesRepository.hasSftpPassword(request.repositoryKey)) {
                throw IllegalStateException(context.getString(R.string.error_sftp_password_not_found_for_repository))
            }

            if (
                selectedRepository.backendType == RepositoryBackendType.REST &&
                selectedRepository.restAuthRequired &&
                !repositoriesRepository.hasRestCredentials(request.repositoryKey)
            ) {
                throw IllegalStateException(context.getString(R.string.error_rest_credentials_not_found_for_repository))
            }

            if (
                selectedRepository.backendType == RepositoryBackendType.S3 &&
                selectedRepository.s3AuthRequired &&
                !repositoriesRepository.hasS3Credentials(request.repositoryKey)
            ) {
                throw IllegalStateException(context.getString(R.string.error_s3_credentials_not_found_for_repository))
            }

            val password = repositoriesRepository.getRepositoryPassword(request.repositoryKey)
                ?: throw IllegalStateException(context.getString(R.string.restore_error_password_not_found))

            passwordFile = File.createTempFile("restic-pass", ".tmp", context.cacheDir)
            passwordFile.writeText(password)

            tempRestoreDir = File(context.cacheDir, "restic-restore-${System.currentTimeMillis()}").also { it.mkdirs() }

            val pathsToRestore = generatePathsToRestore(selectedAppPackages, currentSnapshot, request.restoreTypes)
            if (pathsToRestore.isEmpty()) {
                throw IllegalStateException(context.getString(R.string.restore_error_no_files_found))
            }

            val metadata = selectedRepository.id?.let { repoId ->
                metadataRepository.getMetadataForSnapshot(repoId, currentSnapshot.id)
            }

            val restoreStageTitle = "[${currentStageNum}/${totalStages}] ${stageList[0]}"
            val includes = pathsToRestore.joinToString(" ") { "--include ${shellQuote(it)}" }
            val commandEnvironment = linkedMapOf(
                "HOME" to context.filesDir.absolutePath,
                "TMPDIR" to context.cacheDir.absolutePath
            ).apply {
                putAll(repositoriesRepository.getExecutionEnvironmentVariables(request.repositoryKey))
            }
            val envPrefix = buildShellEnvironmentPrefix(commandEnvironment)
            val resticOptionFlags = buildResticOptionFlags(
                repositoriesRepository.getExecutionResticOptions(request.repositoryKey)
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
                    ResticOutputParser.parse(line, context)?.let { progressUpdate ->
                        val elapsedTime = (System.currentTimeMillis() - startTime) / 1000
                        val newProgress = if (progressUpdate.isFinished) {
                            progressState.copy(
                                isFinished = false,
                                stageTitle = restoreStageTitle,
                                stagePercentage = 1f,
                                overallPercentage = currentStageNum.toFloat() / totalStages.toFloat(),
                                elapsedTime = elapsedTime,
                                finalSummary = ""
                            )
                        } else {
                            progressUpdate.copy(
                                stageTitle = restoreStageTitle,
                                overallPercentage = ((currentStageNum - 1) + progressUpdate.stagePercentage) / totalStages.toFloat(),
                                elapsedTime = elapsedTime,
                                isFinished = false
                            )
                        }
                        progressState = newProgress
                        onProgress(newProgress)
                    }
                }
            }
            val stderr = mutableListOf<String>()
            val restoreResult = Shell.cmd(command).to(stdoutCallback, stderr).exec()
            throwIfCancelled()

            if (!restoreResult.isSuccess) {
                val errorOutput = stderr.joinToString("\n")
                throw IllegalStateException(if (errorOutput.isEmpty()) context.getString(R.string.restore_error_command_failed) else errorOutput)
            }

            val processingAppsStageIndex = stageList.indexOf(context.getString(R.string.restore_stage_processing_apps))
            if (processingAppsStageIndex != -1) {
                currentStageNum = processingAppsStageIndex + 1
                val processingStageTitle = context.getString(R.string.restore_stage_processing_template, currentStageNum, totalStages)

                for ((index, packageName) in selectedAppPackages.withIndex()) {
                    throwIfCancelled()
                    val appName = selectedAppNames[packageName] ?: packageName
                    var appProcessSuccess = true
                    val processProgress = (index + 1).toFloat() / selectedAppPackages.size.toFloat()

                    progressState = progressState.copy(
                        stageTitle = processingStageTitle,
                        stagePercentage = processProgress,
                        overallPercentage = (processingAppsStageIndex + processProgress) / totalStages.toFloat(),
                        currentFile = appName,
                        filesProcessed = index + 1,
                        totalFiles = selectedAppPackages.size,
                        isFinished = false,
                        elapsedTime = (System.currentTimeMillis() - startTime) / 1000
                    )
                    onProgress(progressState)

                    if (apkRestoreSelected) {
                        throwIfCancelled()
                        val originalApkPath = pathsToRestore.find { it.startsWith("/data/app/") && it.contains("/$packageName-") }
                        val restoredContentDir = originalApkPath?.let { File(tempRestoreDir, it.drop(1)) }
                        val apkFiles = restoredContentDir?.walk()?.filter { it.isFile && it.extension == "apk" }?.toList() ?: emptyList()

                        if (apkFiles.isNotEmpty()) {
                            val installFlags = if (request.allowDowngrade) "-r -d" else "-r"
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
                                        failureDetails.add(
                                            context.getString(
                                                R.string.restore_failure_install_commit,
                                                appName,
                                                commitResult.err.joinToString(" ")
                                            )
                                        )
                                    }
                                } else {
                                    appProcessSuccess = false
                                    failureDetails.add(context.getString(R.string.restore_failure_write_apk_splits, appName))
                                    Shell.cmd("pm install-abandon $sessionId").exec()
                                }
                            } else {
                                appProcessSuccess = false
                                failureDetails.add(context.getString(R.string.restore_failure_create_install_session, appName))
                            }
                        } else {
                            appProcessSuccess = false
                            failureDetails.add(context.getString(R.string.restore_failure_no_apk_files, appName))
                        }
                    }

                    if (appProcessSuccess && anyDataRestoreSelected) {
                        if (!moveRestoredDataAndFixPerms(packageName, tempRestoreDir, request.restoreTypes)) {
                            appProcessSuccess = false
                            failureDetails.add(context.getString(R.string.restore_failure_data_restore, appName))
                        }
                    }

                    val permissionsToRestore = metadata?.apps?.get(packageName)?.grantedRuntimePermissions.orEmpty()
                    if (permissionsToRestore.isNotEmpty()) {
                        if (!isPackageInstalled(packageName)) {
                            appProcessSuccess = false
                            failureDetails.add(context.getString(R.string.restore_failure_permissions_app_not_installed, appName))
                        } else {
                            val permissionFailures = restoreGrantedRuntimePermissions(packageName, permissionsToRestore)
                            if (permissionFailures.isNotEmpty()) {
                                appProcessSuccess = false
                                failureDetails.add(
                                    context.getString(
                                        R.string.restore_failure_permissions_partial,
                                        appName,
                                        permissionFailures.joinToString(", ")
                                    )
                                )
                            }
                        }
                    }

                    if (appProcessSuccess) successes++ else failures++
                }
            } else {
                successes = selectedAppPackages.size
            }

            currentStageNum = totalStages
            val cleanupStageTitle = "[${currentStageNum}/${totalStages}] ${context.getString(R.string.restore_stage_cleanup)}"
            progressState = progressState.copy(
                stageTitle = cleanupStageTitle,
                stagePercentage = 0f,
                overallPercentage = (totalStages - 1).toFloat() / totalStages.toFloat(),
                isFinished = false,
                elapsedTime = (System.currentTimeMillis() - startTime) / 1000
            )
            onProgress(progressState)

            tempRestoreDir.let { dir -> Shell.cmd("rm -rf '${dir.absolutePath}'").exec() }
            progressState = progressState.copy(stagePercentage = 1f, overallPercentage = 1f)
            onProgress(progressState)
            throwIfCancelled()

            val finalElapsedTime = (System.currentTimeMillis() - startTime) / 1000
            val summary = buildString {
                append(context.getString(R.string.restore_summary_finished_in, formatElapsedTime(finalElapsedTime)))
                append(
                    context.resources.getQuantityString(
                        R.plurals.restore_summary_processed,
                        successes,
                        successes
                    )
                )
                if (failures > 0) {
                    append(
                        context.resources.getQuantityString(
                            R.plurals.restore_summary_failed,
                            failures,
                            failures
                        )
                    )
                }
                if (failureDetails.isNotEmpty()) {
                    append(context.getString(R.string.restore_summary_details, failureDetails.joinToString("\n- ")))
                }
            }

            val finalProgress = OperationProgress(
                isFinished = true,
                finalSummary = summary,
                error = if (failures > 0) failureDetails.joinToString(", ") else null,
                elapsedTime = finalElapsedTime,
                filesProcessed = successes,
                totalFiles = selectedAppPackages.size
            )

            OperationRunResult(success = failures == 0, progress = finalProgress)
        } catch (e: OperationCancelledException) {
            val finalProgress = progressState.copy(
                isFinished = true,
                error = context.getString(R.string.operation_interrupted),
                finalSummary = context.getString(R.string.operation_interrupted),
                elapsedTime = (System.currentTimeMillis() - startTime) / 1000
            )
            OperationRunResult(success = false, progress = finalProgress)
        } catch (e: Exception) {
            val finalProgress = progressState.copy(
                isFinished = true,
                error = context.getString(R.string.error_fatal_with_message, e.message ?: ""),
                finalSummary = context.getString(R.string.error_fatal_with_message, e.message ?: ""),
                elapsedTime = (System.currentTimeMillis() - startTime) / 1000
            )
            OperationRunResult(success = false, progress = finalProgress)
        } finally {
            if (operationLockAcquired) {
                operationLockManager.release()
            }
            passwordFile?.delete()
            tempRestoreDir?.let { dir -> Shell.cmd("rm -rf '${dir.absolutePath}'").exec() }
        }

        return finalResult
    }

    private suspend fun findSnapshot(request: RestoreWorkRequest): SnapshotInfo? {
        val repository = repositoriesRepository.getRepositoryByKey(request.repositoryKey) ?: return null
        val password = repositoriesRepository.getRepositoryPassword(request.repositoryKey) ?: return null
        val snapshotsResult = resticRepository.getSnapshots(
            repository.path,
            password,
            repositoriesRepository.getExecutionEnvironmentVariables(request.repositoryKey),
            repositoriesRepository.getExecutionResticOptions(request.repositoryKey)
        )
        val snapshots = snapshotsResult.getOrNull() ?: return null
        return snapshots.find { it.id == request.snapshotId } ?: snapshots.find { it.id.startsWith(request.snapshotId) }
    }

    private fun generatePathsToRestore(
        selectedPackageNames: List<String>,
        snapshot: SnapshotInfo,
        types: RestoreTypeSelection
    ): List<String> {
        val paths = mutableListOf<String>()

        selectedPackageNames.forEach { pkg ->
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

    private fun moveRestoredDataAndFixPerms(
        packageName: String,
        tempRestoreDir: File,
        types: RestoreTypeSelection
    ): Boolean {
        var allSucceeded = true

        val ownerResult = Shell.cmd("stat -c '%U:%G' /data/data/$packageName").exec()
        if (!ownerResult.isSuccess || ownerResult.out.isEmpty()) return false
        val owner = ownerResult.out.first().trim()

        val dataMappings = mutableListOf<Pair<File, String>>()
        if (types.data) dataMappings.add(File(tempRestoreDir, "data/data/$packageName") to "/data/data/$packageName")
        if (types.deviceProtectedData) dataMappings.add(File(tempRestoreDir, "data/user_de/0/$packageName") to "/data/user_de/0/$packageName")
        if (types.externalData) dataMappings.add(File(tempRestoreDir, "storage/emulated/0/Android/data/$packageName") to "/storage/emulated/0/Android/data/$packageName")
        if (types.obb) dataMappings.add(File(tempRestoreDir, "storage/emulated/0/Android/obb/$packageName") to "/storage/emulated/0/Android/obb/$packageName")
        if (types.media) dataMappings.add(File(tempRestoreDir, "storage/emulated/0/Android/media/$packageName") to "/storage/emulated/0/Android/media/$packageName")

        Shell.cmd("am force-stop $packageName").exec()

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

                val relabelCommand = if (destination.startsWith("/data/data/")) {
                    "restorecon -RFD '$destination'"
                } else {
                    "restorecon -RF '$destination'"
                }
                val relabelResult = Shell.cmd(relabelCommand).exec()
                if (!relabelResult.isSuccess) {
                    allSucceeded = false
                    Log.w("RestoreOperationRunner", "Failed to relabel restored path $destination")
                }
            }
        }
        return allSucceeded
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
            context.packageManager.getPackageInfo(
                packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0)
            )
            true
        } catch (_: Exception) {
            false
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
}
