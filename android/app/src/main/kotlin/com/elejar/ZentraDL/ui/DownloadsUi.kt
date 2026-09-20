package com.elejar.ZentraDL.ui

import com.elejar.ZentraDL.data.local.TaskRecord
import com.elejar.ZentraDL.designsystem.components.TaskStatus
import com.elejar.ZentraDL.engine.model.DownloadProgress

/** Pure list logic (unit-tested). The screen only renders [ListItem]s. */
object DownloadsUi {

    enum class StatusFilter { All, Active, Queued, Paused, Completed, Failed }

    enum class SortMode { Date, Name, Size, Progress, Speed }

    data class RowUi(
        val record: TaskRecord,
        val status: TaskStatus,
        val progress: DownloadProgress?,
        val percent: Int?,
        /** Pre-formatted meta line (tabular figures, no jitter). */
        val meta: String,
        val semanticsSummary: String,
    )

    sealed interface ListItem {
        data class Header(val title: String) : ListItem
        data class Row(val row: RowUi) : ListItem
    }

    fun mapStatus(dbStatus: String): TaskStatus = when (dbStatus) {
        "downloading" -> TaskStatus.Downloading
        "queued" -> TaskStatus.Queued
        "paused" -> TaskStatus.Paused
        "completed" -> TaskStatus.Completed
        else -> TaskStatus.Failed
    }

    fun metaFor(record: TaskRecord, p: DownloadProgress?): String {
        if (p == null || record.status == "completed") {
            val size = if (record.totalBytes > 0) Format.bytes(record.totalBytes) else "Unknown size"
            val whenText = Format.dayTime(record.createdAt)
            return "$size · $whenText"
        }
        val total = if (p.totalBytes > 0) Format.bytes(p.totalBytes) else "Unknown size"
        val speed = Format.speed(p.bytesPerSecond, p.bytesPerSecond > 0)
        val eta = Format.eta(p.totalBytes - p.downloadedBytes, p.bytesPerSecond)
        return "${Format.bytes(p.downloadedBytes)} of $total · $speed" + (if (eta.isNotEmpty()) " · $eta" else "")
    }

    fun buildList(
        records: List<TaskRecord>,
        progress: Map<String, DownloadProgress>,
        query: String,
        filter: StatusFilter,
        sort: SortMode,
        nowMs: Long = System.currentTimeMillis(),
        categoryId: String? = null,
    ): List<ListItem> {
        val q = query.trim().lowercase()
        val rows = records.mapNotNull { rec ->
            val status = mapStatus(rec.status)
            if (!matchesFilter(status, filter)) return@mapNotNull null
            if (categoryId != null && rec.categoryId != categoryId) return@mapNotNull null
            if (q.isNotEmpty() && !rec.fileName.lowercase().contains(q) && !rec.url.lowercase().contains(q)) {
                return@mapNotNull null
            }
            val p = progress[rec.id]
            RowUi(rec, status, p, p?.percent?.takeIf { p.totalBytes > 0 }, metaFor(rec, p), summaryFor(rec, p))
        }
        val sorted = when (sort) {
            SortMode.Name -> rows.sortedBy { it.record.fileName.lowercase() }
            SortMode.Size -> rows.sortedByDescending { it.record.totalBytes }
            SortMode.Progress -> rows.sortedByDescending { it.percent ?: -1 }
            SortMode.Speed -> rows.sortedByDescending { it.progress?.bytesPerSecond ?: -1 }
            SortMode.Date -> rows.sortedByDescending { it.record.createdAt }
        }
        // Active section first, then completed grouped by day.
        val (active, done) = sorted.partition { it.status != TaskStatus.Completed }
        val out = mutableListOf<ListItem>()
        if (active.isNotEmpty()) {
            out += ListItem.Header("Active")
            active.sortedBy { activeOrder(it.status) }.forEach { out += ListItem.Row(it) }
        }
        var lastBucket: String? = null
        done.forEach {
            val bucket = Format.dayBucket(it.record.createdAt, nowMs)
            if (bucket != lastBucket) {
                out += ListItem.Header(bucket)
                lastBucket = bucket
            }
            out += ListItem.Row(it)
        }
        return out
    }

    private fun activeOrder(s: TaskStatus): Int = when (s) {
        TaskStatus.Downloading -> 0
        TaskStatus.Failed -> 1
        TaskStatus.Queued -> 2
        TaskStatus.Paused -> 3
        else -> 4
    }

    private fun matchesFilter(status: TaskStatus, filter: StatusFilter): Boolean = when (filter) {
        StatusFilter.All -> true
        StatusFilter.Active -> status == TaskStatus.Downloading
        StatusFilter.Queued -> status == TaskStatus.Queued
        StatusFilter.Paused -> status == TaskStatus.Paused
        StatusFilter.Completed -> status == TaskStatus.Completed
        StatusFilter.Failed -> status == TaskStatus.Failed
    }

    private fun summaryFor(record: TaskRecord, p: DownloadProgress?): String {
        if (p == null) return "${record.fileName}, ${record.status}"
        val total = if (p.totalBytes > 0) Format.bytes(p.totalBytes) else "unknown size"
        return "${p.percent} percent, ${Format.bytes(p.downloadedBytes)} of $total, ${Format.speed(p.bytesPerSecond, true)}"
    }
}
