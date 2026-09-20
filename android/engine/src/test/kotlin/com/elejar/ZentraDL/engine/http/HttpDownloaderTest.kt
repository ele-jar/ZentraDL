package com.elejar.ZentraDL.engine.http

import com.elejar.ZentraDL.engine.model.DownloadSpec
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.random.Random
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

/** Serves [data] with optional Range support (P1 offline harness seed). */
private class RangeDispatcher(private val data: ByteArray, private val supportRange: Boolean) : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        val range = request.headers["Range"]
        if (supportRange && range != null) {
            val m = Regex("""bytes=(\d+)-(\d*)""").find(range)
            val start = m?.groupValues?.get(1)?.toLong() ?: 0L
            val end = (m?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }?.toLong() ?: (data.size - 1).toLong())
                .coerceAtMost(data.size - 1L)
            val slice = data.copyOfRange(start.toInt(), (end + 1).toInt())
            return MockResponse.Builder().code(206)
                .addHeader("Content-Range", "bytes $start-$end/${data.size}")
                .addHeader("Accept-Ranges", "bytes")
                .body(Buffer().write(slice)).build()
        }
        return MockResponse.Builder().code(200)
            .addHeader("Accept-Ranges", if (supportRange) "bytes" else "none")
            .addHeader("Content-Length", data.size.toString())
            .body(Buffer().write(data)).build()
    }
}

class HttpDownloaderTest {

    @Rule
    @JvmField
    val tmp: TemporaryFolder = TemporaryFolder()

    private fun server(data: ByteArray, supportRange: Boolean): MockWebServer =
        MockWebServer().apply {
            dispatcher = RangeDispatcher(data, supportRange)
            start()
        }

    @Test fun multiPart_download_matchesBytes(): Unit = runBlocking {
        val data = Random.nextBytes(100_000)
        server(data, true).use { s ->
            val dest = File(tmp.root, "out.bin")
            val events = HttpDownloader().download(DownloadSpec(s.url("/f").toString(), dest, connections = 4)).toList()
            assertThat(dest.readBytes()).isEqualTo(data)
            assertThat(events).isNotEmpty()
            assertThat(events.last().downloadedBytes).isEqualTo(data.size.toLong())
            assertThat(events.last().percent).isEqualTo(100)
        }
    }

    @Test fun resume_continuesFromExistingBytes(): Unit = runBlocking {
        val data = Random.nextBytes(80_000)
        server(data, true).use { s ->
            val dest = File(tmp.root, "out.bin")
            dest.writeBytes(data.copyOfRange(0, 40_000))
            HttpDownloader().download(DownloadSpec(s.url("/f").toString(), dest, connections = 4)).toList()
            assertThat(dest.readBytes()).isEqualTo(data)
        }
    }

    @Test fun nonRangeServer_singleStreamFallback(): Unit = runBlocking {
        val data = Random.nextBytes(50_000)
        server(data, false).use { s ->
            val dest = File(tmp.root, "out.bin")
            val probe = HttpDownloader().probe(s.url("/f").toString())
            assertThat(probe.resumable).isFalse()
            HttpDownloader().download(DownloadSpec(s.url("/f").toString(), dest, connections = 4)).toList()
            assertThat(dest.readBytes()).isEqualTo(data)
        }
    }

    @Test fun completeFile_skipsDownload(): Unit = runBlocking {
        val data = Random.nextBytes(10_000)
        server(data, true).use { s ->
            val dest = File(tmp.root, "out.bin")
            dest.writeBytes(data)
            HttpDownloader().download(DownloadSpec(s.url("/f").toString(), dest)).toList()
            assertThat(s.requestCount).isEqualTo(1) // probe only
            assertThat(dest.readBytes()).isEqualTo(data)
        }
    }
}
