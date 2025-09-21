package io.github.hddq.restoid.data

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed class RootState {
    object Checking : RootState()
    object Granted : RootState()
    object Denied : RootState()
}

class RootRepository {
    private val _rootState = MutableStateFlow<RootState>(RootState.Checking)
    val rootState = _rootState.asStateFlow()

    suspend fun checkRootAccess() {
        _rootState.value = RootState.Checking
        val hasRoot = withContext(Dispatchers.IO) {
            Shell.getShell().isRoot
        }
        _rootState.value = if (hasRoot) RootState.Granted else RootState.Denied
    }
}