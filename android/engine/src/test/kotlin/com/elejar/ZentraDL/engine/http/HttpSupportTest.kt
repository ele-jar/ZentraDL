package com.elejar.ZentraDL.engine.http

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HttpSupportTest {

    @Test fun splitRanges_evenSplit() {
        assertThat(HttpSupport.splitRanges(1000, 4)).containsExactly(0L..249L, 250L..499L, 500L..749L, 750L..999L)
    }

    @Test fun splitRanges_remainderGoesFirst() {
        assertThat(HttpSupport.splitRanges(10, 3)).containsExactly(0L..3L, 4L..6L, 7L..9L)
    }

    @Test fun splitRanges_cappedAt32_andAtRemainingBytes() {
        assertThat(HttpSupport.splitRanges(10_000, 64)).hasSize(32)
        assertThat(HttpSupport.splitRanges(3, 8)).containsExactly(0L..0L, 1L..1L, 2L..2L)
    }

    @Test fun splitRanges_withStartOffset() {
        assertThat(HttpSupport.splitRanges(1000, 2, 400)).containsExactly(400L..699L, 700L..999L)
    }

    @Test fun splitRanges_coversEverythingExactlyOnce() {
        val ranges = HttpSupport.splitRanges(1_000_003, 8, 17)
        assertThat(ranges.first().first).isEqualTo(17L)
        assertThat(ranges.last().last).isEqualTo(1_000_002L)
        var cursor = 17L
        ranges.forEach { r ->
            assertThat(r.first).isEqualTo(cursor)
            cursor = r.last + 1
        }
    }

    @Test fun parseFileName_plainDisposition() {
        assertThat(HttpSupport.parseFileName("attachment; filename=\"vid.mp4\"", "https://x/y")).isEqualTo("vid.mp4")
    }

    @Test fun parseFileName_rfc5987() {
        assertThat(
            HttpSupport.parseFileName("attachment; filename*=UTF-8''%D0%B2%D0%B8%D0%B4%D0%B5%D0%BE.mp4", "https://x/y"),
        ).isEqualTo("видео.mp4")
    }

    @Test fun parseFileName_urlFallback() {
        assertThat(HttpSupport.parseFileName(null, "https://cdn/x/my%20file.zip?token=1")).isEqualTo("my file.zip")
    }

    @Test fun parseFileName_sanitizesTraversal() {
        assertThat(HttpSupport.parseFileName(null, "https://x/..%2Fevil.apk")).isEqualTo(".._evil.apk")
        assertThat(HttpSupport.parseFileName("attachment; filename=\"a/b:c?.mp4\"", "https://x/y")).isEqualTo("a_b_c_.mp4")
    }

    @Test fun speedEma_needsThreeSamples() {
        val ema = SpeedEma()
        ema.addSample(1000, 1000)
        ema.addSample(1000, 1000)
        assertThat(ema.stable).isFalse()
        ema.addSample(1000, 1000)
        assertThat(ema.stable).isTrue()
        assertThat(ema.bytesPerSecond).isEqualTo(1000L)
    }
}
