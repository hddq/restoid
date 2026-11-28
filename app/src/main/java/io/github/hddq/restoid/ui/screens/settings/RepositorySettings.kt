package io.github.hddq.restoid.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.ResticState
import io.github.hddq.restoid.ui.components.ExpressiveCardGroup
import io.github.hddq.restoid.ui.components.ExpressiveSectionHeader
import io.github.hddq.restoid.ui.components.expressiveListItemColors
import io.github.hddq.restoid.ui.screens.settings.components.SelectableRepositoryRow
import io.github.hddq.restoid.ui.screens.settings.components.SelectableRepositoryRowLayout
import io.github.hddq.restoid.ui.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RepositorySettingsContent(
    resticState: ResticState,
    repositories: List<io.github.hddq.restoid.data.LocalRepository>,
    selectedRepositoryKey: String?,
    repositoryKey: (io.github.hddq.restoid.data.LocalRepository) -> String,
    onSelectRepository: (String) -> Unit = {},
    onAddRepository: () -> Unit = {},
    rowContent: @Composable (
        repo: io.github.hddq.restoid.data.LocalRepository,
        isSelected: Boolean,
        shapes: ListItemShapes,
        onSelected: () -> Unit
    ) -> Unit = { repo, isSelected, shapes, onSelected ->
        SelectableRepositoryRowLayout(
            repo = repo,
            isSelected = isSelected,
            shapes = shapes,
            onSelected = onSelected
        )
    }
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ExpressiveSectionHeader(title = stringResource(R.string.backup_repositories_title))

        if (resticState is ResticState.Installed) {
            if (repositories.isEmpty()) {
                SegmentedListItem(
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                    verticalAlignment = Alignment.CenterVertically,
                    colors = expressiveListItemColors(),
                    content = {
                        Text(
                            text = stringResource(R.string.no_repositories_configured),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                )
            } else {
                ExpressiveCardGroup {
                    repositories.forEachIndexed { index, repo ->
                        val repoKey = repositoryKey(repo)
                        rowContent(
                            repo,
                            repoKey == selectedRepositoryKey,
                            ListItemDefaults.segmentedShapes(index = index, count = repositories.size)
                        ) {
                            onSelectRepository(repoKey)
                        }
                    }
                }
            }
        } else {
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                verticalAlignment = Alignment.CenterVertically,
                colors = expressiveListItemColors(),
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null
                    )
                },
                content = {
                    Text(
                        text = stringResource(R.string.restic_not_available_check_settings),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            )
        }

        if (resticState is ResticState.Installed) {
            FilledTonalButton(
                onClick = onAddRepository,
                modifier = Modifier.padding(top = 12.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.cd_add_repository))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RepositorySettings(viewModel: SettingsViewModel) {
    val resticState by viewModel.resticState.collectAsStateWithLifecycle()
    val repositories by viewModel.repositories.collectAsStateWithLifecycle()
    val selectedRepository by viewModel.selectedRepository.collectAsStateWithLifecycle()

    RepositorySettingsContent(
        resticState = resticState,
        repositories = repositories,
        selectedRepositoryKey = selectedRepository,
        repositoryKey = viewModel::repositoryKey,
        onSelectRepository = viewModel::selectRepository,
        onAddRepository = viewModel::onShowAddRepoDialog,
        rowContent = { repo, isSelected, shapes, onSelected ->
            SelectableRepositoryRow(
                repo = repo,
                isSelected = isSelected,
                shapes = shapes,
                onSelected = onSelected,
                viewModel = viewModel
            )
        }
    )
}
