package app.restoid.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// This sealed class represents the different states our UI can be in.
sealed class RootState {
    object Idle : RootState()
    object Checking : RootState()
    object Granted : RootState()
    object Denied : RootState()
}

class SettingsViewModel : ViewModel() {

    // This holds the current state of our root check. UI will observe this.
    private val _rootState = MutableStateFlow<RootState>(RootState.Idle)
    val rootState = _rootState.asStateFlow()

    init {
        // Automatically check for root when the ViewModel is created.
        checkRootAccess(prompt = false)
    }

    fun checkRootAccess(prompt: Boolean) {
        // Set state to Checking so UI can show a loading spinner
        _rootState.value = RootState.Checking

        // Launch a coroutine in the ViewModel's scope.
        // It will be automatically cancelled when the ViewModel is destroyed.
        viewModelScope.launch {
            // Shell.rootAccess() is an I/O operation, so we move it to a background thread.
            val hasRoot = withContext(Dispatchers.IO) {
                if (prompt) {
                    // This will trigger the Magisk root prompt if needed.
                    Shell.getShell().isRoot
                } else {
                    // This just checks if root is already granted without prompting.
                    Shell.rootAccess()
                }
            }

            // Update the state based on the result.
            _rootState.value = if (hasRoot) RootState.Granted else RootState.Denied
        }
    }
}