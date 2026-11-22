package io.github.hddq.restoid.data

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RootRepository(
    private val loadPersistedHasRoot: () -> Boolean = { false },
    private val savePersistedHasRoot: (Boolean) -> Unit = {},
    private val isShellBusy: () -> Boolean = { false },
    private val cachedShellIsRoot: () -> Boolean? = { Shell.getCachedShell()?.isRoot },
    private val closeCachedShell: () -> Unit = { runCatching { Shell.getCachedShell()?.close() } },
    private val probeRootShell: () -> Boolean = { runCatching { Shell.getShell().isRoot }.getOrDefault(false) }
) {
    private val checkMutex = Mutex()
    private val _hasRoot = MutableStateFlow(loadPersistedHasRoot())
    val hasRoot = _hasRoot.asStateFlow()

    suspend fun checkRootAccess() {
        checkMutex.withLock {
            if (isShellBusy()) {
                val cached = cachedShellIsRoot()
                if (cached != null && cached != _hasRoot.value) {
                    _hasRoot.value = cached
                    savePersistedHasRoot(cached)
                }
                return
            }

            val granted = withContext(Dispatchers.IO) {
                runCatching { closeCachedShell() }
                runCatching { probeRootShell() }.getOrDefault(false)
            }
            _hasRoot.value = granted
            savePersistedHasRoot(granted)
        }
    }
}
