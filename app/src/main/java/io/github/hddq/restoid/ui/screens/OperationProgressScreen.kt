package io.github.hddq.restoid.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.hddq.restoid.R
import io.github.hddq.restoid.RestoidApplication
import io.github.hddq.restoid.data.OperationType
import io.github.hddq.restoid.ui.operation.OperationProgressViewModel
import io.github.hddq.restoid.ui.operation.OperationProgressViewModelFactory
import io.github.hddq.restoid.ui.shared.ProgressScreenContent

@Composable
fun OperationProgressScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val application = LocalContext.current.applicationContext as RestoidApplication
    val viewModel: OperationProgressViewModel = viewModel(
        factory = OperationProgressViewModelFactory(application.operationWorkRepository)
    )
    val state by viewModel.state.collectAsState()

    val operationTypeLabel = when (state.operationType) {
        OperationType.BACKUP -> stringResource(R.string.operation_backup)
        OperationType.RESTORE -> stringResource(R.string.operation_restore)
        OperationType.MAINTENANCE -> stringResource(R.string.operation_maintenance)
        null -> ""
    }

    val showProgress = state.isRunning || state.progress.isFinished

    if (showProgress && operationTypeLabel.isNotBlank()) {
        ProgressScreenContent(
            progress = state.progress,
            operationType = operationTypeLabel,
            onDone = {
                viewModel.onDone()
                onNavigateUp()
            },
            modifier = modifier.fillMaxSize()
        )
    } else {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.operation_progress_no_active_operation),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
