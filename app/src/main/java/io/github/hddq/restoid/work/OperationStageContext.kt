package io.github.hddq.restoid.work

data class OperationStageContext(
    val completedStagesBefore: Int,
    val totalStages: Int
) {
    fun absoluteStage(localStage: Int): Int = completedStagesBefore + localStage
}
