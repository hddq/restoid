package io.github.hddq.restoid.util

enum class PrunePhase {
    LOADING_INDEXES,
    LOADING_SNAPSHOTS,
    FINDING_USED_DATA,
    SEARCHING_USED_PACKS,
    COLLECTING_PACKS,
    DELETING_UNREFERENCED,
    REPACKING_PACKS,
    REBUILDING_INDEX,
    REMOVING_OLD_PACKS,
    DONE
}

data class PruneProgressUpdate(
    val phase: PrunePhase?,
    val stagePercentage: Float,
    val isStageIndeterminate: Boolean,
    val itemsProcessed: Int,
    val totalItems: Int,
    val overallFraction: Float,
    val hasDeterminateOverall: Boolean,
    val rawActivity: String? = null
)

data class PruneSummaryStats(
    val repackBlobs: Int,
    val repackSize: String,
    val removedBlobs: Int,
    val removedSize: String,
    val remainingBlobs: Int,
    val remainingSize: String
) {
    val hasFreedData: Boolean
        get() = removedBlobs > 0 || PruneOutputParser.isNonZeroSize(removedSize)

    val hasRepackedData: Boolean
        get() = repackBlobs > 0 || PruneOutputParser.isNonZeroSize(repackSize)
}

class PruneProgressTracker {
    private var currentPhase: PrunePhase? = null
    private var maxOverallFraction: Float = 0f
    private var hasDeterminateOverall: Boolean = false
    private var lastStagePercentage: Float = 0f
    private var lastIsStageIndeterminate: Boolean = true
    private var lastItemsProcessed: Int = 0
    private var lastTotalItems: Int = 0

    fun processLine(line: String): PruneProgressUpdate? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        val lower = trimmed.lowercase()
        if (STATS_LINE_PREFIXES.any { lower.startsWith(it) }) {
            return null
        }

        DETERMINATE_COUNTER_REGEX.matchEntire(trimmed)?.let { match ->
            val percent = match.groupValues[1].toFloatOrNull()
            val processed = match.groupValues[2].toIntOrNull() ?: 0
            val total = match.groupValues[3].toIntOrNull() ?: 0
            val label = match.groupValues[4].trim()

            if (total <= 0) {
                return emitIndeterminateCounter(label)
            }

            val stagePct = ((percent ?: (processed.toFloat() / total.toFloat() * 100f)) / 100f)
                .coerceIn(0f, 1f)
            val inferred = resolveCounterPhaseAndFraction(label, stagePct)
            if (inferred.phase != null) {
                currentPhase = inferred.phase
            }
            maxOverallFraction = maxOf(maxOverallFraction, inferred.overallFraction.coerceIn(0f, 1f))
            hasDeterminateOverall = true
            lastStagePercentage = stagePct
            lastIsStageIndeterminate = false
            lastItemsProcessed = processed
            lastTotalItems = total

            return PruneProgressUpdate(
                phase = currentPhase,
                stagePercentage = stagePct,
                isStageIndeterminate = false,
                itemsProcessed = processed,
                totalItems = total,
                overallFraction = maxOverallFraction,
                hasDeterminateOverall = true
            )
        }

        INDETERMINATE_COUNTER_REGEX.matchEntire(trimmed)?.let { match ->
            val label = match.groupValues[2].trim()
            return emitIndeterminateCounter(label)
        }

        matchPhaseHeader(lower)?.let { phase ->
            currentPhase = phase
            if (phase == PrunePhase.DONE) {
                maxOverallFraction = 1f
                hasDeterminateOverall = true
                lastStagePercentage = 1f
                lastIsStageIndeterminate = false
                lastItemsProcessed = 0
                lastTotalItems = 0
                return PruneProgressUpdate(
                    phase = PrunePhase.DONE,
                    stagePercentage = 1f,
                    isStageIndeterminate = false,
                    itemsProcessed = 0,
                    totalItems = 0,
                    overallFraction = 1f,
                    hasDeterminateOverall = true
                )
            }

            maxOverallFraction = maxOf(maxOverallFraction, phaseStartFraction(phase))
            lastStagePercentage = 0f
            lastIsStageIndeterminate = true
            lastItemsProcessed = 0
            lastTotalItems = 0
            return PruneProgressUpdate(
                phase = phase,
                stagePercentage = 0f,
                isStageIndeterminate = true,
                itemsProcessed = 0,
                totalItems = 0,
                overallFraction = maxOverallFraction,
                hasDeterminateOverall = hasDeterminateOverall
            )
        }

        return PruneProgressUpdate(
            phase = currentPhase,
            stagePercentage = lastStagePercentage,
            isStageIndeterminate = lastIsStageIndeterminate,
            itemsProcessed = lastItemsProcessed,
            totalItems = lastTotalItems,
            overallFraction = maxOverallFraction,
            hasDeterminateOverall = hasDeterminateOverall,
            rawActivity = trimmed
        )
    }

    private fun emitIndeterminateCounter(label: String): PruneProgressUpdate {
        val inferred = resolveCounterPhaseAndFraction(label, 0f)
        if (inferred.phase != null) {
            currentPhase = inferred.phase
        }
        val phaseStart = currentPhase?.let { phaseStartFraction(it) } ?: 0f
        maxOverallFraction = maxOf(maxOverallFraction, phaseStart)
        lastStagePercentage = 0f
        lastIsStageIndeterminate = true
        lastItemsProcessed = 0
        lastTotalItems = 0
        return PruneProgressUpdate(
            phase = currentPhase,
            stagePercentage = 0f,
            isStageIndeterminate = true,
            itemsProcessed = 0,
            totalItems = 0,
            overallFraction = maxOverallFraction,
            hasDeterminateOverall = hasDeterminateOverall
        )
    }

    private data class CounterMapping(
        val phase: PrunePhase?,
        val overallFraction: Float
    )

    private fun resolveCounterPhaseAndFraction(label: String, stagePct: Float): CounterMapping {
        val normalized = label.trim().lowercase()
        return when {
            normalized.startsWith("snapshot") -> CounterMapping(
                phase = PrunePhase.FINDING_USED_DATA,
                overallFraction = 0.05f + (0.20f * stagePct)
            )
            normalized.startsWith("packs processed") || normalized.startsWith("pack processed") -> CounterMapping(
                phase = PrunePhase.COLLECTING_PACKS,
                overallFraction = 0.25f + (0.20f * stagePct)
            )
            normalized.startsWith("packs repacked") || normalized.startsWith("pack repacked") -> CounterMapping(
                phase = PrunePhase.REPACKING_PACKS,
                overallFraction = 0.50f + (0.35f * stagePct)
            )
            normalized.startsWith("indexes processed") || normalized.startsWith("index processed") -> CounterMapping(
                phase = PrunePhase.REBUILDING_INDEX,
                overallFraction = 0.85f + (0.04f * stagePct)
            )
            normalized.startsWith("old indexes deleted") || normalized.startsWith("old index deleted") -> CounterMapping(
                phase = PrunePhase.REBUILDING_INDEX,
                overallFraction = 0.89f + (0.03f * stagePct)
            )
            normalized.startsWith("files deleted") || normalized.startsWith("file deleted") -> {
                val isRemovingOldPacks = when (currentPhase) {
                    PrunePhase.REPACKING_PACKS,
                    PrunePhase.REBUILDING_INDEX,
                    PrunePhase.REMOVING_OLD_PACKS,
                    PrunePhase.DONE -> true
                    else -> false
                }
                if (isRemovingOldPacks) {
                    CounterMapping(
                        phase = PrunePhase.REMOVING_OLD_PACKS,
                        overallFraction = 0.92f + (0.07f * stagePct)
                    )
                } else {
                    CounterMapping(
                        phase = PrunePhase.DELETING_UNREFERENCED,
                        overallFraction = 0.45f + (0.05f * stagePct)
                    )
                }
            }
            else -> CounterMapping(
                phase = currentPhase,
                overallFraction = maxOverallFraction
            )
        }
    }

    private fun matchPhaseHeader(lower: String): PrunePhase? {
        return when {
            lower.startsWith("loading indexes") -> PrunePhase.LOADING_INDEXES
            lower.startsWith("loading all snapshots") -> PrunePhase.LOADING_SNAPSHOTS
            lower.startsWith("finding data that is still in use") -> PrunePhase.FINDING_USED_DATA
            lower.startsWith("searching used packs") -> PrunePhase.SEARCHING_USED_PACKS
            lower.startsWith("collecting packs for deletion and repacking") -> PrunePhase.COLLECTING_PACKS
            lower.startsWith("deleting unreferenced packs") -> PrunePhase.DELETING_UNREFERENCED
            lower.startsWith("repacking packs") -> PrunePhase.REPACKING_PACKS
            lower.startsWith("rebuilding index") -> PrunePhase.REBUILDING_INDEX
            REMOVING_OLD_PACKS_HEADER_REGEX.matches(lower) -> PrunePhase.REMOVING_OLD_PACKS
            DONE_HEADER_REGEX.matches(lower) -> PrunePhase.DONE
            else -> null
        }
    }

    private fun phaseStartFraction(phase: PrunePhase): Float {
        return when (phase) {
            PrunePhase.LOADING_INDEXES -> 0.00f
            PrunePhase.LOADING_SNAPSHOTS -> 0.02f
            PrunePhase.FINDING_USED_DATA -> 0.05f
            PrunePhase.SEARCHING_USED_PACKS -> 0.25f
            PrunePhase.COLLECTING_PACKS -> 0.25f
            PrunePhase.DELETING_UNREFERENCED -> 0.45f
            PrunePhase.REPACKING_PACKS -> 0.50f
            PrunePhase.REBUILDING_INDEX -> 0.85f
            PrunePhase.REMOVING_OLD_PACKS -> 0.92f
            PrunePhase.DONE -> 1.00f
        }
    }

    private companion object {
        val DETERMINATE_COUNTER_REGEX =
            Regex("""^\[(?:\d+:)+\d{2}]\s+(\d+(?:\.\d+)?)%\s+(\d+)\s*/\s*(\d+)\s+(.+)$""")
        val INDETERMINATE_COUNTER_REGEX =
            Regex("""^\[(?:\d+:)+\d{2}]\s+(\d+)\s+(.+)$""")
        val REMOVING_OLD_PACKS_HEADER_REGEX =
            Regex("""^removing\s+\d+\s+old\s+packs?\.{0,3}$""")
        val DONE_HEADER_REGEX =
            Regex("""^done[.!]?$""")

        val STATS_LINE_PREFIXES = listOf(
            "to repack:",
            "this removes:",
            "to delete:",
            "total prune:",
            "not yet compressed:",
            "remaining:",
            "unused size after prune:",
            "used:",
            "duplicates:",
            "unused:",
            "unreferenced:",
            "total:",
            "unused size:",
            "totally used packs:",
            "partly used packs:",
            "unused packs:",
            "to keep:"
        )
    }
}

object PruneOutputParser {
    private val REPACK_STATS_REGEX =
        Regex("""^to repack:\s+(\d+)\s+blobs?\s*/\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val TOTAL_PRUNE_STATS_REGEX =
        Regex("""^total prune:\s+(\d+)\s+blobs?\s*/\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val REMAINING_STATS_REGEX =
        Regex("""^remaining:\s+(\d+)\s+blobs?\s*/\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val LEADING_NUMBER_REGEX =
        Regex("""^([0-9]+(?:\.[0-9]+)?)""")

    fun parseSummaryStats(output: String): PruneSummaryStats? {
        var repackBlobs: Int? = null
        var repackSize: String? = null
        var removedBlobs: Int? = null
        var removedSize: String? = null
        var remainingBlobs: Int? = null
        var remainingSize: String? = null

        for (rawLine in output.split('\r', '\n')) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            REPACK_STATS_REGEX.matchEntire(line)?.let { match ->
                repackBlobs = match.groupValues[1].toLongOrNull()
                    ?.coerceAtMost(Int.MAX_VALUE.toLong())
                    ?.toInt()
                repackSize = match.groupValues[2].trim()
                continue
            }

            TOTAL_PRUNE_STATS_REGEX.matchEntire(line)?.let { match ->
                removedBlobs = match.groupValues[1].toLongOrNull()
                    ?.coerceAtMost(Int.MAX_VALUE.toLong())
                    ?.toInt()
                removedSize = match.groupValues[2].trim()
                continue
            }

            REMAINING_STATS_REGEX.matchEntire(line)?.let { match ->
                remainingBlobs = match.groupValues[1].toLongOrNull()
                    ?.coerceAtMost(Int.MAX_VALUE.toLong())
                    ?.toInt()
                remainingSize = match.groupValues[2].trim()
            }
        }

        val finalRemainingBlobs = remainingBlobs ?: return null
        val finalRemainingSize = remainingSize ?: return null

        return PruneSummaryStats(
            repackBlobs = repackBlobs ?: 0,
            repackSize = repackSize ?: "0 B",
            removedBlobs = removedBlobs ?: 0,
            removedSize = removedSize ?: "0 B",
            remainingBlobs = finalRemainingBlobs,
            remainingSize = finalRemainingSize
        )
    }

    internal fun isNonZeroSize(size: String): Boolean {
        val trimmed = size.trim()
        if (trimmed.isEmpty()) return false
        val number = LEADING_NUMBER_REGEX.find(trimmed)?.groupValues?.get(1)?.toDoubleOrNull()
        return if (number != null) {
            number > 0.0
        } else {
            !trimmed.equals("0 B", ignoreCase = true)
        }
    }
}
