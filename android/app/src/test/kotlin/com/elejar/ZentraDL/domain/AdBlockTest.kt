package com.elejar.ZentraDL.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdBlockTest {

    @Test fun blocksSubdomains() {
        assertThat(AdBlock.shouldBlock("https://ads.doubleclick.net/x")).isTrue()
        assertThat(AdBlock.shouldBlock("https://pagead2.googlesyndication.com/y")).isTrue()
        assertThat(AdBlock.shouldBlock("https://example.com/")).isFalse()
        assertThat(AdBlock.shouldBlock("https://notdoubleclick.net/")).isFalse()
        assertThat(AdBlock.shouldBlock("garbage")).isFalse()
    }

    @Test fun sniffer_kinds() {
        assertThat(MediaSniffer.kindOf("https://x/v.m3u8")).isEqualTo(MediaSniffer.Kind.Playlist)
        assertThat(MediaSniffer.kindOf("https://x/v.mpd?token=1")).isEqualTo(MediaSniffer.Kind.Playlist)
        assertThat(MediaSniffer.kindOf("https://x/movie.mp4")).isEqualTo(MediaSniffer.Kind.Media)
        assertThat(MediaSniffer.kindOf("https://x/page.html")).isNull()
        assertThat(MediaSniffer.sniffable("https://ads.doubleclick.net/v.mp4")).isFalse()
        assertThat(MediaSniffer.sniffable("https://x/v.mp4")).isTrue()
    }
}
