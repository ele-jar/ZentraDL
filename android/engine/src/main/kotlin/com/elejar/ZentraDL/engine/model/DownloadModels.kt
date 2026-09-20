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
    /** Per-connection segment states (empty when unknown/single untracked). */
    val segments: List<SegmentState> = emptyList(),
) {
    val percent: Int
        get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else -1
}

/** One connection's byte range and its progress. Ranges are inclusive. */
data class SegmentState(
    val index: Int,
    val beginByte: Long,
    val endByte: Long,
    val downloadedBytes: Long,
    val retries: Int,
)

/** Transfer engine seam (HttpDownloader is the first implementation). */
interface Downloader {
    suspend fun probe(url: String, headers: Map<String, String>): ResourceInfo
    fun download(spec: DownloadSpec): Flow<DownloadProgress>
}
