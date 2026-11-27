package io.github.hddq.restoid.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import io.github.hddq.restoid.ui.components.ExpressiveSegmentedSurface
import io.github.hddq.restoid.ui.screens.settings.components.SelectableRepositoryRow
import io.github.hddq.restoid.ui.settings.SettingsViewModel

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
        onSelected: () -> Unit
    ) -> Unit = { repo, isSelected, onSelected ->
        io.github.hddq.restoid.ui.screens.settings.components.SelectableRepositoryRowLayout(
            repo = repo,
            isSelected = isSelected,
            onSelected = onSelected
        )
    }
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ExpressiveSectionHeader(title = stringResource(R.string.backup_repositories_title))

        if (resticState is ResticState.Installed) {
            if (repositories.isEmpty()) {
                ExpressiveSegmentedSurface(index = 0, count = 1) {
                    Text(
                        stringResource(R.string.no_repositories_configured),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            } else {
                ExpressiveCardGroup {
                    repositories.forEachIndexed { index, repo ->
                        val repoKey = repositoryKey(repo)
                        ExpressiveSegmentedSurface(
                            index = index,
                            count = repositories.size
                        ) {
                            rowContent(repo, repoKey == selectedRepositoryKey) {
                                onSelectRepository(repoKey)
                            }
                        }
                    }
                }
            }
        } else {
            ExpressiveSegmentedSurface(index = 0, count = 1) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                        tint = LocalContentColor.current.copy(alpha = 0.6f)
                    )
                    Text(
                        stringResource(R.string.restic_not_available_check_settings),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
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
        rowContent = { repo, isSelected, onSelected ->
            SelectableRepositoryRow(
                repo = repo,
                isSelected = isSelected,
                onSelected = onSelected,
                viewModel = viewModel
            )
        }
    )
}
