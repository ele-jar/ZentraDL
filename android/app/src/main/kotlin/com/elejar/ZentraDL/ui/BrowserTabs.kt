package com.elejar.ZentraDL.ui

/** One browser tab (URL + last title; web state itself isn't retained across switches). */
data class WebTab(val id: Long, val url: String, val title: String = "")

/** Pure tab operations (unit-tested). */
object BrowserTabs {
    fun open(tabs: List<WebTab>, url: String, nextId: Long): Pair<List<WebTab>, Long> {
        val tab = WebTab(nextId, url)
        return (tabs + tab) to nextId
    }

    /** Close [id]; selection falls back to the nearest left tab (or -1 when empty). */
    fun close(tabs: List<WebTab>, id: Long): Pair<List<WebTab>, Long> {
        val i = tabs.indexOfFirst { it.id == id }
        if (i < 0) return tabs to (tabs.firstOrNull()?.id ?: -1)
        val rest = tabs.filterNot { it.id == id }
        val sel = when {
            rest.isEmpty() -> -1L
            i - 1 >= 0 -> rest[i - 1].id
            else -> rest.first().id
        }
        return rest to sel
    }

    fun navigate(tabs: List<WebTab>, id: Long, url: String, title: String = ""): List<WebTab> =
        tabs.map { if (it.id == id) it.copy(url = url, title = title) else it }
}
