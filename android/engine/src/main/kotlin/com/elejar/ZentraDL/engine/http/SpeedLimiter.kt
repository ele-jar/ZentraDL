package com.elejar.ZentraDL.engine.http

import java.util.concurrent.atomic.AtomicLong

/**
 * Global download throttle (Q2-lite, P3d). Token-bucket over a 1s window.
 *
 * [throttle] returns the millis the caller should wait — it never sleeps
 * itself, so unit tests assert the math without waiting. Limit 0 = unlimited.
 */
class SpeedLimiter(
    val limitBps: AtomicLong = AtomicLong(0),
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private var windowStart = nanoTime()
    private var windowBytes = 0L

    @Synchronized
    fun throttle(bytes: Long): Long {
        val limit = limitBps.get()
        if (limit <= 0 || bytes <= 0) return 0
        val now = nanoTime()
        if (now - windowStart >= 1_000_000_000L) {
            windowStart = now
            windowBytes = 0
        }
        val elapsed = (now - windowStart).toDouble() / 1e9
        val want = (windowBytes + bytes).toDouble() / limit
        windowBytes += bytes
        return if (want > elapsed) ((want - elapsed) * 1000).toLong() else 0
    }
}
