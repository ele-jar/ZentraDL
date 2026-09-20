package com.elejar.ZentraDL.domain

/**
 * Puts a download into a category from filename/MIME/host (S1-lite, P3a).
 *
 * Extension match = confident; MIME-prefix match = confident; host hint
 * (e.g. play store, apk mirror) = confident; otherwise "other".
 * Pure Kotlin, unit-tested — full rule editor arrives in Phase 6.
 */
object Categorizer {

    data class Result(val categoryId: String, val confident: Boolean)

    private val byExt: Map<String, String> = buildMap {
        listOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "3gp", "ts", "m2ts").forEach { this[it] = "videos" }
        listOf("mp3", "flac", "wav", "ogg", "m4a", "aac", "opus", "wma").forEach { this[it] = "music" }
        listOf("jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "heic", "avif").forEach { this[it] = "images" }
        listOf("pdf", "epub", "doc", "docx", "txt", "md", "csv", "xls", "xlsx", "ppt", "pptx", "odt", "rtf").forEach {
            this[it] = "documents"
        }
        listOf("apk", "aab", "apks", "exe", "msi", "dmg", "pkg", "deb", "rpm").forEach { this[it] = "apps" }
        listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "cab").forEach { this[it] = "archives" }
    }

    private val appHosts = listOf("play.google.com", "f-droid.org", "apkmirror.com", "apkcombo.com")

    fun categorize(fileName: String, mime: String?, url: String?): Result {
        val ext = fileName.substringAfterLast('.', "").lowercase().substringBefore('?')
        byExt[ext]?.let { return Result(it, true) }
        when {
            mime?.startsWith("video/") == true -> return Result("videos", true)
            mime?.startsWith("audio/") == true -> return Result("music", true)
            mime?.startsWith("image/") == true -> return Result("images", true)
            mime == "application/pdf" || mime?.startsWith("text/") == true -> return Result("documents", true)
            mime == "application/vnd.android.package-archive" -> return Result("apps", true)
            mime == "application/zip" || mime?.contains("compressed") == true || mime == "application/x-rar" ->
                return Result("archives", true)
        }
        val host = url?.substringAfter("://", "")?.substringBefore('/', "")?.lowercase().orEmpty()
        if (appHosts.any { host == it || host.endsWith(".$it") }) return Result("apps", true)
        return Result("other", false)
    }
}
