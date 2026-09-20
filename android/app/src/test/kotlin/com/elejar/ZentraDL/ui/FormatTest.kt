package com.elejar.ZentraDL.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormatTest {

    @Test fun bytes_units() {
        assertThat(Format.bytes(0)).isEqualTo("0 B")
        assertThat(Format.bytes(999)).isEqualTo("999 B")
        assertThat(Format.bytes(1024)).isEqualTo("1.0 KB")
        assertThat(Format.bytes(1_572_864)).isEqualTo("1.5 MB")
        assertThat(Format.bytes(1024L * 1024 * 1024)).isEqualTo("1.0 GB")
        assertThat(Format.bytes(-1)).isEqualTo("Unknown size")
    }

    @Test fun speed_stableGate() {
        assertThat(Format.speed(100, false)).isEqualTo("Calculating…")
        assertThat(Format.speed(3_200_000, true)).isEqualTo("3.1 MB/s")
    }

    @Test fun eta_ranges() {
        assertThat(Format.eta(0, 10)).isEqualTo("")
        assertThat(Format.eta(100, 0)).isEqualTo("—")
        assertThat(Format.eta(30, 10)).isEqualTo("3 sec left")
        assertThat(Format.eta(12 * 60, 1)).isEqualTo("12 min left")
    }

    @Test fun dayBucket_groups() {
        val now = 1_750_000_000_000L
        assertThat(Format.dayBucket(now, now)).isEqualTo("Today")
        assertThat(Format.dayBucket(now - 86_400_000L, now)).isEqualTo("Yesterday")
        assertThat(Format.dayBucket(now - 3 * 86_400_000L, now)).isEqualTo("This week")
        assertThat(Format.dayBucket(now - 30 * 86_400_000L, now)).isEqualTo("Earlier")
    }
}
