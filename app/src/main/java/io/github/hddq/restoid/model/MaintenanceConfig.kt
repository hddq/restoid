package io.github.hddq.restoid.model

data class MaintenanceConfig(
    val unlockRepo: Boolean = false,
    val forgetSnapshots: Boolean = false,
    val pruneRepo: Boolean = false,
    val checkRepo: Boolean = true,
    val readData: Boolean = false,
    val keepLast: Int = 5,
    val keepDaily: Int = 7,
    val keepWeekly: Int = 4,
    val keepMonthly: Int = 6,
    val forgetAllHosts: Boolean = false,
    val forgetHosts: List<String> = emptyList()
)
