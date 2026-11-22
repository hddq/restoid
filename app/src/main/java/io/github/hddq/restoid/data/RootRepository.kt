package io.github.hddq.restoid.data

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RootRepository(
    private val isShellBusy: () -> Boolean = { false },
    private val cachedShellIsRoot: () -> Boolean? = { Shell.getCachedShell()?.isRoot },
    private val closeCachedShell: () -> Unit = { runCatching { Shell.getCachedShell()?.close() } },
    private val probeRootShell: () -> Boolean = { Shell.getShell().isRoot }
) {
    private val checkMutex = Mutex()
    private val _hasRoot = MutableStateFlow(false)
    val hasRoot = _hasRoot.asStateFlow()

    suspend fun checkRootAccess(recheckIfDenied: Boolean = false) {
        checkMutex.withLock {
            when (cachedShellIsRoot()) {
                true -> {
                    _hasRoot.value = true
                    return
                }
                false -> {
                    if (!recheckIfDenied || isShellBusy()) {
                        _hasRoot.value = false
                        return
                    }
                }
                null -> Unit
            }

            val granted = withContext(Dispatchers.IO) {
                if (cachedShellIsRoot() == false) {
                    if (isShellBusy()) return@withContext false
                    closeCachedShell()
                }
                runCatching { probeRootShell() }.getOrDefault(false)
            }
            _hasRoot.value = granted
        }
    }
}
