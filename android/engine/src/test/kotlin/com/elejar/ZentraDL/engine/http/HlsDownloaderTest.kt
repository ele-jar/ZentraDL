package com.elejar.ZentraDL.engine.http

import com.google.common.truth.Truth.assertThat
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Serves a variant playlist, an AES-128 media playlist and two segments. */
private class HlsDispatcher(
    private val key: ByteArray,
    private val seg0: ByteArray,
    private val seg1: ByteArray,
) : Dispatcher() {
    private fun encrypt(plain: ByteArray, iv: ByteArray): ByteArray {
        val pad = 16 - (plain.size % 16)
        val padded = plain + ByteArray(pad) { pad.toByte() }
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(padded)
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val body: ByteArray = when (request.url.encodedPath) {
            "/v.m3u8" -> """
                #EXTM3U
                #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
                lo.m3u8
                #EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=1280x720
                hi.m3u8
            """.trimIndent().toByteArray()
            "/hi.m3u8" -> """
                #EXTM3U
                #EXT-X-TARGETDURATION:6
                #EXT-X-MEDIA-SEQUENCE:7
                #EXT-X-KEY:METHOD=AES-128,URI="/key.bin"
                #EXTINF:6.0,
                s0.ts
                #EXTINF:6.0,
                s1.ts
                #EXT-X-ENDLIST
            """.trimIndent().toByteArray()
            "/key.bin" -> key
            "/s0.ts" -> encrypt(seg0, ByteArray(16).also { it[15] = 7 })
            "/s1.ts" -> encrypt(seg1, ByteArray(16).also { it[15] = 8 })
            else -> return MockResponse.Builder().code(404).build()
        }
        return MockResponse.Builder().code(200).body(Buffer().write(body)).build()
    }
}

class HlsDownloaderTest {

    @Rule
    @JvmField
    val tmp: TemporaryFolder = TemporaryFolder()

    private val key = ByteArray(16) { it.toByte() }
    private val seg0 = ByteArray(100) { (it % 251).toByte() }
    private val seg1 = ByteArray(50) { (it % 127).toByte() }

    @Test fun variants_and_encrypted_download(): Unit = runBlocking {
        MockWebServer().apply {
            dispatcher = HlsDispatcher(key, seg0, seg1)
            start()
        }.use { s ->
            val dl = HlsDownloader()
            val variants = dl.variants(s.url("/v.m3u8").toString())
            assertThat(variants.map { it.bandwidthBps }).containsExactly(800_000L, 2_400_000L).inOrder()
            assertThat(variants[1].resolution).isEqualTo("1280x720")

            val dest = File(tmp.root, "out.ts")
            val events = dl.download(
                HlsSpec(s.url("/hi.m3u8").toString(), dest, bandwidthBps = 2_400_000),
            ).toList()
            assertThat(dest.readBytes()).isEqualTo(seg0 + seg1)
            assertThat(events).isNotEmpty()
            assertThat(events.last().percent).isEqualTo(100)
        }
    }

    @Test fun plain_playlist_without_variants(): Unit = runBlocking {
        MockWebServer().apply {
            dispatcher = HlsDispatcher(key, seg0, seg1)
            start()
        }.use { s ->
            val dl = HlsDownloader()
            // A media playlist URL yields itself as the single variant.
            val variants = dl.variants(s.url("/hi.m3u8").toString())
            assertThat(variants).hasSize(1)
            assertThat(variants.single().url).isEqualTo(s.url("/hi.m3u8").toString())
        }
    }
}
