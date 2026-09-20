# bt 1.10 API reference (for Kotlin wrapper)

Sources read at tag `bt-parent-1.10` (commit `a89da71`) via raw.githubusercontent.com,
javadoc index at https://atomashpolskiy.github.io/bt/javadoc/latest (HTTP 200, "Bt 1.10 API").
Maven Central verified: `com.github.atomashpolskiy:bt-core:1.10`,
`:bt-http-tracker-client:1.10`, `:bt-dht:1.10` (each `latestVersion: 1.10`).
Unless noted, methods declare **no checked exceptions** (failures are `bt.BtException extends RuntimeException`,
`IllegalStateException`, `IllegalArgumentException`, `UncheckedIOException`).
Only checked exception in this doc: `InterruptedException` on `waitForAllPieces()`.

Conventions: `Config` = `bt.runtime.Config`. `BtClient` = `bt.runtime.BtClient`.

## 1. Entry / builders / BtClient

```java
package bt;
public class Bt {
    public static StandaloneClientBuilder client();
    public static BtClientBuilder client(BtRuntime runtime); // bt.runtime.BtRuntime
}
public abstract class BaseClientBuilder<B extends BaseClientBuilder> {
    public B initEagerly();
    public BtClient build(); // throws IllegalStateException("Missing runtime") if unset
    protected B runtime(BtRuntime runtime); // protected: NOT callable from wrapper
    protected abstract ProcessingContext buildProcessingContext(BtRuntime runtime);
}
// NO config()/module() on BaseClientBuilder. They live on the two subclasses below.
public class StandaloneClientBuilder extends TorrentClientBuilder<StandaloneClientBuilder> {
    public StandaloneClientBuilder config(Config config);
    public StandaloneClientBuilder module(com.google.inject.Module module);
    public StandaloneClientBuilder module(Class<? extends com.google.inject.Module> moduleType);
    public StandaloneClientBuilder autoLoadModules();
    public BtClient build(); // builds private runtime, then delegates to super
}
public class BtClientBuilder extends TorrentClientBuilder<BtClientBuilder> { /* no extra members */ }
public class TorrentClientBuilder<B extends TorrentClientBuilder> extends BaseClientBuilder<B> {
    public B storage(bt.data.Storage storage);
    public B torrent(java.net.URL torrentUrl);
    public B torrent(java.util.function.Supplier<bt.metainfo.Torrent> torrentSupplier);
    public B magnet(String magnetUri);            // parses via MagnetUriParser.lenientParser()
    public B magnet(bt.magnet.MagnetUri magnetUri);
    public B selector(bt.torrent.selector.PieceSelector pieceSelector); // default randomizedRarest
    public B sequentialSelector();                // selector(SequentialSelector.sequential())
    public B rarestSelector();                    // selector(RarestFirstSelector.rarest())
    public B randomizedRarestSelector();          // selector(RarestFirstSelector.randomizedRarest())
    public B stopWhenDownloaded();
    public B afterTorrentFetched(java.util.function.Consumer<bt.metainfo.Torrent> torrentConsumer);
    public B fileSelector(bt.torrent.fileselector.FilePrioritySkipSelector fileSelector);
    public B afterFilesChosen(Runnable runnable);
    public B afterFileDownloaded(bt.torrent.callbacks.FileDownloadCompleteCallback callback); // since 1.10
    public B afterDownloaded(java.util.function.Consumer<bt.metainfo.Torrent> runnable);      // since 1.10
}
package bt.runtime;
public interface BtClient {
    java.util.concurrent.CompletableFuture<?> startAsync();
    java.util.concurrent.CompletableFuture<?> startAsync(java.util.function.Consumer<bt.torrent.TorrentSessionState> listener, long period); // period: ms
    void stop();
    boolean isStarted(); // NOTE: name is isStarted(), there is NO isRunning()/getSession()
    boolean updateFilePriorities(bt.torrent.fileselector.FilePrioritySelector torrentFilePrioritySelector);
}
```
`startAsync()` twice throws `bt.BtException("Can't start -- already running")` (unchecked).
No `getSession()` on `BtClient`: observe state via the `startAsync(listener, period)` callback
or via `runtime.service(TorrentRegistry.class)` (see §6).
Shared-runtime path: `BtRuntime.builder() / builder(Config) / defaultRuntime()` (§11);
`BtRuntime` methods: `getConfig()`, `<T> T service(Class<T> serviceType)`,
`boolean isRunning()`, `void startup()`, `void shutdown()`,
`void attachClient(BtClient)`, `void detachClient(BtClient)`,
`java.util.Collection<BtClient> getClients()`, `bt.event.EventSource getEventSource()`.
`BtRuntimeBuilder` methods: `config(Config)`, `getConfig()`, `module(Module)`,
`module(Class<? extends Module>)`, `disableAutomaticShutdown()`, `autoLoadModules()`,
`disableStandardExtensions()`, `disablePeerExchange()` (1.10),
`disableLocalServiceDiscovery()` (1.10), `BtRuntime build()`.

## 2. Config (bt.runtime.Config)

All options are `void setX(T)` / `T getX()` pairs (boolean getter for MSE flag is `isMseDisabled()`).
Defaults from the `Config()` constructor:

| Setter / getter | Type | Default |
|---|---|---|
| `setAcceptorAddress` / `getAcceptorAddress` | `java.net.InetAddress` | first non-loopback NIC address |
| `setPeerAddress` / `getPeerAddress` (1.10; HTTP trackers only, NAT case) | `InetAddress` / `Optional<InetAddress>` | `Optional.empty()` |
| `setAcceptorPort` / `getAcceptorPort` | `int` | `6891` |
| `setPeerDiscoveryInterval` / `getPeerDiscoveryInterval` | `java.time.Duration` | 5 s |
| `setPeerHandshakeTimeout` / `getPeerHandshakeTimeout` | `Duration` | 30 s |
| `setPeerConnectionRetryInterval` / `getPeerConnectionRetryInterval` | `Duration` | 5 min |
| `setPeerConnectionRetryCount` / `getPeerConnectionRetryCount` | `int` | `3` |
| `setPeerConnectionTimeout` / `getPeerConnectionTimeout` | `Duration` | 30 s |
| `setPeerConnectionInactivityThreshold` / `getPeerConnectionInactivityThreshold` | `Duration` | 3 min |
| `setTrackerQueryInterval` / `getTrackerQueryInterval` | `Duration` | `null` (= use tracker response) |
| `setTrackerTimeout` / `getTrackerTimeout` (1.10) | `Duration` | `null` (= 30 s HTTP / min(3840 s, t) UDP / 10 s first peer collection) |
| `setMaxPeerConnections` / `getMaxPeerConnections` | `int` | `500` (all torrents) |
| `setMaxPeerConnectionsPerTorrent` / `getMaxPeerConnectionsPerTorrent` | `int` | `500` |
| `setTransferBlockSize` / `getTransferBlockSize` | `int` | `16384` |
| `setMaxTransferBlockSize` / `getMaxTransferBlockSize` | `int` | `131072` |
| `setMaxIOQueueSize` / `getMaxIOQueueSize` | `int` | `Integer.MAX_VALUE` |
| `setShutdownHookTimeout` / `getShutdownHookTimeout` | `Duration` | 30 s |
| `setNumOfHashingThreads` / `getNumOfHashingThreads` | `int` | `1` |
| `setMaxConcurrentlyActivePeerConnectionsPerTorrent` / `getMaxConcurrentlyActivePeerConnectionsPerTorrent` | `int` | `10` |
| `setMaxSimultaneouslyAssignedPieces` / `getMaxSimultaneouslyAssignedPieces` (1.10) | `int` | `3` |
| `setMaxPieceReceivingTime` / `getMaxPieceReceivingTime` | `Duration` | 5 s |
| `setMaxMessageProcessingInterval` / `getMaxMessageProcessingInterval` | `Duration` | 100 ms |
| `setUnreachablePeerBanDuration` / `getUnreachablePeerBanDuration` | `Duration` | 30 min |
| `setMaxPendingConnectionRequests` / `getMaxPendingConnectionRequests` | `int` | `50` |
| `setTimeoutedAssignmentPeerBanDuration` / `getTimeoutedAssignmentPeerBanDuration` | `Duration` | 1 min |
| `setEncryptionPolicy` / `getEncryptionPolicy` | `bt.protocol.crypto.EncryptionPolicy` | `PREFER_PLAINTEXT` |
| `setMetadataExchangeBlockSize` / `getMetadataExchangeBlockSize` | `int` | `16384` |
| `setMetadataExchangeMaxSize` / `getMetadataExchangeMaxSize` | `int` | `2097152` |
| `setMsePrivateKeySize` / `getMsePrivateKeySize` (allowed 16..512 bytes) | `int` | `20` |
| `setMseDisabled` / `isMseDisabled` (1.10) | `boolean` | `false` |
| `setMseWaitBetweenReads` / `getMseWaitBetweenReads` (1.10) | `Duration` | 1 s |
| `setNumberOfPeersToRequestFromTracker` / `getNumberOfPeersToRequestFromTracker` | `int` | `50` |
| `setMaxOutstandingRequests` / `getMaxOutstandingRequests` (1.9) | `int` | `250` |
| `setNetworkBufferSize` / `getNetworkBufferSize` (1.9) | `int` | `1048576` |
| `setHashingBufferSize` / `getHashingBufferSize` (1.10) | `int` | `SHA1Digester.DEFAULT_BUFFER_SIZE` |

**No download/upload rate or bandwidth-cap options exist** (verified by grep for
`*Limit*|*throttle*|RateLimiter` in `Config` and `bt-core`: zero hits).
There is **no DHT port in `Config`** — DHT port is `bt.dht.DHTConfig.setListeningPort` (§11).
Copy ctor: `public Config(Config config)`.

## 3. TorrentId / info-hash hex

```java
package bt.metainfo;
public class TorrentId {
    public static int length();                    // 20
    public static TorrentId fromBytes(byte[] bytes); // throws BtException if length != 20
    public byte[] getBytes();
    public String toString();                      // lowercase hex of the 20 bytes
}
package bt.protocol;
public class Protocols {
    public static String toHex(byte[] bytes);
    public static byte[] fromHex(String s);
    public static byte[] infoHashFromBase32(String s); // 32-char base32 -> 20 bytes
}
```
Hex from magnet: `MagnetUriParser.lenientParser().parse(magnetString).getTorrentId().toString()`.
Hex from file: `metadataService.fromInputStream(in).getTorrentId().toString()`
(`fromUrl(URL)`, `fromByteArray(byte[])` equivalents, §5). `IMetadataService` obtained via
`runtime.service(IMetadataService.class)` or `new MetadataService()` (public no-arg ctor verified).

## 4. MagnetUriParser / MagnetUri

```java
package bt.magnet;
public class MagnetUriParser {
    public static MagnetUriParser parser();         // strict: bad x.pe -> RuntimeException
    public static MagnetUriParser lenientParser();  // lenient: bad x.pe -> warn log, keep going
    public MagnetUri parse(String uriString);       // throws IllegalArgumentException (bad URI), IllegalStateException (xt missing/ambiguous)
    public MagnetUri parse(java.net.URI uri);
}
public class MagnetUri {
    public static Builder torrentId(TorrentId torrentId);
    public TorrentId getTorrentId();                            // xt=urn:btih:<hash>
    public java.util.Optional<String> getDisplayName();         // dn (URL-decoded by parser)
    public java.util.Collection<String> getTrackerUrls();       // tr (URL-decoded)
    public java.util.Collection<bt.net.InetPeerAddress> getPeerAddresses(); // x.pe host:port
    public static class Builder {
        public Builder(TorrentId torrentId);
        public Builder name(String displayName);       // do NOT pre-encode
        public Builder tracker(String trackerUrl);     // do NOT pre-encode
        public Builder peer(InetPeerAddress peerAddress);
        public MagnetUri buildUri();
    }
}
```
Verified limitation nuance: source comment says "base32 not supported", but `buildTorrentId`
accepts 40-char hex **and** 32-char base32 (`Protocols.infoHashFromBase32`); only v1
`urn:btih:` is accepted (`urn:btmh:` values are filtered out and then fail the exactly-1 check).
`InetPeerAddress(String hostname, int port)`: `getHostname()`, `getPort()`, `getAddress()` (1.9, lazy DNS).

## 5. Torrent metainfo (bt.metainfo.Torrent)

```java
public interface Torrent {
    TorrentSource getSource();                                        // 1.3; getMetadata(): Optional<byte[]>, getExchangedMetadata(): byte[]
    java.util.Optional<bt.tracker.AnnounceKey> getAnnounceKey();      // empty = trackerless
    TorrentId getTorrentId();
    String getName();
    long getChunkSize();                    // "piece length", bytes
    Iterable<byte[]> getChunkHashes();      // one 20-byte SHA-1 per piece
    long getSize();                         // total bytes
    java.util.List<TorrentFile> getFiles();
    boolean isPrivate();
    java.util.Optional<java.time.Instant> getCreationDate(); // 1.5
    java.util.Optional<String> getCreatedBy();               // 1.5
}
public interface TorrentFile {
    long getSize();
    java.util.List<String> getPathElements(); // dirs + filename, always >= 1 element
}
public class bt.tracker.AnnounceKey {
    public AnnounceKey(String trackerUrl);                        // single tracker
    public AnnounceKey(java.util.List<java.util.List<String>> trackerUrls); // BEP-12 tiers
    public boolean isMultiKey();
    public String getTrackerUrl();                  // null if multi
    public java.util.List<java.util.List<String>> getTrackerUrls(); // null if single
}
```
No `getPieceCount()`: piece count = `sessionState.getPiecesTotal()`
/ `descriptor.getDataDescriptor().getBitfield().getPiecesTotal()`
/ `chunkHashes bytes length / 20`.
`TorrentFile` has **no offset getter** — compute offsets by cumulative `getSize()`
(single-file torrents: `getFiles()` synthesizes one entry `path=[getName()]`).
Parsing: `IMetadataService { Torrent fromUrl(URL); Torrent fromInputStream(InputStream); Torrent fromByteArray(byte[]); }`
(caller closes the stream; impl `MetadataService` has public no-arg ctor).

## 6. TorrentSessionState (stats, DataDescriptor, peers)

```java
package bt.torrent;
public interface TorrentSessionState {
    long UNKNOWN = -1;
    int getPiecesTotal();
    int getPiecesComplete();    // 1.7
    int getPiecesIncomplete();  // 1.7
    int getPiecesRemaining();
    int getPiecesSkipped();     // 1.7
    int getPiecesNotSkipped();  // 1.7
    long getDownloaded();       // session totals, bytes
    long getUploaded();
    long getLeft();             // 1.10; UNKNOWN if torrent not fetched yet
    boolean startedAsSeed();    // 1.10; true if complete after initial hash
    java.util.Set<bt.net.ConnectionKey> getConnectedPeers(); // 1.9
    boolean updateFileDownloadPriority(bt.processor.ProcessingContext c, bt.torrent.fileselector.FilePrioritySelector prioritySelector);
}
public interface TorrentDescriptor {
    boolean isActive();
    void start();
    void stop();
    void complete();                       // no-op in DefaultTorrentDescriptor (verified)
    bt.data.DataDescriptor getDataDescriptor(); // nullable before creation (magnets)
    java.util.Optional<TorrentSessionState> getSessionState(); // 1.10
}
public interface TorrentRegistry {
    java.util.Collection<Torrent> getTorrents();            // 1.2
    java.util.Collection<TorrentId> getTorrentIds();        // 1.3
    java.util.Optional<Torrent> getTorrent(TorrentId torrentId);
    java.util.Optional<TorrentDescriptor> getDescriptor(Torrent torrent);   // deprecated 1.3
    java.util.Optional<TorrentDescriptor> getDescriptor(TorrentId torrentId);
    TorrentDescriptor getOrCreateDescriptor(Torrent torrent, bt.data.Storage storage); // deprecated 1.3
    TorrentDescriptor register(Torrent torrent, bt.data.Storage storage, FileDownloadCompleteCallback fcb); // 1.3
    void registerSessionState(TorrentId torrentId, TorrentSessionState state); // 1.10
    TorrentDescriptor register(TorrentId torrentId);        // 1.3
    boolean isSupportedAndActive(TorrentId torrentId);
}
```
**No rate getters** on the session state — compute rates in the wrapper by sampling
`getDownloaded()/getUploaded()` over time.
Peers tab: `ConnectionKey { Peer getPeer(); int getRemotePort(); TorrentId getTorrentId(); }`;
`bt.net.Peer { InetAddress getInetAddress(); boolean isPortUnknown(); int getPort();` // `UNKNOWN_PORT = -1` on `InetPeer`
`Optional<PeerId> getPeerId(); PeerOptions getOptions(); }` (`PeerOptions` exposes only
`getEncryptionPolicy()`). `PeerId.toString()` is hex; **no client-name decoder in public API**.
**No public per-peer up/down**: per-connection totals live in internal
`bt.torrent.messaging.ConnectionState { long getDownloaded(); long getUploaded(); boolean isInterested();
boolean isPeerInterested(); boolean isChoking(); boolean isPeerChoking(); ... }`,
reachable via `TorrentWorker { public Set<ConnectionKey> getPeers();
public ConnectionState getConnectionState(ConnectionKey key); }` — but there is **no public path
to obtain the `TorrentWorker`** (held privately by `DefaultTorrentSessionState`, not exposed),
so per-peer transfer stats are UNVERIFIED / not reachable via public API in 1.10.
Registry access: `runtime.service(TorrentRegistry.class).getDescriptor(torrentId)`.

## 7. DataDescriptor / Bitfield / ChunkDescriptor / PieceStatistics

```java
package bt.data;
public interface DataDescriptor extends java.io.Closeable {
    java.util.List<ChunkDescriptor> getChunkDescriptors(); // index == piece index
    LocalBitfield getBitfield();
    java.util.List<bt.metainfo.TorrentFile> getFilesForPiece(int pieceIndex); // deprecated 1.7
    java.util.BitSet getAllPiecesForFiles(java.util.Set<bt.metainfo.TorrentFile> files);   // 1.10
    java.util.BitSet getPiecesWithOnlyFiles(java.util.Set<bt.metainfo.TorrentFile> files); // 1.10
    DataReader getReader();                             // 1.8
    void waitForAllPieces() throws InterruptedException; // CHECKED exception
    boolean startedAsSeed();
    long getLeft();                                     // 1.10
}
public abstract class Bitfield {
    public java.util.BitSet getBitmask();               // 1.7, defensive copy
    public byte[] toByteArray(bt.protocol.BitOrder bitOrder); // 1.7
    public int getPiecesTotal();
    public int getPiecesComplete();
    public int getPiecesIncomplete();                   // 1.7
    public boolean isComplete(int pieceIndex);          // 1.1
    public boolean isVerified(int pieceIndex);          // 1.1
    protected void markVerified(int pieceIndex);
}
public abstract class LocalBitfield extends Bitfield {
    public void markLocalPieceVerified(int pieceIndex);
    public void waitForAllPieces() throws InterruptedException; // CHECKED
    public java.util.BitSet getSkippedBitmask();        // 1.8, copy
    public void setSkippedPieces(java.util.BitSet piecesToSkip); // 1.10
    public int getPiecesSkipped();                      // 1.7
    public int getPiecesNotSkipped();                   // 1.7
    public int getPiecesRemaining();
    public void removeVerifiedPiecesFromBitset(java.util.BitSet bitSet);
}
public interface ChunkDescriptor extends BlockSet {
    byte[] getChecksum();   // 1.2, SHA-1 from metainfo
    DataRange getData();    // 1.2
}
package bt.torrent;
public interface PieceStatistics {
    int getCount(int pieceIndex); // # swarm peers having the piece
    int getPiecesTotal();
}
public class BitfieldBasedStatistics implements PieceStatistics {
    public BitfieldBasedStatistics(Bitfield localBitfield);
    public void addBitfield(ConnectionKey connectionKey, bt.data.PeerBitfield bitfield);
    public void removeBitfield(ConnectionKey connectionKey);
    public void addPiece(ConnectionKey connectionKey, Integer pieceIndex);
    public java.util.Optional<bt.data.PeerBitfield> getPeerBitfield(ConnectionKey connectionKey);
    public int getCount(int pieceIndex);
    public int getPiecesTotal();
}
```
Pieces-map pattern (verified): `descriptor.getBitfield().getBitmask()` (or `isComplete(i)` per
`getPiecesTotal()`). **Per-piece swarm availability caveat:** the live `BitfieldBasedStatistics`
instance lives in the internal `TorrentContext.getPieceStatistics()`; `TorrentContext` objects are
not exposed via `BtClient`, so there is no verified public accessor for per-piece availability —
treat direct availability polling as UNVERIFIED via public API (reachable only with internal
services/processing-context access).

## 8. File selection

```java
package bt.torrent.fileselector;
@FunctionalInterface
public interface FilePrioritySkipSelector { // since 1.10; use this pre-start
    FilePriority prioritize(bt.metainfo.TorrentFile file);
}
public enum FilePriority { SKIP, NORMAL_PRIORITY, HIGH_PRIORITY } // 1.10
@FunctionalInterface
public interface FilePrioritySelector {     // post-start updates only
    UpdatedFilePriority prioritize(bt.metainfo.TorrentFile file);
}
public enum UpdatedFilePriority { NORMAL_PRIORITY, HIGH_PRIORITY } // NO SKIP
@Deprecated public abstract class TorrentFileSelector implements FilePrioritySkipSelector {
    public java.util.List<SelectionResult> selectFiles(java.util.List<bt.metainfo.TorrentFile> files);
    protected abstract SelectionResult select(bt.metainfo.TorrentFile file);
}
@Deprecated public class SelectionResult {
    public static Builder select(); public static SelectionResult skip();
    public boolean shouldSkip();
    public static class Builder { public SelectionResult build(); }
}
```
Subset download: `builder.fileSelector(file -> wanted.contains(file) ? FilePriority.NORMAL_PRIORITY : FilePriority.SKIP)`
(lambda works — it is a `@FunctionalInterface`). Empty files are always created even if skipped (javadoc).
Post-start change: `client.updateFilePriorities(selector)` → `state.updateFileDownloadPriority(...)`
→ `PrioritizedPieceSelector.setHighPriorityPieces(BitSet)`; returns `false` if no state yet.
**Skipping/unskipping mid-download is NOT supported** (only `UpdatedFilePriority`
`NORMAL/HIGH_PRIORITY`; confirmed by 1.10 release notes).

## 9. SequentialSelector

```java
package bt.torrent.selector;
public class SequentialSelector implements PieceSelector {
    public static SequentialSelector sequential();
    public java.util.stream.IntStream getNextPieces(java.util.BitSet relevantChunks, bt.torrent.PieceStatistics pieceStatistics);
    // impl: return relevantChunks.stream();
}
public interface PieceSelector {
    default void initSelector(int numPieces) {}
    java.util.stream.IntStream getNextPieces(java.util.BitSet relevantChunks, bt.torrent.PieceStatistics pieceStatistics);
}
```
Builder shortcut: `builder.sequentialSelector()`. Default selector is
`RarestFirstSelector.randomizedRarest()` (set in `TorrentClientBuilder` ctor).

## 10. afterTorrentFetched / afterFilesChosen / afterDownloaded / stopWhenDownloaded

- `afterTorrentFetched(Consumer<Torrent>)` → fires on `ProcessingEvent.TORRENT_FETCHED`,
  emitted by `FetchTorrentStage` (.torrent path) **and** `FetchMetadataStage` (magnet path) —
  so it is the metadata-ready signal for both.
- `afterFilesChosen(Runnable)` → `ProcessingEvent.FILES_CHOSEN` after `ChooseFilesStage`.
- `afterFileDownloaded(FileDownloadCompleteCallback)` → per-file verify callback (1.10):
  `void fileDownloadCompleted(Torrent torrent, TorrentFile tf, Storage s);`
- `afterDownloaded(Consumer<Torrent>)` → `ProcessingEvent.DOWNLOAD_COMPLETE` (1.10);
  a `storage.flush()` listener on the same event is **always** registered first.
- `stopWhenDownloaded()` adds a `DOWNLOAD_COMPLETE` listener returning `null`, terminating the
  chain before `SeedStage`. **Without it the chain enters `SeedStage`, which loops
  `while (descriptor.isActive()) Thread.sleep(1000)` — i.e. the client keeps seeding
  until `stop()`** (which calls `TorrentDescriptor.stop()` → loop exits → future completes).
- `DefaultClient.stop()` also `complete(null)`s the client future; `BtClient` futures otherwise
  complete when the whole chain (incl. seeding) ends.

## 11. DHT / HTTP / UDP tracker modules

```java
package bt.dht; // artifact bt-dht (wraps mldht.core:libmldht)
public class DHTModule implements com.google.inject.Module {
    public DHTModule();                 // default DHTConfig
    public DHTModule(DHTConfig config);
    public void configure(com.google.inject.Binder binder);
}
public class DHTConfig {
    public int getListeningPort();                          // default 49001
    public void setListeningPort(int listeningPort);        // <-- the "DHT port"
    public boolean shouldUseRouterBootstrap();              // default false
    public void setShouldUseRouterBootstrap(boolean b);
    public boolean shouldUseIPv6();                         // default false
    public void setShouldUseIPv6(boolean b);
    public java.util.Collection<bt.net.InetPeerAddress> getBootstrapNodes(); // default []
    public void setBootstrapNodes(java.util.Collection<bt.net.InetPeerAddress> nodes);
}
package bt.tracker.http; // artifact bt-http-tracker-client
public class HttpTrackerModule implements com.google.inject.Module {
    public void configure(com.google.inject.Binder binder); // serves "http","https"
}
```
Attach (standalone): `Bt.client().config(cfg).module(new DHTModule(dhtCfg)).module(new HttpTrackerModule())...`
or `...module(DHTModule.class).module(HttpTrackerModule.class)...`.
Shared runtime: `BtRuntime.builder(cfg).module(...).build()`, then `Bt.client(runtime)....build()`.
Classpath auto-load alternative: `.autoLoadModules()` picks up `DHTModuleProvider` /
`HttpTrackerModuleProvider` (both are `BtModuleProvider` ServiceLoader entries) when the
artifacts are on the classpath (verified provider sources).
**UDP trackers need no extra artifact**: `ServiceModule` in bt-core registers
`UdpTrackerFactory` for `"udp"` by default (verified in `ServiceModule` source).
Artifacts (Maven Central, version `1.10`, group `com.github.atomashpolskiy`):
`bt-core`, `bt-http-tracker-client`, `bt-dht` (DHT lib module lives at `bt-dht/bt-dht`,
parent pom `bt-dht-parent`; runtime dependency `mldht.core:libmldht`).

## 12. Torrent creation (bt.torrent.maker)

```java
package bt.torrent.maker;
public class TorrentBuilder {
    public TorrentBuilder addFile(java.nio.file.Path file);   // file or dir (walked)
    public TorrentBuilder addFiles(java.nio.file.Path... files);
    public TorrentBuilder rootPath(java.nio.file.Path rootPath); // required (readable dir) for multi-file
    public TorrentBuilder announce(String announce);
    public TorrentBuilder addAnnounceGroup(java.util.Collection<String> announceGroup);
    public TorrentBuilder pieceSize(int pieceSize);           // power of 2, default 1<<18 (256 KiB)
    public TorrentBuilder privateFlag(boolean isPrivate);
    public TorrentBuilder createdBy(String createdBy);
    public TorrentBuilder creationDate(java.util.Date creationDate); // default: build time
    public TorrentBuilder maxNumOpenFiles(int maxNumOpenFiles);      // default 128
    public TorrentBuilder numHashingThreads(int numHashingThreads);  // <1 => ForkJoinPool.commonPool()
    public TorrentBuilder hashingBufferSize(int hashingBufferSize);
    public byte[] build();                                    // == new TorrentMaker(this).makeTorrent()
}
public class TorrentMaker {
    public TorrentMaker(TorrentBuilder torrentBuilder);
    public byte[] makeTorrent(); // throws UncheckedIOException on I/O failure
}
```
Single file → `name` = file name, `length` set; multi-file → `name` = root dir name,
`files` = `[length, path-elements...]` relative to root (files outside root → `IllegalStateException`).

## 13. Magnet metadata fetch with timeout

`startAsync()` future completes only at chain end (not at metadata time), so:
```java
CompletableFuture<Torrent> meta = new CompletableFuture<>();
BtClient c = Bt.client().magnet(magnet).storage(storage).afterTorrentFetched(meta::complete).build();
c.startAsync();
// wait with YOUR OWN timeout, e.g.:
try { Torrent t = meta.get(30, TimeUnit.SECONDS); }
catch (TimeoutException e) { c.stop(); /* treat as metadata timeout */ }
// alt. poll: runtime.service(TorrentRegistry.class).getTorrent(torrentId).isPresent()
```
Verified: `MetadataConsumer.waitForTorrent()` is `public Torrent waitForTorrent()` with **no
timeout overload** (blocks on `Object.wait()` until set) — wrapper-side timeout is mandatory.
Metadata-ready = `TORRENT_FETCHED` / `afterTorrentFetched` / `EventSink.fireMetadataAvailable`
(also `FetchMetadataStage.after()` returns `TORRENT_FETCHED`). On timeout call `client.stop()`:
`DefaultClient.stop()` detaches, completes the future with `null`, and stops the descriptor.

## 14. Recheck / re-announce / seeding default

- **Recheck on restart is automatic, no API**: `DefaultDataDescriptor` ctor hashes existing
  storage (`buildBitfield` → `verifier.verify(chunks, bitfield)`); complete data ⇒
  `startedAsSeed()==true`, `getLeft()==0`. Just reuse the same `Storage` root.
- **Re-announce: no public API.** `TrackerAnnouncer` is constructed internally by
  `ProcessTorrentStage` (`new TrackerAnnouncer(trackerService, torrent, announceKey, sessionState)`);
  its only public methods are `void stop()` and `void complete()`. Periodic announce/peer
  collection runs via `peerRegistry.triggerPeerCollection(torrentId)` + `TrackerPeerSource`
  on `config.getTrackerQueryInterval()` (null = tracker-provided interval).
- **Finished client keeps seeding by default** (§10 `SeedStage` loop); only `stopWhenDownloaded()`
  ends processing at `DOWNLOAD_COMPLETE` (also note release-notes fix "Runtime does not terminate
  when torrent has been downloaded #167" — without the flag the runtime stays up while seeding).

## 15. Storage

```java
package bt.data.file;
public class FileSystemStorage implements bt.data.Storage {
    public FileSystemStorage(java.io.File rootDirectory);
    public FileSystemStorage(java.nio.file.Path rootDirectory);
    public FileSystemStorage(java.nio.file.Path rootDirectory, int maxOpenFiles); // default 256
    public bt.data.StorageUnit getUnit(bt.metainfo.Torrent torrent, bt.metainfo.TorrentFile torrentFile);
    public void flush();
}
public interface bt.data.Storage {
    StorageUnit getUnit(Torrent torrent, TorrentFile torrentFile);
    void flush();
}
```
Layout: single-file torrent → file lands directly in root; multi-file → `root/<torrentName>/...`
(path elements normalized: trims/dots/empties fixed, traversal contained — see class javadoc).
`flush()` → `OpenFileCache.flush()` → `FileChannel.force(false)` per open file
(**data only, not metadata**); auto-flushed on `DOWNLOAD_COMPLETE` by the built-in listener.

## 16. Android / slf4j notes

- `java.lang.management` / `javax.management` / `java.awt(_)` / JavaFX: **zero hits** in
  `bt-core`, `bt-dht`, `bt-http-tracker-client`, `bt-upnp`, `bt-bencoding` main sources (verified grep).
  No AWT anywhere in repo main sources. Source level is Java 8 (`compiler.source/target 1.8`) — desugaring-safe APIs only.
- Multicast (LSD/BEP-14): `AnnounceGroupChannel` uses NIO `DatagramChannel` +
  `StandardSocketOptions.IP_MULTICAST_TTL`; on Android Wi-Fi this needs
  `CHANGE_WIFI_MULTICAST_STATE` + a `WifiManager.MulticastLock`, else LSD discovery silently finds nothing.
  Mitigation (verified, since 1.10): `BtRuntimeBuilder.disableLocalServiceDiscovery()` (also
  `disablePeerExchange()`, `disableStandardExtensions()`).
- Port mapping: `PortMappingModule` is always installed; impl is `NoOpPortMapper` unless the
  separate UPnP support is present — no action needed (verified `bt-core/.../portmapping/impl/` contents).
- DHT on mobile: `DHTConfig` default port `49001`, router bootstrap **off** by default
  (`setShouldUseRouterBootstrap(true)` + optional `setBootstrapNodes(...)` to use); DHT keeps a
  UDP socket + background threads — consider enabling only on demand.
- Threads/lifecycle: `BtRuntime.startup()` registers a JVM shutdown hook (never fires on Android —
  harmless); call `runtime.shutdown()` / `client.stop()` explicitly, and consider
  `disableAutomaticShutdown()` when sharing one runtime across torrents.
- Battery/CPU knobs that matter on Android: `setNumOfHashingThreads` (default 1 — raise only on foreground),
  `setMaxPeerConnections(PerTorrent)`, `setMaxConcurrentlyActivePeerConnectionsPerTorrent` (10),
  `setMaxOutstandingRequests` (250), `setHashingBufferSize`, `setPeerDiscoveryInterval`.
- **slf4j binding**: `bt-core` depends only on `org.slf4j:slf4j-api:1.7.32` (`compile` scope) and ships
  **no binding** (verified poms; `slf4j-simple` appears only in `bt-tests`, log4j only in `bt-cli`/`examples`).
  The app **must** add a binding (e.g. slf4j-android / logback-android); without one all bt logs are dropped
  (slf4j NOP warning). Guice `5.0.1` + Guava `30.1-jre` come transitively with `bt-core`
  (multidex/R8 rules may be needed — device behavior UNVERIFIED, no device test performed).
