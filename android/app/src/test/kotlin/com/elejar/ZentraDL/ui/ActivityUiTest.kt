package com.elejar.ZentraDL.ui

import com.elejar.ZentraDL.data.local.TaskRecord
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ActivityUiTest {

    @Test fun weeklyBytes_buckets() {
        val day = 86_400_000L
        val now = (1_750_000_000_000L / day) * day + 12 * 3_600_000L // a noon
        val records = listOf(
            TaskRecord("a", "u", "a.bin", "/d", "completed", 100, now - 1000),
            TaskRecord("b", "u", "b.bin", "/d", "completed", 50, now - day - 1000),
            TaskRecord("c", "u", "c.bin", "/d", "downloading", 999, now - 1000),
        )
        val weeks = ActivityUi.weeklyBytes(records, now)
        assertThat(weeks).hasSize(7)
        assertThat(weeks.last().bytes).isEqualTo(100)
        assertThat(weeks[5].bytes).isEqualTo(50)
        assertThat(weeks.take(5).sumOf { it.bytes }).isEqualTo(0)
    }

    @Test fun bytesByCategory_sumsCompleted() {
        val records = listOf(
            TaskRecord("a", "u", "a", "/d", "completed", 100, 0, categoryId = "videos"),
            TaskRecord("b", "u", "b", "/d", "completed", 50, 0, categoryId = "videos"),
            TaskRecord("c", "u", "c", "/d", "paused", 999, 0, categoryId = "music"),
        )
        assertThat(ActivityUi.bytesByCategory(records)).containsExactly("videos", 150L)
    }
}
