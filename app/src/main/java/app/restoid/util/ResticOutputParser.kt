package app.restoid.util

import app.restoid.ui.shared.OperationProgress
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ResticOutputParser {

    fun parse(jsonLine: String): OperationProgress? {
        return try {
            val json = JSONObject(jsonLine)
            when (json.optString("message_type")) {
                "status" -> parseStatus(json)
                "summary" -> parseSummary(json)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseStatus(json: JSONObject): OperationProgress {
        // Restore status has "bytes_restored", backup has "bytes_done"
        val isRestore = json.has("bytes_restored")
        val percentDone = json.optDouble("percent_done", 0.0).toFloat()

        return if (isRestore) {
            OperationProgress(
                stagePercentage = percentDone,
                totalFiles = json.optInt("total_files", 0),
                filesProcessed = json.optInt("files_restored", 0),
                totalBytes = json.optLong("total_bytes", 0),
                bytesProcessed = json.optLong("bytes_restored", 0),
                currentFile = "", // Not available in restore status
                stageTitle = "Restoring files..."
            )
        } else {
            // Assume backup
            OperationProgress(
                stagePercentage = percentDone,
                overallPercentage = percentDone, // For backup, stage and overall are the same
                totalFiles = json.optInt("total_files", 0),
                filesProcessed = json.optInt("files_done", 0),
                totalBytes = json.optLong("total_bytes", 0),
                bytesProcessed = json.optLong("bytes_done", 0),
                currentFile = json.optJSONArray("current_files")?.optString(0) ?: "",
                stageTitle = "Backing up..."
            )
        }
    }

    private fun parseSummary(json: JSONObject): OperationProgress {
        val filesNew = json.optInt("files_new", 0)
        val filesChanged = json.optInt("files_changed", 0)
        val dataAdded = json.optLong("data_added", 0)
        val totalDuration = json.optDouble("total_duration", 0.0)

        return OperationProgress(
            stagePercentage = 1.0f,
            overallPercentage = 1.0f,
            totalFiles = json.optInt("total_files_processed", 0),
            filesProcessed = json.optInt("total_files_processed", 0),
            totalBytes = json.optLong("total_bytes_processed", 0),
            bytesProcessed = json.optLong("total_bytes_processed", 0),
            stageTitle = "Finishing...",
            isFinished = true,
            finalSummary = formatSummary(json), // Use the helper to create the string
            // Add the new fields
            filesNew = filesNew,
            filesChanged = filesChanged,
            dataAdded = dataAdded,
            totalDuration = totalDuration
        )
    }

    private fun formatSummary(json: JSONObject): String {
        val filesNew = json.optInt("files_new", 0)
        val filesChanged = json.optInt("files_changed", 0)
        val filesUnmodified = json.optInt("files_unmodified", 0)
        val dataAdded = json.optLong("data_added", 0)
        val totalDuration = json.optDouble("total_duration", 0.0)

        val seconds = totalDuration.toLong()
        val formattedDuration = String.format(
            "%02d:%02d:%02d",
            TimeUnit.SECONDS.toHours(seconds),
            TimeUnit.SECONDS.toMinutes(seconds) % 60,
            seconds % 60
        )

        return "Added ${android.text.format.Formatter.formatShortFileSize(null, dataAdded)} " +
                "($filesNew new, $filesChanged changed files) " +
                "in $formattedDuration."
    }

    // This is likely not needed anymore with JSON parsing, but keep for now.
    fun findSummaryLine(log: String): String? {
        val regex = """files:.*?new,.*?changed,.*?unmodified.*?processed.*?added.*?duration:.*?,.*?total""".toRegex()
        return regex.find(log)?.value
    }
}

