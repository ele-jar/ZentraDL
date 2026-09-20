package com.elejar.ZentraDL.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ErrorTextsTest {

    @Test fun dns() {
        val f = ErrorTexts.classify("java.net.UnknownHostException: cdn.x.com")
        assertThat(f.title).isEqualTo("Couldn't reach server")
        assertThat(f.action).isEqualTo("Retry")
    }

    @Test fun timeout() {
        assertThat(ErrorTexts.classify("read timed out").title).isEqualTo("Server stopped responding")
    }

    @Test fun expiredLink() {
        val f = ErrorTexts.classify("probe failed: HTTP 404 for https://x/f")
        assertThat(f.title).isEqualTo("Link expired or removed")
    }

    @Test fun serverLimiting() {
        assertThat(ErrorTexts.classify("download failed: HTTP 503").title).isEqualTo("Server is limiting us")
    }

    @Test fun diskFull() {
        assertThat(ErrorTexts.classify("ENOSPC: no space left").title).isEqualTo("Not enough space")
    }

    @Test fun blankAndPassthrough() {
        assertThat(ErrorTexts.classify(null).title).isEqualTo("Download failed")
        assertThat(ErrorTexts.classify("weird thing").message).isEqualTo("weird thing")
    }
}
