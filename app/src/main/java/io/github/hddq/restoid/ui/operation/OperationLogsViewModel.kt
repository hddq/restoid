package io.github.hddq.restoid.ui.operation

import androidx.lifecycle.ViewModel
import io.github.hddq.restoid.data.OperationLogEntry
import io.github.hddq.restoid.data.OperationLogRepository
import kotlinx.coroutines.flow.StateFlow

class OperationLogsViewModel(
    private val operationLogRepository: OperationLogRepository
) : ViewModel() {

    val logs: StateFlow<List<OperationLogEntry>> = operationLogRepository.logs

    fun clearLogs() {
        operationLogRepository.clearLogs()
    }
}
