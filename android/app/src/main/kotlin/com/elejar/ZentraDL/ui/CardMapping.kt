package com.elejar.ZentraDL.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import com.elejar.ZentraDL.data.local.TaskRecord
import com.elejar.ZentraDL.designsystem.components.CardData
import com.elejar.ZentraDL.designsystem.components.FileKind
import com.elejar.ZentraDL.designsystem.components.TaskStatus

fun kindOf(fileName: String): FileKind {
    return when (fileName.substringAfterLast('.', "").lowercase()) {
        "mp4", "mkv", "webm", "avi", "mov", "m4v", "3gp" -> FileKind.Video
        "mp3", "m4a", "flac", "ogg", "opus", "wav" -> FileKind.Audio
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic" -> FileKind.Image
        "zip", "rar", "7z", "tar", "gz", "xz" -> FileKind.Archive
        else -> FileKind.Other
    }
}

fun DownloadsUi.RowUi.toCardData(): CardData {
    val (label, icon) = when (status) {
        TaskStatus.Downloading -> "Pause" to Icons.Filled.Pause
        TaskStatus.Queued -> "Cancel" to Icons.Filled.Close
        TaskStatus.Paused -> "Resume" to Icons.Filled.PlayArrow
        TaskStatus.Failed -> "Retry" to Icons.Filled.Refresh
        TaskStatus.Completed -> "Open" to Icons.Filled.OpenInNew
        else -> "Pause" to Icons.Filled.Pause
    }
    return CardData(
        id = record.id,
        title = record.fileName,
        meta = meta,
        status = status,
        progress = percent?.let { it / 100f },
        kind = kindOf(record.fileName),
        semanticsSummary = semanticsSummary,
        actionLabel = label,
        actionIcon = icon,
    )
}

fun openOrComplain(ctx: Context, rec: TaskRecord, message: (String) -> Unit) {
    val file = java.io.File(rec.destPath, rec.fileName)
    if (!FileActions.openFile(ctx, file)) message("No app can open this file")
}
