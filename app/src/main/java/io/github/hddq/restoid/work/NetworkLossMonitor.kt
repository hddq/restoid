package io.github.hddq.restoid.work

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.github.hddq.restoid.data.RepositoryBackendType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Decides whether a running backup operation must be stopped because the
 * network no longer satisfies it. Pure function so the policy is unit
 * testable without Android networking.
 *
 * @param backendIsRemote true for SFTP/REST/S3 repositories; local runs never abort.
 * @param hasInternet true when there is a usable network (active network with
 *   internet capability, which includes LAN-only links without public
 *   internet access). Deliberately NOT gated on VALIDATED, so backups to
 *   LAN hosts (e.g. nas.local over Wi-Fi without upstream) are unaffected.
 * @param isUnmetered true when the active network is unmetered.
 * @param requireUnmetered true when the run must stay on unmetered networks.
 * @param downDurationMs how long the current violation has persisted.
 * @param gracePeriodMs violations shorter than this (e.g. Wi-Fi to mobile handoffs) are tolerated.
 */
fun shouldAbortNetworkRun(
    backendIsRemote: Boolean,
    hasInternet: Boolean,
    isUnmetered: Boolean,
    requireUnmetered: Boolean,
    downDurationMs: Long,
    gracePeriodMs: Long = NETWORK_LOSS_GRACE_PERIOD_MS
): Boolean {
    if (!backendIsRemote) return false
    val violated = !hasInternet || (requireUnmetered && !isUnmetered)
    if (!violated) return false
    return downDurationMs >= gracePeriodMs
}

fun isRemoteBackend(backendType: RepositoryBackendType?): Boolean {
    return backendType == RepositoryBackendType.SFTP ||
        backendType == RepositoryBackendType.REST ||
        backendType == RepositoryBackendType.S3
}

/**
 * Watches connectivity for the duration of a backup operation and invokes
 * [onSustainedViolation] once a violation (lost network, or metered network
 * when unmetered is required) persists past the grace period. Self-contained:
 * owns its coroutine scope, cancelled by [stop].
 *
 * Restic retries failed backend operations for up to 15 minutes each with no
 * off switch, so without this a dead or downgraded link burns CPU, radio,
 * and (on mobile data) the user's allowance indefinitely.
 */
class NetworkLossMonitor(
    context: Context,
    private val gracePeriodMs: Long = NETWORK_LOSS_GRACE_PERIOD_MS,
    private val onSustainedViolation: () -> Unit
) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    var backendIsRemote: Boolean = false

    @Volatile
    var requireUnmetered: Boolean = false

    @Volatile
    private var timer: Job? = null

    @Volatile
    private var poller: Job? = null

    /**
     * Guards [timer] transitions. Evaluations arrive from both
     * ConnectivityManager callback threads and the poller coroutine, so the
     * check-and-create / cancel sequences must be atomic; @Volatile alone
     * does not make them safe (two threads could both see null and launch,
     * leaking a timer that later aborts a new outage early).
     */
    private val lock = Any()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = evaluate()
        override fun onLost(network: Network) = evaluate()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = evaluate()
        override fun onBlockedStatusChanged(network: Network, blocked: Boolean) = evaluate()
    }

    fun start() {
        android.util.Log.d(TAG, "start: backendIsRemote=$backendIsRemote requireUnmetered=$requireUnmetered")
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(callback)
        }.onFailure {
            android.util.Log.w(TAG, "registerDefaultNetworkCallback failed", it)
        }
        // Poll as well as listening: teardown events can be missed or coalesced
        // (observed on emulator airplane toggles), and polling also covers
        // networks that die without a clean teardown.
        poller?.cancel()
        poller = scope.launch {
            while (true) {
                delay(NETWORK_LOSS_POLL_INTERVAL_MS)
                evaluate()
            }
        }
        evaluate()
    }

    fun stop() {
        synchronized(lock) {
            timer?.cancel()
            timer = null
            poller?.cancel()
            poller = null
        }
        runCatching {
            connectivityManager.unregisterNetworkCallback(callback)
        }
        scope.cancel()
    }

    fun configure(backendIsRemote: Boolean, requireUnmetered: Boolean) {
        this.backendIsRemote = backendIsRemote
        this.requireUnmetered = requireUnmetered
        evaluate()
    }

    private fun evaluate() {
        if (!backendIsRemote) return
        // Read state outside the lock (binder calls); only the timer
        // transitions below need to be atomic.
        val (hasInternet, isUnmetered) = currentState()
        android.util.Log.d(TAG, "evaluate: hasInternet=$hasInternet isUnmetered=$isUnmetered requireUnmetered=$requireUnmetered")
        val violated = !hasInternet || (requireUnmetered && !isUnmetered)
        synchronized(lock) {
            if (!violated) {
                // Blip over (or never violated): stand down so a later outage gets a full grace period.
                timer?.cancel()
                timer = null
                return
            }
            // Still violated. If a grace countdown is already running, let it finish —
            // re-evaluations (polls, repeat callbacks) must not restart it or the
            // grace period could never elapse during a sustained outage.
            if (timer != null) return
            android.util.Log.w(TAG, "violation detected, starting ${gracePeriodMs}ms grace timer")
            timer = scope.launch {
                delay(gracePeriodMs)
                val (stillHasInternet, stillUnmetered) = currentState()
                if (shouldAbortNetworkRun(
                        backendIsRemote = true,
                        hasInternet = stillHasInternet,
                        isUnmetered = stillUnmetered,
                        requireUnmetered = requireUnmetered,
                        downDurationMs = gracePeriodMs,
                        gracePeriodMs = gracePeriodMs
                    )
                ) {
                    android.util.Log.w(TAG, "sustained violation, requesting stop")
                    onSustainedViolation()
                }
                synchronized(lock) { timer = null }
            }
        }
    }

    private fun currentState(): Pair<Boolean, Boolean> {
        val network = connectivityManager.activeNetwork ?: return false to false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false to false
        // Require only INTERNET, not VALIDATED: a Wi-Fi network without
        // upstream internet still advertises INTERNET, and LAN backends
        // (e.g. nas.local) stay reachable. VALIDATED would false-positive
        // on those links and abort working backups. Full disconnects still
        // read as down via activeNetwork == null above.
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isUnmetered = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        return hasInternet to isUnmetered
    }
}

const val NETWORK_LOSS_GRACE_PERIOD_MS = 90_000L

/** How often the monitor re-checks connectivity itself, independent of system callbacks. */
const val NETWORK_LOSS_POLL_INTERVAL_MS = 30_000L

private const val TAG = "NetworkLossMonitor"
