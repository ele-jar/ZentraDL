package com.elejar.ZentraDL.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.elejar.ZentraDL.designsystem.theme.ZentraDLTheme

enum class CardDensity { Compact, Comfortable }

enum class FileKind(val icon: ImageVector) {
    Video(Icons.Filled.Movie),
    Audio(Icons.Filled.MusicNote),
    Image(Icons.Filled.Image),
    Archive(Icons.Filled.FolderZip),
    Other(Icons.Filled.Description),
}

/** Immutable UI model for a download row. */
data class CardData(
    val id: String,
    val title: String,
    /** e.g. "312 MB of 1.2 GB · 4.1 MB/s · 3 min left". Tabular, pre-formatted. */
    val meta: String,
    val status: TaskStatus,
    /** 0..1, or null for indeterminate / no progress. */
    val progress: Float?,
    val kind: FileKind,
    /** TalkBack summary, e.g. "45 percent, 312 MB of 1.2 GB, 4 MB per second". */
    val semanticsSummary: String,
    val actionLabel: String,
    val actionIcon: ImageVector,
)

/**
 * Download card. Comfortable (88dp, default): 48dp tile, 2-line title,
 * meta, animated 6dp progress, trailing action + status. Compact (64dp):
 * tile, 1-line title, progress, action.
 */
@Composable
fun DownloadCard(
    data: CardData,
    density: CardDensity,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(
        targetValue = (data.progress ?: 0f).coerceIn(0f, 1f),
        animationSpec = tween(300),
        label = "cardProgress",
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = if (density == CardDensity.Compact) MaterialTheme.shapes.medium else MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .semantics { stateDescription = data.semanticsSummary },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Icon(
                    data.kind.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    data.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = if (density == CardDensity.Compact) 1 else 2,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                Text(
                    data.meta,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (data.status == TaskStatus.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (density == CardDensity.Comfortable) {
                    if (data.progress != null) {
                        LinearProgressIndicator(
                            progress = animated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
                    }
                    StatusChip(data.status)
                } else {
                    if (data.progress != null) {
                        LinearProgressIndicator(
                            progress = animated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                        )
                    }
                }
            }
            IconButton(onClick = onAction, modifier = Modifier.size(48.dp)) {
                Icon(
                    data.actionIcon,
                    contentDescription = data.actionLabel,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

private fun sampleCard() = CardData(
    id = "1",
    title = "ubuntu-24.04-desktop-amd64.iso",
    meta = "312 MB of 1.2 GB · 4.1 MB/s · 3 min left",
    status = TaskStatus.Downloading,
    progress = 0.45f,
    kind = FileKind.Other,
    semanticsSummary = "45 percent, 312 MB of 1.2 GB, 4 MB per second",
    actionLabel = "Pause",
    actionIcon = Icons.Filled.Pause,
)

@Preview(name = "Card comfortable")
@Composable
private fun ComfortableCardPreview() {
    ZentraDLTheme(dynamic = false) {
        DownloadCard(sampleCard(), CardDensity.Comfortable, onAction = {})
    }
}

@Preview(name = "Card compact failed")
@Composable
private fun CompactCardPreview() {
    ZentraDLTheme(dynamic = false) {
        DownloadCard(
            sampleCard().copy(status = TaskStatus.Failed, meta = "Link expired (404). Refresh the link.", progress = null),
            CardDensity.Compact,
            onAction = {},
        )
    }
}
