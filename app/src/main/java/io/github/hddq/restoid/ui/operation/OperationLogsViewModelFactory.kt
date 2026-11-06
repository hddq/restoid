package io.github.hddq.restoid.ui.operation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.hddq.restoid.data.OperationLogRepository

class OperationLogsViewModelFactory(
    private val operationLogRepository: OperationLogRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OperationLogsViewModel::class.java)) {
            return OperationLogsViewModel(operationLogRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
