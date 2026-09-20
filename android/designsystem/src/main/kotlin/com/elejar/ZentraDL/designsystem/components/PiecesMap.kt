package com.elejar.ZentraDL.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.elejar.ZentraDL.designsystem.theme.ZentraDLTheme
import kotlin.math.ceil
import kotlin.math.sqrt

/** One aggregated map cell: share complete + skipped-majority flag. */
data class PieceCell(val fraction: Float, val skipped: Boolean)

/**
 * Torrent Pieces map (PM2): ONE Canvas, aggregated cells, pinch-zoom + pan,
 * tap-to-inspect. States use color AND treatment (solid / partial / outline /
 * hatch), never color alone. In-flight isn't exposed by the engine, so
 * partial cells mean "some pieces complete", not live activity.
 */
@Composable
fun PiecesMap(
    cells: List<PieceCell>,
    summary: String,
    modifier: Modifier = Modifier,
    onTapCell: ((Int) -> Unit)? = null,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val dim = MaterialTheme.colorScheme.surfaceContainerHigh
    val hatch = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(MaterialTheme.shapes.small)
            .semantics { contentDescription = summary }
            .transformable(
                rememberTransformableState { zoom, pan, _ ->
                    scale = (scale * zoom).coerceIn(1f, 8f)
                    offset += pan
                },
            )
            .pointerInput(scale, offset, cells.size) {
                if (onTapCell != null) {
                    detectTapGestures { p ->
                        onTapCell(cellAt(p, scale, offset, cells.size, size.width, size.height))
                    }
                }
            },
    ) {
        if (cells.isEmpty()) return@Canvas
        val (cols, cellW, cellH) = grid(cells.size, size.width, size.height)
        withTransform({
            translate(offset.x, offset.y)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            val stroke = 1.dp.toPx()
            cells.forEachIndexed { i, c ->
                val col = i % cols
                val row = i / cols
                val x = col * cellW
                val y = row * cellH
                when {
                    c.skipped -> {
                        drawRect(dim, Offset(x, y), Size(cellW, cellH))
                        var h = 0f
                        while (h < cellH) {
                            drawLine(hatch, Offset(x, y + h), Offset(x + cellW, y + h - cellW), stroke)
                            h += 4.dp.toPx()
                        }
                    }
                    c.fraction >= 1f -> drawRect(primary, Offset(x, y), Size(cellW, cellH))
                    c.fraction > 0f -> {
                        drawRect(dim, Offset(x, y), Size(cellW, cellH))
                        val fh = cellH * c.fraction
                        drawRect(primary, Offset(x, y + cellH - fh), Size(cellW, fh))
                    }
                    else -> drawRect(outline, Offset(x, y), Size(cellW, cellH), style = Stroke(stroke))
                }
            }
        }
    }
}

private data class Grid(val cols: Int, val cellW: Float, val cellH: Float)

private fun grid(n: Int, w: Float, h: Float): Grid {
    if (n <= 0 || w <= 0 || h <= 0) return Grid(1, w, h)
    val aspect = w / h
    val cols = ceil(sqrt((n * aspect).toDouble())).toInt().coerceAtLeast(1)
    return Grid(cols, w / cols, h / ceil(n.toDouble() / cols).toInt().coerceAtLeast(1))
}

private fun cellAt(p: Offset, scale: Float, offset: Offset, n: Int, w: Int, h: Int): Int {
    val x = (p.x - offset.x) / scale
    val y = (p.y - offset.y) / scale
    val (cols, cellW, cellH) = grid(n, w.toFloat(), h.toFloat())
    if (cellW <= 0 || cellH <= 0) return 0
    val col = (x / cellW).toInt().coerceIn(0, cols - 1)
    val rows = ceil(n.toDouble() / cols).toInt().coerceAtLeast(1)
    val row = (y / cellH).toInt().coerceIn(0, rows - 1)
    return (row * cols + col).coerceIn(0, n - 1)
}

/** Legend with caller-provided labels (strings live in the app). */
@Composable
fun PiecesLegend(completeLabel: String, missingLabel: String, skippedLabel: String, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = modifier) {
        LegendSwatch(MaterialTheme.colorScheme.primary, completeLabel)
        LegendSwatch(null, missingLabel)
        LegendSwatch(MaterialTheme.colorScheme.surfaceContainerHigh, skippedLabel)
    }
}

@Composable
private fun LegendSwatch(color: Color?, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.size(16.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .then(
                    if (color != null) Modifier.background(color)
                    else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall),
                ),
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Preview
@Composable
private fun PiecesMapPreview() {
    ZentraDLTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val cells = List(120) { i ->
                PieceCell(
                    fraction = when {
                        i < 60 -> 1f
                        i < 70 -> (i - 60) / 10f
                        i >= 110 -> 0f
                        else -> 0f
                    },
                    skipped = i >= 100 && i < 110,
                )
            }
            PiecesMap(cells, "72 of 120 pieces complete")
            PiecesLegend("Complete", "Missing", "Skipped")
        }
    }
}
