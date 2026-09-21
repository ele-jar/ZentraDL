package com.elejar.ZentraDL.domain.rules

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Smart rename (S3-lite): junk cleanup + token templates (unit-tested).
 * Templates: `{name}` `{date}` `{ext}`. Full rule editor arrives with R2.
 */
object RenameRules {

    /** "Show.Name.S01E02.1080p.[YTS].mkv" → "Show Name S01E02 1080p.mkv". */
    fun cleanup(name: String): String {
        val dot = name.lastIndexOf('.')
        val (base, ext) = if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
        var b = base.replace(Regex("[._]+"), " ")
        b = b.replace(Regex("\\[[^\\]]*\\]"), " ")
        b = b.replace(Regex("\\{[^}]*\\}"), " ")
        b = b.replace(Regex("\\s+"), " ").trim()
        return b + ext
    }

    fun apply(name: String, template: String, cleanup: Boolean, date: LocalDate = LocalDate.now()): String {
        val dot = name.lastIndexOf('.')
        val (base, ext) = if (dot > 0) name.substring(0, dot) to name.substring(dot + 1) else name to ""
        val clean = if (cleanup) cleanup(name).substringBeforeLast('.', base) else base
        return template
            .replace("{name}", clean)
            .replace("{date}", date.format(DateTimeFormatter.ISO_DATE))
            .replace("{ext}", ext)
            .trim()
            .let { if (ext.isNotEmpty() && !it.endsWith(".$ext")) "$it.$ext" else it }
    }
}
