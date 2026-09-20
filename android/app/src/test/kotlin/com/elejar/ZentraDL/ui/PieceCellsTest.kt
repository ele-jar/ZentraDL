package com.elejar.ZentraDL.ui

import com.elejar.ZentraDL.engine.torrent.PieceRun
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PieceCellsTest {

    @Test fun exactCells_passThrough() {
        val cells = PieceCells.aggregate(
            4, listOf(PieceRun(1, 2), PieceRun(0, 1), PieceRun(1, 1)), 10,
        )
        assertThat(cells).hasSize(4)
        assertThat(cells.map { it.fraction }).containsExactly(1f, 1f, 0f, 1f).inOrder()
    }

    @Test fun aggregates_partialFractions() {
        // 100 pieces, first half done → 10 cells of 10: 5 full, 5 empty.
        val cells = PieceCells.aggregate(100, listOf(PieceRun(1, 50), PieceRun(0, 50)), 10)
        assertThat(cells).hasSize(10)
        assertThat(cells.take(5).all { it.fraction == 1f }).isTrue()
        assertThat(cells.drop(5).all { it.fraction == 0f }).isTrue()
        // Odd split: 25 done of 100 in 10 cells → 2 full + 1 half.
        val odd = PieceCells.aggregate(100, listOf(PieceRun(1, 25), PieceRun(0, 75)), 10)
        assertThat(odd[2].fraction).isWithin(0.001f).of(0.5f)
    }

    @Test fun skipped_majority_flagged() {
        val cells = PieceCells.aggregate(10, listOf(PieceRun(2, 10)), 5)
        assertThat(cells.all { it.skipped }).isTrue()
        val mixed = PieceCells.aggregate(10, listOf(PieceRun(1, 8), PieceRun(2, 2)), 5)
        assertThat(mixed[4].skipped).isTrue()
        assertThat(mixed[0].skipped).isFalse()
    }

    @Test fun empty_safe() {
        assertThat(PieceCells.aggregate(0, emptyList(), 10)).isEmpty()
        assertThat(PieceCells.aggregate(10, emptyList(), 0)).isEmpty()
    }
}
