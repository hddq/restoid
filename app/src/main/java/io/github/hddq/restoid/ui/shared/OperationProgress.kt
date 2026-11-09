package io.github.hddq.restoid.ui.shared

import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.hddq.restoid.R
import io.github.hddq.restoid.ui.theme.RestoidTheme
import java.text.NumberFormat
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

/**
 * Shared data class for representing the progress of a long-running operation
 * like backup or restore. It now supports multi-stage operations.
 */
@Serializable
data class OperationProgress(
    val stageTitle: String = "", // e.g., "Stage 1/2: Restoring Files"
    val stagePercentage: Float = 0f, // progress of the current stage (0.0 to 1.0)
    val overallPercentage: Float = 0f, // overall progress (0.0 to 1.0)
    val isStageIndeterminate: Boolean = false,
    val isOverallIndeterminate: Boolean = false,

    // Details for the current stage
    val totalFiles: Int = 0,
    val filesProcessed: Int = 0,
    val totalBytes: Long = 0,
    val bytesProcessed: Long = 0,
    val currentFile: String = "",
    val currentActivity: String = "",

    // General Info
    val elapsedTime: Long = 0, // in seconds
    val error: String? = null,
    val isFinished: Boolean = false,
    val finalSummary: String = "",
    val snapshotId: String? = null,

    // Detailed summary fields (mostly for backup)
    val filesNew: Int = 0,
    val filesChanged: Int = 0,
    val dataAdded: Long = 0,
    val totalDuration: Double = 0.0
)

/**
 * A generalized composable to display the progress and final summary of
 * a backup or restore operation.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProgressScreenContent(
    progress: OperationProgress,
    operationType: String, // e.g., "Backup", "Restore"
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val operationComplete = stringResource(R.string.progress_operation_complete, operationType)
    val operationFailed = stringResource(R.string.progress_operation_failed, operationType)
    val stageTitle = progress.stageTitle.ifBlank { stringResource(R.string.progress_initializing) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedContent(
            targetState = progress.isFinished,
            label = "ProgressOrFinish",
            transitionSpec = {
                fadeIn(animationSpec = tween(300, 150)) togetherWith
                    fadeOut(animationSpec = tween(150))
            }
        ) { isFinished ->
            if (isFinished) {
                var startAnimation by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    delay(200)
                    startAnimation = true
                }

                val icon = if (progress.error == null) Icons.Default.CheckCircle else Icons.Default.Error
                val iconColor = if (progress.error == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    AnimatedVisibility(
                        visible = startAnimation,
                        enter = scaleIn(animationSpec = spring(dampingRatio = 0.5f, stiffness = 100f))
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(100.dp),
                            tint = iconColor
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = if (progress.error == null) operationComplete else operationFailed,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Text(
                            text = progress.finalSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = onDone,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(stringResource(R.string.action_done))
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stageTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )
                    Spacer(Modifier.height(24.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            val overallPercent = (progress.overallPercentage.coerceIn(0f, 1f) * 100).toInt()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.progress_overall),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (progress.isOverallIndeterminate) {
                                        stringResource(R.string.progress_in_progress)
                                    } else {
                                        "$overallPercent%"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            if (progress.isOverallIndeterminate) {
                                LinearWavyProgressIndicator(
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                LinearWavyProgressIndicator(
                                    progress = { progress.overallPercentage },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(Modifier.height(16.dp))

                            val stagePercent = (progress.stagePercentage.coerceIn(0f, 1f) * 100).toInt()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.progress_stage),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (progress.isStageIndeterminate) {
                                        stringResource(R.string.progress_in_progress)
                                    } else {
                                        "$stagePercent%"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            if (progress.isStageIndeterminate) {
                                LinearWavyProgressIndicator(
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                LinearWavyProgressIndicator(
                                    progress = { progress.stagePercentage },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(Modifier.height(20.dp))

                            val numberFormat = remember { NumberFormat.getIntegerInstance() }
                            val formattedProcessed = numberFormat.format(progress.filesProcessed)
                            val formattedTotal = numberFormat.format(progress.totalFiles)
                            val itemsValue = "$formattedProcessed / $formattedTotal"
                            val hasItems = progress.totalFiles > 0
                            val hasBytes = progress.totalBytes > 0
                            val sizeValue = if (hasBytes) {
                                "${Formatter.formatFileSize(context, progress.bytesProcessed)} / ${
                                    Formatter.formatFileSize(
                                        context,
                                        progress.totalBytes
                                    )
                                }"
                            } else ""

                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                if (!hasItems && !hasBytes) {
                                    ProgressStatCard(
                                        label = stringResource(R.string.progress_elapsed),
                                        value = formatElapsedTime(progress.elapsedTime),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    val isWide = maxWidth >= 440.dp && hasBytes
                                    if (isWide) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(IntrinsicSize.Min),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            ProgressStatCard(
                                                label = stringResource(R.string.progress_elapsed),
                                                value = formatElapsedTime(progress.elapsedTime),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                            )
                                            ProgressStatCard(
                                                label = stringResource(R.string.progress_items),
                                                value = itemsValue,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                            )
                                            ProgressStatCard(
                                                label = stringResource(R.string.progress_size),
                                                value = sizeValue,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                            )
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(IntrinsicSize.Min),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                ProgressStatCard(
                                                    label = stringResource(R.string.progress_elapsed),
                                                    value = formatElapsedTime(progress.elapsedTime),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                )
                                                ProgressStatCard(
                                                    label = stringResource(R.string.progress_items),
                                                    value = itemsValue,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                )
                                            }
                                            if (hasBytes) {
                                                ProgressStatCard(
                                                    label = stringResource(R.string.progress_size),
                                                    value = sizeValue,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            val activeItem = progress.currentFile.ifBlank { progress.currentActivity }
                            if (activeItem.isNotBlank()) {
                                Spacer(Modifier.height(12.dp))
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.progress_processing).trimEnd(' ', ':'),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        AnimatedContent(
                                            targetState = activeItem,
                                            label = "CurrentActivityAnimation",
                                            transitionSpec = {
                                                slideInVertically { height -> height } togetherWith
                                                    slideOutVertically { height -> -height }
                                            }
                                        ) { targetText ->
                                            Text(
                                                text = targetText.ifEmpty { stringResource(R.string.progress_placeholder) },
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ProgressStat(label: String, value: String, modifier: Modifier = Modifier) {
    ProgressStatCard(label = label, value = value, modifier = modifier)
}

private fun formatElapsedTime(seconds: Long): String {
    val hours = TimeUnit.SECONDS.toHours(seconds)
    val minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}

@Preview(name = "Compact Low DPI (320dp)", widthDp = 320)
@Composable
private fun ProgressScreenCompactPreview() {
    RestoidTheme(dynamicColor = false) {
        ProgressScreenContent(
            progress = OperationProgress(
                stageTitle = "[2/4] Backing up items...",
                overallPercentage = 0.48f,
                stagePercentage = 0.75f,
                filesProcessed = 3241,
                totalFiles = 6954,
                bytesProcessed = 2210000000L,
                totalBytes = 3280000000L,
                currentFile = "/storage/emulated/0/DCIM/Camera/IMG_20261109_023411.jpg",
                elapsedTime = 2
            ),
            operationType = "Backup",
            onDone = {}
        )
    }
}

@Preview(name = "Standard Phone (412dp)", widthDp = 412)
@Composable
private fun ProgressScreenStandardPreview() {
    RestoidTheme(dynamicColor = false) {
        ProgressScreenContent(
            progress = OperationProgress(
                stageTitle = "[2/4] Backing up items...",
                overallPercentage = 0.48f,
                stagePercentage = 0.75f,
                filesProcessed = 3241,
                totalFiles = 6954,
                bytesProcessed = 2210000000L,
                totalBytes = 3280000000L,
                currentFile = "/storage/emulated/0/DCIM/Camera/IMG_20261109_023411.jpg",
                elapsedTime = 125
            ),
            operationType = "Backup",
            onDone = {}
        )
    }
}

@Preview(name = "Wide / Tablet (600dp)", widthDp = 600)
@Composable
private fun ProgressScreenWidePreview() {
    RestoidTheme(dynamicColor = false) {
        ProgressScreenContent(
            progress = OperationProgress(
                stageTitle = "[2/4] Backing up items...",
                overallPercentage = 0.48f,
                stagePercentage = 0.75f,
                filesProcessed = 3241,
                totalFiles = 6954,
                bytesProcessed = 2210000000L,
                totalBytes = 3280000000L,
                currentFile = "/storage/emulated/0/DCIM/Camera/IMG_20261109_023411.jpg",
                elapsedTime = 3665
            ),
            operationType = "Backup",
            onDone = {}
        )
    }
}

@Preview(name = "No Bytes / Restore", widthDp = 360)
@Composable
private fun ProgressScreenNoBytesPreview() {
    RestoidTheme(dynamicColor = false) {
        ProgressScreenContent(
            progress = OperationProgress(
                stageTitle = "Restoring Files",
                overallPercentage = 0.30f,
                stagePercentage = 0.60f,
                filesProcessed = 150,
                totalFiles = 500,
                totalBytes = 0,
                elapsedTime = 42
            ),
            operationType = "Restore",
            onDone = {}
        )
    }
}

@Preview(name = "Indeterminate Maintenance / Prune", widthDp = 360)
@Composable
private fun ProgressScreenIndeterminatePreview() {
    RestoidTheme(dynamicColor = false) {
        ProgressScreenContent(
            progress = OperationProgress(
                stageTitle = "[3/4] Running 'Prune'…",
                overallPercentage = 0.50f,
                isStageIndeterminate = true,
                currentActivity = "repacking packs...",
                elapsedTime = 45
            ),
            operationType = "Maintenance",
            onDone = {}
        )
    }
}
