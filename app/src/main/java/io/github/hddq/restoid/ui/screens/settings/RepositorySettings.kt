package io.github.hddq.restoid.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hddq.restoid.data.ResticState
import io.github.hddq.restoid.ui.screens.settings.components.SelectableRepositoryRow
import io.github.hddq.restoid.ui.settings.SettingsViewModel

@Composable
fun RepositorySettings(viewModel: SettingsViewModel) {
    val resticState by viewModel.resticState.collectAsStateWithLifecycle()
    val repositories by viewModel.repositories.collectAsStateWithLifecycle()
    val selectedRepository by viewModel.selectedRepository.collectAsStateWithLifecycle()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Backup Repositories", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                if (resticState is ResticState.Installed) {
                    IconButton(onClick = { viewModel.onShowAddRepoDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Repository")
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            if (resticState is ResticState.Installed) {
                if (repositories.isEmpty()) {
                    Text(
                        "No repositories configured. Add one to get started.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column {
                        repositories.forEach { repo ->
                            SelectableRepositoryRow(
                                repo = repo,
                                isSelected = repo.path == selectedRepository,
                                onSelected = { viewModel.selectRepository(repo.path) },
                                viewModel = viewModel
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                        tint = LocalContentColor.current.copy(alpha = 0.6f)
                    )
                    Text(
                        "Install restic to manage repositories.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
