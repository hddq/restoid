package io.github.hddq.restoid.util

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
    fun parse(taskName: String, output: String): String {
        return when (taskName.lowercase()) {
            "prune" -> parsePrune(output)
            "forget" -> parseForget(output)
            "check" -> parseCheck(output)
            "unlock" -> parseUnlock(output)
            else -> output // Fallback for unknown tasks, return original output
        }
    }

    /**
     * Extracts a summary from the 'prune' command output.
     * It typically just returns the last few lines which contain the result.
     */
    private fun parsePrune(output: String): String {
        val summary = output.lines().filter { it.isNotBlank() }.takeLast(3).joinToString("\n")
        return summary.ifBlank { "Prune operation completed." }
    }

    /**
     * Extracts a summary from the 'forget' command output.
     * It looks for a summary line or counts the number of removed snapshots.
     */
    private fun parseForget(output: String): String {
        val lines = output.lines().map { it.trim() }

        // Best case: Look for the "remove X snapshots:" summary line from the logs.
        val removeSummaryRegex = """^remove\s+(\d+)\s+snapshots?:?$""".toRegex(RegexOption.IGNORE_CASE)
        for (line in lines) {
            val match = removeSummaryRegex.find(line)
            if (match != null) {
                val count = match.groupValues[1]
                return "Removed $count snapshot(s)."
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
            return "Removed $removedCount snapshot(s)."
        }

        // Fallback 3: Look for an explicit "no snapshots were removed" message.
        val noRemovalLine = lines.firstOrNull { it.contains("no snapshots were removed", ignoreCase = true) }
        if (noRemovalLine != null) {
            return "No snapshots matched the policy to be removed."
        }

        // Final Fallback: If none of the above match, it's highly likely nothing was changed.
        return "No snapshots matched the policy to be removed."
    }

    /**
     * Extracts a summary from the 'check' command output.
     * It looks for the "no errors found" message or returns the last line.
     */
    private fun parseCheck(output: String): String {
        val lines = output.lines().filter { it.isNotBlank() }
        val noErrorsLine = lines.lastOrNull { it.contains("no errors found", ignoreCase = true) }
        return noErrorsLine ?: lines.lastOrNull() ?: "Check operation completed."
    }

    /**
     * Extracts a summary from the 'unlock' command output.
     */
    private fun parseUnlock(output: String): String {
        return output.lines().find { it.contains("successfully removed", ignoreCase = true) }
            ?: "Unlock operation finished."
    }
}

