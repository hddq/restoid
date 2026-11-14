package io.github.hddq.restoid.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hddq.restoid.R
import io.github.hddq.restoid.ui.components.ExpressiveCardGroup
import io.github.hddq.restoid.ui.components.ExpressiveSectionHeader
import io.github.hddq.restoid.ui.components.ExpressiveSegmentedSurface
import io.github.hddq.restoid.ui.screens.settings.components.RcloneDependencyRow
import io.github.hddq.restoid.ui.screens.settings.components.ResticDependencyRow
import io.github.hddq.restoid.ui.settings.SettingsViewModel

@Composable
fun DependencySettings(viewModel: SettingsViewModel) {
    val resticState by viewModel.resticState.collectAsStateWithLifecycle()
    val rcloneState by viewModel.rcloneState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxWidth()) {
        ExpressiveSectionHeader(title = stringResource(R.string.dependencies_title))
        ExpressiveCardGroup {
            ExpressiveSegmentedSurface(index = 0, count = 2) {
                ResticDependencyRow(state = resticState)
            }
            ExpressiveSegmentedSurface(index = 1, count = 2) {
                RcloneDependencyRow(state = rcloneState)
            }
        }
    }
}
