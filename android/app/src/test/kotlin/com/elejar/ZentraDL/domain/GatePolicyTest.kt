package com.elejar.ZentraDL.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GatePolicyTest {
    private val open = NetState(connected = true, unmetered = true, charging = true)

    @Test fun clear_whenNothingSet() {
        assertThat(GatePolicyCheck.check(GatePolicy(), open, 600)).isNull()
    }

    @Test fun noNetwork_wins() {
        assertThat(GatePolicyCheck.check(GatePolicy(), open.copy(connected = false), 600))
            .isEqualTo(GateBlock.NoNetwork)
    }

    @Test fun wifiOnly_blocksMetered() {
        val p = GatePolicy(wifiOnly = true)
        assertThat(GatePolicyCheck.check(p, open.copy(unmetered = false), 600))
            .isEqualTo(GateBlock.WifiOnly)
        assertThat(GatePolicyCheck.check(p, open, 600)).isNull()
    }

    @Test fun chargingOnly_blocks() {
        val p = GatePolicy(chargingOnly = true)
        assertThat(GatePolicyCheck.check(p, open.copy(charging = false), 600))
            .isEqualTo(GateBlock.ChargingOnly)
    }

    @Test fun window_blocksOutside_reportsStart() {
        val p = GatePolicy(windowStartMin = 1320, windowEndMin = 360) // 22:00–06:00
        assertThat(GatePolicyCheck.check(p, open, 1400)).isNull() // 23:20 inside
        assertThat(GatePolicyCheck.check(p, open, 100)).isNull() // 01:40 inside (wrap)
        assertThat(GatePolicyCheck.check(p, open, 720)).isEqualTo(GateBlock.Scheduled(1320))
    }

    @Test fun window_daytime() {
        val p = GatePolicy(windowStartMin = 60, windowEndMin = 120)
        assertThat(GatePolicyCheck.check(p, open, 90)).isNull()
        assertThat(GatePolicyCheck.check(p, open, 200)).isEqualTo(GateBlock.Scheduled(60))
    }

    @Test fun window_disabledWhenEqual() {
        assertThat(GatePolicyCheck.check(GatePolicy(windowStartMin = 60, windowEndMin = 60), open, 900))
            .isNull()
    }
}
