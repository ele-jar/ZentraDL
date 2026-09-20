package com.elejar.ZentraDL.engine.torrent

import bt.Bt
import bt.data.file.FileSystemStorage
import bt.magnet.MagnetUriParser
import bt.metainfo.IMetadataService
import bt.metainfo.MetadataService
import bt.metainfo.Torrent
import bt.metainfo.TorrentFile
import bt.metainfo.TorrentId
import bt.protocol.Protocols
import bt.runtime.BtClient
import bt.runtime.BtRuntime
import bt.runtime.Config
import bt.torrent.TorrentRegistry
import bt.torrent.TorrentSessionState
import bt.torrent.fileselector.FilePriority
import bt.torrent.fileselector.UpdatedFilePriority
import bt.tracker.http.HttpTrackerModule
import bt.dht.DHTConfig
import bt.dht.DHTModule
import java.io.File
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Thrown when magnet metadata doesn't arrive in time (T11: "Couldn't fetch torrent info"). */
class MetadataTimeoutException(magnet: String) : Exception("Couldn't fetch torrent info in time for $magnet")

data class BtOptions(
    val acceptorPort: Int = 6891,
    /** Bind address override (tests use 127.0.0.1; default = first non-loopback NIC). */
    val bindHost: String? = null,
    val dhtPort: Int = 49001,
    val maxPeersPerTorrent: Int = 50,
    val hashingThreads: Int = 1,
    val enableDht: Boolean = true,
)

/**
 * JVM torrent engine over atomashpolskiy/bt 1.10 (P4a).
 *
 * One shared [BtRuntime] (single DHT socket) with automatic shutdown DISABLED:
 * bare `BtClient.stop()` only detaches, so fetch-temp clients and pause/resume
 * are safe. Finished clients keep seeding until [BtSession.stop]. Paused
 * sessions resume on the SAME client (stop empties the guards; restart
 * re-verifies automatically). Rates are sampled here — bt exposes totals only.
 * No Android imports (JVM-testable).
 */
class BtEngine(
    private val opts: BtOptions = BtOptions(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val metaService: IMetadataService = MetadataService()
    private var runtime: BtRuntime? = null

    @Synchronized
    fun start() {
        if (runtime != null) return
        val config = Config()
        config.acceptorPort = opts.acceptorPort
        if (opts.bindHost != null) config.acceptorAddress = java.net.InetAddress.getByName(opts.bindHost)
        config.maxPeerConnectionsPerTorrent = opts.maxPeersPerTorrent
        config.numOfHashingThreads = opts.hashingThreads
        val builder = BtRuntime.builder(config)
            .disableLocalServiceDiscovery()
            .disableAutomaticShutdown()
        if (opts.enableDht) {
            val dht = DHTConfig()
            dht.listeningPort = opts.dhtPort
            dht.setShouldUseRouterBootstrap(true)
            builder.module(DHTModule(dht))
        }
        runtime = builder.module(HttpTrackerModule()).build().also { it.startup() }
    }

    @Synchronized
    fun shutdown() {
        runtime?.shutdown()
        runtime = null
    }

    /** Offline magnet parse (no swarm contact). Throws IllegalArgumentException on garbage. */
    fun parseMagnet(magnet: String): MagnetRef {
        val uri = MagnetUriParser.lenientParser().parse(magnet)
        return MagnetRef(
            idHex = uri.torrentId.toString(),
            displayName = uri.displayName.orElse(null),
            trackers = uri.trackerUrls.toList(),
            peers = uri.peerAddresses.toList(),
        )
    }

    /** Offline .torrent parse. */
    fun parseTorrentBytes(bytes: ByteArray): TorrentMeta = toMeta(metaService.fromByteArray(bytes), null)

    /**
     * Fetch magnet metadata (own timeout; temp client detached after). Returns the
     * exchanged bytes too, so restarts don't re-fetch. Throws [MetadataTimeoutException].
     */
    suspend fun fetchMetadata(magnet: String, timeoutMs: Long = 60_000): FetchedMeta =
        withContext(ioDispatcher) {
            val rt = runtime ?: throw IllegalStateException("BtEngine not started")
            val latch = CompletableDeferred<FetchedMeta>()
            val tmp = File(System.getProperty("java.io.tmpdir"), "zdl-meta-${System.nanoTime()}")
            val client = Bt.client(rt)
                .storage(FileSystemStorage(tmp))
                .magnet(magnet)
                .afterTorrentFetched { t ->
                    latch.complete(FetchedMeta(toMeta(t, magnet), t.source.exchangedMetadata))
                }
                .build()
            client.startAsync()
            try {
                withTimeout(timeoutMs) { latch.await() }
            } catch (e: TimeoutCancellationException) {
                throw MetadataTimeoutException(magnet)
            } finally {
                runCatching { client.stop() }
            }
        }

    /** Start (or resume — existing data is re-verified automatically) a download. */
    fun download(spec: TorrentDownloadSpec): BtSession {
        val rt = runtime ?: throw IllegalStateException("BtEngine not started")
        val builder = Bt.client(rt).storage(FileSystemStorage(spec.saveDir))
        if (spec.torrentBytes != null) {
            val bytes = spec.torrentBytes
            builder.torrent { metaService.fromByteArray(bytes) }
        } else {
            builder.magnet(requireNotNull(spec.magnet) { "magnet or torrentBytes required" })
        }
        if (spec.sequential) builder.sequentialSelector()
        val wanted = spec.selectedPaths
        if (wanted != null) {
            builder.fileSelector { f ->
                if (joinPath(f) in wanted) FilePriority.NORMAL_PRIORITY else FilePriority.SKIP
            }
        }
        val client = builder.build()
        val idHex = spec.torrentBytes?.let { toMeta(metaService.fromByteArray(it), spec.magnet).idHex }
            ?: parseMagnet(requireNotNull(spec.magnet)).idHex
        val pieceLength = spec.torrentBytes?.let { metaService.fromByteArray(it).chunkSize } ?: -1L
        return BtSession(rt, client, idHex, pieceLength)
    }

    private fun toMeta(t: Torrent, magnet: String?): TorrentMeta {
        val key = t.announceKey.orElse(null)
        val trackers = when {
            key == null -> emptyList()
            key.isMultiKey -> key.trackerUrls.flatten()
            key.trackerUrl != null -> listOf(key.trackerUrl)
            else -> emptyList()
        }
        return TorrentMeta(
            idHex = t.torrentId.toString(),
            name = t.name,
            sizeBytes = t.size,
            pieceLength = t.chunkSize,
            pieceCount = t.chunkHashes.count(),
            files = t.files.map { TorrentFileMeta(joinPath(it), it.size) },
            trackers = trackers,
            isPrivate = t.isPrivate,
            createdBy = t.createdBy.orElse(null),
            magnetUri = magnet,
        )
    }

    private fun joinPath(f: TorrentFile): String = f.pathElements.joinToString("/")
}

/** Live handle for one torrent (stats flow, pieces, peers, priority, stop/resume). */
class BtSession internal constructor(
    private val runtime: BtRuntime,
    private val client: BtClient,
    val idHex: String,
    private val pieceLength: Long,
) {
    private val _stats = MutableStateFlow(
        TorrentStats(0, 0, -1, 0, 0, 0, 0, 0, TorrentRunState.FETCHING),
    )
    val stats: StateFlow<TorrentStats> = _stats.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    @Volatile private var lastState: TorrentSessionState? = null
    @Volatile private var prevDl = -1L
    @Volatile private var prevUp = -1L
    @Volatile private var prevAt = -1L
    @Volatile private var stopped = false

    init {
        startListening()
    }

    private fun startListening() {
        val future: CompletableFuture<*> = client.startAsync({ s -> onState(s) }, 1000L)
        future.whenComplete { _, e ->
            if (e != null && !stopped) {
                val msg = e.message ?: e.toString()
                _error.value = msg
                _stats.value = _stats.value.copy(state = TorrentRunState.FAILED)
            }
        }
    }

    private fun onState(s: TorrentSessionState) {
        if (stopped) return
        lastState = s
        val now = System.nanoTime()
        val dl = s.downloaded
        val up = s.uploaded
        val downRate = rate(dl, prevDl, now, prevAt)
        val upRate = rate(up, prevUp, now, prevAt)
        prevDl = dl
        prevUp = up
        prevAt = now
        val left = s.left
        _stats.value = TorrentStats(
            downloadedBytes = dl,
            uploadedBytes = up,
            leftBytes = left,
            downRate = downRate,
            upRate = upRate,
            piecesTotal = s.piecesTotal,
            piecesComplete = s.piecesComplete,
            peers = s.connectedPeers.size,
            state = when {
                left == TorrentSessionState.UNKNOWN -> TorrentRunState.FETCHING
                s.piecesRemaining == 0 -> TorrentRunState.SEEDING
                else -> TorrentRunState.DOWNLOADING
            },
        )
    }

    private fun rate(now: Long, prev: Long, at: Long, prevAt: Long): Long {
        if (prev < 0 || prevAt < 0 || at <= prevAt) return 0
        return ((now - prev) * 1e9 / (at - prevAt)).toLong().coerceAtLeast(0)
    }

    /** RLE piece map (null until the data descriptor exists). Skipped pieces included. */
    fun pieceMap(): TorrentPieceMap? {
        val id = TorrentId.fromBytes(Protocols.fromHex(idHex))
        val descriptor = runtime.service(TorrentRegistry::class.java).getDescriptor(id).orElse(null)
            ?: return null
        val bitfield = descriptor.dataDescriptor?.bitfield ?: return null
        return TorrentPieceMap(
            total = bitfield.piecesTotal,
            pieceLength = pieceLength,
            runs = PieceRuns.build(bitfield.piecesTotal, bitfield.bitmask, bitfield.skippedBitmask),
        )
    }

    fun peers(): List<TorrentPeer> = (lastState?.connectedPeers.orEmpty()).map { key ->
        val p = key.peer
        TorrentPeer(
            address = p.inetAddress?.hostAddress ?: "?",
            port = if (!p.isPortUnknown) p.port else key.remotePort,
            peerIdHex = p.peerId.map { it.toString() }.orElse(null),
        )
    }

    /** Raise files to high priority mid-download (un-skipping is NOT supported by bt). */
    fun setHighPriority(paths: Set<String>): Boolean =
        client.updateFilePriorities { f ->
            if (joinPath(f) in paths) UpdatedFilePriority.HIGH_PRIORITY else UpdatedFilePriority.NORMAL_PRIORITY
        }

    /** Pause (data kept; [resume] restarts on the same client). */
    fun stop() {
        stopped = true
        runCatching { client.stop() }
        _stats.value = _stats.value.copy(state = TorrentRunState.PAUSED)
    }

    /** Resume a stopped session. */
    fun resume() {
        stopped = false
        _error.value = null
        startListening()
    }

    private fun joinPath(f: TorrentFile): String = f.pathElements.joinToString("/")
}
