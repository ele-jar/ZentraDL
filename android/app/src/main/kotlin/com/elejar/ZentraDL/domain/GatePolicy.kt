package com.elejar.ZentraDL.domain

/** When downloads may run (Q3-lite, P3d). Window in minutes-of-day; -1 = off. */
data class GatePolicy(
    val wifiOnly: Boolean = false,
    val chargingOnly: Boolean = false,
    val windowStartMin: Int = -1,
    val windowEndMin: Int = -1,
)

/** Point-in-time device state (Android side reads this; logic stays pure here). */
data class NetState(
    val connected: Boolean,
    val unmetered: Boolean,
    val charging: Boolean,
)

/** Why the queue is held. Null = clear to run. */
sealed interface GateBlock {
    data object NoNetwork : GateBlock
    data object WifiOnly : GateBlock
    data object ChargingOnly : GateBlock
    /** Downloads resume at [startsAtMin] (minutes-of-day). */
    data class Scheduled(val startsAtMin: Int) : GateBlock
}

/** Pure gate check (unit-tested). Overnight windows (start > end) wrap midnight. */
object GatePolicyCheck {
    fun check(policy: GatePolicy, net: NetState, nowMin: Int): GateBlock? {
        if (!net.connected) return GateBlock.NoNetwork
        if (policy.wifiOnly && !net.unmetered) return GateBlock.WifiOnly
        if (policy.chargingOnly && !net.charging) return GateBlock.ChargingOnly
        val (start, end) = policy.windowStartMin to policy.windowEndMin
        if (start in 0..<1440 && end in 0..1440 && start != end) {
            val inside = if (start < end) nowMin in start..<end else nowMin >= start || nowMin < end
            if (!inside) return GateBlock.Scheduled(start)
        }
        return null
    }
}
