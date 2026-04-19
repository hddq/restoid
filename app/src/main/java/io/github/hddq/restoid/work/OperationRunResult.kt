package io.github.hddq.restoid.work

import io.github.hddq.restoid.ui.shared.OperationProgress

data class OperationRunResult(
    val success: Boolean,
    val progress: OperationProgress
)
