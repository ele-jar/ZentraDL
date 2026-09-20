package com.elejar.ZentraDL.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.elejar.ZentraDL.designsystem.theme.LocalStatusColors
import com.elejar.ZentraDL.designsystem.theme.ZentraDLTheme

/** Download states with the exact labels from the UX skill. */
enum class TaskStatus(val label: String, val icon: ImageVector) {
    Downloading("Downloading", Icons.Filled.Download),
    Queued("Queued", Icons.Filled.HourglassEmpty),
    Paused("Paused", Icons.Filled.Pause),
    Completed("Completed", Icons.Filled.CheckCircle),
    Failed("Failed", Icons.Filled.Error),
    Seeding("Seeding", Icons.Filled.CloudUpload),
    Checking("Checking", Icons.Filled.Sync),
}

@Composable
fun statusColor(status: TaskStatus): Color {
    val s = LocalStatusColors.current
    return when (status) {
        TaskStatus.Downloading -> s.downloading
        TaskStatus.Queued -> s.queued
        TaskStatus.Paused -> s.paused
        TaskStatus.Completed -> s.completed
        TaskStatus.Failed -> s.failed
        TaskStatus.Seeding -> s.seeding
        TaskStatus.Checking -> s.checking
    }
}

/** 32dp tonal status chip: icon + label, never color alone. Display-only. */
@Composable
fun StatusChip(status: TaskStatus, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .height(32.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Icon(
                status.icon,
                contentDescription = null,
                tint = statusColor(status),
                modifier = Modifier.size(20.dp),
            )
            Text(status.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Preview(name = "Chips")
@Composable
private fun StatusChipsPreview() {
    ZentraDLTheme(dynamic = false) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TaskStatus.entries.forEach { StatusChip(it) }
        }
    }
}
