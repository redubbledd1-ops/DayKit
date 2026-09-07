package com.dd.daykit.util

/**
 * Shared countdown text for Agenda Alarm, Timer, and Stopwatch.
 *
 * All modes use **floor** (truncate toward zero) when splitting milliseconds into
 * hours, minutes, and seconds. Older tray code (e.g. [com.dd.daykit.AgendaAlarmNotificationCopy])
 * used `ceil` for minute/hour labels; that could show one unit *ahead* of in-app clocks and is intentionally
 * not used here.
 *
 * Stopwatch centiseconds stay in screen-local UI code only — not part of this formatter.
 */
enum class CountdownMode {
    /** Notification tray: `9:58`, `42:05`, `1:02:05` */
    COMPACT_TRAY,

    /** In-app screens: `09:58`, `42:05`, `1:02:05` */
    PRECISE_UI,

    /** In-app screens without seconds when ≥ 1 h: `19:02`; under 1 h: `42:05` */
    PRECISE_UI_NO_SECONDS,

    /** In-app banners: `9 min`, `42 min`, `1u` */
    SHORT_LABEL,
}

object CountdownFormatter {

    private const val MS_PER_SECOND = 1_000L
    private const val MS_PER_MINUTE = 60_000L
    private const val MS_PER_HOUR = 3_600_000L
    private const val TEN_MINUTES_MS = 600_000L

    fun format(remainingMs: Long, mode: CountdownMode): String {
        val ms = remainingMs.coerceAtLeast(0L)
        if (ms == 0L) {
            return zeroOutput(mode)
        }

        val totalSeconds = ms / MS_PER_SECOND
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when (mode) {
            CountdownMode.COMPACT_TRAY -> formatCompactTray(ms, hours, minutes, seconds)
            CountdownMode.PRECISE_UI -> formatPreciseUi(ms, hours, minutes, seconds)
            CountdownMode.PRECISE_UI_NO_SECONDS -> formatPreciseUiNoSeconds(ms, hours, minutes, seconds)
            CountdownMode.SHORT_LABEL -> formatShortLabel(ms, hours, minutes)
        }
    }

    private fun zeroOutput(mode: CountdownMode): String = when (mode) {
        CountdownMode.COMPACT_TRAY -> "0:00"
        CountdownMode.PRECISE_UI -> "00:00"
        CountdownMode.PRECISE_UI_NO_SECONDS -> "0:00"
        CountdownMode.SHORT_LABEL -> "0 min"
    }

    private fun formatCompactTray(ms: Long, hours: Long, minutes: Long, seconds: Long): String =
        when {
            ms >= MS_PER_HOUR -> "$hours:${pad2(minutes)}:${pad2(seconds)}"
            ms >= TEN_MINUTES_MS -> "$minutes:${pad2(seconds)}"
            else -> "$minutes:${pad2(seconds)}"
        }

    private fun formatPreciseUi(ms: Long, hours: Long, minutes: Long, seconds: Long): String =
        when {
            ms >= MS_PER_HOUR -> "$hours:${pad2(minutes)}:${pad2(seconds)}"
            ms >= TEN_MINUTES_MS -> "$minutes:${pad2(seconds)}"
            else -> "${pad2(minutes)}:${pad2(seconds)}"
        }

    private fun formatPreciseUiNoSeconds(ms: Long, hours: Long, minutes: Long, seconds: Long): String =
        when {
            ms >= MS_PER_HOUR -> "$hours:${pad2(minutes)}"
            else -> "$minutes:${pad2(seconds)}"
        }

    private fun formatShortLabel(ms: Long, hours: Long, minutes: Long): String =
        when {
            ms >= MS_PER_HOUR -> "${hours}u"
            else -> "${minutes} min"
        }

    private fun pad2(value: Long): String =
        value.toString().padStart(2, '0')
}
