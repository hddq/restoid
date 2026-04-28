package io.github.hddq.restoid.work

import android.content.Context
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.AppInfoRepository
import io.github.hddq.restoid.data.OperationLockManager
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.ResticBinaryManager
import io.github.hddq.restoid.data.ResticRepository
import io.github.hddq.restoid.ui.shared.OperationProgress

class RunTasksOperationRunner(
    private val context: Context,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticBinaryManager: ResticBinaryManager,
    private val resticRepository: ResticRepository,
    private val appInfoRepository: AppInfoRepository,
    private val operationLockManager: OperationLockManager
) {

    private val backupRunner by lazy {
        BackupOperationRunner(
            context = context,
            repositoriesRepository = repositoriesRepository,
            resticBinaryManager = resticBinaryManager,
            resticRepository = resticRepository,
            appInfoRepository = appInfoRepository,
            operationLockManager = operationLockManager
        )
    }

    private val maintenanceRunner by lazy {
        MaintenanceOperationRunner(
            context = context,
            repositoriesRepository = repositoriesRepository,
            resticBinaryManager = resticBinaryManager,
            resticRepository = resticRepository,
            operationLockManager = operationLockManager
        )
    }

    suspend fun run(
        request: RunTasksWorkRequest,
        onProgress: (OperationProgress) -> Unit,
        shouldStop: () -> Boolean = { false }
    ): OperationRunResult {
        fun throwIfCancelled() {
            if (shouldStop()) {
                throw OperationCancelledException(context.getString(R.string.operation_interrupted))
            }
        }

        val backupStageCount = if (request.backupEnabled) 3 else 0
        val maintenanceTaskCount = listOf(
            request.unlockRepo,
            request.forgetSnapshots,
            request.pruneRepo,
            request.checkRepo
        ).count { it }
        val totalEnabledTasks = backupStageCount + maintenanceTaskCount

        if (totalEnabledTasks == 0) {
            return OperationRunResult(
                success = false,
                progress = OperationProgress(
                    isFinished = true,
                    error = context.getString(R.string.maintenance_error_no_tasks),
                    finalSummary = context.getString(R.string.maintenance_summary_no_tasks)
                )
            )
        }

        val startTime = System.currentTimeMillis()
        var completedTaskUnits = 0
        var progressState = OperationProgress(stageTitle = context.getString(R.string.progress_initializing))
        val summaries = mutableListOf<String>()
        var overallSuccess = true
        var wasCancelled = false

        fun mapChildProgress(child: OperationProgress, spanTasks: Int): OperationProgress {
            val remappedOverall = (
                completedTaskUnits + (child.overallPercentage.coerceIn(0f, 1f) * spanTasks)
            ) / totalEnabledTasks.toFloat()

            return child.copy(
                overallPercentage = remappedOverall,
                elapsedTime = (System.currentTimeMillis() - startTime) / 1000,
                isFinished = false,
                error = null,
                finalSummary = ""
            )
        }

        try {
            throwIfCancelled()

            if (request.backupEnabled) {
                val backupRequest = BackupWorkRequest(
                    repositoryKey = request.repositoryKey,
                    backupTypes = request.backupTypes,
                    selectedPackageNames = request.selectedPackageNames
                )

                val result = backupRunner.run(
                    request = backupRequest,
                    onProgress = { childProgress ->
                        progressState = mapChildProgress(childProgress, backupStageCount)
                        onProgress(progressState)
                    },
                    shouldStop = shouldStop,
                    stageContext = OperationStageContext(
                        completedStagesBefore = completedTaskUnits,
                        totalStages = totalEnabledTasks
                    )
                )

                completedTaskUnits += backupStageCount
                summaries += context.getString(
                    R.string.run_tasks_phase_summary,
                    context.getString(R.string.operation_backup),
                    result.progress.finalSummary
                )
                overallSuccess = overallSuccess && result.success
                throwIfCancelled()
            }

            if (maintenanceTaskCount > 0) {
                val maintenanceRequest = MaintenanceWorkRequest(
                    repositoryKey = request.repositoryKey,
                    checkRepo = request.checkRepo,
                    pruneRepo = request.pruneRepo,
                    unlockRepo = request.unlockRepo,
                    readData = request.readData,
                    forgetSnapshots = request.forgetSnapshots,
                    keepLast = request.keepLast,
                    keepDaily = request.keepDaily,
                    keepWeekly = request.keepWeekly,
                    keepMonthly = request.keepMonthly
                )

                val result = maintenanceRunner.run(
                    request = maintenanceRequest,
                    onProgress = { childProgress ->
                        progressState = mapChildProgress(childProgress, maintenanceTaskCount)
                        onProgress(progressState)
                    },
                    shouldStop = shouldStop,
                    stageContext = OperationStageContext(
                        completedStagesBefore = completedTaskUnits,
                        totalStages = totalEnabledTasks
                    )
                )

                completedTaskUnits += maintenanceTaskCount
                summaries += context.getString(
                    R.string.run_tasks_phase_summary,
                    context.getString(R.string.operation_maintenance),
                    result.progress.finalSummary
                )
                overallSuccess = overallSuccess && result.success
                throwIfCancelled()
            }
        } catch (e: OperationCancelledException) {
            overallSuccess = false
            wasCancelled = true
            summaries += context.getString(R.string.operation_interrupted)
        } catch (e: Exception) {
            overallSuccess = false
            summaries += context.getString(R.string.error_fatal_with_message, e.message ?: "")
        }

        val finalSummary = summaries.joinToString("\n\n").trim()
        return OperationRunResult(
            success = overallSuccess,
            progress = progressState.copy(
                stageTitle = if (overallSuccess) {
                    context.getString(R.string.progress_operation_complete, context.getString(R.string.operation_run_tasks))
                } else {
                    context.getString(R.string.progress_operation_failed, context.getString(R.string.operation_run_tasks))
                },
                stagePercentage = 1f,
                overallPercentage = 1f,
                elapsedTime = (System.currentTimeMillis() - startTime) / 1000,
                isFinished = true,
                error = if (!overallSuccess) {
                    if (wasCancelled) context.getString(R.string.operation_interrupted)
                    else context.getString(R.string.run_tasks_error_one_or_more_failed)
                } else {
                    null
                },
                finalSummary = finalSummary
            )
        )
    }
}
