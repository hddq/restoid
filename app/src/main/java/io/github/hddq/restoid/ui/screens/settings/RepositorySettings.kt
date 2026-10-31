package io.github.hddq.restoid.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
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
fun RepositorySettings(viewModel: SettingsViewModel) {
    val resticState by viewModel.resticState.collectAsStateWithLifecycle()
    val repositories by viewModel.repositories.collectAsStateWithLifecycle()
    val selectedRepository by viewModel.selectedRepository.collectAsStateWithLifecycle()

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
                        val repoKey = viewModel.repositoryKey(repo)
                        ExpressiveSegmentedSurface(
                            index = index,
                            count = repositories.size
                        ) {
                            SelectableRepositoryRow(
                                repo = repo,
                                isSelected = repoKey == selectedRepository,
                                onSelected = { viewModel.selectRepository(repoKey) },
                                viewModel = viewModel
                            )
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
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                        tint = LocalContentColor.current.copy(alpha = 0.6f)
                    )
                    Text(
                        stringResource(R.string.install_restic_manage_repositories),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        if (resticState is ResticState.Installed) {
            FilledTonalButton(
                onClick = { viewModel.onShowAddRepoDialog() },
                modifier = Modifier.padding(top = 12.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.cd_add_repository))
            }
        }
    }
}
