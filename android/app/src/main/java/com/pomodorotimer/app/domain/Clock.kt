package com.pomodorotimer.app.domain

/**
 * Shared clock with a server-derived correction, ported from `src/time.ts`.
 *
 * Online devices apply the offset between the server and the local clock; offline devices keep the
 * last known correction so records made on different devices still line up on the same axis.
 */
object Clock {
    @Volatile
    private var correction: Long = 0L

    private var source: () -> Long = System::currentTimeMillis

    fun useMonotonicSource(elapsedRealtime: () -> Long) {
        val epoch = System.currentTimeMillis() - elapsedRealtime()
        source = { epoch + elapsedRealtime() }
    }

    fun uncorrectedNow(): Long = source()

    fun now(): Long = uncorrectedNow() + correction

    fun correction(): Long = correction

    fun setCorrection(value: Long) {
        if (value != Long.MIN_VALUE) correction = value
    }
}
