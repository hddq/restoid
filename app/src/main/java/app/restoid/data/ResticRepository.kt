package app.restoid.data

import android.content.Context
import android.os.Build
import com.topjohnwu.superuser.Shell
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
    data class Downloading(val progress: Float) : ResticState() // Progress from 0.0 to 1.0
    object Extracting : ResticState() // State for when decompression is in progress
    data class Installed(val path: String, val version: String) : ResticState()
    data class Error(val message: String) : ResticState()
}

class ResticRepository(private val context: Context) {

    private val _resticState = MutableStateFlow<ResticState>(ResticState.Idle)
    val resticState = _resticState.asStateFlow()

    private val resticFile = File(context.filesDir, "restic")
    private val resticReleaseVersion = "0.18.0"

    // Check the status of the restic binary by executing it
    suspend fun checkResticStatus() {
        withContext(Dispatchers.IO) {
            if (resticFile.exists() && resticFile.canExecute()) {
                // Execute the binary with the 'version' command
                val result = Shell.cmd("${resticFile.absolutePath} version").exec()
                if (result.isSuccess) {
                    // Get the first line of the output, e.g., "restic 0.18.0 ..."
                    val versionOutput = result.out.firstOrNull()?.trim() ?: "Unknown version"
                    _resticState.value = ResticState.Installed(resticFile.absolutePath, versionOutput)
                } else {
                    // If the command fails, the binary might be corrupted
                    _resticState.value = ResticState.Error("Binary corrupted or invalid")
                    resticFile.delete() // Clean up the bad file
                }
            } else {
                _resticState.value = ResticState.NotInstalled
            }
        }
    }

    // Download, decompress, and verify the restic binary
    suspend fun downloadAndInstallRestic() {
        withContext(Dispatchers.IO) {
            try {
                // Determine architecture based on device ABI
                val arch = getArchForRestic()
                if (arch == null) {
                    _resticState.value = ResticState.Error("Unsupported device architecture")
                    return@withContext
                }

                // Download the bz2 compressed file and report progress
                val url = URL("https://github.com/restic/restic/releases/download/v$resticReleaseVersion/restic_${resticReleaseVersion}_linux_$arch.bz2")
                val bz2File = File(context.cacheDir, "restic.bz2")
                val urlConnection = url.openConnection()
                val fileSize = urlConnection.contentLength.toLong()

                urlConnection.getInputStream().use { input ->
                    FileOutputStream(bz2File).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloadedSize = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedSize += bytesRead
                            if (fileSize > 0) {
                                val progress = downloadedSize.toFloat() / fileSize.toFloat()
                                _resticState.value = ResticState.Downloading(progress)
                            }
                        }
                    }
                }

                // Switch to extracting state
                _resticState.value = ResticState.Extracting

                // Decompress the file
                FileInputStream(bz2File).use { fileInput ->
                    BZip2CompressorInputStream(fileInput).use { bzip2Input ->
                        FileOutputStream(resticFile).use { fileOutput ->
                            bzip2Input.copyTo(fileOutput)
                        }
                    }
                }

                // Make the binary executable and clean up
                resticFile.setExecutable(true)
                bz2File.delete()

                // Verify installation by checking the version
                checkResticStatus()

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

