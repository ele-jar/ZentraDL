package com.elejar.ZentraDL.engine.http

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.atomic.AtomicLong
import org.junit.Test

class SpeedLimiterTest {
    @Test fun unlimited_neverWaits() {
        val l = SpeedLimiter(AtomicLong(0))
        assertThat(l.throttle(1_000_000)).isEqualTo(0)
    }

    @Test fun overLimit_returnsWaitMath() {
        var now = 0L
        val l = SpeedLimiter(AtomicLong(1000)) { now }
        // 500 of 1000 B/s budget: want 0.5s, elapsed 0 → wait 500ms.
        assertThat(l.throttle(500)).isEqualTo(500)
        // Instant second call: want 1.0s total → wait 1000ms.
        assertThat(l.throttle(500)).isEqualTo(1000)
        // 1.2s later the window resets: budget fresh again.
        now = 1_200_000_000L
        assertThat(l.throttle(500)).isEqualTo(500)
    }
}
