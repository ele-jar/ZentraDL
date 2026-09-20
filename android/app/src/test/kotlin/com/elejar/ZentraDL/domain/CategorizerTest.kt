package com.elejar.ZentraDL.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CategorizerTest {

    @Test fun ext_video_isConfident() {
        assertThat(Categorizer.categorize("movie.mkv", null, null))
            .isEqualTo(Categorizer.Result("videos", true))
    }

    @Test fun ext_caseInsensitive_queryStripped() {
        assertThat(Categorizer.categorize("SONG.MP3?dl=1", null, null).categoryId).isEqualTo("music")
    }

    @Test fun mime_fallback_whenNoExt() {
        assertThat(Categorizer.categorize("download", "video/mp4", null))
            .isEqualTo(Categorizer.Result("videos", true))
        assertThat(Categorizer.categorize("download", "application/pdf", null).categoryId)
            .isEqualTo("documents")
    }

    @Test fun apkMirror_host_mapsToApps() {
        assertThat(Categorizer.categorize("get", null, "https://www.apkmirror.com/x").categoryId)
            .isEqualTo("apps")
    }

    @Test fun unknown_fallsBackToOther_unconfident() {
        assertThat(Categorizer.categorize("blob", null, "https://example.com/blob"))
            .isEqualTo(Categorizer.Result("other", false))
    }

    @Test fun archives_covered() {
        listOf("a.zip", "b.7z", "c.tar.gz").forEach {
            assertThat(Categorizer.categorize(it, null, null).categoryId).isEqualTo("archives")
        }
    }
}
