package io.github.hddq.restoid.data

/**
 * Represents the state of the restic binary on the device.
 * Extracted to a separate file to be shared across Manager and Repository.
 */
sealed class ResticState {
    object Idle : ResticState()
    data class Installed(val path: String, val version: String, val fullVersionOutput: String) : ResticState()
    data class Error(val message: String) : ResticState()
}
