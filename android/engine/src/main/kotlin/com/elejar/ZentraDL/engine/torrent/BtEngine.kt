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
import java.util.concurrent.atomic.AtomicInteger
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
    /** Base ports; each runtime takes base + an allocated offset (never shared). */
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
 * One standalone runtime per torrent session. bt reuses data descriptors per
 * torrent id within a runtime and unregisters them async on stop, which made
 * shared-runtime pause/resume/fetch a race farm (stale bans, poisoned
 * descriptors, auto-shutdown cross-talk). Isolated runtimes make every
 * lifecycle deterministic: stop tears everything down, resume rebuilds
 * (existing data is re-verified automatically). Finished clients keep seeding
 * until [BtSession.stop]. Rates are sampled here — bt exposes totals only.
 * No Android imports (JVM-testable).
 */
class BtEngine(
    @Volatile var opts: BtOptions = BtOptions(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val metaService: IMetadataService = MetadataService()
    private val portOffset = AtomicInteger(0)

    private fun buildRuntime(): BtRuntime {
        var last: Exception? = null
        repeat(10) {
            val off = portOffset.getAndIncrement()
            var rt: BtRuntime? = null
            try {
                rt = buildOnce(opts.acceptorPort + off, opts.dhtPort + off)
                rt.startup()
                return rt
            } catch (e: Exception) {
                last = e
                runCatching { rt?.shutdown() }
            }
        }
        throw last ?: IllegalStateException("no free ports for torrent runtime")
    }

    private fun buildOnce(acceptorPort: Int, dhtPort: Int): BtRuntime {
        val config = Config()
        config.acceptorPort = acceptorPort
        if (opts.bindHost != null) config.acceptorAddress = java.net.InetAddress.getByName(opts.bindHost)
        config.maxPeerConnectionsPerTorrent = opts.maxPeersPerTorrent
        config.numOfHashingThreads = opts.hashingThreads
        val builder = BtRuntime.builder(config)
            .disableLocalServiceDiscovery()
            .disableAutomaticShutdown()
        if (opts.enableDht) {
            val dht = DHTConfig()
            dht.listeningPort = dhtPort
            dht.setShouldUseRouterBootstrap(true)
            builder.module(DHTModule(dht))
        }
        return builder.module(HttpTrackerModule()).build()
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
     * Fetch magnet metadata on a transient runtime (own timeout). Returns the
     * exchanged bytes too, so restarts don't re-fetch. Throws [MetadataTimeoutException].
     */
    suspend fun fetchMetadata(magnet: String, timeoutMs: Long = 60_000): FetchedMeta =
        withContext(ioDispatcher) {
            val rt = buildRuntime()
            try {
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
                    tmp.deleteRecursively()
                }
            } finally {
                runCatching { rt.shutdown() }
            }
        }

    /** Start (or resume — existing data is re-verified automatically) a download. */
    fun download(spec: TorrentDownloadSpec): BtSession {
        val rt = buildRuntime()
        try {
            val builder = Bt.client(rt).storage(FileSystemStorage(spec.saveDir))
            // Magnet first: it carries x.pe peer hints, which the bare-bytes path would lose.
            // Bytes are only the source when no magnet is known (pure .torrent adds).
            if (spec.magnet != null) {
                builder.magnet(spec.magnet)
            } else {
                val bytes = requireNotNull(spec.torrentBytes) { "magnet or torrentBytes required" }
                builder.torrent { metaService.fromByteArray(bytes) }
            }
            if (spec.sequential) builder.sequentialSelector()
            val wanted = spec.selectedPaths
            if (wanted != null) {
                builder.fileSelector { f ->
                    if (joinPath(f) in wanted) FilePriority.NORMAL_PRIORITY else FilePriority.SKIP
                }
            }
            val client = builder.build()
            val idHex = spec.magnet?.let { parseMagnet(it).idHex }
                ?: spec.torrentBytes?.let { toMeta(metaService.fromByteArray(it), null).idHex }
                ?: throw IllegalArgumentException("magnet or torrentBytes required")
            val pieceLength = spec.torrentBytes?.let { metaService.fromByteArray(it).chunkSize }
                ?: spec.pieceLength
            return BtSession(rt, client, idHex, pieceLength, spec.saveDir)
        } catch (e: Exception) {
            runCatching { rt.shutdown() }
            throw e
        }
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

/** Live handle for one torrent (own runtime; stats flow, pieces, peers, priority, stop). */
class BtSession internal constructor(
    private val runtime: BtRuntime,
    private val client: BtClient,
    val idHex: String,
    private val pieceLength: Long,
    val saveDir: File,
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
        val total = s.piecesTotal
        _stats.value = TorrentStats(
            downloadedBytes = dl,
            uploadedBytes = up,
            leftBytes = left,
            downRate = downRate,
            upRate = upRate,
            piecesTotal = total,
            piecesComplete = s.piecesComplete,
            peers = s.connectedPeers.size,
            // NOTE: total == 0 means metadata not in yet — never SEEDING.
            state = when {
                left == TorrentSessionState.UNKNOWN || total == 0 -> TorrentRunState.FETCHING
                s.piecesRemaining == 0 -> TorrentRunState.SEEDING
                else -> TorrentRunState.DOWNLOADING
            },
        )
    }

    private fun rate(now: Long, prev: Long, at: Long, prevAt: Long): Long {
        if (prev < 0 || prevAt < 0 || at <= prevAt) return 0
        return ((now - prev) * 1e9 / (at - prevAt)).toLong().coerceAtLeast(0)
    }

    /** RLE piece map (null until the data descriptor exists or after stop). */
    fun pieceMap(): TorrentPieceMap? = runCatching {
        val id = TorrentId.fromBytes(Protocols.fromHex(idHex))
        val descriptor = runtime.service(TorrentRegistry::class.java).getDescriptor(id).orElse(null)
            ?: return null
        val bitfield = descriptor.dataDescriptor?.bitfield ?: return null
        TorrentPieceMap(
            total = bitfield.piecesTotal,
            pieceLength = pieceLength,
            runs = PieceRuns.build(bitfield.piecesTotal, bitfield.bitmask, bitfield.skippedBitmask),
        )
    }.getOrNull()

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

    /** Pause: stops the client and tears down its runtime (resume rebuilds). */
    fun stop() {
        stopped = true
        runCatching { client.stop() }
        runCatching { runtime.shutdown() }
        _stats.value = _stats.value.copy(state = TorrentRunState.PAUSED)
    }

    private fun joinPath(f: TorrentFile): String = f.pathElements.joinToString("/")
}
