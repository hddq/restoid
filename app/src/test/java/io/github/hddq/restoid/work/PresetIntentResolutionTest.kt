package io.github.hddq.restoid.work

import io.github.hddq.restoid.data.LocalRepository
import io.github.hddq.restoid.model.TaskPreset
import org.junit.Assert.assertEquals
import org.junit.Test

class PresetIntentResolutionTest {

    private val repoA = LocalRepository(path = "/backup/a", name = "RepoA", id = "repo-a")
    private val repoB = LocalRepository(path = "/backup/b", name = "RepoB", id = "repo-b")
    private val repos = listOf(repoA, repoB)

    private val dailyA = TaskPreset(id = "id-daily-a", name = "Daily Backup", config = RunTasksConfig())
    private val weeklyA = TaskPreset(id = "id-weekly-a", name = "Weekly", config = RunTasksConfig())
    private val dailyB = TaskPreset(id = "id-daily-b", name = "Daily Backup", config = RunTasksConfig())

    private val presetsByRepo = mapOf(
        "repo-a" to listOf(dailyA, weeklyA),
        "repo-b" to listOf(dailyB)
    )

    private fun keyOf(repo: LocalRepository): String = "${repo.backendType.name}::${repo.path}"

    @Test
    fun missingIdentifierReturnsMissingIdentifier() {
        val result = resolvePresetForIntent(
            repositories = repos,
            repositoryKeyOf = ::keyOf,
            selectedRepoKey = null,
            presetsByRepoId = presetsByRepo,
            presetId = "  ",
            presetName = null,
            repositoryQuery = null
        )
        assertEquals(PresetIntentResolution.MissingIdentifier, result)
    }

    @Test
    fun resolvesUniquePresetByNameCaseInsensitive() {
        val result = resolvePresetForIntent(
            repositories = repos,
            repositoryKeyOf = ::keyOf,
            selectedRepoKey = null,
            presetsByRepoId = presetsByRepo,
            presetId = null,
            presetName = "  weekly ",
            repositoryQuery = null
        )
        assertEquals(PresetIntentResolution.Matched(repoA, weeklyA), result)
    }

    @Test
    fun resolvesDuplicateNameUsingRepositoryExtra() {
        val result = resolvePresetForIntent(
            repositories = repos,
            repositoryKeyOf = ::keyOf,
            selectedRepoKey = keyOf(repoA),
            presetsByRepoId = presetsByRepo,
            presetId = null,
            presetName = "Daily Backup",
            repositoryQuery = "repob"
        )
        assertEquals(PresetIntentResolution.Matched(repoB, dailyB), result)
    }

    @Test
    fun resolvesDuplicateNameUsingSelectedRepoFallback() {
        val result = resolvePresetForIntent(
            repositories = repos,
            repositoryKeyOf = ::keyOf,
            selectedRepoKey = keyOf(repoB),
            presetsByRepoId = presetsByRepo,
            presetId = null,
            presetName = "Daily Backup",
            repositoryQuery = null
        )
        assertEquals(PresetIntentResolution.Matched(repoB, dailyB), result)
    }

    @Test
    fun returnsAmbiguousWhenDuplicateNameHasNoDisambiguation() {
        val result = resolvePresetForIntent(
            repositories = repos,
            repositoryKeyOf = ::keyOf,
            selectedRepoKey = null,
            presetsByRepoId = presetsByRepo,
            presetId = null,
            presetName = "Daily Backup",
            repositoryQuery = null
        )
        assertEquals(PresetIntentResolution.AmbiguousPreset("Daily Backup"), result)
    }

    @Test
    fun resolvesByPresetIdWithPrecedenceOverName() {
        val result = resolvePresetForIntent(
            repositories = repos,
            repositoryKeyOf = ::keyOf,
            selectedRepoKey = null,
            presetsByRepoId = presetsByRepo,
            presetId = "id-daily-b",
            presetName = "Weekly",
            repositoryQuery = null
        )
        assertEquals(PresetIntentResolution.Matched(repoB, dailyB), result)
    }

    @Test
    fun returnsRepositoryNotFoundWhenRepositoryQueryDoesNotMatch() {
        val result = resolvePresetForIntent(
            repositories = repos,
            repositoryKeyOf = ::keyOf,
            selectedRepoKey = null,
            presetsByRepoId = presetsByRepo,
            presetId = null,
            presetName = "Weekly",
            repositoryQuery = "MissingRepo"
        )
        assertEquals(PresetIntentResolution.RepositoryNotFound("MissingRepo"), result)
    }

    @Test
    fun returnsPresetNotFoundWhenPresetDoesNotExist() {
        val result = resolvePresetForIntent(
            repositories = repos,
            repositoryKeyOf = ::keyOf,
            selectedRepoKey = null,
            presetsByRepoId = presetsByRepo,
            presetId = null,
            presetName = "Renamed Preset",
            repositoryQuery = null
        )
        assertEquals(PresetIntentResolution.PresetNotFound("Renamed Preset"), result)
    }
}
