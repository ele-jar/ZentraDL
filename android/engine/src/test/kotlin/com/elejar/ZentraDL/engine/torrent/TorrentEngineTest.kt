package com.elejar.ZentraDL.engine.torrent

import bt.torrent.maker.TorrentBuilder
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import java.util.BitSet
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

/** Offline swarm: in-JVM seeder + leecher over loopback, no internet (brief §9). */
class TorrentEngineTest {

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private fun dataDir(): File {
        val root = Files.createTempDirectory("swarm").toFile()
        val data = File(root, "data").apply { mkdirs() }
        File(data, "a.bin").writeBytes(ByteArray(40_000) { (it % 251).toByte() })
        File(data, "sub").apply { mkdirs() }
        File(data, "sub/b.bin").writeBytes(ByteArray(25_000) { (it % 127).toByte() })
        return root
    }

    private fun torrentBytes(root: File): ByteArray {
        val data = File(root, "data")
        return TorrentBuilder()
            .rootPath(data.toPath())
            .addFile(File(data, "a.bin").toPath())
            .addFile(File(data, "sub/b.bin").toPath())
            .pieceSize(1 shl 14)
            .build()
    }

    @Test fun rle_coalescesStates() {
        val complete = BitSet().apply { set(0); set(1); set(4) }
        val skipped = BitSet().apply { set(2) }
        assertThat(PieceRuns.build(5, complete, skipped)).containsExactly(
            PieceRun(1, 2), PieceRun(2, 1), PieceRun(0, 1), PieceRun(1, 1),
        ).inOrder()
        assertThat(PieceRuns.build(0, BitSet(), BitSet())).isEmpty()
    }

    @Test fun parseMagnet_rejectsGarbage() {
        val engine = BtEngine(BtOptions(enableDht = false))
        try {
            engine.parseMagnet("not a magnet")
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun seeder_to_leecher_over_loopback(): Unit = runBlocking {
        val root = dataDir()
        val bytes = torrentBytes(root)
        val meta = BtEngine(BtOptions(enableDht = false)).parseTorrentBytes(bytes)
        assertThat(meta.name).isEqualTo("data")
        assertThat(meta.files.map { it.path }).containsExactly("a.bin", "sub/b.bin")
        assertThat(meta.pieceLength).isEqualTo(16384L)
        assertThat(meta.pieceCount).isEqualTo(4)

        val seedPort = freePort()
        val seeder = BtEngine(BtOptions(acceptorPort = seedPort, bindHost = "127.0.0.1", enableDht = false))
        seeder.start()
        val seedSession = seeder.download(TorrentDownloadSpec(null, bytes, root))

        // Magnet link with loopback seeder as the x.pe peer hint.
        val magnet = "magnet:?xt=urn:btih:${meta.idHex}&x.pe=127.0.0.1:$seedPort"
        assertThat(magnet).startsWith("magnet:?")
        // Sanity: our own parser round-trips it, peer hint intact.
        val ref = BtEngine(BtOptions(enableDht = false)).parseMagnet(magnet)
        assertThat(ref.idHex).isEqualTo(meta.idHex)
        assertThat(ref.peers.map { "${it.hostname}:${it.port}" }).containsExactly("127.0.0.1:$seedPort")

        val leecher = BtEngine(BtOptions(acceptorPort = freePort(), bindHost = "127.0.0.1", enableDht = false))
        leecher.start()
        try {
            val fetched = leecher.fetchMetadata(magnet, timeoutMs = 60_000)
            assertThat(fetched.meta.idHex).isEqualTo(meta.idHex)
            assertThat(fetched.meta.name).isEqualTo("data")
            // Reuse the exchanged bytes: no second metadata round-trip, known piece length.
            assertThat(fetched.rawBytes).isNotNull()

            val dest = Files.createTempDirectory("leech").toFile()
            val session = leecher.download(
                TorrentDownloadSpec(magnet, fetched.rawBytes, dest, pieceLength = fetched.meta.pieceLength),
            )
            withTimeout(120_000) {
                while (session.stats.value.state != TorrentRunState.SEEDING) {
                    session.error.value?.let { throw IllegalStateException("session failed: $it") }
                    delay(500)
                }
            }
            val map = session.pieceMap()
            assertThat(map).isNotNull()
            assertThat(map!!.complete).isEqualTo(map.total)
            val tree = dest.walkTopDown().map { it.relativeTo(dest).toString() }.sorted().toList()
            fun content(name: String): ByteArray {
                val f = dest.walkTopDown().firstOrNull { it.isFile && it.name == name }
                    ?: throw AssertionError("missing $name; tree=$tree")
                return f.readBytes()
            }
            assertThat(content("a.bin")).isEqualTo(File(root, "data/a.bin").readBytes())
            assertThat(content("b.bin")).isEqualTo(File(root, "data/sub/b.bin").readBytes())
            assertThat(session.error.value).isNull()
            session.stop()
        } finally {
            seedSession.stop()
            seeder.shutdown()
            leecher.shutdown()
        }
    }
}
