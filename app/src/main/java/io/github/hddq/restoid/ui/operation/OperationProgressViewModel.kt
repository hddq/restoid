package io.github.hddq.restoid.ui.operation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hddq.restoid.data.OperationType
import io.github.hddq.restoid.data.OperationRuntimeState
import io.github.hddq.restoid.work.OperationWorkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OperationProgressViewModel(
    private val operationWorkRepository: OperationWorkRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OperationRuntimeState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            operationWorkRepository.operationState.collect { runtimeState ->
                _state.value = runtimeState
            }
        }
    }

    fun onDone() {
        operationWorkRepository.clearFinished(_state.value.operationType)
        _state.value = OperationRuntimeState()
    }
}
