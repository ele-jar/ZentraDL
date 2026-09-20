package com.elejar.ZentraDL.domain

/**
 * Host blocklist for the browser ad-blocker (P5b). Compact bundled list;
 * custom URLs + allow-list arrive in P6. Match = host equals or ends with.
 */
object AdBlock {
    // Top ad/tracker/pop-up hosts (compact; full lists are a P6 update channel).
    private val blocked = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "ads.yahoo.com", "adsystem.com",
        "advertising.com", "adnxs.com", "adsrvr.org", "pubmatic.com",
        "rubiconproject.com", "openx.net", "criteo.com", "outbrain.com",
        "taboola.com", "revcontent.com", "popads.net", "popcash.net",
        "adcash.com", "exoclick.com", "juicyads.com", "ero-advertising.com",
        "trackersimulator.org", "analytics.google.com", "hotjar.com",
        "fullstory.com", "mixpanel.com", "segment.io", "amplitude.com",
        "crashlytics.com", "appsflyer.com", "adjust.com", "branch.io",
        "doubleverify.com", "moatads.com", "iasds01.com", "scorecardresearch.com",
        "quantserve.com", "chartbeat.com", "crazyegg.com", "mouseflow.com",
    )

    fun hostOf(url: String): String =
        url.substringAfter("://", "").substringBefore('/', "").substringBefore('?')
            .substringBefore('@').substringAfter('@').lowercase()

    fun shouldBlock(url: String): Boolean {
        val host = hostOf(url)
        if (host.isEmpty()) return false
        return blocked.any { host == it || host.endsWith(".$it") }
    }
}

/**
 * Media URL matcher for the sniffer (B3-lite). Extension allowlist + tiny
 * filter: playlists always qualify; direct files qualify (size unknown
 * pre-download, so no size gate — the sheet shows everything found).
 */
object MediaSniffer {
    private val playlistExt = setOf("m3u8", "mpd")
    private val mediaExt = setOf(
        "mp4", "webm", "mkv", "avi", "mov", "m4v", "3gp",
        "mp3", "flac", "wav", "ogg", "opus", "m4a", "aac",
    )

    enum class Kind { Playlist, Media }

    fun extOf(url: String): String =
        url.substringAfterLast('/', "").substringBefore('?').substringAfterLast('.', "").lowercase()

    fun kindOf(url: String): Kind? {
        val ext = extOf(url)
        return when {
            ext in playlistExt -> Kind.Playlist
            ext in mediaExt -> Kind.Media
            else -> null
        }
    }

    /** Sniffable = media-ish URL on a non-blocked host. */
    fun sniffable(url: String): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        if (AdBlock.shouldBlock(url)) return false
        return kindOf(url) != null
    }
}
