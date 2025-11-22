package io.github.hddq.restoid.data

/**
 * Represents the state of the restic binary on the device.
 * Extracted to a separate file to be shared across Manager and Repository.
 */
sealed class ResticState {
    object Idle : ResticState() // Not checked yet
    object NotInstalled : ResticState()
    data class Downloading(val progress: Float) : ResticState() // Progress from 0.0 to 1.0
    object Extracting : ResticState() // State for when decompression is in progress
    data class Installed(val path: String, val version: String, val fullVersionOutput: String) : ResticState()
    data class Error(val message: String) : ResticState()
}