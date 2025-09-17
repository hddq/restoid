package app.restoid.ui.shared

import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit

/**
 * Shared data class for representing the progress of a long-running operation
 * like backup or restore.
 */
data class OperationProgress(
    val percentage: Float = 0f,
    val totalFiles: Int = 0,
    val filesProcessed: Int = 0,
    val totalBytes: Long = 0,
    val bytesProcessed: Long = 0,
    val currentFile: String = "",
    val currentAction: String = "Initializing...",
    val elapsedTime: Long = 0, // in seconds
    val error: String? = null,
    val isFinished: Boolean = false,
    val finalSummary: String = "",
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
        if (progress.isFinished) {
            // Finished State
            val icon = if (progress.error == null) Icons.Default.CheckCircle else Icons.Default.Error
            val iconColor = if (progress.error == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = iconColor
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (progress.error == null) "$operationType Complete" else "$operationType Failed",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = progress.finalSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onDone) {
                Text("Done")
            }

        } else {
            // In-Progress State
            Text(text = progress.currentAction, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    // Progress Bar
                    LinearProgressIndicator(
                        progress = { progress.percentage },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))

                    // Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        ProgressStat(label = "Elapsed", value = formatElapsedTime(progress.elapsedTime))
                        ProgressStat(
                            label = "Files",
                            value = "${progress.filesProcessed}/${progress.totalFiles}"
                        )
                        ProgressStat(
                            label = "Size",
                            value = "${Formatter.formatFileSize(context, progress.bytesProcessed)} / ${Formatter.formatFileSize(context, progress.totalBytes)}"
                        )
                    }
                    Spacer(Modifier.height(16.dp))

                    // Current File Text (only show for backup as restore is app-based)
                    if (operationType == "Backup") {
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

@Composable
fun ProgressStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
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
