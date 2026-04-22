package io.github.hddq.restoid.ui.operation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.hddq.restoid.work.OperationWorkRepository

class OperationProgressViewModelFactory(
    private val operationWorkRepository: OperationWorkRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OperationProgressViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OperationProgressViewModel(operationWorkRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
