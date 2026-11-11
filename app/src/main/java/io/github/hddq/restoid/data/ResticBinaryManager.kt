package io.github.hddq.restoid.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.topjohnwu.superuser.Shell
import io.github.hddq.restoid.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Responsible solely for managing the Restic binary executable.
 * - Resolves the bundled binary path
 * - Checks if installed and executable
 */
class ResticBinaryManager(
    private val context: Context,
    private val nativeLibDirProvider: () -> File = { File(context.applicationInfo.nativeLibraryDir) },
    private val stringResolver: (Int) -> String = { id ->
        runCatching { context.getString(id) }.getOrElse { "res_$id" }
    }
) {

    private val statusMutex = Mutex()
    private val _resticState = MutableStateFlow<ResticState>(ResticState.Idle)
    val resticState = _resticState.asStateFlow()

    private val resticFile: File
        get() = resolveBundledBinary("librestic.so")

    private val sshAskpassFile: File
        get() = resolveBundledBinary("libssh_askpass.so")

    val isInstalled: Boolean
        get() = getBinaryPath() != null

    init {
        val path = resticFile.takeIf { it.exists() && it.canExecute() }?.absolutePath
        if (path != null) {
            _resticState.value = ResticState.Installed(path, "", "")
        } else {
            _resticState.value = ResticState.Error(stringResolver(R.string.restic_error_bundled_not_found))
        }
    }

    fun getBinaryPath(): String? {
        if (_resticState.value is ResticState.Error) return null
        return resticFile.takeIf { it.exists() && it.canExecute() }?.absolutePath
    }

    fun getSshAskpassPath(): String? =
        sshAskpassFile.takeIf { it.exists() && it.canExecute() }?.absolutePath

    // Check the status of the restic binary by executing it
    suspend fun checkResticStatus() {
        val current = _resticState.value
        if (current is ResticState.Installed && current.fullVersionOutput.isNotBlank()) {
            return
        }
        statusMutex.withLock {
            val lockedCurrent = _resticState.value
            if (lockedCurrent is ResticState.Installed && lockedCurrent.fullVersionOutput.isNotBlank()) {
                return
            }
            withContext(Dispatchers.IO) {
                val binary = resticFile
                if (binary.exists() && binary.canExecute()) {
                    val result = Shell.cmd("${binary.absolutePath} version").exec()
                    if (result.isSuccess) {
                        val versionOutput = result.out.firstOrNull()?.trim() ?: stringResolver(R.string.restic_unknown_version)
                        val version = versionOutput.split(" ").getOrNull(1) ?: stringResolver(R.string.restic_unknown)
                        _resticState.value = ResticState.Installed(binary.absolutePath, version, versionOutput)
                    } else {
                        _resticState.value = ResticState.Error(stringResolver(R.string.restic_error_binary_corrupted))
                    }
                } else {
                    _resticState.value = ResticState.Error(stringResolver(R.string.restic_error_bundled_not_found))
                }
            }
        }
    }

    private fun resolveBundledBinary(libraryName: String = "librestic.so"): File {
        val nativeLibDir = nativeLibDirProvider()
        val directBinary = File(nativeLibDir, libraryName)
        if (directBinary.exists()) {
            return directBinary
        }

        val abi = Build.SUPPORTED_ABIS?.firstOrNull().orEmpty()
        if (abi.isNotEmpty()) {
            val fallbackBinary = File(nativeLibDir, "../$abi/$libraryName")
            if (fallbackBinary.exists()) {
                return fallbackBinary
            }
        }

        runCatching { Log.w("ResticBinMgr", "Bundled binary $libraryName missing in ${nativeLibDir.absolutePath}") }
        return directBinary
    }
}
