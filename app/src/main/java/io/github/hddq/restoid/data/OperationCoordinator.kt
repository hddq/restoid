package io.github.hddq.restoid.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OperationType {
    BACKUP,
    RESTORE,
    MAINTENANCE
}

data class ActiveOperation(
    val type: OperationType,
    val backendType: RepositoryBackendType?,
    val startedAtMillis: Long = System.currentTimeMillis()
)

class OperationCoordinator {
    private val monitor = Any()
    private val _activeOperation = MutableStateFlow<ActiveOperation?>(null)
    val activeOperation = _activeOperation.asStateFlow()

    fun tryStart(type: OperationType, backendType: RepositoryBackendType?): Boolean {
        synchronized(monitor) {
            if (_activeOperation.value != null) return false
            _activeOperation.value = ActiveOperation(type = type, backendType = backendType)
            return true
        }
    }

    fun finish(type: OperationType) {
        synchronized(monitor) {
            val current = _activeOperation.value ?: return
            if (current.type == type) {
                _activeOperation.value = null
            }
        }
    }
}
