package io.github.hddq.restoid.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.topjohnwu.superuser.Shell
import io.github.hddq.restoid.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL

/**
 * Responsible solely for managing the Restic binary executable.
 * - Checks if installed
 * - Downloads updates
 * - Extracts bundled binaries
 */
class ResticBinaryManager(private val context: Context) {

    private val _resticState = MutableStateFlow<ResticState>(ResticState.Idle)
    val resticState = _resticState.asStateFlow()

    private val _latestResticVersion = MutableStateFlow<String?>(null)
    val latestResticVersion = _latestResticVersion.asStateFlow()

    // Public access to the binary file location
    val resticFile = File(context.filesDir, "restic")
    val stableResticVersion = "0.18.1"

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs: SharedPreferences = context.getSharedPreferences("restic_repo_prefs", Context.MODE_PRIVATE)
    private val KEY_EXTRACTED_VERSION = "extracted_pkg_version"

    // Check the status of the restic binary by executing it
    suspend fun checkResticStatus() {
        withContext(Dispatchers.IO) {
            if (BuildConfig.IS_BUNDLED) {
                ensureBundledResticIsReady()
            }

            if (resticFile.exists() && resticFile.canExecute()) {
                // Execute the binary with the 'version' command
                val result = Shell.cmd("${resticFile.absolutePath} version").exec()
                if (result.isSuccess) {
                    // Get the first line of the output, e.g., "restic 0.18.0 ..."
                    val versionOutput = result.out.firstOrNull()?.trim() ?: "Unknown version"
                    val version = versionOutput.split(" ").getOrNull(1) ?: "unknown"
                    _resticState.value = ResticState.Installed(resticFile.absolutePath, version, versionOutput)
                } else {
                    // If the command fails, the binary might be corrupted
                    _resticState.value = ResticState.Error("Binary corrupted or invalid")
                    resticFile.delete() // Clean up the bad file
                }
            } else {
                if (_resticState.value !is ResticState.Error) {
                    if (BuildConfig.IS_BUNDLED) {
                        _resticState.value = ResticState.Error("Bundled binary not found or not executable.")
                    } else {
                        _resticState.value = ResticState.NotInstalled
                    }
                }
            }
        }
    }

    private suspend fun ensureBundledResticIsReady() {
        withContext(Dispatchers.IO) {
            val currentAppVersion = getAppVersionCode()
            val lastExtractedVersion = prefs.getLong(KEY_EXTRACTED_VERSION, -1L)
            val needsExtraction = !resticFile.exists() || (currentAppVersion > lastExtractedVersion)

            if (!needsExtraction) return@withContext

            try {
                _resticState.value = ResticState.Extracting
                Log.d("ResticBinMgr", "Extracting bundled binary. App Ver: $currentAppVersion")

                // The binary is now in the native library directory as librestic.so
                val nativeLibDir = context.applicationInfo.nativeLibraryDir
                val sourceBinary = File(nativeLibDir, "librestic.so")

                if (!sourceBinary.exists()) {
                    // Fallback for some devices
                    val abi = Build.SUPPORTED_ABIS[0]
                    val altSourceBinary = File(nativeLibDir, "../$abi/librestic.so")
                    if (altSourceBinary.exists()) {
                        copyFile(altSourceBinary, resticFile)
                    } else {
                        throw Exception("Bundled restic binary not found in native library directory")
                    }
                } else {
                    copyFile(sourceBinary, resticFile)
                }

                resticFile.setExecutable(true)
                prefs.edit().putLong(KEY_EXTRACTED_VERSION, currentAppVersion).apply()
                Log.d("ResticBinMgr", "Bundled restic copied to ${resticFile.absolutePath}")

            } catch (e: Exception) {
                Log.e("ResticBinMgr", "Failed to prepare bundled restic binary", e)
                _resticState.value = ResticState.Error("Failed to prepare bundled binary: ${e.message}")
            }
        }
    }

    private fun copyFile(source: File, dest: File) {
        source.inputStream().use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun getAppVersionCode(): Long {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun fetchLatestResticVersion() {
        withContext(Dispatchers.IO) {
            try {
                _latestResticVersion.value = getLatestResticVersionFromGitHub()
            } catch (e: Exception) {
                Log.e("ResticBinMgr", "Failed to fetch latest restic version", e)
            }
        }
    }

    private suspend fun getLatestResticVersionFromGitHub(): String = withContext(Dispatchers.IO) {
        val url = URL("https://api.github.com/repos/restic/restic/releases/latest")
        val jsonString = url.readText()
        val jsonElement = json.parseToJsonElement(jsonString)
        val tagName = jsonElement.jsonObject["tag_name"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("tag_name not found in GitHub API response")
        tagName.removePrefix("v")
    }

    suspend fun downloadAndInstallLatestRestic() {
        if (BuildConfig.IS_BUNDLED) return
        withContext(Dispatchers.IO) {
            try {
                _resticState.value = ResticState.Downloading(0f)
                val latestVersion = getLatestResticVersionFromGitHub()
                downloadAndInstallRestic(latestVersion)
            } catch (e: Exception) {
                _resticState.value = ResticState.Error("Could not fetch latest version: ${e.message}")
            }
        }
    }

    suspend fun downloadAndInstallRestic(versionToDownload: String = stableResticVersion) {
        if (BuildConfig.IS_BUNDLED) {
            _resticState.value = ResticState.Error("Download is not supported in the bundled flavor.")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val arch = getArchForRestic()
                if (arch == null) {
                    _resticState.value = ResticState.Error("Unsupported device architecture")
                    return@withContext
                }

                _resticState.value = ResticState.Downloading(0f)
                val url = URL("https://github.com/restic/restic/releases/download/v$versionToDownload/restic_${versionToDownload}_linux_$arch.bz2")
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

                _resticState.value = ResticState.Extracting
                FileInputStream(bz2File).use { fileInput ->
                    BZip2CompressorInputStream(fileInput).use { bzip2Input ->
                        FileOutputStream(resticFile).use { fileOutput ->
                            bzip2Input.copyTo(fileOutput)
                        }
                    }
                }

                resticFile.setExecutable(true)
                bz2File.delete()
                checkResticStatus()

            } catch (e: Exception) {
                e.printStackTrace()
                _resticState.value = ResticState.Error(e.message ?: "An unknown error occurred")
            }
        }
    }

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