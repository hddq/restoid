package io.github.hddq.restoid.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.topjohnwu.superuser.Shell
import io.github.hddq.restoid.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Responsible solely for managing the Restic binary executable.
 * - Resolves the bundled binary path
 * - Checks if installed and executable
 */
class ResticBinaryManager(private val context: Context) {

    private val _resticState = MutableStateFlow<ResticState>(ResticState.Idle)
    val resticState = _resticState.asStateFlow()

    private val resticFile: File
        get() = resolveBundledBinary()

    fun getBinaryPath(): String? =
        resticFile.takeIf { it.exists() && it.canExecute() }?.absolutePath

    // Check the status of the restic binary by executing it
    suspend fun checkResticStatus() {
        withContext(Dispatchers.IO) {
            if (resticFile.exists() && resticFile.canExecute()) {
                val result = Shell.cmd("${resticFile.absolutePath} version").exec()
                if (result.isSuccess) {
                    val versionOutput = result.out.firstOrNull()?.trim() ?: context.getString(R.string.restic_unknown_version)
                    val version = versionOutput.split(" ").getOrNull(1) ?: context.getString(R.string.restic_unknown)
                    _resticState.value = ResticState.Installed(resticFile.absolutePath, version, versionOutput)
                } else {
                    _resticState.value = ResticState.Error(context.getString(R.string.restic_error_binary_corrupted))
                }
            } else {
                _resticState.value = ResticState.Error(context.getString(R.string.restic_error_bundled_not_found))
            }
        }
    }

    private fun resolveBundledBinary(): File {
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        val directBinary = File(nativeLibDir, "librestic.so")
        if (directBinary.exists()) {
            return directBinary
        }

        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        val fallbackBinary = File(nativeLibDir, "../$abi/librestic.so")
        if (fallbackBinary.exists()) {
            return fallbackBinary
        }

        Log.w("ResticBinMgr", "Bundled restic binary missing in ${nativeLibDir.absolutePath}")
        return directBinary
    }
}
