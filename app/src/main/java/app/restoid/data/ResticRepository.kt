package app.restoid.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL

// Represents the state of the restic binary
sealed class ResticState {
    object Idle : ResticState() // Not checked yet
    object NotInstalled : ResticState()
    data class Downloading(val progress: Float) : ResticState() // Progress is not implemented yet, dummy values
    data class Installed(val path: String, val version: String) : ResticState()
    data class Error(val message: String) : ResticState()
}

class ResticRepository(private val context: Context) {

    private val _resticState = MutableStateFlow<ResticState>(ResticState.Idle)
    val resticState = _resticState.asStateFlow()

    private val resticFile = File(context.filesDir, "restic")
    private val resticVersion = "0.18.0"

    // Check the status of the restic binary
    suspend fun checkResticStatus() {
        withContext(Dispatchers.IO) {
            if (resticFile.exists() && resticFile.canExecute()) {
                // A simple check is enough for now. A version check could be added later.
                _resticState.value = ResticState.Installed(resticFile.absolutePath, resticVersion)
            } else {
                _resticState.value = ResticState.NotInstalled
            }
        }
    }

    // Download and install the restic binary
    suspend fun downloadAndInstallRestic() {
        withContext(Dispatchers.IO) {
            try {
                _resticState.value = ResticState.Downloading(0f)

                // Determine architecture based on device ABI
                val arch = getArchForRestic()
                if (arch == null) {
                    _resticState.value = ResticState.Error("Unsupported device architecture")
                    return@withContext
                }

                // Download the bz2 compressed file
                val url = URL("https://github.com/restic/restic/releases/download/v$resticVersion/restic_${resticVersion}_linux_$arch.bz2")
                val bz2File = File(context.cacheDir, "restic.bz2")

                url.openStream().use { input ->
                    FileOutputStream(bz2File).use { output ->
                        input.copyTo(output) // In a real app, you'd want to report progress here
                    }
                }
                _resticState.value = ResticState.Downloading(0.5f) // Dummy progress update

                // Decompress the file
                FileInputStream(bz2File).use { fileInput ->
                    BZip2CompressorInputStream(fileInput).use { bzip2Input ->
                        FileOutputStream(resticFile).use { fileOutput ->
                            bzip2Input.copyTo(fileOutput)
                        }
                    }
                }
                _resticState.value = ResticState.Downloading(1.0f)

                // Make the binary executable and clean up the downloaded archive
                resticFile.setExecutable(true)
                bz2File.delete()

                if (resticFile.exists() && resticFile.canExecute()) {
                    _resticState.value = ResticState.Installed(resticFile.absolutePath, resticVersion)
                } else {
                    _resticState.value = ResticState.Error("Failed to make binary executable")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _resticState.value = ResticState.Error(e.message ?: "An unknown error occurred")
            }
        }
    }

    // Map Android ABI to restic's architecture name convention
    private fun getArchForRestic(): String? {
        val abi = Build.SUPPORTED_ABIS[0]
        return when {
            abi.startsWith("arm64") -> "arm64"
            abi.startsWith("armeabi") -> "arm"
            abi.startsWith("x86_64") -> "amd64"
            abi.startsWith("x86") -> "386"
            else -> null
        }
    }
}
