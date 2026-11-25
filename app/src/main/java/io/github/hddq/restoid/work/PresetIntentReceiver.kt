package io.github.hddq.restoid.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.hddq.restoid.R
import io.github.hddq.restoid.RestoidApplication
import io.github.hddq.restoid.data.LocalRepository
import io.github.hddq.restoid.model.TaskPreset

class PresetIntentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_RUN_PRESET) return
        val app = context.applicationContext as? RestoidApplication ?: return
        if (!app.preferencesRepository.loadAllowExternalIntents()) return

        val data = runCatching {
            Data.Builder()
                .putString(EXTRA_PRESET_ID, intent.getStringExtra(EXTRA_PRESET_ID)?.trim())
                .putString(EXTRA_PRESET_NAME, intent.getStringExtra(EXTRA_PRESET_NAME)?.trim())
                .putString(EXTRA_REPOSITORY, intent.getStringExtra(EXTRA_REPOSITORY)?.trim())
                .build()
        }.getOrNull() ?: return

        val request = OneTimeWorkRequestBuilder<PresetIntentWorker>()
            .setInputData(data)
            .build()

        WorkManager.getInstance(app).enqueue(request)
    }

    companion object {
        const val ACTION_RUN_PRESET = "io.github.hddq.restoid.action.RUN_PRESET"
        const val EXTRA_PRESET_NAME = "preset_name"
        const val EXTRA_PRESET_ID = "preset_id"
        const val EXTRA_REPOSITORY = "repository"
    }
}

class PresetIntentWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val app = applicationContext as RestoidApplication

    override suspend fun doWork(): Result {
        if (!app.preferencesRepository.loadAllowExternalIntents()) {
            return Result.success()
        }

        app.repositoriesRepository.loadRepositories()

        val presetId = inputData.getString(PresetIntentReceiver.EXTRA_PRESET_ID)?.takeIf { it.isNotBlank() }
        val presetName = inputData.getString(PresetIntentReceiver.EXTRA_PRESET_NAME)?.takeIf { it.isNotBlank() }
        val repositoryQuery = inputData.getString(PresetIntentReceiver.EXTRA_REPOSITORY)?.takeIf { it.isNotBlank() }

        val repositories = app.repositoriesRepository.repositories.value
        val presetsByRepoId = buildMap {
            for (repo in repositories) {
                val repoId = repo.id ?: continue
                put(repoId, app.metadataRepository.getTaskPresets(repoId))
            }
        }

        val resolution = resolvePresetForIntent(
            repositories = repositories,
            repositoryKeyOf = app.repositoriesRepository::repositoryKey,
            selectedRepoKey = app.repositoriesRepository.selectedRepository.value,
            presetsByRepoId = presetsByRepoId,
            presetId = presetId,
            presetName = presetName,
            repositoryQuery = repositoryQuery
        )

        return when (resolution) {
            is PresetIntentResolution.Matched -> {
                val repoId = resolution.repository.id ?: return Result.failure()
                val repoKey = app.repositoriesRepository.repositoryKey(resolution.repository)
                val workRequest = resolution.preset.config.toWorkRequest(
                    repositoryKey = repoKey,
                    hasRoot = app.preferencesRepository.loadHasRoot(),
                    presetName = resolution.preset.name
                )
                val enqueued = app.operationWorkRepository.enqueueRunTasks(workRequest)
                if (!enqueued) {
                    return if (runAttemptCount < MAX_ENQUEUE_RETRY_ATTEMPTS) Result.retry() else Result.failure()
                }
                app.taskPresetRepository.updateLastRunTimestamp(repoId, resolution.preset.id)
                Result.success()
            }

            PresetIntentResolution.MissingIdentifier -> {
                notifyIntentErrorIfIdle(applicationContext.getString(R.string.intent_error_missing_preset))
                Result.failure()
            }

            is PresetIntentResolution.RepositoryNotFound -> {
                notifyIntentErrorIfIdle(
                    applicationContext.getString(R.string.intent_error_repository_not_found, resolution.query)
                )
                Result.failure()
            }

            is PresetIntentResolution.PresetNotFound -> {
                notifyIntentErrorIfIdle(
                    applicationContext.getString(R.string.intent_error_preset_not_found, resolution.query)
                )
                Result.failure()
            }

            is PresetIntentResolution.AmbiguousPreset -> {
                notifyIntentErrorIfIdle(
                    applicationContext.getString(R.string.intent_error_preset_ambiguous, resolution.name)
                )
                Result.failure()
            }
        }
    }

    private fun notifyIntentErrorIfIdle(message: String) {
        if (app.operationRuntimeRepository.state.value.isRunning) return
        app.notificationRepository.showOperationFinishedNotification(
            operationName = applicationContext.getString(R.string.operation_run_tasks),
            success = false,
            summary = message
        )
    }

    private companion object {
        const val MAX_ENQUEUE_RETRY_ATTEMPTS = 5
    }
}

internal sealed interface PresetIntentResolution {
    data class Matched(val repository: LocalRepository, val preset: TaskPreset) : PresetIntentResolution
    data object MissingIdentifier : PresetIntentResolution
    data class RepositoryNotFound(val query: String) : PresetIntentResolution
    data class PresetNotFound(val query: String) : PresetIntentResolution
    data class AmbiguousPreset(val name: String) : PresetIntentResolution
}

internal fun resolvePresetForIntent(
    repositories: List<LocalRepository>,
    repositoryKeyOf: (LocalRepository) -> String,
    selectedRepoKey: String?,
    presetsByRepoId: Map<String, List<TaskPreset>>,
    presetId: String?,
    presetName: String?,
    repositoryQuery: String?
): PresetIntentResolution {
    val normalizedId = presetId?.trim()?.takeIf { it.isNotEmpty() }
    val normalizedName = presetName?.trim()?.takeIf { it.isNotEmpty() }
    val normalizedRepo = repositoryQuery?.trim()?.takeIf { it.isNotEmpty() }

    if (normalizedId == null && normalizedName == null) {
        return PresetIntentResolution.MissingIdentifier
    }

    val candidateRepos = if (normalizedRepo != null) {
        repositories.filter { repo ->
            repo.name.equals(normalizedRepo, ignoreCase = true) ||
                repo.path == normalizedRepo ||
                repositoryKeyOf(repo) == normalizedRepo
        }.also { matched ->
            if (matched.isEmpty()) return PresetIntentResolution.RepositoryNotFound(normalizedRepo)
        }
    } else {
        repositories
    }

    val allCandidates = buildList {
        for (repo in candidateRepos) {
            val repoId = repo.id ?: continue
            val presets = presetsByRepoId[repoId].orEmpty()
            for (preset in presets) {
                add(repo to preset)
            }
        }
    }

    if (normalizedId != null) {
        val match = allCandidates.firstOrNull { (_, preset) -> preset.id == normalizedId }
        return if (match != null) {
            PresetIntentResolution.Matched(match.first, match.second)
        } else {
            PresetIntentResolution.PresetNotFound(normalizedId)
        }
    }

    val name = normalizedName!!
    val exactMatches = allCandidates.filter { (_, preset) -> preset.name == name }
    val matches = exactMatches.ifEmpty {
        allCandidates.filter { (_, preset) -> preset.name.equals(name, ignoreCase = true) }
    }

    return when (matches.size) {
        0 -> PresetIntentResolution.PresetNotFound(name)
        1 -> PresetIntentResolution.Matched(matches[0].first, matches[0].second)
        else -> {
            val selectedMatch = selectedRepoKey?.let { key ->
                matches.singleOrNull { (repo, _) -> repositoryKeyOf(repo) == key }
            }
            if (selectedMatch != null) {
                PresetIntentResolution.Matched(selectedMatch.first, selectedMatch.second)
            } else {
                PresetIntentResolution.AmbiguousPreset(name)
            }
        }
    }
}
