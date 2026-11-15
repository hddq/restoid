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

fun isRemoteBackend(backendType: RepositoryBackendType?, isRcloneLocal: Boolean = false): Boolean {
    if (backendType == RepositoryBackendType.RCLONE && isRcloneLocal) {
        return false
    }
    return backendType == RepositoryBackendType.SFTP ||
        backendType == RepositoryBackendType.REST ||
        backendType == RepositoryBackendType.S3 ||
        backendType == RepositoryBackendType.RCLONE
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
 *
 * The primary constructor takes its collaborators as parameters so the
 * timer/polling behavior is unit testable with virtual time; production
 * callers use the secondary constructor, which wires the real
 * ConnectivityManager.
 */
class NetworkLossMonitor(
    private val stateProvider: () -> Pair<Boolean, Boolean>,
    private val registerCallback: ((ConnectivityManager.NetworkCallback) -> Unit)? = null,
    private val unregisterCallback: ((ConnectivityManager.NetworkCallback) -> Unit)? = null,
    private val gracePeriodMs: Long = NETWORK_LOSS_GRACE_PERIOD_MS,
    private val pollIntervalMs: Long = NETWORK_LOSS_POLL_INTERVAL_MS,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val onSustainedViolation: () -> Unit
) {
    constructor(
        context: Context,
        gracePeriodMs: Long = NETWORK_LOSS_GRACE_PERIOD_MS,
        onSustainedViolation: () -> Unit
    ) : this(
        stateProvider = { productionState(context.applicationContext) },
        registerCallback = { callback ->
            connectivityManagerOf(context.applicationContext).registerDefaultNetworkCallback(callback)
        },
        unregisterCallback = { callback ->
            connectivityManagerOf(context.applicationContext).unregisterNetworkCallback(callback)
        },
        gracePeriodMs = gracePeriodMs,
        onSustainedViolation = onSustainedViolation
    )

    companion object {
        private fun connectivityManagerOf(context: Context) =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        private fun productionState(appContext: Context): Pair<Boolean, Boolean> {
            val connectivityManager = connectivityManagerOf(appContext)
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

    @Volatile
    var backendIsRemote: Boolean = false

    @Volatile
    var requireUnmetered: Boolean = false

    @Volatile
    private var timer: Job? = null

    @Volatile
    private var poller: Job? = null

    /** Set once the violation has been reported; cleared on recovery so the
     * next outage earns its own full grace period instead of re-firing. */
    @Volatile
    private var fired = false

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
        val register = registerCallback
        if (register != null) {
            runCatching {
                register(callback)
            }.onFailure {
                android.util.Log.w(TAG, "registerDefaultNetworkCallback failed", it)
            }
        }
        // Poll as well as listening: teardown events can be missed or coalesced
        // (observed on emulator airplane toggles), and polling also covers
        // networks that die without a clean teardown.
        poller?.cancel()
        poller = scope.launch {
            while (true) {
                delay(pollIntervalMs)
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
        val unregister = unregisterCallback
        if (unregister != null) {
            runCatching {
                unregister(callback)
            }
        }
        scope.cancel()
    }

    fun configure(backendIsRemote: Boolean, requireUnmetered: Boolean) {
        this.backendIsRemote = backendIsRemote
        this.requireUnmetered = requireUnmetered
        evaluate()
    }

    internal fun evaluate() {
        if (!backendIsRemote) return
        // Read state outside the lock (binder calls); only the timer
        // transitions below need to be atomic.
        val (hasInternet, isUnmetered) = stateProvider()
        android.util.Log.d(TAG, "evaluate: hasInternet=$hasInternet isUnmetered=$isUnmetered requireUnmetered=$requireUnmetered")
        val violated = !hasInternet || (requireUnmetered && !isUnmetered)
        synchronized(lock) {
            if (!violated) {
                // Blip over (or never violated): stand down so a later outage gets a full grace period.
                timer?.cancel()
                timer = null
                fired = false
                return
            }
            // Still violated. If a grace countdown is already running, or this
            // outage was already reported, let things be: re-evaluations
            // (polls, repeat callbacks) must not restart the countdown or the
            // grace period could never elapse during a sustained outage.
            if (timer != null || fired) return
            android.util.Log.w(TAG, "violation detected, starting ${gracePeriodMs}ms grace timer")
            timer = scope.launch {
                delay(gracePeriodMs)
                val (stillHasInternet, stillUnmetered) = stateProvider()
                val shouldFire = synchronized(lock) {
                    val abort = !fired && shouldAbortNetworkRun(
                        backendIsRemote = true,
                        hasInternet = stillHasInternet,
                        isUnmetered = stillUnmetered,
                        requireUnmetered = requireUnmetered,
                        downDurationMs = gracePeriodMs,
                        gracePeriodMs = gracePeriodMs
                    )
                    if (abort) fired = true
                    timer = null
                    abort
                }
                if (shouldFire) {
                    android.util.Log.w(TAG, "sustained violation, requesting stop")
                    onSustainedViolation()
                }
            }
        }
    }
}

const val NETWORK_LOSS_GRACE_PERIOD_MS = 90_000L

/** How often the monitor re-checks connectivity itself, independent of system callbacks. */
const val NETWORK_LOSS_POLL_INTERVAL_MS = 30_000L

private const val TAG = "NetworkLossMonitor"
