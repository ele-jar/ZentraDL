package com.elejar.ZentraDL.engine.http

import com.elejar.ZentraDL.engine.model.DownloadProgress
import com.elejar.ZentraDL.engine.model.DownloadSpec
import com.elejar.ZentraDL.engine.model.ResourceInfo
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

/**
 * Pure-JVM multi-part HTTP downloader (P1 walking skeleton).
 *
 * Resume = file-length based (validated range re-request); ETag/size validation
 * and persisted segment maps are Phase 3. Retry = 3 attempts linear backoff (P3:
 * none/N/unlimited + jitter). Progress throttled to ~4 Hz.
 */
class HttpDownloader(
    private val client: OkHttpClient = defaultHttpClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Pre-check: final URL, filename, size, MIME, resumable (H4). */
    suspend fun probe(url: String, headers: Map<String, String> = emptyMap()): ResourceInfo {
        val req = Request.Builder().url(url).header("Range", "bytes=0-0").apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        // Network on caller's dispatcher; callers (Flow below) already confine to IO.
        val resp = client.newCall(req).execute()
        resp.use {
            val finalUrl = it.request.url.toString()
            val disposition = it.header("Content-Disposition")
            return when (it.code) {
                206 -> {
                    val total = it.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L
                    ResourceInfo(
                        finalUrl = finalUrl,
                        fileName = HttpSupport.parseFileName(disposition, finalUrl),
                        totalBytes = total,
                        mimeType = it.header("Content-Type")?.substringBefore(';')?.trim(),
                        resumable = true,
                    )
                }
                in 200..299 -> {
                    val total = it.header("Content-Length")?.toLongOrNull() ?: -1L
                    ResourceInfo(
                        finalUrl = finalUrl,
                        fileName = HttpSupport.parseFileName(disposition, finalUrl),
                        totalBytes = total,
                        mimeType = it.header("Content-Type")?.substringBefore(';')?.trim(),
                        resumable = false,
                    )
                }
                else -> throw IOException("probe failed: HTTP ${it.code} for $url")
            }
        }
    }

    /** Cold flow: emits ~4 Hz progress, completes at 100%. Throws IOException on failure. */
    fun download(spec: DownloadSpec): Flow<DownloadProgress> = channelFlow {
        val info = withContext(ioDispatcher) { probe(spec.url, spec.headers) }
        val dest = spec.destFile
        dest.parentFile?.mkdirs()
        val total = info.totalBytes
        val existing = if (dest.exists()) dest.length() else 0L
        if (total > 0 && existing == total) {
            send(DownloadProgress(total, total, 0))
            return@channelFlow
        }
        val resumeFrom = if (info.resumable && total > 0 && existing > 0 && existing < total) existing else 0L
        if (resumeFrom == 0L && dest.exists()) dest.delete()

        val downloaded = AtomicLong(resumeFrom)
        val ema = SpeedEma()
        fun snapshot() = DownloadProgress(downloaded.get(), total, if (ema.stable) ema.bytesPerSecond else 0L)

        val ticker = launch {
            while (true) {
                delay(250)
                send(snapshot())
            }
        }
        try {
            if (info.resumable && total > 0) {
                RandomAccessFile(dest, "rw").use { it.setLength(total) }
                val ranges = HttpSupport.splitRanges(total, spec.connections, resumeFrom)
                ranges.map { range ->
                    async(ioDispatcher) { fetchRange(spec, range, dest, downloaded, ema) }
                }.awaitAll()
            } else {
                val req = requestFor(spec, if (resumeFrom > 0) resumeFrom else null)
                withContext(ioDispatcher) { fetchSingle(req, dest, resumeFrom > 0, downloaded, ema) }
            }
            ticker.cancelAndJoin()
            send(DownloadProgress(downloaded.get(), total, ema.bytesPerSecond))
        } finally {
            ticker.cancel()
        }
    }.buffer(Channel.CONFLATED)

    private fun requestFor(spec: DownloadSpec, from: Long?): Request {
        val b = Request.Builder().url(spec.url)
        spec.headers.forEach { (k, v) -> b.header(k, v) }
        if (from != null) b.header("Range", "bytes=$from-")
        return b.build()
    }

    private fun requestForRange(spec: DownloadSpec, range: LongRange): Request {
        val b = Request.Builder().url(spec.url)
        spec.headers.forEach { (k, v) -> b.header(k, v) }
        b.header("Range", "bytes=${range.first}-${range.last}")
        return b.build()
    }

    private suspend fun fetchRange(
        spec: DownloadSpec,
        range: LongRange,
        dest: java.io.File,
        downloaded: AtomicLong,
        ema: SpeedEma,
    ) = retryIo {
        client.newCall(requestForRange(spec, range)).execute().use { resp ->
            if (resp.code != 206) throw IOException("range request failed: HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("empty body")
            RandomAccessFile(dest, "rw").use { raf ->
                raf.seek(range.first)
                copyInto(body.byteStream(), raf, downloaded, ema)
            }
        }
    }

    private suspend fun fetchSingle(
        req: Request,
        dest: java.io.File,
        append: Boolean,
        downloaded: AtomicLong,
        ema: SpeedEma,
    ) = retryIo {
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("download failed: HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("empty body")
            FileOutputStream(dest, append).use { out ->
                val buf = ByteArray(8192)
                var last = System.nanoTime()
                while (true) {
                    val n = body.byteStream().read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    val now = System.nanoTime()
                    ema.addSample(n.toLong(), (now - last) / 1_000_000)
                    last = now
                    downloaded.addAndGet(n.toLong())
                }
            }
        }
    }

    private fun copyInto(
        input: java.io.InputStream,
        raf: RandomAccessFile,
        downloaded: AtomicLong,
        ema: SpeedEma,
    ) {
        input.use {
            val buf = ByteArray(8192)
            var last = System.nanoTime()
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                raf.write(buf, 0, n)
                val now = System.nanoTime()
                ema.addSample(n.toLong(), (now - last) / 1_000_000)
                last = now
                downloaded.addAndGet(n.toLong())
            }
        }
    }

    private suspend fun retryIo(times: Int = 3, block: suspend () -> Unit) {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: IOException) {
                attempt++
                if (attempt >= times) throw e
                delay(1000L * attempt)
            }
        }
    }
}
