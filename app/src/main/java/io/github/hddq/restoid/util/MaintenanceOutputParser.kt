package io.github.hddq.restoid.util

import android.content.Context
import io.github.hddq.restoid.R

/**
 * A utility object to parse the human-readable output of restic maintenance commands
 * and extract a concise summary.
 */
object MaintenanceOutputParser {

    /**
     * Parses the output of a given maintenance task.
     * @param taskName The name of the task (e.g., "Prune", "Forget").
     * @param output The full string output from the restic command.
     * @return A concise summary string.
     */
    fun parse(taskName: String, output: String, context: Context): String {
        return when (taskName.lowercase()) {
            context.getString(R.string.maintenance_task_prune).lowercase() -> parsePrune(output, context)
            context.getString(R.string.maintenance_task_forget).lowercase() -> parseForget(output, context)
            context.getString(R.string.maintenance_task_check).lowercase() -> parseCheck(output, context)
            context.getString(R.string.maintenance_task_unlock).lowercase() -> parseUnlock(output, context)
            else -> output // Fallback for unknown tasks, return original output
        }
    }

    /**
     * Extracts a summary from the 'prune' command output.
     * It typically just returns the last few lines which contain the result.
     */
    private fun parsePrune(output: String, context: Context): String {
        val summary = output.lines().filter { it.isNotBlank() }.takeLast(3).joinToString("\n")
        return summary.ifBlank { context.getString(R.string.maintenance_summary_prune_completed) }
    }

    /**
     * Extracts a summary from the 'forget' command output.
     * It looks for a summary line or counts the number of removed snapshots.
     */
    private fun parseForget(output: String, context: Context): String {
        val lines = output.lines().map { it.trim() }

        // Best case: Look for the "remove X snapshots:" summary line from the logs.
        val removeSummaryRegex = """^remove\s+(\d+)\s+snapshots?:?$""".toRegex(RegexOption.IGNORE_CASE)
        for (line in lines) {
            val match = removeSummaryRegex.find(line)
            if (match != null) {
                val count = match.groupValues[1].toIntOrNull() ?: 0
                return context.resources.getQuantityString(
                    R.plurals.maintenance_summary_removed_snapshots,
                    count,
                    count
                )
            }
        }

        // Fallback 1: Look for an explicit summary line like "2 snapshots have been removed."
        val summaryLine = lines.lastOrNull {
            (it.contains("snapshots have been removed", ignoreCase = true) ||
                    it.startsWith("removed", ignoreCase = true)) &&
                    it.contains("snapshots", ignoreCase = true)
        }
        if (summaryLine != null && summaryLine.isNotBlank()) {
            return summaryLine
        }

        // Fallback 2: If no summary line, count the individual 'remove snapshot' lines.
        val removedCount = lines.count { it.startsWith("remove snapshot", ignoreCase = true) }
        if (removedCount > 0) {
            return context.resources.getQuantityString(
                R.plurals.maintenance_summary_removed_snapshots,
                removedCount,
                removedCount
            )
        }

        // Fallback 3: Look for an explicit "no snapshots were removed" message.
        val noRemovalLine = lines.firstOrNull { it.contains("no snapshots were removed", ignoreCase = true) }
        if (noRemovalLine != null) {
            return context.getString(R.string.maintenance_summary_no_snapshots_removed)
        }

        // Final Fallback: If none of the above match, it's highly likely nothing was changed.
        return context.getString(R.string.maintenance_summary_no_snapshots_removed)
    }

    /**
     * Extracts a summary from the 'check' command output.
     * It looks for the "no errors found" message or returns the last line.
     */
    private fun parseCheck(output: String, context: Context): String {
        val lines = output.lines().filter { it.isNotBlank() }
        val noErrorsLine = lines.lastOrNull { it.contains("no errors found", ignoreCase = true) }
        return noErrorsLine ?: lines.lastOrNull() ?: context.getString(R.string.maintenance_summary_check_completed)
    }

    /**
     * Extracts a summary from the 'unlock' command output.
     */
    private fun parseUnlock(output: String, context: Context): String {
        return output.lines().find { it.contains("successfully removed", ignoreCase = true) }
            ?: context.getString(R.string.maintenance_summary_unlock_finished)
    }
}
