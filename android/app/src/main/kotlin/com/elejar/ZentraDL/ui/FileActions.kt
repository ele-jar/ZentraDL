package com.elejar.ZentraDL.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/** Open a completed file with the right app (FileProvider, correct MIME). */
object FileActions {
    fun openFile(ctx: Context, file: File): Boolean {
        if (!file.exists()) return false
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            ctx.startActivity(Intent.createChooser(intent, file.name))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    fun copyLink(ctx: Context, url: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("link", url))
    }

    /** Share a downloaded file with other apps (FileProvider, correct MIME). */
    fun shareFile(ctx: Context, file: File): Boolean {
        if (!file.exists()) return false
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        val intent = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            ctx.startActivity(Intent.createChooser(intent, file.name))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
