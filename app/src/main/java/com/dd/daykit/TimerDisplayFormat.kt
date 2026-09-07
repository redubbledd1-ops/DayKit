package com.dd.daykit

import com.dd.daykit.util.CountdownFormatter
import com.dd.daykit.util.CountdownMode

/**
 * Tray/status compact time — delegates to [CountdownFormatter] (timer, stopwatch notifications).
 */
object TimerDisplayFormat {

    fun formatMillisCompact(millis: Long): String =
        CountdownFormatter.format(millis, CountdownMode.COMPACT_TRAY)
}
