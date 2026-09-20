package com.elejar.ZentraDL.ui

import com.elejar.ZentraDL.designsystem.components.PieceCell
import com.elejar.ZentraDL.engine.torrent.PieceRun

/**
 * Downsamples piece runs to a drawable cell budget (PM2: one Canvas, RLE in,
 * never per-piece composables). Pure + unit-tested.
 */
object PieceCells {
    fun aggregate(total: Int, runs: List<PieceRun>, maxCells: Int): List<PieceCell> {
        if (total <= 0 || maxCells <= 0) return emptyList()
        val cells = maxCells.coerceAtMost(total)
        val per = (total + cells - 1) / cells
        val out = mutableListOf<PieceCell>()
        var runIdx = 0
        var runLeft = runs.getOrNull(0)?.count ?: 0
        var i = 0
        while (i < total) {
            var done = 0
            var skip = 0
            var n = 0
            while (n < per && i + n < total) {
                while (runLeft == 0 && runIdx + 1 < runs.size) {
                    runIdx++
                    runLeft = runs[runIdx].count
                }
                if (runLeft == 0) break
                val take = minOf(runLeft, per - n, total - (i + n))
                when (runs[runIdx].state) {
                    1 -> done += take
                    2 -> skip += take
                }
                n += take
                runLeft -= take
            }
            if (n == 0) break
            out += PieceCell(if (done == n) 1f else done.toFloat() / n, skip * 2 >= n && skip > 0)
            i += n
        }
        return out
    }
}
