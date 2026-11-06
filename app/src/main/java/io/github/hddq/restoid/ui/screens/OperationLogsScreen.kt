package io.github.hddq.restoid.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.hddq.restoid.LocalAppBarActions
import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.OperationLogEntry
import io.github.hddq.restoid.data.OperationType
import io.github.hddq.restoid.ui.operation.OperationLogsViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun OperationLogsScreen(
    viewModel: OperationLogsViewModel,
    initialSelectedLogId: String? = null,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val logs by viewModel.logs.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val expandedLogIds = remember { mutableStateListOf<String>() }
    var showClearDialog by remember { mutableStateOf(false) }
    var handledInitialSelectedLogId by remember(initialSelectedLogId) { mutableStateOf<String?>(null) }

    // Auto-expand and scroll to initialSelectedLogId once when found
    LaunchedEffect(initialSelectedLogId, logs) {
        if (initialSelectedLogId != null && handledInitialSelectedLogId != initialSelectedLogId) {
            val index = logs.indexOfFirst { it.id == initialSelectedLogId }
            if (index >= 0) {
                if (!expandedLogIds.contains(initialSelectedLogId)) {
                    expandedLogIds.add(initialSelectedLogId)
                }
                listState.animateScrollToItem(index)
                handledInitialSelectedLogId = initialSelectedLogId
            }
        }
    }

    // Install top bar clear-all action button
    val appBarActions = LocalAppBarActions.current
    DisposableEffect(logs.isNotEmpty()) {
        appBarActions.value = if (logs.isNotEmpty()) {
            {
                IconButton(onClick = { showClearDialog = true }) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = stringResource(R.string.operation_logs_clear)
                    )
                }
            }
        } else null

        onDispose {
            appBarActions.value = null
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.operation_logs_clear_confirm_title)) },
            text = { Text(stringResource(R.string.operation_logs_clear_confirm_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearLogs()
                    }
                ) {
                    Text(stringResource(R.string.action_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (logs.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.operation_logs_empty),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.operation_logs_empty_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier.fillMaxSize()
        ) {
            items(logs, key = { it.id }) { log ->
                val isExpanded = expandedLogIds.contains(log.id)
                OperationLogItem(
                    log = log,
                    isExpanded = isExpanded,
                    onToggleExpand = {
                        if (isExpanded) {
                            expandedLogIds.remove(log.id)
                        } else {
                            expandedLogIds.add(log.id)
                        }
                    },
                    onCopyLog = {
                        val textToCopy = buildString {
                            appendLine(log.stageTitle)
                            if (!log.summary.isNullOrBlank()) {
                                appendLine(log.summary)
                            }
                            if (!log.error.isNullOrBlank()) {
                                appendLine("Error: ${log.error}")
                            }
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Operation Log", textToCopy))
                        Toast.makeText(context, context.getString(R.string.operation_logs_copied), Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
private fun OperationLogItem(
    log: OperationLogEntry,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCopyLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val operationLabel = when (log.operationType) {
        OperationType.BACKUP -> stringResource(R.string.operation_backup)
        OperationType.RUN_TASKS -> stringResource(R.string.operation_run_tasks)
        OperationType.RESTORE -> stringResource(R.string.operation_restore)
        OperationType.MAINTENANCE -> stringResource(R.string.operation_maintenance)
    }

    val title = if (log.scheduleName != null) {
        stringResource(R.string.operation_schedule_name, log.scheduleName)
    } else {
        operationLabel
    }

    val formattedDate = remember(log.startTimeMillis) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(log.startTimeMillis))
    }

    val formattedDuration = remember(log.durationSeconds) {
        DateUtils.formatElapsedTime(log.durationSeconds)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (log.success) Icons.Filled.CheckCircle else Icons.Filled.Error,
                    contentDescription = stringResource(
                        if (log.success) R.string.operation_status_success else R.string.operation_status_failed
                    ),
                    tint = if (log.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "$formattedDate • ${stringResource(R.string.operation_logs_duration, formattedDuration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = stringResource(
                            if (isExpanded) R.string.action_collapse else R.string.action_expand
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = log.stageTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (log.summary.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SelectionContainer {
                                Text(
                                    text = log.summary.trim(),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }

                    if (!log.error.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SelectionContainer {
                                Text(
                                    text = log.error.trim(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = onCopyLog,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_copy_log))
                        }
                    }
                }
            }
        }
    }
}
