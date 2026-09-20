package com.elejar.ZentraDL.engine.torrent

import bt.net.InetPeerAddress
import java.io.File
import java.util.BitSet

/** Torrent metadata (parsed .torrent or fetched magnet metadata). Pure JVM. */
data class TorrentMeta(
    /** Lowercase hex info-hash — doubles as our task id. */
    val idHex: String,
    val name: String,
    val sizeBytes: Long,
    val pieceLength: Long,
    val pieceCount: Int,
    val files: List<TorrentFileMeta>,
    val trackers: List<String>,
    val isPrivate: Boolean,
    val createdBy: String?,
    /** Original magnet (null when added from .torrent bytes). */
    val magnetUri: String?,
)

/** One file inside the torrent; [path] uses "/" separators. */
data class TorrentFileMeta(val path: String, val size: Long)

/** Parsed magnet link (offline — no swarm contact). */
data class MagnetRef(
    val idHex: String,
    val displayName: String?,
    val trackers: List<String>,
    val peers: List<InetPeerAddress>,
)

/** Metadata plus the raw exchanged bytes (null when we already have them). */
data class FetchedMeta(val meta: TorrentMeta, val rawBytes: ByteArray?)

/** What to download. */
data class TorrentDownloadSpec(
    val magnet: String?,
    /** Raw .torrent bytes (used only when no magnet is known). */
    val torrentBytes: ByteArray?,
    val saveDir: File,
    /** Subset of file paths (as in [TorrentFileMeta.path]); null = all. */
    val selectedPaths: Set<String>? = null,
    val sequential: Boolean = false,
    /** Known piece length (from the add-flow metadata); -1 = unknown yet. */
    val pieceLength: Long = -1,
)

/** Live counters for one session. Rates are sampled by the wrapper. */
enum class TorrentRunState { FETCHING, DOWNLOADING, SEEDING, PAUSED, FAILED }

data class TorrentStats(
    val downloadedBytes: Long,
    val uploadedBytes: Long,
    val leftBytes: Long,
    val downRate: Long,
    val upRate: Long,
    val piecesTotal: Int,
    val piecesComplete: Int,
    val peers: Int,
    val state: TorrentRunState,
)

/** One connected peer (public API has no per-peer counters or client names). */
data class TorrentPeer(val address: String, val port: Int, val peerIdHex: String?)

/** 1 Hz live snapshot for the details screen (PM1 data shape: RLE, never full arrays). */
data class TorrentLive(
    val stats: TorrentStats?,
    val error: String?,
    val peers: List<TorrentPeer>,
    val pieces: TorrentPieceMap?,
)

/** Piece states: 0 = missing, 1 = complete, 2 = skipped. */
data class PieceRun(val state: Int, val count: Int)

data class TorrentPieceMap(
    val total: Int,
    val pieceLength: Long,
    val runs: List<PieceRun>,
) {
    val complete: Int get() = runs.filter { it.state == 1 }.sumOf { it.count }
}

/** RLE over per-piece states (PM1 data shape: runs, never full arrays per tick). */
object PieceRuns {
    fun build(total: Int, complete: BitSet, skipped: BitSet): List<PieceRun> {
        if (total <= 0) return emptyList()
        val runs = mutableListOf<PieceRun>()
        var cur = -1
        var n = 0
        for (i in 0 until total) {
            val s = when {
                complete.get(i) -> 1
                skipped.get(i) -> 2
                else -> 0
            }
            if (s == cur) {
                n++
            } else {
                if (cur >= 0) runs += PieceRun(cur, n)
                cur = s
                n = 1
            }
        }
        if (cur >= 0) runs += PieceRun(cur, n)
        return runs
    }
}
