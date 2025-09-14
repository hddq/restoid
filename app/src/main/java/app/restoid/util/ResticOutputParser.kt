package app.restoid.util

import app.restoid.ui.backup.BackupProgress
import org.json.JSONObject

object ResticOutputParser {

    fun parse(jsonLine: String): BackupProgress? {
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

    private fun parseStatus(json: JSONObject): BackupProgress {
        return BackupProgress(
            percentage = json.optDouble("percent_done", 0.0).toFloat(),
            totalFiles = json.optInt("total_files", 0),
            filesProcessed = json.optInt("files_done", 0),
            totalBytes = json.optLong("total_bytes", 0),
            bytesProcessed = json.optLong("bytes_done", 0),
            currentFile = json.optJSONArray("current_files")?.optString(0) ?: "",
            currentAction = "Backing up..."
        )
    }

    private fun parseSummary(json: JSONObject): BackupProgress {
        val totalBytes = json.optLong("total_bytes_processed", 0)
        return BackupProgress(
            percentage = 1.0f,
            totalFiles = json.optInt("total_files_processed", 0),
            filesProcessed = json.optInt("total_files_processed", 0),
            totalBytes = totalBytes,
            bytesProcessed = totalBytes,
            currentAction = "Finishing...",
            isFinished = true,
            finalSummary = formatSummary(json)
        )
    }

    private fun formatSummary(json: JSONObject): String {
        val filesNew = json.optInt("files_new", 0)
        val filesChanged = json.optInt("files_changed", 0)
        val filesUnmodified = json.optInt("files_unmodified", 0)
        val dataAdded = json.optLong("data_added", 0)
        val totalDuration = json.optDouble("total_duration", 0.0)

        return "Processed $filesNew new, $filesChanged changed, $filesUnmodified unmodified files. Added ${android.text.format.Formatter.formatShortFileSize(null, dataAdded)}. Took ${String.format("%.2f", totalDuration)}s."
    }

    fun findSummaryLine(log: String): String? {
        val regex = """files:.*?new,.*?changed,.*?unmodified.*?processed.*?added.*?duration:.*?,.*?total""".toRegex()
        return regex.find(log)?.value
    }
}
