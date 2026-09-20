package com.elejar.ZentraDL.engine.http

import java.net.URLDecoder

/** Pure helpers (unit-tested, no I/O). All ranges are inclusive. */
object HttpSupport {

    /**
     * Split [startOffset, totalBytes) into at most [connections] contiguous ranges.
     * Remainder bytes go to the earliest segments. Each segment gets >= 1 byte.
     */
    fun splitRanges(totalBytes: Long, connections: Int, startOffset: Long = 0L): List<LongRange> {
        require(totalBytes > startOffset) { "nothing to split" }
        val remaining = totalBytes - startOffset
        val n = connections.coerceIn(1, 32).coerceAtMost(remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        val base = remaining / n
        val extra = (remaining % n).toInt()
        var cursor = startOffset
        return List(n) { i ->
            val size = base + if (i < extra) 1 else 0
            val range = cursor until cursor + size
            cursor += size
            range
        }
    }

    /** Filename from Content-Disposition (plain + RFC 5987 *), else URL path, sanitized. */
    fun parseFileName(contentDisposition: String?, url: String): String {
        var name: String? = null
        if (contentDisposition != null) {
            val encoded = Regex("""filename\*\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE).find(contentDisposition)
            if (encoded != null) {
                val v = encoded.groupValues[1].trim().trim('"')
                val payload = v.substringAfter("''", missingDelimiterValue = v)
                name = runCatching { URLDecoder.decode(payload, "UTF-8") }.getOrNull()
            }
            if (name == null) {
                val plain = Regex("""filename\s*=\s*"?([^";]+)"?""", RegexOption.IGNORE_CASE).find(contentDisposition)
                if (plain != null) name = plain.groupValues[1].trim()
            }
        }
        if (name.isNullOrBlank()) {
            name = runCatching {
                URLDecoder.decode(url.substringAfterLast('/').substringBefore('?'), "UTF-8")
            }.getOrNull()
        }
        var clean = (name ?: "").replace("[\\\\/:*?\"<>|\\p{Cntrl}]".toRegex(), "_").trim().trimEnd('.', ' ')
        if (clean.isBlank()) clean = "download"
        return clean
    }
}

/** Exponential-moving-average speed. "Calculating…" until [stable]. */
class SpeedEma(private val alpha: Double = 0.3) {
    private var ema: Double = 0.0
    private var samples: Int = 0

    fun addSample(bytes: Long, elapsedMs: Long) {
        if (elapsedMs <= 0 || bytes < 0) return
        val rate = bytes * 1000.0 / elapsedMs
        ema = if (samples == 0) rate else alpha * rate + (1 - alpha) * ema
        samples++
    }

    val stable: Boolean get() = samples >= 3
    val bytesPerSecond: Long get() = ema.toLong()
}
