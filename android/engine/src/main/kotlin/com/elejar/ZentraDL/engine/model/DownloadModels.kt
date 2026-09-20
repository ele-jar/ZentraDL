package com.elejar.ZentraDL.engine.model

import java.io.File
import kotlinx.coroutines.flow.Flow

/** What to download. Pure JVM — no Android imports in :engine. */
data class DownloadSpec(
    val url: String,
    val destFile: File,
    val headers: Map<String, String> = emptyMap(),
    /** 1..32, default 8. Ignored when the server doesn't support ranges. */
    val connections: Int = 8,
)

/** Result of the pre-check probe (H4). */
data class ResourceInfo(
    val finalUrl: String,
    val fileName: String,
    /** Total bytes, or -1 when unknown. */
    val totalBytes: Long,
    val mimeType: String?,
    val resumable: Boolean,
)

/** Progress snapshot. Throttled to ~4 Hz by the downloader. */
data class DownloadProgress(
    val downloadedBytes: Long,
    /** Total bytes, or -1 when unknown. */
    val totalBytes: Long,
    val bytesPerSecond: Long,
) {
    val percent: Int
        get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else -1
}

/** Transfer engine seam (HttpDownloader is the first implementation). */
interface Downloader {
    suspend fun probe(url: String, headers: Map<String, String> = emptyMap()): ResourceInfo
    fun download(spec: DownloadSpec): Flow<DownloadProgress>
}
