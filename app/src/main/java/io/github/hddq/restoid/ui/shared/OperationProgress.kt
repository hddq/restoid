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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

/**
 * Shared data class for representing the progress of a long-running operation
 * like backup or restore. It now supports multi-stage operations.
 */
data class OperationProgress(
    val stageTitle: String = "Initializing...", // e.g., "Stage 1/2: Restoring Files"
    val stagePercentage: Float = 0f, // progress of the current stage (0.0 to 1.0)
    val overallPercentage: Float = 0f, // overall progress (0.0 to 1.0)

    // Details for the current stage
    val totalFiles: Int = 0,
    val filesProcessed: Int = 0,
    val totalBytes: Long = 0,
    val bytesProcessed: Long = 0,
    val currentFile: String = "",

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
@Composable
fun ProgressScreenContent(
    progress: OperationProgress,
    operationType: String, // e.g., "Backup", "Restore"
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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
                // Finished State
                var startAnimation by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    delay(200) // A small delay to let the screen transition in
                    startAnimation = true
                }

                val icon = if (progress.error == null) Icons.Default.CheckCircle else Icons.Default.Error
                val iconColor = if (progress.error == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
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
                        text = if (progress.error == null) "$operationType Complete" else "$operationType Failed",
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Card(
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text("Done")
                    }
                }

            } else {
                // In-Progress State
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = progress.stageTitle, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(24.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            // Overall Progress
                            Text("Overall Progress", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progress.overallPercentage },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                strokeCap = StrokeCap.Round
                            )
                            Spacer(Modifier.height(20.dp))

                            // Stage Progress
                            Text("Stage Progress", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progress.stagePercentage },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                strokeCap = StrokeCap.Round
                            )
                            Spacer(Modifier.height(24.dp))


                            // Stats Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                ProgressStat(label = "Elapsed", value = formatElapsedTime(progress.elapsedTime))
                                ProgressStat(
                                    label = "Items",
                                    value = "${progress.filesProcessed}/${progress.totalFiles}"
                                )
                                // Only show size if it's relevant (not 0)
                                if (progress.totalBytes > 0) {
                                    ProgressStat(
                                        label = "Size",
                                        value = "${Formatter.formatFileSize(context, progress.bytesProcessed)} / ${
                                            Formatter.formatFileSize(
                                                context,
                                                progress.totalBytes
                                            )
                                        }"
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))

                            // Current File Text (only show for backup as restore is app-based)
                            if (operationType == "Backup" && progress.currentFile.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Processing: ",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    AnimatedContent(
                                        targetState = progress.currentFile,
                                        label = "CurrentFileAnimation",
                                        transitionSpec = {
                                            slideInVertically { height -> height } togetherWith
                                                    slideOutVertically { height -> -height }
                                        }
                                    ) { targetFile ->
                                        Text(
                                            text = targetFile.ifEmpty { "..." },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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

@Composable
fun ProgressStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
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
