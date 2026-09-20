package com.elejar.ZentraDL.ui

/** Human error cards from raw failure text (skill §2 taxonomy). Pure, unit-tested. */
object ErrorTexts {

    data class Fix(val title: String, val message: String, val action: String)

    fun classify(raw: String?): Fix {
        val t = (raw ?: "").lowercase()
        return when {
            t.contains("unknownhost") || t.contains("unable to resolve host") ->
                Fix("Couldn't reach server", "A DNS lookup failed. Check the URL or your network.", "Retry")
            t.contains("timed out") || t.contains("timeout") || t.contains("sockettimeout") ->
                Fix("Server stopped responding", "The connection timed out. The server may be slow.", "Retry")
            listOf("404", "410", "403").any { t.contains(it) } ->
                Fix("Link expired or removed", "The server answered ${codeOf(t)}. Refresh the link.", "Retry")
            t.contains("429") || t.contains("503") || t.contains("500") ->
                Fix("Server is limiting us", "The server is busy or rate-limiting. Try again later.", "Retry")
            t.contains("no space") || t.contains("enospc") ->
                Fix("Not enough space", "Free up storage, then retry.", "Retry")
            t.contains("refused") || t.contains("unreachable") || t.contains("network") ->
                Fix("No connection", "Paused — no connection. Resumes automatically.", "Retry")
            raw.isNullOrBlank() ->
                Fix("Download failed", "Something went wrong.", "Retry")
            else ->
                Fix("Download failed", raw.take(160), "Retry")
        }
    }

    private fun codeOf(t: String): String =
        listOf("404", "410", "403", "429", "503").firstOrNull { t.contains(it) } ?: "with an error"
}
