package io.github.hddq.restoid.work

import android.content.Context
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.OperationLockManager
import io.github.hddq.restoid.data.RepositoriesRepository
import io.github.hddq.restoid.data.RepositoryBackendType
import io.github.hddq.restoid.data.ResticBinaryManager
import io.github.hddq.restoid.data.ResticRepository
import io.github.hddq.restoid.data.ResticState
import io.github.hddq.restoid.ui.shared.OperationProgress
import io.github.hddq.restoid.util.MaintenanceOutputParser

class MaintenanceOperationRunner(
    private val context: Context,
    private val repositoriesRepository: RepositoriesRepository,
    private val resticBinaryManager: ResticBinaryManager,
    private val resticRepository: ResticRepository,
    private val operationLockManager: OperationLockManager
) {

    suspend fun run(
        request: MaintenanceWorkRequest,
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
        var finalSummary = StringBuilder()
        var overallSuccess = true
        var wasCancelled = false

        try {
            throwIfCancelled()
            val errorState = preflightChecks(request)
            if (errorState != null) {
                return OperationRunResult(success = false, progress = errorState)
            }

            val repository = repositoriesRepository.getRepositoryByKey(request.repositoryKey)
                ?: throw IllegalStateException(context.getString(R.string.summary_no_backup_repository_selected))

            operationLockManager.acquire(repository.backendType)
            operationLockAcquired = true

            val selectedRepoPath = repository.path
            val repositoryEnvironment = repositoriesRepository.getExecutionEnvironmentVariables(request.repositoryKey)
            val repositoryResticOptions = repositoriesRepository.getExecutionResticOptions(request.repositoryKey)
            val password = repositoriesRepository.getRepositoryPassword(request.repositoryKey)
                ?: throw IllegalStateException(context.getString(R.string.error_password_not_found_for_repository))

            val tasksToRun = mutableListOf<Pair<String, suspend () -> Result<String>>>()
            if (request.unlockRepo) {
                tasksToRun.add(
                    context.getString(R.string.maintenance_task_unlock) to {
                        resticRepository.unlock(selectedRepoPath, password, repositoryEnvironment, repositoryResticOptions)
                    }
                )
            }
            if (request.forgetSnapshots) {
                tasksToRun.add(
                    context.getString(R.string.maintenance_task_forget) to {
                        resticRepository.forget(
                            selectedRepoPath,
                            password,
                            request.keepLast,
                            request.keepDaily,
                            request.keepWeekly,
                            request.keepMonthly,
                            repositoryEnvironment,
                            repositoryResticOptions
                        )
                    }
                )
            }
            if (request.pruneRepo) {
                tasksToRun.add(
                    context.getString(R.string.maintenance_task_prune) to {
                        resticRepository.prune(selectedRepoPath, password, repositoryEnvironment, repositoryResticOptions)
                    }
                )
            }
            if (request.checkRepo) {
                tasksToRun.add(
                    context.getString(R.string.maintenance_task_check) to {
                        resticRepository.check(
                            selectedRepoPath,
                            password,
                            request.readData,
                            repositoryEnvironment,
                            repositoryResticOptions
                        )
                    }
                )
            }

            if (tasksToRun.isEmpty()) throw IllegalStateException(context.getString(R.string.maintenance_error_no_tasks_selected))

            tasksToRun.forEachIndexed { index, (taskName, taskAction) ->
                throwIfCancelled()
                val stageTitle = context.getString(R.string.maintenance_stage_running_task, index + 1, tasksToRun.size, taskName)
                progressState = progressState.copy(
                    stageTitle = stageTitle,
                    overallPercentage = index.toFloat() / tasksToRun.size,
                    stagePercentage = 0f,
                    isFinished = false,
                    elapsedTime = (System.currentTimeMillis() - startTime) / 1000
                )
                onProgress(progressState)

                val result = taskAction()
                throwIfCancelled()

                progressState = progressState.copy(
                    overallPercentage = (index + 1f) / tasksToRun.size,
                    stagePercentage = 1f,
                    elapsedTime = (System.currentTimeMillis() - startTime) / 1000
                )
                onProgress(progressState)

                result.fold(
                    onSuccess = { output ->
                        val summary = MaintenanceOutputParser.parse(taskName, output, context)
                        finalSummary.append(context.getString(R.string.maintenance_task_successful, taskName, summary))
                    },
                    onFailure = { exception ->
                        overallSuccess = false
                        finalSummary.append(context.getString(R.string.maintenance_task_failed, taskName, exception.message ?: ""))
                    }
                )
            }

            if ((request.pruneRepo || request.forgetSnapshots) && overallSuccess) {
                resticRepository.refreshSnapshots(
                    selectedRepoPath,
                    password,
                    repositoryEnvironment,
                    repositoryResticOptions
                )
            }
        } catch (e: OperationCancelledException) {
            overallSuccess = false
            wasCancelled = true
            finalSummary.append(context.getString(R.string.operation_interrupted))
        } catch (e: Exception) {
            overallSuccess = false
            finalSummary.append(context.getString(R.string.error_fatal_with_message, e.message ?: ""))
        } finally {
            if (operationLockAcquired) {
                operationLockManager.release()
            }
        }

        val finalProgress = progressState.copy(
            isFinished = true,
            finalSummary = finalSummary.toString().trim(),
            error = if (!overallSuccess) {
                if (wasCancelled) context.getString(R.string.operation_interrupted)
                else context.getString(R.string.maintenance_error_one_or_more_failed)
            } else {
                null
            },
            elapsedTime = (System.currentTimeMillis() - startTime) / 1000,
            overallPercentage = 1f,
            stagePercentage = 1f
        )

        return OperationRunResult(success = overallSuccess, progress = finalProgress)
    }

    private fun preflightChecks(request: MaintenanceWorkRequest): OperationProgress? {
        if (!request.checkRepo && !request.pruneRepo && !request.unlockRepo && !request.forgetSnapshots) {
            return OperationProgress(
                isFinished = true,
                error = context.getString(R.string.maintenance_error_no_tasks),
                finalSummary = context.getString(R.string.maintenance_summary_no_tasks)
            )
        }
        if (resticBinaryManager.resticState.value !is ResticState.Installed) {
            return OperationProgress(
                isFinished = true,
                error = context.getString(R.string.error_restic_not_installed),
                finalSummary = context.getString(R.string.summary_restic_binary_not_installed)
            )
        }

        val repository = repositoriesRepository.getRepositoryByKey(request.repositoryKey)
        if (repository == null) {
            return OperationProgress(
                isFinished = true,
                error = context.getString(R.string.error_no_backup_repository_selected),
                finalSummary = context.getString(R.string.summary_no_backup_repository_selected)
            )
        }

        if (repositoriesRepository.getRepositoryPassword(request.repositoryKey) == null) {
            return OperationProgress(
                isFinished = true,
                error = context.getString(R.string.error_password_not_found_for_repository),
                finalSummary = context.getString(R.string.summary_password_not_found)
            )
        }

        if (repository.backendType == RepositoryBackendType.SFTP && !repositoriesRepository.hasSftpPassword(request.repositoryKey)) {
            return OperationProgress(
                isFinished = true,
                error = context.getString(R.string.error_sftp_password_not_found_for_repository),
                finalSummary = context.getString(R.string.summary_sftp_password_not_found)
            )
        }

        return null
    }
}
