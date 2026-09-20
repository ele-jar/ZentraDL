package com.elejar.ZentraDL.engine.http

import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import com.elejar.ZentraDL.engine.model.DownloadProgress

/** One HLS variant (EXT-X-STREAM-INF). */
data class HlsVariant(val bandwidthBps: Long, val resolution: String?, val url: String)

/** What to download: a media playlist + its segments concatenated (.ts). */
data class HlsSpec(
    val mediaPlaylistUrl: String,
    val destFile: File,
    /** From the variant (bits/s); estimates total bytes. 0 = unknown. */
    val bandwidthBps: Long = 0,
    val headers: Map<String, String> = emptyMap(),
)

private data class HlsKey(val bytes: ByteArray, val iv: ByteArray)
private data class HlsSegment(val url: String, val durationSec: Double, val key: HlsKeyRef?)
private data class HlsKeyRef(val uri: String, val ivHex: String?)

/**
 * HLS downloader (P5b: variant playlists, TS segments, AES-128).
 *
 * Sequential segment fetch (correctness first); no resume (restart);
 * single key scope per playlist version (key changes re-fetch). DASH
 * fragmented-mp4 merge is NOT attempted (documented gap).
 */
class HlsDownloader(
    private val client: OkHttpClient = defaultHttpClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Variant list, or a single entry when the URL is already a media playlist. */
    suspend fun variants(playlistUrl: String, headers: Map<String, String> = emptyMap()): List<HlsVariant> {
        val text = getText(playlistUrl, headers)
        val lines = text.lines()
        val out = mutableListOf<HlsVariant>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val attrs = parseAttrs(line.substringAfter(':'))
                val uri = lines.getOrNull(i + 1)?.trim().orEmpty()
                if (uri.isNotEmpty() && !uri.startsWith("#")) {
                    out += HlsVariant(
                        bandwidthBps = attrs["BANDWIDTH"]?.toLongOrNull() ?: 0,
                        resolution = attrs["RESOLUTION"],
                        url = resolve(playlistUrl, uri),
                    )
                }
                i += 2
            } else {
                i++
            }
        }
        return out.ifEmpty { listOf(HlsVariant(0, null, playlistUrl)) }
    }

    fun download(spec: HlsSpec): Flow<DownloadProgress> = flow {
        val text = getText(spec.mediaPlaylistUrl, spec.headers)
        val (segments, mediaSeq) = parseSegments(text, spec.mediaPlaylistUrl)
        if (segments.isEmpty()) throw java.io.IOException("no segments in playlist")
        val totalDur = segments.sumOf { it.durationSec }
        val estTotal = if (spec.bandwidthBps > 0 && totalDur > 0) {
            (spec.bandwidthBps * totalDur / 8).toLong()
        } else {
            -1L
        }
        spec.destFile.parentFile?.mkdirs()
        var done = 0L
        val start = System.nanoTime()
        val keys = mutableMapOf<String, ByteArray>()
        spec.destFile.outputStream().use { out ->
            segments.forEachIndexed { i, seg ->
                val raw = getBytes(seg.url, spec.headers)
                val plain = seg.key?.let { ref ->
                    val keyBytes = keys.getOrPut(ref.uri) { getBytes(ref.uri, spec.headers) }
                    aesDecrypt(raw, keyBytes, ivFor(ref.ivHex, mediaSeq + i))
                } ?: raw
                out.write(plain)
                done += plain.size
                val elapsed = (System.nanoTime() - start).toDouble() / 1e9
                val rate = if (elapsed > 0.2) (done / elapsed).toLong() else 0L
                emit(DownloadProgress(done, estTotal, rate))
            }
        }
        emit(DownloadProgress(done, done, 0))
    }.flowOn(ioDispatcher)

    private fun parseSegments(text: String, base: String): Pair<List<HlsSegment>, Int> {
        val lines = text.lines()
        var seq = 0
        lines.forEach { line ->
            val t = line.trim()
            if (t.startsWith("#EXT-X-MEDIA-SEQUENCE")) {
                seq = t.substringAfter(':').trim().toIntOrNull() ?: 0
            }
        }
        val segs = mutableListOf<HlsSegment>()
        var key: HlsKeyRef? = null
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            when {
                line.startsWith("#EXT-X-KEY") -> {
                    val attrs = parseAttrs(line.substringAfter(':'))
                    val method = attrs["METHOD"]
                    val uri = attrs["URI"]?.trim('"')
                    key = if (method == "AES-128" && uri != null) {
                        HlsKeyRef(resolve(base, uri), attrs["IV"])
                    } else {
                        null
                    }
                    i++
                }
                line.startsWith("#EXTINF") -> {
                    val dur = line.substringAfter(':').substringBefore(',').trim().toDoubleOrNull() ?: 0.0
                    val uri = lines.getOrNull(i + 1)?.trim().orEmpty()
                    if (uri.isNotEmpty() && !uri.startsWith("#")) {
                        segs += HlsSegment(resolve(base, uri), dur, key)
                    }
                    i += 2
                }
                else -> i++
            }
        }
        return segs to seq
    }

    private fun parseAttrs(s: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        Regex("""([A-Z0-9-]+)=("[^"]*"|[^,]*)""").findAll(s).forEach { m ->
            out[m.groupValues[1]] = m.groupValues[2]
        }
        return out
    }

    private fun resolve(base: String, relative: String): String {
        if (relative.startsWith("http://") || relative.startsWith("https://")) return relative
        return java.net.URL(java.net.URL(base), relative).toString()
    }

    private fun ivFor(ivHex: String?, seq: Int): ByteArray {
        if (ivHex != null) {
            val hex = ivHex.removePrefix("0x").removePrefix("0X")
            require(hex.length == 32) { "bad IV" }
            return ByteArray(16) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        }
        return ByteArray(16).also { it[15] = (seq and 0xFF).toByte(); it[14] = ((seq shr 8) and 0xFF).toByte() }
    }

    private fun aesDecrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val out = cipher.doFinal(data)
        val pad = out.last().toInt() and 0xFF
        require(pad in 1..16) { "bad PKCS7 padding" }
        return out.copyOf(out.size - pad)
    }

    private fun requestFor(url: String, headers: Map<String, String>): Request {
        val b = Request.Builder().url(url)
        headers.forEach { (k, v) -> b.header(k, v) }
        return b.build()
    }

    private fun getText(url: String, headers: Map<String, String>): String {
        client.newCall(requestFor(url, headers)).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code} for $url")
            return resp.body.string()
        }
    }

    private fun getBytes(url: String, headers: Map<String, String>): ByteArray {
        client.newCall(requestFor(url, headers)).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code} for $url")
            return resp.body.bytes()
        }
    }
}
