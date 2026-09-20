package com.elejar.ZentraDL.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Human formats (skill §8 microcopy; 1024-base, 1 decimal). Pure, unit-tested. */
object Format {
    fun bytes(b: Long): String {
        if (b < 0) return "Unknown size"
        if (b < 1024) return "$b B"
        val kb = b / 1024.0
        if (kb < 1024) return trimmed(kb) + " KB"
        val mb = kb / 1024.0
        if (mb < 1024) return trimmed(mb) + " MB"
        return trimmed(mb / 1024.0) + " GB"
    }

    private fun trimmed(v: Double): String =
        if (v >= 100) v.toInt().toString() else "%.1f".format(Locale.US, v)

    fun speed(bytesPerSecond: Long, stable: Boolean): String =
        if (!stable) "Calculating…" else bytes(bytesPerSecond) + "/s"

    fun eta(remainingBytes: Long, bytesPerSecond: Long): String {
        if (remainingBytes <= 0) return ""
        if (bytesPerSecond <= 0) return "—"
        val s = remainingBytes / bytesPerSecond
        return when {
            s < 60 -> "$s sec left"
            s < 3600 -> "${s / 60} min left"
            else -> "${s / 3600} hr left"
        }
    }

    /** "Today 21:40" / "Yesterday 21:40" / "12 May 21:40". */
    fun dayTime(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
        if (isSameDay(epochMs, nowMs)) return "Today $time"
        if (isSameDay(epochMs, nowMs - 86_400_000L)) return "Yesterday $time"
        val cal = Calendar.getInstance()
        cal.timeInMillis = epochMs
        val thisYear = Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.YEAR)
        val pattern = if (cal.get(Calendar.YEAR) == thisYear) "d MMM HH:mm" else "d MMM yyyy HH:mm"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(epochMs))
    }

    fun dayBucket(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        if (isSameDay(epochMs, nowMs)) return "Today"
        if (isSameDay(epochMs, nowMs - 86_400_000L)) return "Yesterday"
        val days = (startOfDay(nowMs) - startOfDay(epochMs)) / 86_400_000L
        if (days in 2..6) return "This week"
        return "Earlier"
    }

    private fun startOfDay(ms: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun isSameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }
}
