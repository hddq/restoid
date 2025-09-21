package io.github.hddq.restoid.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hddq.restoid.ui.screens.settings.components.ResticDependencyRow
import io.github.hddq.restoid.ui.settings.SettingsViewModel

@Composable
fun DependencySettings(viewModel: SettingsViewModel) {
    val resticState by viewModel.resticState.collectAsStateWithLifecycle()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Dependencies", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            ResticDependencyRow(
                state = resticState,
                onDownloadClick = { viewModel.downloadRestic() }
            )
        }
    }
}
