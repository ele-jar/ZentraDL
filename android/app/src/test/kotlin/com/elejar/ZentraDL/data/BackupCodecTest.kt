package com.elejar.ZentraDL.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BackupCodecTest {

    @Test fun roundTrip() {
        val payload = BackupPayload(
            settings = mapOf("connections" to "int:8", "wifi_only" to "bool:true", "theme_mode" to "str:Dark"),
            categories = listOf(BackupCategory("videos", "Videos", "Videos")),
            bookmarks = listOf(BackupBookmark("T", "https://x.test")),
            rules = listOf(BackupRule("R", true, "ON_COMPLETE", listOf("ext:mkv"), listOf("moveCat:videos"))),
            tasks = listOf(
                BackupTask("id1", "https://x.test/f", "f.bin", "/d", "paused", 10, 5, "other", "http", ""),
            ),
            torrents = listOf(BackupTorrent("ab12", "magnet:?x", "", false, "T", 99)),
        )
        val back = BackupManager.decode(BackupManager.encode(payload))
        assertThat(back).isEqualTo(payload)
    }

    @Test fun garbage_rejected() {
        assertThat(BackupManager.decode("not json".toByteArray())).isNull()
        assertThat(BackupManager.decode("{}".toByteArray())?.version).isEqualTo(1)
    }
}
