package io.github.hddq.restoid.ui.operation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hddq.restoid.data.OperationRuntimeState
import io.github.hddq.restoid.work.OperationWorkRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface OperationProgressUiEvent {
    data object NavigateUp : OperationProgressUiEvent
}

class OperationProgressViewModel(
    private val operationWorkRepository: OperationWorkRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OperationRuntimeState())
    val state = _state.asStateFlow()
    private val _uiEvents = MutableSharedFlow<OperationProgressUiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<OperationProgressUiEvent> = _uiEvents.asSharedFlow()

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

    fun onStopConfirmed() {
        operationWorkRepository.cancelCurrentOperation()
        _uiEvents.tryEmit(OperationProgressUiEvent.NavigateUp)
    }
}
